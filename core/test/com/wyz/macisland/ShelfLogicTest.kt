package com.wyz.macisland.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfLogicTest {
    @Test
    fun `layout capacity is clamped and spacing matches the reference`() {
        assertEquals(3, ShelfLayout.capacity(300f))
        assertEquals(9, ShelfLayout.capacity(1_440f))
        assertEquals(12, ShelfLayout.capacity(4_000f))
        assertEquals(150f, ShelfLayout.CARD_WIDTH)
        assertEquals(174f, ShelfLayout.SPACING)
        assertEquals(210f, ShelfLayout.PANEL_HEIGHT)
    }

    @Test
    fun `rope follows a parabola and card slots center around the screen`() {
        assertEquals(10f, ShelfLayout.ropeY(0f, 1_000f))
        assertEquals(28f, ShelfLayout.ropeY(500f, 1_000f))
        assertEquals(10f, ShelfLayout.ropeY(1_000f, 1_000f))
        assertEquals(326f, ShelfLayout.slotX(0, 3, 1_000f))
        assertEquals(674f, ShelfLayout.slotX(2, 3, 1_000f))
    }

    @Test
    fun `drop actions keep copy move remove and cancel distinct`() {
        assertEquals(ShelfDropAction.COPY_AND_KEEP, dropActionFor(ShelfDropTarget.APPLICATION))
        assertEquals(ShelfDropAction.MOVE_AND_REMOVE, dropActionFor(ShelfDropTarget.FOLDER))
        assertEquals(ShelfDropAction.MOVE_AND_REMOVE, dropActionFor(ShelfDropTarget.DESKTOP))
        assertEquals(ShelfDropAction.TRASH_AND_REMOVE, dropActionFor(ShelfDropTarget.TRASH))
        assertEquals(ShelfDropAction.CANCEL, dropActionFor(ShelfDropTarget.INVALID))
    }

    @Test
    fun `drag operation masks map to copy move delete and cancel`() {
        assertEquals(ShelfDragResult.COPY, shelfDragResultFor(NS_DRAG_OPERATION_COPY))
        assertEquals(ShelfDragResult.MOVE, shelfDragResultFor(NS_DRAG_OPERATION_MOVE))
        assertEquals(ShelfDragResult.DELETE, shelfDragResultFor(NS_DRAG_OPERATION_DELETE))
        assertEquals(ShelfDragResult.CANCEL, shelfDragResultFor(0L))
    }

    @Test
    fun `card bounds and hit testing use the visible slot`() {
        val first = ShelfLayout.cardBounds(0, 3, 1_000f)
        val second = ShelfLayout.cardBounds(1, 3, 1_000f)

        assertEquals(251f, first.left)
        assertEquals(ShelfLayout.CARD_WIDTH, first.width)
        assertEquals(ShelfLayout.CARD_HEIGHT, first.height)
        assertTrue(first.contains(first.left + 10f, first.top + 10f))
        assertTrue(second.contains(second.left + 10f, second.top + 10f))
        assertFalse(first.contains(second.left + 10f, second.top + 10f))
        assertEquals(0, ShelfLayout.cardIndexAt(first.left + 10f, first.top + 10f, 3, 1_000f))
        assertEquals(1, ShelfLayout.cardIndexAt(second.left + 10f, second.top + 10f, 3, 1_000f))
        assertNull(ShelfLayout.cardIndexAt(0f, 0f, 3, 1_000f))
    }

    @Test
    fun `dedicated folder accepts images while other folders require the screenshot tag`() {
        val root = Files.createTempDirectory("macisland-accept")
        val dedicated = Files.createDirectories(root.resolve("screenshots"))
        val file = Files.writeString(dedicated.resolve("capture.PNG"), "image")
        val other = Files.writeString(root.resolve("other.png"), "image")

        assertTrue(acceptsShelfFile(file, dedicated) { false })
        assertFalse(acceptsShelfFile(other, dedicated) { false })
        assertTrue(acceptsShelfFile(other, dedicated) { true })
        assertFalse(acceptsShelfFile(root.resolve("notes.txt"), dedicated) { true })
    }

    @Test
    fun `collection deduplicates limits and removes missing files`() {
        val root = Files.createTempDirectory("macisland-collection")
        val files = (0 until 5).map { Files.writeString(root.resolve("$it.png"), "image") }
        val collection = ShelfCollection(maxItems = 3)

        files.forEach { path -> assertTrue(collection.add(path)) }
        assertFalse(collection.add(files.last(), 99L))
        assertEquals(listOf("2.png", "3.png", "4.png"), collection.snapshot().map { java.nio.file.Path.of(it.path).fileName.toString() })

        Files.deleteIfExists(files[3])
        assertEquals(listOf("3.png"), collection.prune().map { java.nio.file.Path.of(it.path).fileName.toString() })
        assertTrue(collection.remove(files[4]))
        assertTrue(collection.remove(files[2]))
        assertTrue(collection.snapshot().isEmpty())
    }
}
