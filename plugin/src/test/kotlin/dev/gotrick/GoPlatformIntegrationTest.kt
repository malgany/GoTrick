package dev.gotrick

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.dap.definitions.extension.DebugAdapterServerExtensionPointBean
import dev.gotrick.debug.GoDebugAdapterFactory
import dev.gotrick.language.GoFileType
import dev.gotrick.language.GoModFileType
import dev.gotrick.language.GoModLanguage
import dev.gotrick.language.GoTrickLanguage
import dev.gotrick.language.GoWorkFileType
import dev.gotrick.language.GoWorkLanguage
import dev.gotrick.project.GoNewProjectAction
import dev.gotrick.settings.GoTrickSettings
import dev.gotrick.lsp.GoLanguageServerFactory

/** Verifies registration and platform wiring, beyond the standalone lexer tests. */
class GoPlatformIntegrationTest : BasePlatformTestCase() {
    fun testGoFileTypeAndPsiAreRegistered() {
        // These tests assert registration/PSI, so they need not open editors and start
        // gopls. On Windows a still-starting server can hold the fixture's cwd at teardown.
        val file = myFixture.addFileToProject("main.go", "package main\nfunc main() { println(42) }\n")
        assertSame(GoFileType, file.fileType)
        assertSame(GoTrickLanguage, file.language)
        assertEquals("package main\nfunc main() { println(42) }\n", file.node.text)
        assertNotNull(file.firstChild)
    }

    fun testGoModuleFileTypeIsRegistered() {
        val file = myFixture.addFileToProject("go.mod", "module example.com/test\n\ngo 1.22\n")
        assertSame(GoModLanguage, file.language)
        assertSame(GoModFileType, file.fileType)
        assertEquals("module example.com/test\n\ngo 1.22\n", file.node.text)
        val work = myFixture.addFileToProject("go.work", "go 1.22\n\nuse .\n")
        assertSame(GoWorkLanguage, work.language)
        assertSame(GoWorkFileType, work.fileType)
        assertNotNull(work.firstChild)
    }

    fun testSettingsAndLanguageServerFactoryCanBeConstructed() {
        assertNotNull(GoTrickSettings.getInstance())
        val factory = GoLanguageServerFactory()
        assertNotNull(factory.createConnectionProvider(project))
    }

    fun testGoActionsAndDebugAdapterAreRegistered() {
        val actions = ActionManager.getInstance()
        assertTrue(actions.getAction("GoTrick.NewProject") is GoNewProjectAction)
        for (id in listOf("GoTrick.Menu", "GoTrick.Run", "GoTrick.Debug", "GoTrick.Build", "GoTrick.Test", "GoTrick.Vet", "GoTrick.Tidy")) {
            assertNotNull("Missing registered action $id", actions.getAction(id))
        }
        val debug = DebugAdapterServerExtensionPointBean.EP_NAME.extensionList.single { it.id == "gotrick.delve" }
        assertTrue(debug.instance is GoDebugAdapterFactory)
    }

    fun testGoplsMappingsAreRegisteredAgainstOurFileTypes() {
        val registry = LanguageServersRegistry.getInstance()
        val server = registry.getServerDefinition("gotrick.gopls")
        assertNotNull("gopls server registration", server)
        assertEquals("go", server!!.getLanguageId(GoTrickLanguage))
        assertEquals("go.mod", server.getLanguageId(GoModLanguage))
        assertEquals("go.work", server.getLanguageId(GoWorkLanguage))
        val file = myFixture.addFileToProject("mapped.go", "package main\n")
        assertTrue(registry.isFileSupported(file))
        assertEquals("go", server.getLanguageId(file))
    }
}
