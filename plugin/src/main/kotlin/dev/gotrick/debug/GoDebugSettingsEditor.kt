package dev.gotrick.debug

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.*
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration
import dev.gotrick.run.GoCommandSpec
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

internal object GoDebugParameters {
    const val READY_PATTERN = "DAP server listening at: \${address}:\${port}"
    private val gson = Gson()
    fun parse(json: String?): MutableMap<String, Any> = if (json.isNullOrBlank()) linkedMapOf() else
        gson.fromJson(json, object : TypeToken<LinkedHashMap<String, Any>>() {}.type) ?: linkedMapOf()
    fun serialize(values: Map<String, Any>): String = gson.toJson(values)
}

/** A Go launch form; transport commands and unrelated adapter options stay internal. */
class GoDebugSettingsEditor(private val project: Project) : SettingsEditor<DAPRunConfiguration>() {
    private val directory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Go package directory", "Choose a Go package to debug", project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    private val tests = JBCheckBox("Debug package tests")
    private val stop = JBCheckBox("Stop on entry")
    private val arguments = JBTextField(40)
    private val flags = JBTextField(40)
    private val variables = JBTextArea(5, 40)
    private val panel = JPanel(BorderLayout()).apply {
        arguments.emptyText.text = "Application arguments, or test flags such as -test.run ^TestSum$"
        flags.emptyText.text = "Optional go build flags, for example -tags=integration"
        variables.emptyText.text = "NAME=value (one per line)"
        add(FormBuilder.createFormBuilder()
            .addLabeledComponent("Package directory:", directory)
            .addComponent(tests)
            .addLabeledComponent("Program arguments:", arguments)
            .addLabeledComponent("Build flags:", flags)
            .addLabeledComponent("Environment:", JBScrollPane(variables), true)
            .addComponent(stop)
            .addComponent(JBLabel("<html>Delve builds the package with debugging enabled.<br>" +
                "Set breakpoints in the editor, then use Debug, Step Over, Step Into and Evaluate Expression.</html>"))
            .panel, BorderLayout.NORTH)
    }

    override fun resetEditorFrom(config: DAPRunConfiguration) {
        val values = GoDebugParameters.parse(config.launchConfiguration)
        directory.text = config.workingDirectory?.takeIf { it.isNotBlank() } ?: project.basePath.orEmpty()
        tests.isSelected = values["mode"] == "test" || (values["mode"] == null && config.file?.endsWith("_test.go") == true)
        stop.isSelected = values["stopOnEntry"] == true
        arguments.text = ParametersListUtil.join((values["args"] as? List<*>)?.map { it.toString() }.orEmpty())
        flags.text = values["buildFlags"]?.toString().orEmpty()
        variables.text = (values["env"] as? Map<*, *>)?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()
    }

    override fun applyEditorTo(config: DAPRunConfiguration) {
        val path = directory.text.trim().replace("\$PROJECT_DIR\$", project.basePath.orEmpty())
            .replace("\$ProjectFileDir\$", project.basePath.orEmpty())
        if (!File(path).isDirectory) throw ConfigurationException("Choose an existing Go package directory.")
        val env = try { GoCommandSpec.parseEnvironment(variables.text) }
            catch (error: IllegalArgumentException) { throw ConfigurationException(error.message.orEmpty()) }
        config.workingDirectory = path
        config.serverId = "gotrick.delve"
        config.serverName = "GoTrick / Delve"
        config.debugMode = DebugMode.LAUNCH
        config.envData = EnvironmentVariablesData.create(env, true)
        config.launchConfiguration = GoDebugParameters.serialize(linkedMapOf(
            "mode" to if (tests.isSelected) "test" else "debug",
            "args" to ParametersListUtil.parse(arguments.text), "env" to env,
            "buildFlags" to flags.text, "stopOnEntry" to stop.isSelected,
        ))
    }

    override fun createEditor(): JComponent = panel
    override fun disposeEditor() { directory.dispose() }
}
