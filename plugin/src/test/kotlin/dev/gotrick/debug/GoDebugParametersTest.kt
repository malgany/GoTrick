package dev.gotrick.debug

import com.redhat.devtools.lsp4ij.dap.configurations.extractors.NetworkAddressExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDebugParametersTest {
    @Test fun `debug arguments and environment survive settings serialization`() {
        val values: Map<String, Any> = mapOf("args" to listOf("--message", "hello world", "a&b"),
            "env" to mapOf("MESSAGE" to "x=y & z"), "mode" to "test", "stopOnEntry" to true)
        assertEquals(values, GoDebugParameters.parse(GoDebugParameters.serialize(values)))
    }

    @Test fun `Delve readiness message yields actual host and port`() {
        val result = NetworkAddressExtractor(GoDebugParameters.READY_PATTERN).extract("DAP server listening at: 127.0.0.1:43210\n")
        assertEquals("127.0.0.1", result.address())
        assertEquals("43210", result.port())
    }

    @Test fun `empty launch configuration has defaults`() {
        assertTrue(GoDebugParameters.parse(null).isEmpty())
        assertTrue(GoDebugParameters.parse("").isEmpty())
    }
}
