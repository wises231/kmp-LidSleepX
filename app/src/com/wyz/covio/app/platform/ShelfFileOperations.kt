package com.wyz.covio.app.platform

import com.wyz.covio.core.ShelfDropAction
import com.wyz.covio.core.ShelfDropTarget
import com.wyz.covio.core.dropActionFor
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ShelfFileOperations {
    fun copyTo(destination: Path, source: Path): Boolean = runCatching {
        requireDirectory(destination)
        val target = uniqueTarget(destination, source.fileName.toString())
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
        true
    }.getOrDefault(false)

    fun moveTo(destination: Path, source: Path): Boolean = runCatching {
        requireDirectory(destination)
        val target = uniqueTarget(destination, source.fileName.toString())
        runCatching { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE) }
            .getOrElse { Files.move(source, target) }
        true
    }.getOrDefault(false)

    fun trash(source: Path): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            Desktop.getDesktop().moveToTrash(source.toFile())
        } else {
            moveToFallbackTrash(source)
            true
        }
    }.getOrDefault(false)

    fun perform(action: ShelfDropAction, source: Path, destination: Path?, destinationKind: ShelfDropTarget): Boolean =
        when (action) {
            ShelfDropAction.COPY_AND_KEEP -> destination?.let { copyTo(it, source) } == true
            ShelfDropAction.MOVE_AND_REMOVE -> destination?.let { moveTo(it, source) } == true
            ShelfDropAction.TRASH_AND_REMOVE -> trash(source)
            ShelfDropAction.CANCEL -> false
        }.also { succeeded ->
            if (succeeded && action != ShelfDropAction.COPY_AND_KEEP && destinationKind != ShelfDropTarget.INVALID) {
                // The caller removes the shelf entry after the filesystem action succeeds.
            }
        }

    private fun requireDirectory(directory: Path) {
        require(Files.isDirectory(directory)) { "Destination is not a directory: $directory" }
    }

    private fun uniqueTarget(directory: Path, name: String): Path {
        val first = directory.resolve(name)
        if (!Files.exists(first)) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = directory.resolve("$base $index$extension")
            if (!Files.exists(candidate)) return candidate
            index += 1
        }
    }

    private fun moveToFallbackTrash(source: Path) {
        val trash = Path.of(System.getProperty("user.home"), ".Trash")
        Files.createDirectories(trash)
        Files.move(source, uniqueTarget(trash, source.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
    }
}

fun actionForTarget(target: ShelfDropTarget): ShelfDropAction = dropActionFor(target)
