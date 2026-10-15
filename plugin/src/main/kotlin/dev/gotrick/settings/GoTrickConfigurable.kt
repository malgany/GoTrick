package dev.gotrick.settings

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import dev.gotrick.toolchain.GoToolchain
import java.awt.BorderLayout
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class GoTrickConfigurable : SearchableConfigurable {
    private var form: JPanel? = null
    private var go: TextFieldWithBrowseButton? = null
    private var gopls: TextFieldWithBrowseButton? = null
    private var delve: TextFieldWithBrowseButton? = null
    private var status: JBLabel? = null
    private var check: JButton? = null

    override fun getId() = "dev.gotrick.settings"
    override fun getDisplayName() = "GoTrick / Go"
    override fun getPreferredFocusedComponent(): JComponent? = go?.textField

    override fun createComponent(): JComponent {
        fun path(title: String) = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(title, "Select the executable. Leave blank for automatic discovery.", null,
                FileChooserDescriptorFactory.createSingleFileDescriptor())
            textField.columns = 45
            textField.toolTipText = "Automatic: bundled tools, then PATH"
        }
        go = path("Go SDK executable")
        gopls = path("gopls language server")
        delve = path("Delve debugger")
        status = JBLabel(" ")
        check = JButton("Check tools").apply { addActionListener { checkTools() } }
        val fields = FormBuilder.createFormBuilder()
            .addLabeledComponent("Default Go executable (optional):", go!!)
            .addLabeledComponent("gopls executable:", gopls!!)
            .addLabeledComponent("Delve executable:", delve!!)
            .addComponent(JBLabel("<html>Select a project SDK in Tools / GoTrick / Go SDK; it overrides this default.<br>" +
                "Go is detected separately. gopls and Delve use bundled tools, then PATH.<br>" +
                "After changing gopls, restart its server from the Language Servers tool window.</html>"))
            .addVerticalGap(12)
            .addComponent(check!!)
            .addComponent(status!!)
            .panel
        form = JPanel(BorderLayout()).apply { add(fields, BorderLayout.NORTH) }
        return form!!
    }

    override fun isModified(): Boolean {
        if (form == null) return false
        val state = GoTrickSettings.getInstance()
        return go!!.text.trim() != state.goExecutable || gopls!!.text.trim() != state.goplsExecutable ||
            delve!!.text.trim() != state.delveExecutable
    }

    override fun apply() {
        if (form == null) return
        for ((label, field) in listOf("Go" to go!!, "gopls" to gopls!!, "Delve" to delve!!)) {
            val value = field.text.trim().removeSurrounding("\"")
            if (value.isNotEmpty() && (value.contains('/') || value.contains('\\')) && !File(value).isFile)
                throw ConfigurationException("$label executable does not exist: $value")
        }
        GoTrickSettings.getInstance().apply {
            goExecutable = go!!.text.trim().removeSurrounding("\"")
            goplsExecutable = gopls!!.text.trim().removeSurrounding("\"")
            delveExecutable = delve!!.text.trim().removeSurrounding("\"")
        }
    }

    override fun reset() {
        val state = GoTrickSettings.getInstance()
        go?.text = state.goExecutable
        gopls?.text = state.goplsExecutable
        delve?.text = state.delveExecutable
    }

    private fun checkTools() {
        val label = status ?: return
        val button = check ?: return
        val checks = listOf("Go" to (GoToolchain.resolve(go!!.text, "go") to listOf("version")),
            "gopls" to (GoToolchain.resolve(gopls!!.text, "gopls") to listOf("version")),
            "Delve" to (GoToolchain.resolve(delve!!.text, "dlv") to listOf("version")))
        button.isEnabled = false
        label.text = "Checking tools…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val lines = checks.map { (name, command) ->
                val result = runCatching {
                    val output = CapturingProcessHandler(GeneralCommandLine(command.first).withParameters(command.second)
                        .withEnvironment(GoToolchain.environment(goExecutable = checks[0].second.first))).runProcess(8_000)
                    if (output.isTimeout) "timed out" else if (output.exitCode == 0)
                        output.stdout.lineSequence().firstOrNull { it.isNotBlank() } ?: "available"
                    else output.stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: "exit ${output.exitCode}"
                }.getOrElse { it.message ?: "not available" }
                "$name: ${escape(result)}"
            }
            ApplicationManager.getApplication().invokeLater {
                if (status === label) { label.text = "<html>${lines.joinToString("<br>")}</html>"; button.isEnabled = true }
            }
        }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    override fun disposeUIResources() {
        go?.dispose(); gopls?.dispose(); delve?.dispose()
        form = null; go = null; gopls = null; delve = null; status = null; check = null
    }
}
