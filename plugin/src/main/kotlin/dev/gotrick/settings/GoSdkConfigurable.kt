package dev.gotrick.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import dev.gotrick.toolchain.GoSdkDiscovery
import dev.gotrick.toolchain.GoSdkDownload
import dev.gotrick.lsp.GoLanguageServerLifecycle
import java.awt.BorderLayout
import java.io.File
import java.nio.file.Path
import javax.swing.*

class GoSdkConfigurable(private val project: Project) : SearchableConfigurable {
    private var sdk: TextFieldWithBrowseButton? = null
    private var form: JPanel? = null
    override fun getId() = "dev.gotrick.sdk"
    override fun getDisplayName() = "GoTrick / Go SDK"
    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener("Select Go SDK", "Choose the SDK directory containing bin and src.", project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor())
            textField.columns = 45
        }
        sdk = field
        val detect = JButton("Detect installed SDK").apply { addActionListener {
            val candidates = GoSdkDiscovery.discover()
            if (candidates.isEmpty()) Messages.showInfoMessage(project, "No Go SDK found. Select a local installation or use Download SDK.", "Go SDK")
            else {
                val chosen = Messages.showEditableChooseDialog("Select the SDK for this project:", "Detected Go SDKs", null,
                    candidates.map { it.absolutePath }.toTypedArray(), candidates.first().absolutePath, null)
                if (chosen != null) field.text = chosen
            }
        } }
        val download = JButton("Download SDK…").apply { addActionListener { downloadSdk() } }
        val buttons = JPanel().apply { add(detect); add(download) }
        form = JPanel(BorderLayout()).apply { add(FormBuilder.createFormBuilder()
            .addLabeledComponent("Project Go SDK (GOROOT):", field)
            .addComponent(JLabel("<html>Leave blank to detect an installed SDK or use the application default.<br>" +
                "The SDK is separate from GoTrick. This choice applies only to this project.<br>" +
                "Code analysis restarts automatically after applying a different SDK.</html>"))
            .addComponent(buttons).panel, BorderLayout.NORTH) }
        return form!!
    }
    override fun isModified() = sdk != null && sdk!!.text.trim() != GoProjectSdkSettings.getInstance(project).home
    override fun reset() { sdk?.text = GoProjectSdkSettings.getInstance(project).home }
    override fun apply() {
        val home = sdk?.text?.trim() ?: return
        if (home.isNotEmpty() && !GoSdkDiscovery.isSdk(File(home))) throw ConfigurationException("Select a Go SDK containing bin/go.exe, src and pkg/tool.")
        val settings = GoProjectSdkSettings.getInstance(project)
        if (settings.home != home) {
            settings.home = home
            GoLanguageServerLifecycle.sdkChanged(project)
        }
    }
    override fun disposeUIResources() { sdk?.dispose(); sdk = null; form = null }

    private fun downloadSdk() {
        val targetField = sdk ?: return
        object : Task.Backgroundable(project, "Loading official Go SDK versions", true) {
            var releases = emptyList<GoSdkDownload.Release>()
            override fun run(indicator: ProgressIndicator) { releases = GoSdkDownload.releases(); indicator.checkCanceled() }
            override fun onThrowable(error: Throwable) { Messages.showErrorDialog(project, error.message ?: "Could not load Go versions", "Go SDK") }
            override fun onSuccess() {
                if (sdk !== targetField || project.isDisposed) return
                if (releases.isEmpty()) { Messages.showInfoMessage(project, "No matching SDK downloads were found.", "Go SDK"); return }
                val dialog = DownloadDialog(project, releases)
                if (!dialog.showAndGet()) return
                val release = dialog.selectedRelease
                val destination = Path.of(dialog.destination)
                object : Task.Backgroundable(project, "Downloading ${release.version}", true) {
                    var installed: Path? = null
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = false
                        installed = GoSdkDownload.install(release, destination, { indicator.checkCanceled() }, { indicator.fraction = it })
                    }
                    override fun onThrowable(error: Throwable) { Messages.showErrorDialog(project, error.message ?: "SDK installation failed", "Go SDK") }
                    override fun onSuccess() {
                        if (sdk === targetField && !project.isDisposed) targetField.text = installed.toString()
                    }
                }.queue()
            }
        }.queue()
    }

    private class DownloadDialog(project: Project, releases: List<GoSdkDownload.Release>) : DialogWrapper(project) {
        private val versions = JComboBox(releases.toTypedArray())
        private val directory = TextFieldWithBrowseButton().apply {
            text = File(System.getProperty("user.home"), "sdk").absolutePath
            addBrowseFolderListener("SDK download directory", "Each version gets a separate directory.", project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor())
        }
        val selectedRelease get() = versions.selectedItem as GoSdkDownload.Release
        val destination get() = directory.text.trim()
        init { title = "Download Go SDK"; setOKButtonText("Download"); init() }
        override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent("Version:", versions).addLabeledComponent("Install under:", directory)
            .addComponent(JLabel("Downloaded from go.dev and verified using its SHA-256 checksum.")).panel
        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? =
            if (destination.isBlank() || runCatching { Path.of(destination).isAbsolute }.getOrDefault(false).not())
                com.intellij.openapi.ui.ValidationInfo("Choose an absolute SDK download directory.", directory) else null
        override fun dispose() { directory.dispose(); super.dispose() }
    }
}
