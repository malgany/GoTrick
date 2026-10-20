package dev.gotrick.toolchain

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import com.intellij.util.io.Decompressor

/** Optional SDK installation, separate from the IDE and its internal tools. */
object GoSdkDownload {
    data class Release(val version: String, val filename: String, val sha256: String, val size: Long) {
        override fun toString() = version
    }

    fun releases(): List<Release> {
        require(System.getProperty("os.name").let { it.startsWith("Windows") || it == "Linux" } && System.getProperty("os.arch") in setOf("amd64", "x86_64")) {
            "SDK downloads support Windows and Linux x64. Select an existing SDK on other systems."
        }
        val connection = connect("https://go.dev/dl/?mode=json&include=all")
        try {
            return connection.inputStream.bufferedReader().use { parseReleases(it.readText()) }
        } finally { connection.disconnect() }
    }

    internal fun parseReleases(json: String, os: String = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"): List<Release> = JsonParser.parseString(json).asJsonArray.flatMap { item ->
        val release = item.asJsonObject
        if (!release.get("stable").asBoolean) emptyList() else release.getAsJsonArray("files").mapNotNull { value ->
            val file = value.asJsonObject
            if (file.get("os").asString != os || file.get("arch").asString != "amd64" || file.get("kind").asString != "archive") null
            // Historical releases can lack SHA-256 or size metadata. Never offer
            // unverifiable archives, but do not hide valid modern releases.
            else runCatching {
                Release(release.get("version").asString, file.get("filename").asString,
                    file.get("sha256").asString, file.get("size").asLong).also(::validateRelease)
            }.getOrNull()
        }
    }

    private fun validateRelease(release: Release) {
        require(Regex("go[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(release.version)) { "Invalid SDK version" }
        require(release.filename in setOf("${release.version}.windows-amd64.zip", "${release.version}.linux-amd64.tar.gz")) { "Invalid SDK archive name" }
        require(Regex("[a-fA-F0-9]{64}").matches(release.sha256) && release.size > 0) { "Missing SDK checksum or size" }
    }

    private fun connect(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "GoTrick/0.1")
        require(responseCode == 200) { "Go download service returned HTTP $responseCode" }
    }

    fun install(release: Release, destinationParent: Path, canceled: () -> Unit = {}, progress: (Double) -> Unit = {}): Path {
        validateRelease(release)
        val parent = destinationParent.toAbsolutePath().normalize()
        Files.createDirectories(parent)
        val destination = parent.resolve(release.version)
        require(!Files.exists(destination)) { "SDK destination already exists: $destination. Select it as a local SDK, or choose another directory." }
        val stage = Files.createTempDirectory(parent, ".gotrick-sdk-")
        try {
            val archive = stage.resolve(release.filename)
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = connect("https://go.dev/dl/${release.filename}")
            var size = 0L
            try {
                connection.inputStream.use { input -> Files.newOutputStream(archive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        canceled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= release.size) { "Downloaded SDK exceeds published size" }
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        progress(size.toDouble() / release.size * 0.8)
                    }
                } }
            } finally { connection.disconnect() }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(size == release.size && hash.equals(release.sha256, true)) { "SDK checksum verification failed; no SDK was installed." }
            val extracted = stage.resolve("unpacked")
            extract(archive, extracted, canceled)
            val sdk = extracted.resolve("go")
            require(GoSdkDiscovery.isSdk(sdk.toFile())) { "Archive does not contain a valid Go SDK" }
            canceled()
            // Move only our verified staging tree, and never replace another SDK.
            require(sdk.normalize().startsWith(stage) && destination.parent == parent)
            Files.move(sdk, destination)
            progress(1.0)
            return destination
        } finally {
            require(stage.toAbsolutePath().normalize().parent == parent && stage.fileName.toString().startsWith(".gotrick-sdk-"))
            Files.walk(stage).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    internal fun extract(archive: Path, destination: Path, canceled: () -> Unit = {}) {
        val root = destination.toAbsolutePath().normalize()
        Files.createDirectories(root)
        if (archive.fileName.toString().endsWith(".tar.gz")) {
            Decompressor.Tar(archive).entryFilter { entry ->
                canceled()
                require(entry.type != Decompressor.Entry.Type.SYMLINK && entry.linkTarget.isNullOrEmpty()) { "SDK archive links are not allowed" }
                require(!entry.name.contains('\\') && !entry.name.contains(':')) { "Invalid SDK archive path" }
                require((entry.name == "go" || entry.name.startsWith("go/")) && root.resolve(entry.name).normalize().startsWith(root)) { "SDK archive path escapes destination" }
                true
            }.overwrite(false).extract(root)
            return
        }
        ZipFile(archive.toFile()).use { zip ->
            for (entry in zip.entries()) {
                canceled()
                require(!entry.name.contains('\\') && !entry.name.contains(':')) { "Invalid SDK archive path" }
                val target = root.resolve(entry.name).normalize()
                require(target.startsWith(root) && entry.name.startsWith("go/")) { "SDK archive path escapes destination" }
                if (entry.isDirectory) Files.createDirectories(target)
                else {
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input -> Files.newOutputStream(target).use { output -> input.copyTo(output) } }
                }
            }
        }
    }
}
