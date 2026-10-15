package dev.gotrick.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import org.jdom.Element
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the installed extension, settings serialization and actual Delve transport. */
class DebugPlatformIntegrationTest : BasePlatformTestCase() {
    override fun isWriteActionRequired() = false

    fun testRegisteredAdapterPreservesSettingsAndStartsDelve() {
        dev.gotrick.settings.GoProjectSdkSettings.getInstance(project).home =
            java.io.File(System.getProperty("gotrick.tools"), "go").absolutePath
        val temporary = Files.createTempDirectory("gotrick-dap-platform-")
        try {
            Files.writeString(temporary.resolve("go.mod"), "module example.org/gotrick-dap\n\ngo 1.23\n")
            Files.writeString(temporary.resolve("main_test.go"), "package main\n")
            val manager = RunManager.getInstance(project)
            val type = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.single { it.id == "DAPConfiguration" }
            val settings = manager.createConfiguration("Go debug platform check", type.configurationFactories.first())
            val config = settings.configuration as DAPRunConfiguration
            config.serverId = "gotrick.delve"
            config.serverName = "GoTrick / Delve"
            config.workingDirectory = temporary.toString()
            config.file = temporary.resolve("main_test.go").toString()
            val arguments = listOf("-test.run", "^TestSum$", "literal & value")
            val environment = mapOf("GOTRICK_DEBUG_VALUE" to "a=b & c")
            config.launchConfiguration = GoDebugParameters.serialize(mapOf("mode" to "test", "args" to arguments,
                "env" to environment, "buildFlags" to "-tags=integration", "stopOnEntry" to true))
            @Suppress("UNCHECKED_CAST")
            val editor = config.configurationEditor as SettingsEditor<DAPRunConfiguration>
            assertTrue("Registered adapter must provide our Go form", editor is GoDebugSettingsEditor)
            try {
                assertNotNull(editor.component)
                editor.resetFrom(config)
                editor.applyTo(config)
            } finally { Disposer.dispose(editor) }

            val persisted = Element("configuration")
            config.writeExternal(persisted)
            val restoredSettings = manager.createConfiguration("Restored Go debug", type.configurationFactories.first())
            val restored = restoredSettings.configuration as DAPRunConfiguration
            restored.readExternal(persisted)
            val executor = DefaultDebugExecutor.getDebugExecutorInstance()
            val execution = ExecutionEnvironmentBuilder.create(executor, restoredSettings).build()
            val state = restored.getState(executor, execution) as DAPCommandLineState
            val descriptor = state.serverDescriptor
            assertEquals("gotrick.delve", descriptor.id)
            assertEquals(DebugMode.LAUNCH, state.debugMode)
            val parameters = descriptor.dapParameters
            assertEquals("test", parameters["mode"])
            assertEquals(arguments, parameters["args"])
            assertEquals(environment, parameters["env"])
            assertEquals("-tags=integration", parameters["buildFlags"])
            assertEquals(true, parameters["stopOnEntry"])
            assertEquals(temporary.toString(), parameters["program"])
            assertEquals(temporary.toString(), parameters["cwd"])
            assertEquals("launch", parameters["request"])

            PlatformTestUtil.waitForFuture(CompletableFuture.runAsync {
                startAndInitialize(descriptor)
            }, 30_000)
        } finally {
            Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun startAndInitialize(descriptor: DebugAdapterDescriptor) {
        val output = StringBuffer()
        val ready = CountDownLatch(1)
        val handler = descriptor.startServer()
        try {
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                    if (Regex("DAP server listening at: [^\\r\\n]+:\\d+\\r?\\n").containsMatchIn(output)) ready.countDown()
                }
            })
            if (!handler.isStartNotified) handler.startNotify()
            assertTrue("Delve did not become ready: $output", ready.await(15, TimeUnit.SECONDS))
            val line = output.toString().lineSequence().first { it.startsWith("DAP server listening at:") }
            val endpoint = descriptor.getServerReadyConfig(DebugMode.LAUNCH).debugServerReadyPattern!!.extract(line)
            assertTrue(endpoint.matches())
            val port = endpoint.port()!!.toInt()
            assertEquals("Port metadata must match Delve's actual listener", port, DebugAdapterDescriptor.getServerPort(handler) ?: -1)
            Socket().use { socket ->
                socket.soTimeout = 5_000
                socket.connect(InetSocketAddress(endpoint.address(), port), 5_000)
                val message = """{"seq":1,"type":"request","command":"initialize","arguments":{"adapterID":"go","linesStartAt1":true,"columnsStartAt1":true,"pathFormat":"path"}}""".toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("Content-Length: ${message.size}\r\n\r\n".toByteArray(Charsets.US_ASCII)); write(message); flush()
                }
                val input = BufferedInputStream(socket.getInputStream())
                var response: JsonObject? = null
                repeat(8) {
                    if (response == null) {
                        val item = readMessage(input)
                        if (item.get("type")?.asString == "response" && item.get("request_seq")?.asInt == 1) response = item
                    }
                }
                assertNotNull("Delve initialize response", response)
                assertTrue(response.toString(), response!!.get("success").asBoolean)
                assertEquals("initialize", response!!.get("command").asString)
            }
        } finally {
            handler.destroyProcess()
            assertTrue("Delve process must terminate before fixture cleanup", handler.waitFor(5_000))
        }
    }

    private fun readMessage(input: BufferedInputStream): JsonObject {
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            val next = input.read()
            check(next >= 0) { "Delve closed its DAP socket before returning a response" }
            header.append(next.toChar())
            check(header.length < 8_192) { "Invalid DAP frame header" }
        }
        val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE).find(header)!!.groupValues[1].toInt()
        check(length in 1..1_048_576) { "Unexpected DAP message length" }
        return JsonParser.parseString(String(input.readNBytes(length), Charsets.UTF_8)).asJsonObject
    }
}
