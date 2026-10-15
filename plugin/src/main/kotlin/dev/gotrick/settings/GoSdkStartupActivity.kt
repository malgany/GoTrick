package dev.gotrick.settings

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.gotrick.toolchain.GoToolchain
import java.io.File

class GoSdkStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val root = project.basePath ?: return
        if (!File(root, "go.mod").isFile && !File(root, "go.work").isFile) return
        if (File(GoToolchain.resolveGo(project)).isFile) return
        NotificationGroupManager.getInstance().getNotificationGroup("GoTrick")
            .createNotification("Select a Go SDK", "Choose an installed SDK or download one to build, analyze and debug this project.", NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimple("Configure Go SDK") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GoSdkConfigurable::class.java)
            }).notify(project)
    }
}
