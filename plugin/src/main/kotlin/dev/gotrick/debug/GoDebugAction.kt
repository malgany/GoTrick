package dev.gotrick.debug

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration

class GoDebugAction : DumbAwareAction("Debug Package", "Debug the current Go package with Delve", null) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) { e.presentation.isEnabledAndVisible = e.project?.basePath != null }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val directory = when {
            file?.extension == "go" -> file.parent.path
            file?.isDirectory == true -> file.path
            else -> project.basePath ?: return
        }
        val type = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.firstOrNull { it.id == "DAPConfiguration" } ?: return
        FileDocumentManager.getInstance().saveAllDocuments()
        val manager = RunManager.getInstance(project)
        val settings = manager.createConfiguration("GoTrick Debug: ${directory.substringAfterLast('/').substringAfterLast('\\')}", type.configurationFactories.first())
        val config = settings.configuration as DAPRunConfiguration
        config.serverId = "gotrick.delve"
        config.serverName = "GoTrick / Delve"
        config.workingDirectory = directory
        config.file = file?.path ?: "$directory/main.go"
        manager.setTemporaryConfiguration(settings)
        manager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
    }
}
