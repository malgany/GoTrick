package dev.gotrick.toolchain

import com.intellij.openapi.project.Project
import dev.gotrick.settings.GoTrickSettings
import dev.gotrick.settings.GoProjectSdkSettings
import java.io.File

object GoToolchain {
    private val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private fun binary(name: String) = if (windows) "$name.exe" else name

    @JvmStatic fun resolveGo(project: Project? = null): String {
        val home = project?.let { GoProjectSdkSettings.getInstance(it).home }?.trim().orEmpty()
        if (home.isNotEmpty()) return GoSdkDiscovery.executable(home).absolutePath
        return resolve(GoTrickSettings.getInstance().goExecutable, "go", project)
    }
    @JvmStatic fun resolveGopls(project: Project? = null): String =
        resolve(GoTrickSettings.getInstance().goplsExecutable, "gopls", project)
    @JvmStatic fun resolveDelve(project: Project? = null): String =
        resolve(GoTrickSettings.getInstance().delveExecutable, "dlv", project)

    fun hasGoSdk(project: Project): Boolean {
        val executable = File(resolveGo(project))
        val sdk = executable.parentFile?.parentFile ?: return false
        return executable.isAbsolute && executable.isFile && GoSdkDiscovery.isSdk(sdk)
    }

    fun resolve(configured: String, name: String, project: Project? = null): String {
        val selected = configured.trim().removeSurrounding("\"")
        if (selected.isNotEmpty()) return selected
        // The project SDK is independent of the IDE's bundled helper programs.
        // Never silently select the SDK used to build GoTrick itself.
        if (name == "go") return GoSdkDiscovery.discover().firstOrNull()
            ?.let { GoSdkDiscovery.executable(it.path).absolutePath } ?: binary("go")
        val roots = buildList {
            System.getProperty("gotrick.tools")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            System.getenv("GOTRICK_TOOLS")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            project?.basePath?.let { add(File(it, ".tools")) }
        }.distinctBy { it.absolutePath }
        val paths = buildList {
            for (root in roots) {
                add(File(root, if (name == "go") "go/bin/${binary(name)}" else "gotools/${binary(name)}"))
                add(File(root, "bin/${binary(name)}"))
                add(File(root, binary(name)))
            }
            if (name == "go") System.getenv("GOROOT")?.let { add(File(it, "bin/${binary(name)}")) }
            System.getenv("PATH")?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach {
                add(File(it.trim('"'), binary(name)))
            }
            if (name != "go") {
                System.getenv("GOPATH")?.split(File.pathSeparator)?.forEach { add(File(it, "bin/${binary(name)}")) }
                add(File(System.getProperty("user.home"), "go/bin/${binary(name)}"))
            }
        }
        return paths.firstOrNull { it.isFile }?.absolutePath ?: binary(name)
    }

    /** Children can find the portable SDK without changing the machine's PATH. */
    fun environment(project: Project? = null, goExecutable: String = resolveGo(project)): Map<String, String> {
        val go = File(goExecutable)
        if (!go.isAbsolute || !go.isFile) return emptyMap()
        val parentPath = System.getenv("PATH").orEmpty()
        val sdk = go.parentFile?.parentFile
        // A launcher may set GOROOT for its bundled SDK. Explicitly selecting
        // another SDK must replace it, or Go mixes compiler/runtime versions.
        val goroot = sdk?.takeIf { File(it, "src").isDirectory && File(it, "pkg/tool").isDirectory }?.absolutePath.orEmpty()
        return mapOf("PATH" to go.parent + File.pathSeparator + parentPath, "GOROOT" to goroot)
    }
}
