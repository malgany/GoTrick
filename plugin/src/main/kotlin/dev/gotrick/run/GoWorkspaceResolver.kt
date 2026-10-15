package dev.gotrick.run

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class GoProjectScope(val directory: Path, val packages: List<String>)

/** Resolves go.work using Go itself, so quoted paths and comments obey Go's grammar. Runs off the UI thread. */
object GoWorkspaceResolver {
    fun resolve(go: String, base: Path, operation: GoOperation, selected: Path? = null,
        environment: Map<String, String> = emptyMap(), checkCanceled: () -> Unit = {}): GoProjectScope {
        val directory = base.toAbsolutePath().normalize()
        require(Files.isDirectory(directory)) { "Project directory does not exist: $directory" }
        if (operation == GoOperation.TIDY) {
            val selectedModule = selected?.let(::nearestModule)
            if (selectedModule != null) return GoProjectScope(selectedModule, emptyList())
            if (Files.isRegularFile(directory.resolve("go.mod"))) return GoProjectScope(directory, emptyList())
        }
        if (!Files.isRegularFile(directory.resolve("go.work"))) {
            if (operation == GoOperation.TIDY)
                throw IllegalArgumentException("Open a file in a Go module before running Tidy. No go.mod was found in this project directory.")
            return GoProjectScope(directory, listOf("./..."))
        }
        val json = runGo(go, listOf("work", "edit", "-json", directory.resolve("go.work").toString()), directory, environment, checkCanceled)
        val use = JsonParser.parseString(json).asJsonObject.get("Use")?.takeIf { it.isJsonArray }?.asJsonArray
        val modules = use?.map { entry ->
            val disk = Path.of(entry.asJsonObject.get("DiskPath").asString)
            val module = (if (disk.isAbsolute) disk else directory.resolve(disk)).normalize()
            require(Files.isRegularFile(module.resolve("go.mod"))) { "Workspace module has no go.mod: $module" }
            module
        }?.distinct().orEmpty()
        require(modules.isNotEmpty()) { "This go.work contains no modules. Add a module with go work use, then try again." }
        if (operation == GoOperation.TIDY) {
            require(modules.size == 1) { "This workspace has ${modules.size} modules. Select a Go file or module folder, then run Tidy Go Modules again." }
            return GoProjectScope(modules.single(), emptyList())
        }
        return GoProjectScope(directory, modules.map { module ->
            val relative = runCatching { directory.relativize(module) }.getOrDefault(module).toString().replace('\\', '/')
            when {
                relative.isEmpty() -> "./..."
                relative.startsWith("../") || Path.of(relative).isAbsolute -> "$relative/..."
                else -> "./$relative/..."
            }
        })
    }

    private fun nearestModule(selected: Path): Path? {
        var candidate: Path? = selected.toAbsolutePath().normalize().let { if (Files.isDirectory(it)) it else it.parent }
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("go.mod"))) return candidate
            candidate = candidate.parent
        }
        return null
    }

    private fun runGo(go: String, arguments: List<String>, directory: Path, environment: Map<String, String>, checkCanceled: () -> Unit): String {
        val builder = ProcessBuilder(listOf(go) + arguments).directory(directory.toFile())
        builder.environment().putAll(environment)
        val process = builder.start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val errors = CompletableFuture.supplyAsync { process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                checkCanceled()
                check(System.nanoTime() < deadline) { "Timed out while reading go.work. Check the Go SDK in Settings | GoTrick / Go." }
            }
            checkCanceled()
            val stderr = errors.get(5, TimeUnit.SECONDS)
            check(process.exitValue() == 0) { stderr.ifBlank { "go work edit exited with code ${process.exitValue()}" }.trim() }
            return output.get(5, TimeUnit.SECONDS)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
