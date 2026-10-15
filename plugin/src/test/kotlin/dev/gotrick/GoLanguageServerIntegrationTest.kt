package dev.gotrick

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

/** Exercises the installed LSP4IJ registration, GoConnection provider and a real gopls process. */
class GoLanguageServerIntegrationTest : HeavyPlatformTestCase() {
    private var previousAsyncProgress: String? = null

    // A light project may be reused after its original test temp directory is deleted.
    // A server process needs a real, isolated project directory for its entire lifetime.
    override fun isCreateDirectoryBasedProject(): Boolean = true
    override fun runInDispatchThread(): Boolean = true

    override fun setUp() {
        super.setUp()
        dev.gotrick.settings.GoProjectSdkSettings.getInstance(project).home =
            java.io.File(System.getProperty("gotrick.tools"), "go").absolutePath
        // Headless tests otherwise run Backgroundable progress tasks synchronously.
        // LSP4IJ must return from progress/begin to receive progress/end on the same
        // JSON-RPC reader. Use the normal IDE's asynchronous progress behavior.
        previousAsyncProgress = System.setProperty("intellij.progress.task.ignoreHeadless", "true")
    }

    fun testRegisteredGoplsCompletesAndFindsDefinitions() {
        createProjectFile("go.mod", "module example/bridge\n\ngo 1.22\n")
        val source = """
            package main
            func greet(name string) string { return "Hello, " + name }
            func main() { _ = greet("world") }
        """.trimIndent() + "\n"
        val file = createProjectFile("main.go", source)
        FileDocumentManager.getInstance().saveAllDocuments()

        // Accessor resolves the registered language mapping, starts our connection provider,
        // initializes gopls and sends didOpen for this actual project file.
        val servers = PlatformTestUtil.waitForFuture(
            LanguageServiceAccessor.getInstance(project).getLanguageServers(file, { true }, { true }), 60_000,
        )
        val server = servers.single { it.serverDefinition.id == "gotrick.gopls" }
        val wrapper = server.serverWrapper
        assertNotNull("The real gopls process must have started", wrapper.currentProcessId)
        assertTrue("The accessor must connect the file", wrapper.isConnectedTo(wrapper.toUri(file.virtualFile)))
        val uri = wrapper.toUriString(file.virtualFile)
        val callOffset = source.lastIndexOf("greet(")
        val line = source.take(callOffset).count { it == '\n' }
        val column = callOffset - source.lastIndexOf('\n', callOffset) - 1

        val completion = PlatformTestUtil.waitForFuture(server.textDocumentService.completion(
            CompletionParams(TextDocumentIdentifier(uri), Position(line, column + 3))), 60_000)
        val candidates = if (completion.isLeft) completion.left else completion.right.items
        assertTrue("Expected greet completion, received ${candidates.map { it.label }}",
            candidates.any { it.label.startsWith("greet") })

        val definitions = PlatformTestUtil.waitForFuture(server.textDocumentService.definition(
            DefinitionParams(TextDocumentIdentifier(uri), Position(line, column + 1))), 60_000)
        val definitionLines = if (definitions.isLeft) {
            definitions.left.filter { it.uri == uri }.map { it.range.start.line }
        } else {
            definitions.right.filter { it.targetUri == uri }.map { it.targetSelectionRange.start.line }
        }
        assertTrue("Definition must point to the greet declaration on line 2", 1 in definitionLines)
    }

    private fun createProjectFile(name: String, content: String): PsiFile {
        lateinit var file: VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            file = getOrCreateProjectBaseDir().createChildData(this, name)
            VfsUtil.saveText(file, content)
        }
        return PsiManager.getInstance(project).findFile(file)!!
    }

    override fun tearDown() {
        try {
            // Await protocol shutdown AND process exit before the fixture removes its cwd.
            // Windows rejects directory deletion while a child process still uses it.
            LanguageServiceAccessor.getInstance(project).startedServers.toList().forEach(::stopAndWait)
        } finally {
            try {
                super.tearDown()
            } finally {
                val previous = previousAsyncProgress
                if (previous == null) System.clearProperty("intellij.progress.task.ignoreHeadless")
                else System.setProperty("intellij.progress.task.ignoreHeadless", previous)
            }
        }
    }

    private fun stopAndWait(wrapper: LanguageServerWrapper) {
        val process = wrapper.currentProcessId?.let { ProcessHandle.of(it).orElse(null) }
        try {
            PlatformTestUtil.waitForFuture(wrapper.stop(), 15_000)
            if (process?.isAlive == true) PlatformTestUtil.waitForFuture(process.onExit(), 10_000)
        } finally {
            // Preserve fixture cleanup if a failed server initialization/shutdown leaves
            // this exact spawned process alive. The original test failure is still raised.
            if (process?.isAlive == true) {
                process.destroyForcibly()
                PlatformTestUtil.waitForFuture(process.onExit(), 10_000)
            }
        }
    }
}
