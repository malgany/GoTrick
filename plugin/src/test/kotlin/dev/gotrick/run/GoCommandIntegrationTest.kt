package dev.gotrick.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GoCommandIntegrationTest {
    @Test fun `portable Go executes run build test and vet plans`() {
        val tools = System.getProperty("gotrick.tools")?.let(Path::of)
        assumeTrue("Portable toolchain is available only in the GoTrick workspace", tools != null)
        val executable = tools!!.resolve("go/bin/" + if (System.getProperty("os.name").startsWith("Windows")) "go.exe" else "go")
        assumeTrue("Go SDK has been installed", Files.isRegularFile(executable))
        val workspace = Files.createTempDirectory("gotrick-run-")
        try {
            Files.writeString(workspace.resolve("go.mod"), "module example.org/gotrick-run-check\n\ngo 1.23\n")
            Files.writeString(workspace.resolve("main.go"), """
                package main
                import ("fmt"; "os")
                func Sum(a, b int) int { return a + b }
                func main() { fmt.Printf("%s|%s", os.Args[1], os.Getenv("GOTRICK_TEST_VALUE")) }
            """.trimIndent())
            Files.writeString(workspace.resolve("main_test.go"), """
                package main
                import "testing"
                func TestSum(t *testing.T) { if Sum(2, 3) != 5 { t.Fatal("wrong sum") } }
                func TestOther(t *testing.T) { t.Log("other ran") }
            """.trimIndent())
            var invocation = 0
            fun run(spec: GoCommandSpec): String {
                val output = workspace.resolve("output-${invocation++}.txt")
                val builder = ProcessBuilder(listOf(executable.toString()) + spec.arguments())
                    .directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(output.toFile())
                builder.environment().putAll(mapOf("GOTOOLCHAIN" to "local", "GOROOT" to tools.resolve("go").toString(), "GOCACHE" to tools.resolve("go-cache").toString(),
                    "GOPATH" to tools.resolve("go-work").toString(), "GOTRICK_TEST_VALUE" to "literal & value"))
                val process = builder.start()
                if (!process.waitFor(90, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    throw AssertionError("Go ${spec.operation} exceeded 90 seconds")
                }
                val text = Files.readString(output)
                assertEquals(text, 0, process.exitValue())
                return text
            }
            assertEquals("hello world|literal & value", run(GoCommandSpec(GoOperation.RUN, programArguments = "\"hello world\"")))
            run(GoCommandSpec(GoOperation.BUILD))
            run(GoCommandSpec(GoOperation.VET))
            val tests = run(GoCommandSpec(GoOperation.TEST, goFlags = "-v -count=1", testName = "TestSum"))
            assertTrue(tests, tests.contains("--- PASS: TestSum"))
            assertTrue(tests, !tests.contains("TestOther"))
        } finally {
            Files.walk(workspace).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
