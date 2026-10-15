package dev.gotrick.lsp

import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import dev.gotrick.toolchain.GoToolchain

/** Uses the open LSP4IJ client; no dependency on GoLand or commercial IntelliJ APIs. */
class GoLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider = GoConnection(project)

    override fun createLanguageClient(project: Project): LanguageClientImpl = object : LanguageClientImpl(project) {
        override fun createSettings(): Any = JsonObject().apply { add("gopls", GoLanguageServerOptions.create()) }
    }
}

private class GoConnection(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        commandLine = GeneralCommandLine(GoToolchain.resolveGopls(project), "serve")
            .withWorkDirectory(project.basePath ?: System.getProperty("user.home"))
            .withCharset(Charsets.UTF_8)
            .withEnvironment(GoToolchain.environment(project))
    }

    override fun getInitializationOptions(rootUri: VirtualFile?): Any = GoLanguageServerOptions.create()
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
