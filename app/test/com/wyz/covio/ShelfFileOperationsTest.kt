package com.wyz.covio.app

import com.wyz.covio.app.platform.ShelfFileOperations
import com.wyz.covio.core.ShelfDropAction
import com.wyz.covio.core.ShelfDropTarget
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShelfFileOperationsTest {
    private val operations = ShelfFileOperations()

    @Test
    fun `copy keeps the source and creates a destination copy`() {
        val source = Files.createTempFile("covio-source", ".png")
        val destination = Files.createTempDirectory("covio-destination")
        Files.writeString(source, "image")

        assertTrue(operations.perform(ShelfDropAction.COPY_AND_KEEP, source, destination, ShelfDropTarget.APPLICATION))

        assertTrue(Files.exists(source))
        assertEquals("image", Files.readString(destination.resolve(source.fileName.toString())))
    }

    @Test
    fun `move removes the source and creates a destination file`() {
        val source = Files.createTempFile("covio-source", ".png")
        val destination = Files.createTempDirectory("covio-destination")
        Files.writeString(source, "image")

        assertTrue(operations.perform(ShelfDropAction.MOVE_AND_REMOVE, source, destination, ShelfDropTarget.FOLDER))

        assertFalse(Files.exists(source))
        assertEquals("image", Files.readString(destination.resolve(source.fileName.toString())))
    }

    @Test
    fun `cancel leaves the source in place`() {
        val source = Files.createTempFile("covio-source", ".png")
        Files.writeString(source, "image")

        assertFalse(operations.perform(ShelfDropAction.CANCEL, source, null, ShelfDropTarget.INVALID))
        assertTrue(Files.exists(source))
    }
}
