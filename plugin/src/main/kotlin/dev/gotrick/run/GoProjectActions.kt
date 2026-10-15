package dev.gotrick.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.util.execution.ParametersListUtil
import dev.gotrick.toolchain.GoToolchain
import java.nio.file.Path

abstract class GoProjectAction(private val operation: GoOperation, private val currentPackage: Boolean = false) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.basePath != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val base = project.basePath ?: return
        val selected = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val directory = if (currentPackage) selected?.let { if (it.isDirectory) it.path else it.parent?.path } ?: base else base
        FileDocumentManager.getInstance().saveAllDocuments()
        fun launch(scope: GoProjectScope) {
            if (project.isDisposed) return
            val manager = RunManager.getInstance(project)
            val settings = manager.createConfiguration("${operation.label}: ${if (currentPackage) scope.directory.fileName else project.name}",
                GoRunConfigurationType.getInstance().configurationFactories[0])
            val configuration = settings.configuration as GoRunConfiguration
            configuration.operation = operation
            configuration.workingDirectory = scope.directory.toString()
            configuration.packages = ParametersListUtil.join(scope.packages)
            configuration.goFlags = if (operation == GoOperation.TEST) "-v" else ""
            manager.setTemporaryConfiguration(settings)
            manager.selectedConfiguration = settings
            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
        if (currentPackage) { launch(GoProjectScope(Path.of(directory), listOf("."))); return }
        val selectedPath = selected?.path?.let(Path::of)
        val selectedProject = project
        object : Task.Backgroundable(project, "Preparing Go ${operation.command}", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val scope = GoWorkspaceResolver.resolve(GoToolchain.resolveGo(selectedProject), Path.of(base), operation, selectedPath,
                        GoToolchain.environment(selectedProject)) { indicator.checkCanceled() }
                    ApplicationManager.getApplication().invokeLater { launch(scope) }
                } catch (canceled: ProcessCanceledException) { throw canceled }
                catch (error: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!selectedProject.isDisposed) Messages.showErrorDialog(selectedProject, error.message ?: "Could not prepare Go command.", "GoTrick")
                    }
                }
            }
        }.queue()
    }
}

class GoRunPackageAction : GoProjectAction(GoOperation.RUN, true)
class GoTestProjectAction : GoProjectAction(GoOperation.TEST)
class GoBuildProjectAction : GoProjectAction(GoOperation.BUILD)
class GoVetProjectAction : GoProjectAction(GoOperation.VET)
class GoModTidyAction : GoProjectAction(GoOperation.TIDY)
