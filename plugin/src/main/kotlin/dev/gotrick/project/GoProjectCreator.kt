package dev.gotrick.project

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/** Creates a small, runnable local module without executing commands or downloading dependencies. */
object GoProjectCreator {
    private val reservedNames = setOf("CON", "PRN", "AUX", "NUL", "CLOCK$", "CONIN$", "CONOUT$") +
        (1..9).flatMap { listOf("COM$it", "LPT$it") }
    private val moduleCharacters = Regex("[A-Za-z0-9._~/\\-]+")

    fun validateFolderName(name: String): String? = when {
        name.isBlank() -> "Enter a project name."
        name != name.trim() -> "The project name cannot begin or end with spaces."
        name == "." || name == ".." -> "Choose a project name, not a relative path."
        name.any { it.code < 32 || it in "<>:\"/\\|?*" } -> "The project name contains a character that is not allowed in a folder name."
        name.endsWith('.') -> "The project name cannot end with a dot."
        name.substringBefore('.').uppercase(Locale.ROOT) in reservedNames -> "This name is reserved by Windows."
        else -> null
    }

    /** Validates lexical module paths, including local-only names such as example/hello. */
    fun validateModulePath(path: String): String? {
        if (path.isBlank()) return "Enter a module path, such as example.com/team/app."
        if (!moduleCharacters.matches(path)) return "Use ASCII letters, digits, dots, hyphens, underscores, tildes and forward slashes in the module path."
        val parts = path.split('/')
        for (part in parts) {
            if (part.isEmpty()) return "The module path cannot begin or end with / or contain empty segments."
            if (part.startsWith('.') || part.endsWith('.')) return "A module path segment cannot begin or end with a dot."
            if (part.substringBefore('.').uppercase(Locale.ROOT) in reservedNames) return "The module path contains a reserved Windows name."
            if (Regex(".*~[0-9]+$").matches(part)) return "A module path segment cannot end with a tilde followed by digits."
        }
        if (path.startsWith('-')) return "The module path cannot begin with a hyphen."
        val finalPart = parts.last()
        if (parts.size > 1 && Regex("v[0-9.]+").matches(finalPart) &&
            (!Regex("v[1-9][0-9]*").matches(finalPart) || finalPart == "v1")) {
            return "A major version suffix must be /v2 or higher, without leading zeros or dots."
        }
        return null
    }

    fun target(parent: Path, name: String): Path {
        require(validateFolderName(name) == null) { validateFolderName(name)!! }
        require(Files.isDirectory(parent)) { "Choose an existing parent folder." }
        val realParent = parent.toRealPath()
        val target = realParent.resolve(name).normalize()
        require(target.parent == realParent) { "The project must be created directly inside the selected folder." }
        return target
    }

    fun create(parent: Path, name: String, modulePath: String): Path {
        require(validateModulePath(modulePath) == null) { validateModulePath(modulePath)!! }
        val target = target(parent, name)
        // createDirectory is atomic and refuses existing files, directories and dangling links.
        Files.createDirectory(target)
        val created = mutableListOf<Path>()
        try {
            for ((filename, contents) in files(modulePath)) {
                val file = target.resolve(filename)
                Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    created.add(file)
                    output.write(contents.toByteArray(Charsets.UTF_8))
                }
            }
            return target
        } catch (failure: Exception) {
            // Delete only files created by this call. Never recursively delete a directory.
            for (file in created.asReversed()) {
                try {
                    if (file.parent == target && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file)
                } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            }
            try { Files.delete(target) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    private fun files(modulePath: String): Map<String, String> = linkedMapOf(
        "go.mod" to "module $modulePath\n\ngo 1.22\n",
        "main.go" to """
            package main

            import "fmt"

            func greeting(name string) string {
            	return "Hello, " + name + "!"
            }

            func main() {
            	fmt.Println(greeting("GoTrick"))
            }
        """.trimIndent() + "\n",
        "main_test.go" to """
            package main

            import "testing"

            func TestGreeting(t *testing.T) {
            	got := greeting("GoTrick")
            	want := "Hello, GoTrick!"
            	if got != want {
            		t.Errorf("greeting() = %q, want %q", got, want)
            	}
            }
        """.trimIndent() + "\n",
    )
}
