package com.wyz.lidsleepx.app.platform

internal class IdleSleepTimeoutCache(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutReader: () -> Long?,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val lock = Any()
    private var valid = false
    private var cachedAtMillis = 0L
    private var cachedTimeoutSeconds: Long? = null

    fun get(): Long? {
        val now = clock()
        synchronized(lock) {
            if (valid && now - cachedAtMillis in 0 until ttlMillis) return cachedTimeoutSeconds
        }

        val timeout = runCatching(timeoutReader).getOrNull()
        synchronized(lock) {
            cachedTimeoutSeconds = timeout
            cachedAtMillis = now
            valid = true
        }
        return timeout
    }

    fun invalidate() {
        synchronized(lock) {
            valid = false
            cachedAtMillis = 0L
            cachedTimeoutSeconds = null
        }
    }

    companion object {
        private const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
