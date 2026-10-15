package dev.gotrick.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GoRunSettingsEditor(project: Project) : SettingsEditor<GoRunConfiguration>() {
    private val operation = ComboBox(GoOperation.entries.toTypedArray())
    private val workingDirectory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Working directory", "Directory containing the Go package or module", project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    private val packages = JBTextField(40)
    private val flags = JBTextField(40)
    private val arguments = JBTextField(40)
    private val testName = JBTextField(40)
    private val environment = JBTextArea(5, 40)
    private val panel = JPanel(BorderLayout())

    init {
        packages.emptyText.text = ". or ./..."
        flags.emptyText.text = "For example: -race or -v -count=1"
        arguments.emptyText.text = "Arguments passed to your application"
        testName.emptyText.text = "Optional exact Test, Benchmark, Example or Fuzz function"
        environment.emptyText.text = "NAME=value (one per line); inherited environment is preserved"
        panel.add(FormBuilder.createFormBuilder()
            .addLabeledComponent("Operation:", operation)
            .addLabeledComponent("Working directory:", workingDirectory)
            .addLabeledComponent("Packages / files:", packages)
            .addLabeledComponent("Go tool flags:", flags)
            .addLabeledComponent("Program arguments:", arguments)
            .addLabeledComponent("Test function:", testName)
            .addLabeledComponent("Environment:", JBScrollPane(environment), true)
            .addComponent(JBLabel("<html>Use . for the current package or ./... for all packages.<br>" +
                "Quote arguments containing spaces. Commands run directly, with no shell.<br>" +
                "For Test, program arguments follow -args; test options belong in Go tool flags.</html>"))
            .panel, BorderLayout.NORTH)
        operation.addActionListener { updateEnabled() }
        updateEnabled()
    }

    private fun updateEnabled() {
        val selected = operation.selectedItem as GoOperation
        packages.isEnabled = selected != GoOperation.TIDY
        arguments.isEnabled = selected == GoOperation.RUN || selected == GoOperation.TEST
        testName.isEnabled = selected == GoOperation.TEST
    }

    override fun resetEditorFrom(config: GoRunConfiguration) {
        operation.selectedItem = config.operation
        workingDirectory.text = config.workingDirectory
        packages.text = config.packages
        flags.text = config.goFlags
        arguments.text = config.programArguments
        testName.text = config.testName
        environment.text = config.environmentText
        updateEnabled()
    }

    override fun applyEditorTo(config: GoRunConfiguration) {
        config.operation = operation.selectedItem as GoOperation
        config.workingDirectory = workingDirectory.text.trim()
        config.packages = packages.text.trim()
        config.goFlags = flags.text.trim()
        config.programArguments = if (arguments.isEnabled) arguments.text else ""
        config.testName = if (testName.isEnabled) testName.text.trim() else ""
        config.environmentText = environment.text
    }

    override fun createEditor(): JComponent = panel
    override fun disposeEditor() { workingDirectory.dispose() }
}
