package com.wyz.covio.core

import java.nio.file.Path

object ShelfLayout {
    const val PANEL_HEIGHT = 210f
    const val CARD_WIDTH = 150f
    const val SPACING = 174f
    const val MAX_SAG = 30f
    const val MIN_CAPACITY = 3
    const val MAX_CAPACITY = 12

    fun capacity(screenWidth: Float): Int {
        if (!screenWidth.isFinite() || screenWidth <= 0f) return MIN_CAPACITY
        val raw = (screenWidth / SPACING).toInt() + 1
        return raw.coerceIn(MIN_CAPACITY, MAX_CAPACITY)
    }

    fun sag(screenWidth: Float): Float {
        if (!screenWidth.isFinite() || screenWidth <= 0f) return 0f
        return minOf(MAX_SAG, screenWidth * 0.018f)
    }

    fun ropeY(x: Float, screenWidth: Float): Float {
        if (!screenWidth.isFinite() || screenWidth <= 0f) return 10f
        val fraction = (x / screenWidth).coerceIn(0f, 1f)
        return 10f + 4f * sag(screenWidth) * fraction * (1f - fraction)
    }

    fun slotX(index: Int, count: Int, screenWidth: Float): Float {
        if (count <= 0) return screenWidth / 2f
        val total = (count - 1).coerceAtLeast(0) * SPACING
        return screenWidth / 2f - total / 2f + index.coerceAtLeast(0) * SPACING
    }

    fun cardBounds(index: Int, count: Int, screenWidth: Float): ShelfCardBounds {
        val left = slotX(index, count, screenWidth) - CARD_WIDTH / 2f
        val top = ropeY(left + CARD_WIDTH / 2f, screenWidth) + CARD_TOP_OFFSET
        return ShelfCardBounds(left, top, CARD_WIDTH, CARD_HEIGHT)
    }

    fun cardIndexAt(x: Float, y: Float, count: Int, screenWidth: Float): Int? {
        if (count <= 0) return null
        return (0 until count).firstOrNull { index ->
            cardBounds(index, count, screenWidth).contains(x, y)
        }
    }

    const val CARD_HEIGHT = 158f
    const val CARD_TOP_OFFSET = 8f
}

data class ShelfCardBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

enum class ShelfDropTarget { APPLICATION, FOLDER, DESKTOP, TRASH, INVALID }
enum class ShelfDropAction { COPY_AND_KEEP, MOVE_AND_REMOVE, TRASH_AND_REMOVE, CANCEL }

enum class ShelfDragResult { COPY, MOVE, DELETE, CANCEL }

const val NS_DRAG_OPERATION_COPY = 1L
const val NS_DRAG_OPERATION_MOVE = 2L
const val NS_DRAG_OPERATION_DELETE = 32L

fun shelfDragResultFor(operationMask: Long): ShelfDragResult = when {
    operationMask and NS_DRAG_OPERATION_DELETE != 0L -> ShelfDragResult.DELETE
    operationMask and NS_DRAG_OPERATION_MOVE != 0L -> ShelfDragResult.MOVE
    operationMask and NS_DRAG_OPERATION_COPY != 0L -> ShelfDragResult.COPY
    else -> ShelfDragResult.CANCEL
}

fun dropActionFor(target: ShelfDropTarget): ShelfDropAction = when (target) {
    ShelfDropTarget.APPLICATION -> ShelfDropAction.COPY_AND_KEEP
    ShelfDropTarget.FOLDER,
    ShelfDropTarget.DESKTOP,
    -> ShelfDropAction.MOVE_AND_REMOVE

    ShelfDropTarget.TRASH -> ShelfDropAction.TRASH_AND_REMOVE
    ShelfDropTarget.INVALID -> ShelfDropAction.CANCEL
}

val SHELF_IMAGE_EXTENSIONS: Set<String> = setOf(
    "png",
    "jpg",
    "jpeg",
    "heic",
    "tif",
    "tiff",
    "gif",
    "webp",
)

fun isShelfImageFile(path: Path): Boolean =
    path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase() in SHELF_IMAGE_EXTENSIONS

fun acceptsShelfFile(
    path: Path,
    dedicatedDirectory: Path,
    hasScreenCaptureAttribute: (Path) -> Boolean,
): Boolean {
    if (!isShelfImageFile(path)) return false
    val parent = path.toAbsolutePath().normalize()
    val dedicated = dedicatedDirectory.toAbsolutePath().normalize()
    return parent.startsWith(dedicated) || hasScreenCaptureAttribute(path)
}

class ShelfCollection(
    initialItems: List<ShelfItem> = emptyList(),
    private val maxItems: Int = ShelfStore.MAX_ITEMS,
    private val exists: (Path) -> Boolean = { java.nio.file.Files.exists(it) },
) {
    private val items = ArrayDeque<ShelfItem>()

    init {
        initialItems.forEach { add(Path.of(it.path), it.addedAtEpochSeconds) }
    }

    fun snapshot(): List<ShelfItem> = items.toList()

    fun add(path: Path, addedAtEpochSeconds: Long = 0L): Boolean {
        if (!exists(path)) return false
        val normalized = ShelfStore.canonicalPath(path.toString())
        if (items.any { ShelfStore.canonicalPath(it.path) == normalized }) return false
        items.addLast(ShelfItem(normalized, addedAtEpochSeconds.coerceAtLeast(0L)))
        while (items.size > maxItems.coerceAtLeast(0)) items.removeFirst()
        return true
    }

    fun remove(path: Path): Boolean {
        val normalized = ShelfStore.canonicalPath(path.toString())
        val removed = items.removeIf { ShelfStore.canonicalPath(it.path) == normalized }
        return removed
    }

    fun prune(): List<ShelfItem> {
        val removed = items.filterNot { exists(Path.of(it.path)) }
        items.removeIf { !exists(Path.of(it.path)) }
        return removed
    }

    fun clear(): List<ShelfItem> {
        val previous = items.toList()
        items.clear()
        return previous
    }
}

private fun ArrayDeque<ShelfItem>.removeIf(predicate: (ShelfItem) -> Boolean): Boolean {
    val keep = filterNot(predicate)
    val changed = keep.size != size
    clear()
    addAll(keep)
    return changed
}
