package dev.gotrick.debug

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class GoDebugShutdownTest {
    private fun readFrame(body: String, closing: Boolean, failOnRead: Boolean): Pair<Int, List<Throwable>> {
        val bytes = ("Content-Length: ${if (failOnRead) 100 else body.toByteArray().size}\r\n\r\n" + body).toByteArray()
        var offset = 0
        var closed = 0
        val failures = mutableListOf<Throwable>()
        val input = object : InputStream() {
            override fun read(): Int {
                if (offset < bytes.size) return bytes[offset++].toInt() and 255
                if (failOnRead) throw SocketException("Socket closed")
                return -1
            }
        }
        val reader = object : GoDebugMessageProducer(input, MessageJsonHandler(emptyMap()), null, AtomicBoolean(closing)) {
            override fun fireStreamClosed(error: Exception) { closed++ }
            override fun reportUnexpectedError(error: Throwable) { failures.add(error); close() }
        }
        reader.listen { }
        return closed to failures
    }

    @Test fun `local close while parsing final frame is normal during disposal`() {
        val (closed, failures) = readFrame("{", true, true)
        assertEquals(1, closed)
        assertTrue(failures.toString(), failures.isEmpty())
    }

    @Test fun `closed socket in active session remains an error`() {
        val (_, failures) = readFrame("{", false, true)
        assertEquals(1, failures.size)
    }

    @Test fun `malformed JSON remains an error even during disposal`() {
        val (_, failures) = readFrame("not-json", true, false)
        assertEquals(1, failures.size)
    }
}
