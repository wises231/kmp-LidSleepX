package com.wyz.lidsleepx.app.platform

import com.wyz.lidsleepx.core.APP_ID
import com.wyz.lidsleepx.core.LoginItem
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MacLoginItem(
    private val plistPath: Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", "$APP_ID.plist"),
    private val launchctl: (List<String>) -> Boolean = ::runLaunchctl,
) : LoginItem {
    override fun isEnabled(): Boolean = Files.exists(plistPath)

    override fun enable(): Boolean = runCatching {
        Files.createDirectories(plistPath.parent)
        Files.writeString(plistPath, plistText(), StandardCharsets.UTF_8)
        val domain = "gui/${currentUid()}"
        val target = "$domain/$APP_ID"
        if (!launchctl(listOf("enable", target))) return false
        if (launchctl(listOf("print", target))) return true
        launchctl(listOf("bootstrap", domain, plistPath.toString()))
    }.getOrDefault(false)

    override fun disable(): Boolean = runCatching {
        Files.deleteIfExists(plistPath)
        launchctl(listOf("disable", "gui/${currentUid()}/$APP_ID"))
    }.getOrDefault(false)

    fun developmentCommand(): String {
        val (command, arguments) = currentCommand()
        return commandLine(command, arguments)
    }

    internal fun plistTextFor(command: String, arguments: List<String>): String = plistText(command, arguments)

    private fun currentCommand(): Pair<String, List<String>> {
        val packagedPath = System.getProperty("jpackage.app-path")
        if (!packagedPath.isNullOrBlank()) return packagedPath to emptyList()
        val info = ProcessHandle.current().info()
        val command = info.command().orElse("java")
        val arguments = info.arguments().orElse(emptyArray()).toList()
        return command to arguments
    }

    private fun plistText(): String {
        val (command, arguments) = currentCommand()
        return plistText(command, arguments)
    }

    private fun plistText(command: String, arguments: List<String>): String {
        val values = (listOf(command) + arguments).joinToString("\n") { "        <string>${xmlEscape(it)}</string>" }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$APP_ID</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |$values
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |    <key>KeepAlive</key>
            |    <dict>
            |        <key>SuccessfulExit</key>
            |        <false/>
            |    </dict>
            |    <key>ProcessType</key>
            |    <string>Interactive</string>
            |</dict>
            |</plist>
        """.trimMargin()
    }

    companion object {
        fun currentUid(): String = ProcessBuilder("/usr/bin/id", "-u").start().inputStream.bufferedReader().readText().trim()

        fun commandLine(command: String, arguments: List<String>): String =
            (listOf(command) + arguments).joinToString(" ") { shellQuote(it) }

        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        fun xmlEscape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

private fun runLaunchctl(args: List<String>): Boolean {
    val process = ProcessBuilder(listOf("/bin/launchctl") + args)
        .redirectErrorStream(true)
        .start()
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return false
    }
    process.inputStream.bufferedReader().readText()
    return process.exitValue() == 0
}
