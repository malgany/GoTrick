package dev.gotrick.run

import com.intellij.util.execution.ParametersListUtil
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GoWorkspaceResolverTest {
    @Test fun `workspace targets include every module and preserve paths with spaces`() {
        val tools = System.getProperty("gotrick.tools")?.let(Path::of)
        assumeTrue(tools != null)
        val go = tools!!.resolve("go/bin/" + if (System.getProperty("os.name").startsWith("Windows")) "go.exe" else "go")
        assumeTrue(Files.isRegularFile(go))
        val environment = mapOf("GOROOT" to tools.resolve("go").toString(), "GOTOOLCHAIN" to "local",
            "GOCACHE" to tools.resolve("go-cache").toString(), "GOWORK" to "", "GOENV" to "off", "GOFLAGS" to "")
        val root = Files.createTempDirectory("gotrick-workspace-")
        try {
            val moduleNames = listOf("first", "second module")
            moduleNames.forEachIndexed { index, name ->
                val module = Files.createDirectory(root.resolve(name))
                Files.writeString(module.resolve("go.mod"), "module example.org/module$index\n\ngo 1.23\n")
                Files.writeString(module.resolve("value.go"), "package value\nfunc Value() int { return $index }\n")
                Files.writeString(module.resolve("value_test.go"), "package value\nimport \"testing\"\nfunc TestValue(t *testing.T) { if Value() != $index { t.Fatal(\"bad value\") } }\n")
            }
            val work = "go 1.23\n\nuse (\n ./first // module one\n \"./second module\"\n)\n"
            Files.writeString(root.resolve("go.work"), work)
            val scope = GoWorkspaceResolver.resolve(go.toString(), root, GoOperation.TEST, environment = environment)
            assertEquals(listOf("./first/...", "./second module/..."), scope.packages)
            assertEquals(work, Files.readString(root.resolve("go.work")))
            val command = GoCommandSpec(GoOperation.TEST, ParametersListUtil.join(scope.packages), "-v -count=1").arguments()
            val outputFile = root.resolve("test-output.txt")
            val process = ProcessBuilder(listOf(go.toString()) + command).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).apply { environment().putAll(environment) }.start()
            if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Go workspace tests timed out") }
            val output = Files.readString(outputFile)
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, output.contains("example.org/module0"))
            assertTrue(output, output.contains("example.org/module1"))
            val selected = GoWorkspaceResolver.resolve(go.toString(), root, GoOperation.TIDY,
                root.resolve("second module/value.go"), environment)
            assertEquals(root.resolve("second module"), selected.directory)
            try {
                GoWorkspaceResolver.resolve(go.toString(), root, GoOperation.TIDY, environment = environment)
                fail("Tidy must require a module selection in a multi-module workspace")
            } catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("Select a Go file")) }
            Files.writeString(root.resolve("go.work"), "go 1.23\n")
            try {
                GoWorkspaceResolver.resolve(go.toString(), root, GoOperation.TEST, environment = environment)
                fail("Empty workspace should produce an actionable error")
            } catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("contains no modules")) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
