package dev.gotrick.debug

import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.client.DAPClient
import com.redhat.devtools.lsp4ij.settings.ServerTrace
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.*
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.json.*
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.UnaryOperator

/** Keep transport shutdown diagnostics local to this Go debug session. */
internal class GoDebugClient(
    process: DAPDebugProcess, parameters: MutableMap<String, Any>, debug: Boolean,
    mode: DebugMode, trace: ServerTrace, parent: DAPClient?
) : DAPClient(process, parameters, debug, mode, trace, parent) {
    private val disposing = AtomicBoolean(false)

    override fun dispose() {
        if (disposing.compareAndSet(false, true)) super.dispose()
    }

    override fun createLauncher(wrapper: UnaryOperator<MessageConsumer>, input: InputStream,
                                output: OutputStream, executor: ExecutorService): Launcher<IDebugProtocolServer> {
        val builder = object : DebugLauncher.Builder<IDebugProtocolServer>() {
            private lateinit var endpoint: RemoteEndpoint
            private lateinit var jsonHandler: MessageJsonHandler
            override fun createRemoteEndpoint(handler: MessageJsonHandler): RemoteEndpoint =
                super.createRemoteEndpoint(handler).also { endpoint = it; jsonHandler = handler }

            override fun createMessageProcessor(producer: MessageProducer, consumer: MessageConsumer,
                                                proxy: IDebugProtocolServer): ConcurrentMessageProcessor {
                val reader = GoDebugMessageProducer(input, jsonHandler, endpoint, disposing)
                return super.createMessageProcessor(reader, consumer, proxy)
            }
        }
        return builder.setLocalService(this).setRemoteInterface(IDebugProtocolServer::class.java)
            .setInput(input).setOutput(output).setExecutorService(executor).wrapMessages(wrapper).create()
    }
}

internal open class GoDebugMessageProducer(
    input: InputStream, handler: MessageJsonHandler, issues: MessageIssueHandler?,
    private val disposing: AtomicBoolean
) : StreamMessageProducer(input, handler, issues) {
    override fun fireError(error: Throwable) {
        // Gson wraps an IOException from a partial frame in JsonSyntaxException.
        // Only the explicit local socket close during disposal is expected.
        val socket = generateSequence(error) { it.cause }.filterIsInstance<SocketException>().firstOrNull()
        if (disposing.get() && socket?.message.equals("Socket closed", ignoreCase = true)) {
            close()
            fireStreamClosed(socket!!)
        } else {
            reportUnexpectedError(error)
        }
    }

    protected open fun reportUnexpectedError(error: Throwable) = super.fireError(error)
}
