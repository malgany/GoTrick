package dev.gotrick.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GoProjectCreatorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun createsUtf8ProjectInFolderWithSpacesAndUnicode() {
        val parent = temporary.newFolder("Projetos de João").toPath()
        val project = GoProjectCreator.create(parent, "Olá mundo", "example/hello")
        assertEquals(parent.toRealPath().resolve("Olá mundo"), project)
        assertEquals("module example/hello\n\ngo 1.22\n", Files.readString(project.resolve("go.mod")))
        assertTrue(Files.readString(project.resolve("main.go")).contains("func main()"))
        assertTrue(Files.readString(project.resolve("main_test.go")).contains("func TestGreeting(t *testing.T)"))
        assertEquals(3, Files.list(project).use { it.count().toInt() })
    }

    @Test fun refusesToOverwriteAnExistingDirectoryOrFile() {
        val parent = temporary.root.toPath()
        val directory = Files.createDirectory(parent.resolve("existing"))
        val sentinel = Files.writeString(directory.resolve("keep.txt"), "preserve me")
        assertThrows(FileAlreadyExistsException::class.java) { GoProjectCreator.create(parent, "existing", "example/app") }
        assertEquals("preserve me", Files.readString(sentinel))
        assertFalse(Files.exists(directory.resolve("go.mod")))
        Files.writeString(parent.resolve("existing-file"), "also preserve me")
        assertThrows(FileAlreadyExistsException::class.java) { GoProjectCreator.create(parent, "existing-file", "example/app") }
        assertEquals("also preserve me", Files.readString(parent.resolve("existing-file")))
    }

    @Test fun rejectsTraversalAndReservedNamesBeforeWriting() {
        for (name in listOf("", "..", ".", "../escape", "nested/folder", "nested\\folder", "C:\\escape", "CON", "nul.txt", "LPT1", "space ", "dot.", "bad\nname")) {
            assertNotNull("Must reject $name", GoProjectCreator.validateFolderName(name))
            assertThrows(IllegalArgumentException::class.java) { GoProjectCreator.create(temporary.root.toPath(), name, "example/app") }
        }
        assertEquals(0, temporary.root.listFiles()!!.size)
    }

    @Test fun permitsPortableModuleNamesAndVersionSuffixes() {
        for (module in listOf("example/hello", "hello", "example.com/team/app", "github.com/Team/app-name/v2", "gopkg.in/yaml.v3", "example.com/my_app")) {
            assertNull(module, GoProjectCreator.validateModulePath(module))
        }
        assertNull(GoProjectCreator.validateFolderName("Ação com espaço"))
    }

    @Test fun rejectsInvalidModulePathsAndDirectiveInjection() {
        for (module in listOf("", "example bad/app", "example.com/a\ngo 1.99", "example.com/é", "/absolute", "trailing/", "double//slash", "../relative", "example.com/CON", "example.com/a~1", "example.com/app/v1", "example.com/app/v01", "example.com/app/v2.0", "-bad/name", "example.com\\windows")) {
            assertNotNull(module, GoProjectCreator.validateModulePath(module))
            assertThrows(IllegalArgumentException::class.java) { GoProjectCreator.create(temporary.root.toPath(), "invalid", module) }
            assertFalse(Files.exists(temporary.root.toPath().resolve("invalid")))
        }
    }

    @Test fun requiresAnExistingParentFolder() {
        assertThrows(IllegalArgumentException::class.java) {
            GoProjectCreator.create(temporary.root.toPath().resolve("missing"), "app", "example/app")
        }
        assertEquals(0, temporary.root.listFiles()!!.size)
    }

    @Test fun generatedProjectRunsItsTestWithTheBundledGoToolchain() {
        val tools = System.getProperty("gotrick.tools")
        assumeTrue("Portable Go toolchain is not configured", !tools.isNullOrBlank())
        val go = Path.of(tools, "go", "bin", if (System.getProperty("os.name").startsWith("Windows")) "go.exe" else "go")
        assumeTrue("Portable Go toolchain is not installed", Files.isRegularFile(go))
        val project = GoProjectCreator.create(temporary.root.toPath(), "Go teste ação", "example/smoke")
        val log = temporary.root.toPath().resolve("go-test.log")
        val builder = ProcessBuilder(go.toString(), "test", "./...")
            .directory(project.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        builder.environment()["GOWORK"] = "off"
        builder.environment()["GOTOOLCHAIN"] = "local"
        val process = builder.start()
        try {
            assertTrue("go test timed out", process.waitFor(90, TimeUnit.SECONDS))
            assertEquals(Files.readString(log), 0, process.exitValue())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
