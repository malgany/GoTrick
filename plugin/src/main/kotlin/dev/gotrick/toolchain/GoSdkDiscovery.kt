package dev.gotrick.toolchain

import java.io.File

object GoSdkDiscovery {
    val executableName get() = if (System.getProperty("os.name").startsWith("Windows")) "go.exe" else "go"
    fun executable(home: String) = File(home, "bin/$executableName")
    fun isSdk(home: File): Boolean = executable(home.path).isFile && File(home, "src").isDirectory &&
        File(home, "pkg/tool").isDirectory

    fun discover(environment: Map<String, String> = System.getenv(), userHome: File = File(System.getProperty("user.home"))): List<File> {
        val candidates = buildList {
            environment["GOROOT"]?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            environment["PATH"]?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach {
                File(it.trim('"')).parentFile?.let { parent -> add(parent) }
            }
            environment["ProgramFiles"]?.let { add(File(it, "Go")) }
            add(File("C:/Go"))
            add(File("/usr/local/go"))
            File(userHome, "sdk").listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }
                ?.sortedByDescending { it.lastModified() }?.let { addAll(it) }
        }
        return candidates.filter(::isSdk).distinctBy { it.absoluteFile.normalize().path.lowercase() }
    }
}
