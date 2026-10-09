package com.wyz.covio.app.helper

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

internal interface PosixLibrary : Library {
    fun socket(domain: Int, type: Int, protocol: Int): Int
    fun bind(socket: Int, address: Pointer, addressLength: Int): Int
    fun listen(socket: Int, backlog: Int): Int
    fun accept(socket: Int, address: Pointer?, addressLength: Pointer?): Int
    fun read(socket: Int, buffer: Pointer, count: Long): Long
    fun write(socket: Int, buffer: Pointer, count: Long): Long
    fun close(fd: Int): Int
    fun unlink(path: String): Int
    fun chmod(path: String, mode: Int): Int
    fun getpeereid(socket: Int, uid: IntByReference, gid: IntByReference): Int
}

object HelperEntrypoint {
    fun run(): Int {
        val logger = HelperLog()
        return try {
            HelperServer(logger).run()
            0
        } catch (error: Throwable) {
            logger.error("Helper failed", error)
            1
        }
    }
}

internal class HelperLog {
    private val path = Path.of(HelperProtocol.LOG_PATH)

    fun info(message: String) = write("INFO", message)
    fun error(message: String, error: Throwable? = null) =
        write("ERROR", message + (error?.let { ": ${it.stackTraceToString()}" } ?: ""))

    @Synchronized
    private fun write(level: String, message: String) {
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(
                path,
                "${Instant.now()} $level $message\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}

internal class HelperServer(private val logger: HelperLog) {
    private val posix = Native.load("System", PosixLibrary::class.java)
    private var serverSocket = -1

    fun run() {
        val socketPath = File(HelperProtocol.SOCKET_PATH)
        socketPath.parentFile?.mkdirs()
        Files.deleteIfExists(socketPath.toPath())
        serverSocket = posix.socket(AF_UNIX, SOCK_STREAM, 0)
        check(serverSocket >= 0) { "socket() failed" }

        bindServerSocket(HelperProtocol.SOCKET_PATH)
        check(posix.listen(serverSocket, 16) == 0) { "listen() failed" }
        posix.chmod(HelperProtocol.SOCKET_PATH, 0x1B6) // 0666
        logger.info("Helper listening on ${HelperProtocol.SOCKET_PATH}")

        while (!Thread.currentThread().isInterrupted) {
            val client = posix.accept(serverSocket, null, null)
            if (client < 0) continue
            Thread { handleClient(client) }.apply {
                name = "Covio-helper-client"
                isDaemon = true
                start()
            }
        }
    }

    private fun bindServerSocket(path: String): Unit {
        val bytes = path.toByteArray(StandardCharsets.UTF_8)
        val addressLength = 2 + bytes.size + 1
        val memory = Memory((addressLength + 16).toLong())
        memory.clear()
        memory.setByte(0, (addressLength and 0xFF).toByte())
        memory.setByte(1, AF_UNIX.toByte())
        memory.write(2, bytes, 0, bytes.size)
        check(posix.bind(serverSocket, memory, addressLength) == 0) { "bind() failed" }
    }

    internal fun handleClient(client: Int) {
        try {
            val uid = IntByReference()
            val gid = IntByReference()
            if (posix.getpeereid(client, uid, gid) != 0) {
                writeReply(client, HelperProtocol.failure("", "Cannot determine peer uid"))
                return
            }
            val allowed = readAllowedUid()
            if (allowed == null || uid.value != allowed) {
                logger.info("Rejected helper request from uid=${uid.value}")
                writeReply(client, HelperProtocol.failure("", "Peer uid is not allowed"))
                return
            }

            val requestLine = readLine(client) ?: return
            val request = HelperProtocol.decodeRequest(requestLine)
            if (request == null) {
                writeReply(client, HelperProtocol.failure("", "Invalid JSON request"))
                return
            }
            val validationError = HelperProtocol.validate(request)
            if (validationError != null) {
                writeReply(client, HelperProtocol.failure(request.requestId, validationError))
                return
            }
            val reply = when (request.command) {
                "version" -> HelperProtocol.versionReply(request.requestId)
                "setDisableSleep" -> setDisableSleep(request.requestId, request.payload["disabled"] == "true")
                "setHibernateMode" -> setHibernateMode(
                    request.requestId,
                    request.payload["mode"]?.toIntOrNull() ?: return,
                )
                else -> HelperProtocol.failure(request.requestId, "Command is not allowed")
            }
            writeReply(client, reply)
        } catch (error: Throwable) {
            logger.error("Client request failed", error)
        } finally {
            posix.close(client)
        }
    }

    private fun readAllowedUid(): Int? = runCatching {
        Files.readString(Path.of(HelperProtocol.ALLOWED_UID_PATH)).trim().toInt()
    }.getOrNull()

    private fun setDisableSleep(requestId: String, disabled: Boolean) = runCatching {
        val process = ProcessBuilder("/usr/bin/pmset", "-a", "disablesleep", if (disabled) "1" else "0")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exit = process.waitFor()
        if (exit == 0) HelperProtocol.success(requestId)
        else HelperProtocol.failure(requestId, output.ifBlank { "pmset exited with $exit" })
    }.getOrElse { HelperProtocol.failure(requestId, it.message ?: "pmset failed") }

    private fun setHibernateMode(requestId: String, mode: Int) = runCatching {
        val process = ProcessBuilder("/usr/bin/pmset", "-a", "hibernatemode", mode.toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exit = process.waitFor()
        if (exit == 0) HelperProtocol.success(requestId)
        else HelperProtocol.failure(requestId, output.ifBlank { "pmset exited with $exit" })
    }.getOrElse { HelperProtocol.failure(requestId, it.message ?: "pmset failed") }

    private fun readLine(fd: Int): String? {
        val output = StringBuilder()
        val one = Memory(1)
        while (output.length < MAX_REQUEST_BYTES) {
            val count = posix.read(fd, one, 1)
            if (count <= 0L) return output.takeIf { it.isNotEmpty() }?.toString()
            val byte = one.getByte(0).toInt() and 0xFF
            if (byte == '\n'.code) return output.toString()
            output.append(byte.toChar())
        }
        return null
    }

    private fun writeReply(fd: Int, reply: com.wyz.covio.core.HelperReply) {
        val bytes = (HelperProtocol.encodeReply(reply) + "\n").toByteArray(StandardCharsets.UTF_8)
        val memory = Memory(bytes.size.toLong())
        memory.write(0, bytes, 0, bytes.size)
        var offset = 0
        while (offset < bytes.size) {
            val count = posix.write(fd, memory.share(offset.toLong()), (bytes.size - offset).toLong())
            if (count <= 0L) return
            offset += count.toInt()
        }
    }

    companion object {
        private const val AF_UNIX = 1
        private const val SOCK_STREAM = 1
        private const val MAX_REQUEST_BYTES = 64 * 1024
    }
}
