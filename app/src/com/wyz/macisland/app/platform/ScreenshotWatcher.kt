package com.wyz.macisland.app.platform

import com.wyz.macisland.core.acceptsShelfFile
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ScreenshotWatcher(
    private val directory: Path,
    private val dedicatedDirectory: Path,
    private val requireScreenCaptureAttribute: Boolean,
    private val onNew: (Path) -> Unit,
    private val onChange: () -> Unit = {},
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val attributeReader: (Path) -> Boolean = ::hasScreenCaptureAttribute,
    private val debounceSleeper: (Long) -> Unit = { Thread.sleep(it) },
) : AutoCloseable {
    private val known = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var watchService: WatchService? = null

    fun start() {
        if (running) return
        runCatching { Files.createDirectories(directory) }
        if (!Files.isDirectory(directory)) return
        known += listFiles().map(::canonical)
        running = true
        thread = Thread(::watchLoop, "MacIsland-screenshot-watcher").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        running = false
        runCatching { watchService?.close() }
        thread?.interrupt()
        thread = null
        watchService = null
    }

    fun isCandidate(path: Path): Boolean = candidate(path)

    internal fun scanNow() {
        if (!Files.isDirectory(directory)) return
        val current = listFiles()
        var changed = false
        for (path in current) {
            if (known.add(canonical(path)) && candidate(path)) {
                onNew(path)
                changed = true
            }
        }
        if (changed) onChange()
    }

    internal fun awaitDebounce() {
        if (debounceMillis > 0L) debounceSleeper(debounceMillis)
    }

    private fun watchLoop() {
        val service = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull() ?: return
        watchService = service
        runCatching {
            directory.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
        }.getOrElse { return }
        while (running) {
            val key = runCatching { service.poll(500L, TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
            drain(key)
            awaitDebounce()
            drain(key)
            scanNow()
            onChange()
        }
    }

    private fun drain(key: WatchKey) {
        key.pollEvents()
        key.reset()
    }

    private fun listFiles(): List<Path> = runCatching {
        Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) }.toList()
        }
    }.getOrDefault(emptyList())

    private fun candidate(path: Path): Boolean =
        acceptsShelfFile(path, dedicatedDirectory) { candidatePath ->
            !requireScreenCaptureAttribute || attributeReader(candidatePath)
        }

    private fun canonical(path: Path): String = runCatching { path.toRealPath().toString() }
        .getOrElse { path.toAbsolutePath().normalize().toString() }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 200L

        fun desktop(): Path = Path.of(System.getProperty("user.home"), "Desktop")
        fun dedicated(): Path = Path.of(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "MacIsland",
            "Screenshots",
        )
    }
}

private interface ExtendedLibC : Library {
    fun getxattr(path: String, name: String, value: Pointer?, size: Long, position: Int, options: Int): Long
}

private val extendedLibC: ExtendedLibC? by lazy {
    runCatching { Native.load("c", ExtendedLibC::class.java) }.getOrNull()
}

internal fun hasScreenCaptureAttribute(path: Path): Boolean = runCatching {
    extendedLibC?.getxattr(
        path.toAbsolutePath().toString(),
        "com.apple.metadata:kMDItemIsScreenCapture",
        null,
        0L,
        0,
        0,
    )?.let { it >= 0L } ?: false
}.getOrDefault(false)
