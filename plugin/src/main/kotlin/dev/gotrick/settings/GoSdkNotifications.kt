package dev.gotrick.settings

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/** One actionable SDK warning per project, instead of an error for each LSP request. */
@Service(Service.Level.PROJECT)
class GoSdkNotifications(private val project: Project) {
    private var notified = false
    private var notification: Notification? = null

    @Synchronized fun showMissingSdk() {
        if (notified || project.isDisposed || ApplicationManager.getApplication().isUnitTestMode) return
        notified = true
        notification = NotificationGroupManager.getInstance().getNotificationGroup("GoTrick")
            .createNotification("Select a Go SDK", "Code analysis is waiting for a Go SDK. Select an installed SDK or download one for this project.", NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimple("Configure Go SDK") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GoSdkConfigurable::class.java)
            }).also { it.notify(project) }
    }

    @Synchronized fun clear() {
        notification?.expire()
        notification = null
        notified = false
    }

    companion object {
        fun getInstance(project: Project): GoSdkNotifications = project.getService(GoSdkNotifications::class.java)
    }
}
