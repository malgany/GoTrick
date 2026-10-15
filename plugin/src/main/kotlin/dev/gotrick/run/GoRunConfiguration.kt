package dev.gotrick.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import dev.gotrick.toolchain.GoToolchain
import org.jdom.Element
import java.io.File
import java.nio.charset.StandardCharsets

class GoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
    var operation = GoOperation.RUN
    var workingDirectory = ""
    var packages = "."
    var goFlags = ""
    var programArguments = ""
    var environmentText = ""
    var testName = ""

    fun resolvedWorkingDirectory(): String = (workingDirectory.ifBlank { project.basePath.orEmpty() })
        .replace("\$PROJECT_DIR\$", project.basePath.orEmpty())
        .replace("\$ProjectFileDir\$", project.basePath.orEmpty())

    fun commandSpec() = GoCommandSpec(operation, packages, goFlags, programArguments, testName)

    override fun getConfigurationEditor(): SettingsEditor<out GoRunConfiguration> = GoRunSettingsEditor(project)

    override fun checkConfiguration() {
        if (!File(resolvedWorkingDirectory()).isDirectory)
            throw RuntimeConfigurationError("Choose an existing working directory.")
        try { GoCommandSpec.parseEnvironment(environmentText) }
        catch (error: IllegalArgumentException) { throw RuntimeConfigurationError(error.message.orEmpty()) }
        if (operation != GoOperation.TIDY && packages.isBlank())
            throw RuntimeConfigurationError("Specify a package, such as . or ./...")
        if (programArguments.isNotBlank() && operation != GoOperation.RUN && operation != GoOperation.TEST)
            throw RuntimeConfigurationError("Program arguments apply only to Run or Test.")
        val executable = GoToolchain.resolveGo(project)
        if (!File(executable).isFile)
            throw RuntimeConfigurationError("Go SDK not found. Use GoTrick | Configure Go SDK to select or download an SDK.")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        // The Go execution type handles Run. A debugger adapter can provide a separate runner.
        if (executor.id != DefaultRunExecutor.EXECUTOR_ID) return null
        return object : CommandLineState(environment) {
            init {
                consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project).apply {
                    addFilter(GoConsoleFilter(project, resolvedWorkingDirectory()))
                }
            }
            override fun startProcess(): ProcessHandler {
                val command = GeneralCommandLine(GoToolchain.resolveGo(project))
                    .withParameters(commandSpec().arguments())
                    .withWorkDirectory(resolvedWorkingDirectory())
                    .withCharset(StandardCharsets.UTF_8)
                    .withEnvironment(GoToolchain.environment(project))
                    .withEnvironment(GoCommandSpec.parseEnvironment(environmentText))
                return KillableColoredProcessHandler(command).also { ProcessTerminatedListener.attach(it) }
            }
        }
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        val state = element.getChild("gotrick") ?: return
        operation = runCatching { GoOperation.valueOf(state.getAttributeValue("operation") ?: "RUN") }.getOrDefault(GoOperation.RUN)
        workingDirectory = state.getAttributeValue("workingDirectory").orEmpty()
        packages = state.getAttributeValue("packages") ?: "."
        goFlags = state.getAttributeValue("goFlags").orEmpty()
        programArguments = state.getAttributeValue("programArguments").orEmpty()
        environmentText = state.getChildText("environment").orEmpty()
        testName = state.getAttributeValue("testName").orEmpty()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.removeChildren("gotrick")
        element.addContent(Element("gotrick").apply {
            setAttribute("operation", operation.name)
            setAttribute("workingDirectory", workingDirectory)
            setAttribute("packages", packages)
            setAttribute("goFlags", goFlags)
            setAttribute("programArguments", programArguments)
            setAttribute("testName", testName)
            addContent(Element("environment").setText(environmentText))
        })
    }
}
