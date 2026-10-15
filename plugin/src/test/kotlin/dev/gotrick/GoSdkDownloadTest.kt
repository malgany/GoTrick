package dev.gotrick

import dev.gotrick.toolchain.GoSdkDiscovery
import dev.gotrick.toolchain.GoSdkDownload
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GoSdkDownloadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `extract valid Go archive into a separate SDK directory`() {
        val archive = temporary.root.toPath().resolve("sdk.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            for (path in listOf("go/bin/go.exe", "go/src/runtime/runtime.go", "go/pkg/tool/windows_amd64/compile.exe")) {
                zip.putNextEntry(ZipEntry(path)); zip.write(byteArrayOf(1)); zip.closeEntry()
            }
        }
        val output = temporary.root.toPath().resolve("output")
        GoSdkDownload.extract(archive, output)
        assertTrue(GoSdkDiscovery.isSdk(output.resolve("go").toFile()))
        assertEquals(listOf(output.resolve("go").toFile()), GoSdkDiscovery.discover(mapOf("GOROOT" to output.resolve("go").toString()), temporary.root))
    }

    @Test fun `reject archive traversal`() {
        val archive = temporary.root.toPath().resolve("bad.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("go/../../outside.txt")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { GoSdkDownload.extract(archive, temporary.root.toPath().resolve("output")) }
        assertFalse(File(temporary.root, "outside.txt").exists())
    }

    @Test fun `release selection excludes installers prereleases and other architectures`() {
        val hash = "a".repeat(64)
        val json = """[{"version":"go1.27.2","stable":true,"files":[
          {"os":"windows","arch":"amd64","kind":"archive","filename":"go1.27.2.windows-amd64.zip","sha256":"$hash","size":123},
          {"os":"windows","arch":"amd64","kind":"installer"},
          {"os":"linux","arch":"amd64","kind":"archive"}]},
          {"version":"go1.28rc1","stable":false,"files":[]},
          {"version":"go1.2","stable":true,"files":[
          {"os":"windows","arch":"amd64","kind":"archive","filename":"go1.2.windows-amd64.zip","sha256":"","size":0}]}]"""
        assertEquals(listOf("go1.27.2"), GoSdkDownload.parseReleases(json).map { it.version })
    }
}
