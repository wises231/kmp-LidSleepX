package com.wyz.covio.app

import com.wyz.covio.app.platform.ScreenshotPreferenceStore
import com.wyz.covio.app.platform.ScreenshotTakeoverController
import com.wyz.covio.core.ScreenshotInboxSnapshot
import com.wyz.covio.core.ShelfStoreState
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenshotTakeoverControllerTest {
    @Test
    fun `enable saves the old values and applies the screenshot directory`() {
        val store = FakePreferenceStore(ScreenshotInboxSnapshot("/tmp/Desktop", null, true))
        var state = ShelfStoreState()
        val controller = controller(store, { state }) { state = it }

        assertTrue(controller.enable())

        assertTrue(controller.active)
        assertEquals(1, store.readCount)
        assertEquals(1, store.applyCount)
        assertEquals(ScreenshotInboxSnapshot("/tmp/Desktop", null, true), state.preferenceSnapshot)
    }

    @Test
    fun `repeated enable reuses the saved values`() {
        val store = FakePreferenceStore(ScreenshotInboxSnapshot("/tmp/Desktop", null, true))
        var state = ShelfStoreState()
        val controller = controller(store, { state }) { state = it }

        assertTrue(controller.enable())
        assertTrue(controller.enable())

        assertEquals(1, store.readCount)
        assertEquals(2, store.applyCount)
    }

    @Test
    fun `disable restores the old values and clears the snapshot`() {
        val oldValues = ScreenshotInboxSnapshot("/tmp/Desktop", "/tmp/Other", false)
        val store = FakePreferenceStore(oldValues)
        var state = ShelfStoreState()
        val controller = controller(store, { state }) { state = it }
        assertTrue(controller.enable())

        assertTrue(controller.disable())

        assertFalse(controller.active)
        assertEquals(oldValues, store.restoredSnapshot)
        assertNull(state.preferenceSnapshot)
    }

    @Test
    fun `restore on exit restores the old values`() {
        val oldValues = ScreenshotInboxSnapshot("/tmp/Desktop", null, true)
        val store = FakePreferenceStore(oldValues)
        var state = ShelfStoreState()
        val controller = controller(store, { state }) { state = it }
        assertTrue(controller.enable())

        assertTrue(controller.restoreOnExit())

        assertEquals(oldValues, store.restoredSnapshot)
        assertNull(state.preferenceSnapshot)
        assertFalse(controller.active)
    }

    @Test
    fun `restart reuses a saved snapshot instead of reading current preferences`() {
        val oldValues = ScreenshotInboxSnapshot("/tmp/Desktop", null, true)
        val store = FakePreferenceStore(ScreenshotInboxSnapshot("/tmp/Current", null, false))
        var state = ShelfStoreState(preferenceSnapshot = oldValues)
        val controller = controller(store, { state }) { state = it }

        assertTrue(controller.enable())

        assertEquals(0, store.readCount)
        assertEquals(oldValues, state.preferenceSnapshot)
        assertEquals(1, store.applyCount)
    }

    @Test
    fun `failed apply leaves the controller inactive`() {
        val store = FakePreferenceStore(ScreenshotInboxSnapshot("/tmp/Desktop", null, true), applyResult = false)
        var state = ShelfStoreState()
        val controller = controller(store, { state }) { state = it }

        assertFalse(controller.enable())

        assertFalse(controller.active)
        assertEquals(1, store.readCount)
    }

    private fun controller(
        store: FakePreferenceStore,
        loadState: () -> ShelfStoreState,
        saveState: (ShelfStoreState) -> Unit,
    ): ScreenshotTakeoverController = ScreenshotTakeoverController(
        store = store,
        directory = Path.of("/tmp/Covio Screenshots"),
        loadState = loadState,
        saveState = { updated ->
            saveState(updated)
            updated
        },
    )

    private class FakePreferenceStore(
        private val values: ScreenshotInboxSnapshot,
        private val applyResult: Boolean = true,
    ) : ScreenshotPreferenceStore {
        var readCount = 0
        var applyCount = 0
        var restoredSnapshot: ScreenshotInboxSnapshot? = null

        override fun read(): ScreenshotInboxSnapshot {
            readCount += 1
            return values
        }

        override fun apply(directory: Path): Boolean {
            applyCount += 1
            return applyResult
        }

        override fun restore(snapshot: ScreenshotInboxSnapshot): Boolean {
            restoredSnapshot = snapshot
            return true
        }
    }
}
