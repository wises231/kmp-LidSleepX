package com.wyz.covio.app

import com.wyz.covio.app.helper.HelperClient
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNull

class HelperClientTest {
    @Test
    fun `missing socket returns null`() {
        val directory = Files.createTempDirectory("covio-helper")
        val socket = directory.resolve("missing.sock")
        assertNull(HelperClient(socket).request("version"))
    }

    @Test
    fun `silent server times out and returns null`() {
        val directory = Files.createTempDirectory("covio-helper")
        val socket = directory.resolve("silent.sock")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
            server.bind(UnixDomainSocketAddress.of(socket))
            val result = AtomicReference<Any?>("pending")
            val worker = Thread { result.set(HelperClient(socket).request("version", timeoutMillis = 200)) }
            worker.start()
            val accepted = server.accept()
            try {
                worker.join(5_000)
                assertNull(result.get())
            } finally {
                accepted.close()
            }
        }
    }

    @Test
    fun `non positive timeout returns null`() {
        val directory = Files.createTempDirectory("covio-helper")
        val socket = directory.resolve("any.sock")
        assertNull(HelperClient(socket).request("version", timeoutMillis = 0))
    }
}
