package com.wyz.covio.app.platform

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MarkupService {
    fun edit(path: Path): Boolean {
        return if (MacShelfNative.openMarkup(path)) true else fallback(path)
    }

    private fun fallback(path: Path): Boolean = runCatching {
        val process = ProcessBuilder("/usr/bin/open", "-a", "Preview", path.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
        process.waitFor(5L, TimeUnit.SECONDS)
        process.exitValue() == 0
    }.getOrDefault(false)

}
