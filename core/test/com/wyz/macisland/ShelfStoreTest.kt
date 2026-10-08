package com.wyz.macisland.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShelfStoreTest {
    @Test
    fun `store round trips items revealed state and screenshot snapshot`() {
        val directory = Files.createTempDirectory("macisland-shelf")
        val first = directory.resolve("one.png")
        Files.writeString(first, "one")
        val path = directory.resolve("shelf.json")
        val store = ShelfStore(path)
        val expected = ShelfStoreState(
            items = listOf(ShelfItem(first.toString(), 12L)),
            revealed = false,
            preferenceSnapshot = ScreenshotInboxSnapshot("/tmp", null, true),
        )

        store.save(expected)

        assertEquals(expected, store.load())
    }

    @Test
    fun `normalization removes duplicate paths and missing files`() {
        val directory = Files.createTempDirectory("macisland-shelf")
        val first = directory.resolve("one.png")
        val second = directory.resolve("two.png")
        Files.writeString(first, "one")
        Files.writeString(second, "two")
        val state = ShelfStoreState(
            items = listOf(
                ShelfItem(first.toString(), 1L),
                ShelfItem(first.toString(), 2L),
                ShelfItem(second.toString(), 3L),
                ShelfItem(directory.resolve("missing.png").toString(), 4L),
            ),
        )

        val loaded = ShelfStore(ShelfStore.defaultShelfPath()).let {
            ShelfStore.normalizeItems(state.items) { candidate -> Files.exists(candidate) }
        }

        assertEquals(listOf(first.toString(), second.toString()), loaded.map(ShelfItem::path))
    }

    @Test
    fun `normalization keeps only the newest configured number of items`() {
        val directory = Files.createTempDirectory("macisland-shelf")
        val items = (0 until 5).map { index ->
            val file = directory.resolve("$index.png")
            Files.writeString(file, index.toString())
            ShelfItem(file.toString(), index.toLong())
        }

        val loaded = ShelfStore.normalizeItems(items, limit = 3) { Files.exists(it) }

        assertEquals(listOf("2.png", "3.png", "4.png"), loaded.map { java.nio.file.Path.of(it.path).fileName.toString() })
    }

    @Test
    fun `clear removes the shelf file and load returns defaults`() {
        val directory = Files.createTempDirectory("macisland-shelf")
        val path = directory.resolve("shelf.json")
        val store = ShelfStore(path)
        store.save(ShelfStoreState(items = listOf(ShelfItem("/tmp/example.png"))))

        assertTrue(Files.exists(path))
        assertTrue(store.clear())
        assertFalse(Files.exists(path))
        assertEquals(ShelfStoreState(), store.load())
    }
}
