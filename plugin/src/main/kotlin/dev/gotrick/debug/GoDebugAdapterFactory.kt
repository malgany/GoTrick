package dev.gotrick.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.NetUtils
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig
import dev.gotrick.language.GoFileType
import dev.gotrick.toolchain.GoToolchain
import java.io.File

class GoDebugAdapterFactory : DebugAdapterDescriptorFactory() {
    override fun createDebugAdapterDescriptor(options: RunConfigurationOptions, environment: ExecutionEnvironment): DebugAdapterDescriptor =
        GoDebugAdapter(options, environment, serverDefinition)

    override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean = file.extension == "go"
    override fun canRun(executorId: String): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID
    override fun getConfigurationEditor(project: Project): SettingsEditor<out RunConfiguration> = GoDebugSettingsEditor(project)
}

private class GoDebugAdapter(
    options: RunConfigurationOptions,
    environment: ExecutionEnvironment,
    definition: DebugAdapterServerDefinition,
) : DebugAdapterDescriptor(options, environment, definition) {
    override fun createClient(
        debugProcess: com.redhat.devtools.lsp4ij.dap.DAPDebugProcess,
        dapParameters: MutableMap<String, Any>, isDebug: Boolean, debugMode: DebugMode,
        serverTrace: com.redhat.devtools.lsp4ij.settings.ServerTrace,
        parentClient: com.redhat.devtools.lsp4ij.dap.client.DAPClient?
    ): com.redhat.devtools.lsp4ij.dap.client.DAPClient =
        GoDebugClient(debugProcess, dapParameters, isDebug, debugMode, serverTrace, parentClient)

    private val config get() = options as DAPRunConfigurationOptions
    private val directory get() = (config.workingDirectory?.takeIf { it.isNotBlank() }
        ?: environment.project.basePath ?: System.getProperty("user.home"))
        .replace("\$PROJECT_DIR\$", environment.project.basePath.orEmpty())
        .replace("\$ProjectFileDir\$", environment.project.basePath.orEmpty())

    override fun startServer(): ProcessHandler {
        if (!File(GoToolchain.resolveGo(environment.project)).isFile)
            throw ExecutionException("Go SDK not found. Use GoTrick | Configure Go SDK to select or download an SDK.")
        val port = NetUtils.findAvailableSocketPort()
        if (!File(directory).isDirectory) throw ExecutionException("Go debug working directory does not exist: $directory")
        val command = GeneralCommandLine(GoToolchain.resolveDelve(environment.project), "dap", "--listen=127.0.0.1:$port")
            .withWorkDirectory(directory)
            .withCharset(Charsets.UTF_8)
            .withEnvironment(GoToolchain.environment(environment.project))
        command.putUserData(SERVER_PORT, port)
        return startServer(command)
    }

    override fun getDapParameters(): MutableMap<String, Any> = GoDebugParameters.parse(config.launchConfiguration).apply {
        put("type", "go"); put("request", "launch"); put("name", "GoTrick Debug")
        putIfAbsent("mode", if (config.file?.endsWith("_test.go") == true) "test" else "debug")
        put("program", directory); put("cwd", directory)
        putIfAbsent("stopOnEntry", false)
    }

    override fun getDebugMode(): DebugMode = DebugMode.LAUNCH

    override fun getServerReadyConfig(debugMode: DebugMode): ServerReadyConfig = ServerReadyConfig(GoDebugParameters.READY_PATTERN)
    override fun getFileType(): FileType = GoFileType
    override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean = file.extension == "go"
}
