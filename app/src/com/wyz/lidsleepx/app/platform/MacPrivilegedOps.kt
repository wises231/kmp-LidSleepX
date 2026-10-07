package com.wyz.lidsleepx.app.platform

import com.wyz.lidsleepx.app.helper.HelperClient
import com.wyz.lidsleepx.app.helper.HelperInstaller
import com.wyz.lidsleepx.app.helper.HelperProtocol
import com.wyz.lidsleepx.core.AppLogger
import com.wyz.lidsleepx.core.HelperStatus
import com.wyz.lidsleepx.core.PrivilegedOps
import java.nio.file.Files
import java.nio.file.Path

class MacPrivilegedOps(
    private val logger: AppLogger,
    private val executablePath: String = currentExecutablePath(),
) : PrivilegedOps {
    private val client = HelperClient()

    override fun isInstalled(): Boolean = Files.exists(Path.of(HelperProtocol.SOCKET_PATH))

    override fun status(): HelperStatus {
        if (!isInstalled()) return HelperStatus.NOT_INSTALLED
        val version = helperVersion() ?: return HelperStatus.ERROR
        return if (version == com.wyz.lidsleepx.core.APP_VERSION) HelperStatus.INSTALLED else HelperStatus.OUTDATED
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

    companion object {
        private fun currentExecutablePath(): String {
            val command = ProcessHandle.current().info().command().orElse("")
            return if (command.endsWith("LidSleepX")) command else command.ifBlank { System.getProperty("java.home") }
        }
    }
}
