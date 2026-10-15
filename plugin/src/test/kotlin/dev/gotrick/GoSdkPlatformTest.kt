package dev.gotrick

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gotrick.settings.GoProjectSdkSettings
import dev.gotrick.settings.GoSdkConfigurable
import dev.gotrick.settings.GoTrickSettings
import dev.gotrick.toolchain.GoToolchain
import java.io.File

class GoSdkPlatformTest : BasePlatformTestCase() {
    fun testProjectSdkOverridesApplicationDefaultAndEnvironment() {
        val settings = GoProjectSdkSettings.getInstance(project)
        val global = GoTrickSettings.getInstance()
        val previous = global.goExecutable
        val sdk = File(System.getProperty("gotrick.tools"), "go").absolutePath
        try {
            global.goExecutable = "C:/nonexistent/other-go.exe"
            settings.home = sdk
            val executable = GoToolchain.resolveGo(project)
            assertEquals(File(sdk, "bin/go.exe").absolutePath, executable)
            assertEquals(sdk, GoToolchain.environment(project)["GOROOT"])
            assertTrue(GoToolchain.environment(project)["PATH"]!!.startsWith(File(sdk, "bin").path))
            val restored = GoProjectSdkSettings()
            restored.loadState(settings.state.copy())
            assertEquals(sdk, restored.home)
            val ui = GoSdkConfigurable(project)
            try { assertNotNull(ui.createComponent()); ui.reset(); assertFalse(ui.isModified); ui.apply() }
            finally { ui.disposeUIResources() }
        } finally { settings.home = ""; global.goExecutable = previous }
    }

    fun testInvalidProjectSdkDoesNotSilentlyFallbackToDeveloperSdk() {
        val settings = GoProjectSdkSettings.getInstance(project)
        try {
            settings.home = "C:/missing/gotrick-test-sdk"
            assertTrue(GoToolchain.resolveGo(project).replace('\\', '/').contains("C:/missing/gotrick-test-sdk/bin/go.exe"))
        } finally { settings.home = "" }
    }
}
