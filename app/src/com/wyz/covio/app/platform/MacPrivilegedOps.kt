package com.wyz.covio.app.platform

import com.wyz.covio.app.helper.HelperClient
import com.wyz.covio.app.helper.HelperInstaller
import com.wyz.covio.app.helper.HelperProtocol
import com.wyz.covio.core.AppLogger
import com.wyz.covio.core.HelperStatus
import com.wyz.covio.core.PrivilegedOps
import com.wyz.covio.core.SUPPORTED_HIBERNATE_MODES
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MacPrivilegedOps(
    private val logger: AppLogger,
    private val executablePath: String = currentExecutablePath(),
) : PrivilegedOps {
    private val client = HelperClient()

    override fun isInstalled(): Boolean = Files.exists(Path.of(HelperProtocol.SOCKET_PATH))

    override fun status(): HelperStatus {
        if (!isInstalled()) return HelperStatus.NOT_INSTALLED
        val version = helperVersion() ?: return HelperStatus.ERROR
        return if (version == com.wyz.covio.core.APP_VERSION) HelperStatus.INSTALLED else HelperStatus.OUTDATED
    }

    override fun helperVersion(): String? = client.version()

    override fun install(): Boolean = HelperInstaller.install(executablePath, logger::info)

    override fun uninstall(): Boolean = HelperInstaller.uninstall(logger::info)

    override fun setDisableSleep(disabled: Boolean): Boolean {
        if (!client.setDisableSleep(disabled)) {
            logger.warn("Privileged helper did not accept setDisableSleep($disabled)")
            return false
        }
        return true
    }

    override fun hibernateMode(): Int? = runCatching {
        val process = ProcessBuilder("/usr/bin/pmset", "-g")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        Regex("""(?m)^\s*hibernatemode\s+(\d+)\s*$""")
            .find(process.inputStream.bufferedReader().readText())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in SUPPORTED_HIBERNATE_MODES }
    }.getOrNull()

    override fun setHibernateMode(mode: Int): Boolean {
        if (mode !in SUPPORTED_HIBERNATE_MODES) return false
        if (!client.setHibernateMode(mode)) {
            logger.warn("Privileged helper did not accept setHibernateMode($mode)")
            return false
        }
        return true
    }

    companion object {
        private fun currentExecutablePath(): String {
            val command = ProcessHandle.current().info().command().orElse("")
            return if (command.endsWith("Covio")) command else command.ifBlank { System.getProperty("java.home") }
        }
    }
}
