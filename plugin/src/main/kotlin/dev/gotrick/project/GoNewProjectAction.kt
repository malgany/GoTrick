package dev.gotrick.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class GoNewProjectAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val dialog = GoNewProjectDialog(event.project)
        if (!dialog.showAndGet()) return
        val parent = dialog.parentPath
        val name = dialog.projectName
        val module = dialog.modulePath
        ProgressManager.getInstance().run(object : Task.Backgroundable(event.project, "Creating Go project", false) {
            private var createdProject: Path? = null
            override fun run(indicator: ProgressIndicator) {
                createdProject = GoProjectCreator.create(parent, name, module)
            }

            override fun onSuccess() {
                val directory = createdProject ?: return
                try {
                    val project = ProjectUtil.openOrImport(directory,
                        OpenProjectTask.build().withForceOpenInNewFrame(true).asNewProject())
                    if (project == null) {
                        Messages.showInfoMessage("Project files were created at $directory. Use File > Open to open this folder.", "Go Project Created")
                    }
                } catch (error: Exception) {
                    Messages.showErrorDialog("Project files were created at $directory, but the IDE could not open them.\n\n${error.message.orEmpty()}", "Open Go Project")
                }
            }

            override fun onThrowable(error: Throwable) {
                val message = if (error is FileAlreadyExistsException) {
                    "A file or folder already exists at ${error.file}. Choose a different project name."
                } else error.message ?: "The project could not be created. Check the selected folder and its permissions."
                Messages.showErrorDialog(message, "Create Go Project")
            }
        })
    }
}

private class GoNewProjectDialog(project: Project?) : DialogWrapper(project) {
    private val nameField = JBTextField("hello-go", 32)
    private val locationField = TextFieldWithBrowseButton()
    private val moduleField = JBTextField("example/hello-go", 32)
    private var suggestedModule = "example/hello-go"

    val parentPath: Path get() = Path.of(locationField.text.trim())
    val projectName: String get() = nameField.text
    val modulePath: String get() = moduleField.text

    init {
        title = "New Go Project"
        setOKButtonText("Create")
        locationField.text = project?.basePath?.let { Path.of(it).parent?.toString() }
            ?: System.getProperty("user.home")
        locationField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir())
        Disposer.register(disposable, locationField)
        nameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                val slug = nameField.text.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifEmpty { "app" }
                val nextSuggestion = "example/$slug"
                if (moduleField.text == suggestedModule) moduleField.text = nextSuggestion
                suggestedModule = nextSuggestion
            }
        })
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).apply {
        fun row(number: Int, label: String, field: JComponent) {
            add(JLabel(label).apply { labelFor = field }, GridBagConstraints().apply {
                gridx = 0; gridy = number; anchor = GridBagConstraints.WEST
                insets = Insets(6, 0, 6, 14)
            })
            add(field, GridBagConstraints().apply {
                gridx = 1; gridy = number; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = Insets(6, 0, 6, 0)
            })
        }
        row(0, "Project name:", nameField)
        row(1, "Parent folder:", locationField)
        row(2, "Module path:", moduleField)
        add(JLabel("<html>Creates a Go module, a runnable program and a unit test.<br>Module example: example.com/team/app</html>"), GridBagConstraints().apply {
            gridx = 0; gridy = 3; gridwidth = 2; anchor = GridBagConstraints.WEST
            insets = Insets(12, 0, 4, 0)
        })
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun doValidate(): ValidationInfo? {
        GoProjectCreator.validateFolderName(projectName)?.let { return ValidationInfo(it, nameField) }
        GoProjectCreator.validateModulePath(modulePath)?.let { return ValidationInfo(it, moduleField) }
        try {
            if (locationField.text.isBlank()) return ValidationInfo("Choose an existing parent folder.", locationField)
            val target = GoProjectCreator.target(parentPath, projectName)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return ValidationInfo("This project folder already exists. Choose another project name.", nameField)
            }
        } catch (error: Exception) {
            return ValidationInfo(error.message ?: "Choose a valid parent folder.", locationField)
        }
        return null
    }
}
