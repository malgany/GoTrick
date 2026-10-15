package dev.gotrick.run

import org.junit.Assert.assertEquals
import org.junit.Test

class GoCommandSpecTest {
    @Test fun `run arguments preserve quoting without interpreting shell commands`() {
        val result = GoCommandSpec(GoOperation.RUN, ".", "-race",
            "--label \"hello world\" \"a&b\" \"x;y\"").arguments()
        assertEquals(listOf("run", "-race", ".", "--label", "hello world", "a&b", "x;y"), result)
    }

    @Test fun `tests place flags before packages and custom arguments after args`() {
        val result = GoCommandSpec(GoOperation.TEST, "./...", "-v -count=1", "-custom=value", "TestSum").arguments()
        assertEquals(listOf("test", "-v", "-count=1", "-run", "^\\QTestSum\\E$", "./...", "-args", "-custom=value"), result)
    }

    @Test fun `benchmark selection disables normal tests`() {
        val result = GoCommandSpec(GoOperation.TEST, ".", testName = "BenchmarkSum").arguments()
        assertEquals(listOf("test", "-run", "^$", "-bench", "^\\QBenchmarkSum\\E$", "."), result)
    }

    @Test fun `tidy never appends package targets`() {
        assertEquals(listOf("mod", "tidy", "-v"), GoCommandSpec(GoOperation.TIDY, "./...", "-v").arguments())
    }

    @Test fun `environment values retain equals and intentional spaces`() {
        assertEquals(mapOf("TOKEN" to "a=b", "MESSAGE" to "hello world  ", "EMPTY" to ""),
            GoCommandSpec.parseEnvironment("TOKEN=a=b\nMESSAGE=hello world  \n\nEMPTY="))
    }

    @Test(expected = IllegalArgumentException::class) fun `invalid environment rejected`() {
        GoCommandSpec.parseEnvironment("NO_EQUALS")
    }

    @Test(expected = IllegalArgumentException::class) fun `invalid environment name rejected`() {
        GoCommandSpec.parseEnvironment("HAS SPACE=value")
    }
}
