package dev.gotrick.lsp

import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import dev.gotrick.toolchain.GoToolchain
import dev.gotrick.settings.GoSdkNotifications

/** Uses the open LSP4IJ client; no dependency on GoLand or commercial IntelliJ APIs. */
class GoLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider = GoConnection(project)

    override fun createLanguageClient(project: Project): LanguageClientImpl = object : LanguageClientImpl(project) {
        override fun createSettings(): Any = JsonObject().apply { add("gopls", GoLanguageServerOptions.create()) }
    }

    override fun createClientFeatures(): LSPClientFeatures = object : LSPClientFeatures() {
        override fun isEnabled(file: VirtualFile): Boolean {
            if (!GoToolchain.hasGoSdk(project)) {
                GoSdkNotifications.getInstance(project).showMissingSdk()
                return false
            }
            return super.isEnabled(file)
        }
    }
}

private class GoConnection(private val project: Project) : OSProcessStreamConnectionProvider() {
    override fun start() {
        // Also guard explicit starts from the Language Servers window. Resolve
        // again on every start so changing the project SDK replaces PATH/GOROOT.
        if (!GoToolchain.hasGoSdk(project)) {
            GoSdkNotifications.getInstance(project).showMissingSdk()
            throw CannotStartProcessException("Select a Go SDK in GoTrick > Configure Go SDK before starting gopls.")
        }
        commandLine = GeneralCommandLine(GoToolchain.resolveGopls(project), "serve")
            .withWorkDirectory(project.basePath ?: System.getProperty("user.home"))
            .withCharset(Charsets.UTF_8)
            .withEnvironment(GoToolchain.environment(project))
        super.start()
    }

    override fun getInitializationOptions(rootUri: VirtualFile?): Any = GoLanguageServerOptions.create()
}

object GoLanguageServerLifecycle {
    fun sdkChanged(project: Project) {
        val manager = LanguageServerManager.getInstance(project)
        if (!GoToolchain.hasGoSdk(project)) {
            manager.stop("gotrick.gopls")
            GoSdkNotifications.getInstance(project).showMissingSdk()
            return
        }
        GoSdkNotifications.getInstance(project).clear()
        // Preserve an explicit user choice to disable this language server.
        val definition = LanguageServersRegistry.getInstance().getServerDefinition("gotrick.gopls") ?: return
        if (definition.isEnabled(project)) {
            manager.start("gotrick.gopls", LanguageServerManager.StartOptions().setForceRestart(true))
        }
    }
}

object GoLanguageServerOptions {
    fun create(): JsonObject = JsonObject().apply {
        addProperty("gofumpt", false)
        addProperty("usePlaceholders", true)
        addProperty("completeUnimported", true)
        addProperty("semanticTokens", true)
        add("hints", JsonObject().apply {
            addProperty("assignVariableTypes", true)
            addProperty("compositeLiteralFields", true)
            addProperty("constantValues", true)
            addProperty("parameterNames", true)
            addProperty("rangeVariableTypes", true)
        })
    }
}
