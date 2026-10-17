package dev.gotrick.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.gotrick.toolchain.GoToolchain
import java.io.File

class GoSdkStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val root = project.basePath ?: return
        if (!File(root, "go.mod").isFile && !File(root, "go.work").isFile) return
        if (GoToolchain.hasGoSdk(project)) return
        GoSdkNotifications.getInstance(project).showMissingSdk()
    }
}
