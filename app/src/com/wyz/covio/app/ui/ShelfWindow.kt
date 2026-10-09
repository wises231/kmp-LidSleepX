@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.wyz.covio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.wyz.covio.core.ShelfItem
import com.wyz.covio.core.ShelfLayout
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun ShelfWindow(runtime: AppRuntime) {
    if (!runtime.config.shelfEnabled || !runtime.state.shelf.visible) return
    val screen = runtime.shelfScreenBounds() ?: return
    val windowState = rememberWindowState(
        width = screen.width.dp,
        height = ShelfLayout.PANEL_HEIGHT.dp,
        position = WindowPosition.Absolute(screen.x.dp, screen.y.dp),
    )
    Window(
        onCloseRequest = runtime::hideShelf,
        state = windowState,
        title = runtime.strings.shelf,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        DisposableEffect(window) {
            runtime.attachShelfWindow(window)
            onDispose { runtime.detachShelfWindow(window) }
        }
        LaunchedEffect(runtime.state.shelf.visible) {
            runtime.setShelfMouseInside(false)
        }
        MaterialTheme {
            ShelfContent(
                runtime = runtime,
                screen = screen,
                onPointerInside = { runtime.setShelfMouseInside(it) },
            )
        }
    }
}

@Composable
private fun ShelfContent(
    runtime: AppRuntime,
    screen: Rectangle,
    onPointerInside: (Boolean) -> Unit,
) {
    val items = runtime.state.shelf.items.takeLast(ShelfLayout.capacity(screen.width.toFloat()))
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Enter) { onPointerInside(true) }
            .onPointerEvent(PointerEventType.Exit) { onPointerInside(false) },
    ) {
        val width = maxWidth.value
        Canvas(Modifier.fillMaxSize()) {
            val ropeColor = Color(0xCC2B2B2E)
            val rope = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, ShelfLayout.ropeY(0f, width).dp.toPx())
                val steps = 48
                for (index in 1..steps) {
                    val x = width * index / steps
                    lineTo(x.dp.toPx(), ShelfLayout.ropeY(x, width).dp.toPx())
                }
            }
            drawPath(rope, ropeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        if (items.isEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 76.dp)
                    .background(Color(0xD91C1C1E), RoundedCornerShape(12.dp)),
            ) {
                Text(
                    runtime.strings.shelfEmpty,
                    color = Color.White,
                    modifier = Modifier.offset(x = 16.dp, y = 8.dp).size(width = 120.dp, height = 24.dp),
                )
            }
        }
        items.forEachIndexed { index, item ->
            val x = ShelfLayout.slotX(index, items.size, width) - ShelfLayout.CARD_WIDTH / 2f
            val y = ShelfLayout.ropeY(x + ShelfLayout.CARD_WIDTH / 2f, width) + 8f
            ShelfCard(
                runtime = runtime,
                item = item,
                modifier = Modifier.offset(x.dp, y.dp),
            )
        }
    }
}

@Composable
private fun ShelfCard(
    runtime: AppRuntime,
    item: ShelfItem,
    modifier: Modifier = Modifier,
) {
    var menuVisible by remember(item.path) { mutableStateOf(false) }
    val bitmap = remember(item.path) { loadImage(item.path) }
    Box(
        modifier
            .width(ShelfLayout.CARD_WIDTH.dp)
            .height(158.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2FFFFFF))
            .combinedClickable(
                onClick = { runtime.copyShelfItem(item) },
                onDoubleClick = { runtime.openShelfItem(item) },
                onLongClick = { runtime.markupShelfItem(item) },
            )
            .pointerInput(item.path) {
                detectDragGestures(
                    onDragStart = { runtime.startShelfDrag(item) },
                    onDrag = { change, _ -> change.consume() },
                )
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    menuVisible = true
                }
            },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFE5E5EA)))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 6.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xB3000000))
                .combinedClickable(onClick = { menuVisible = true }),
            contentAlignment = Alignment.Center,
        ) {
            Text("⋯", color = Color.White, fontSize = 15.sp)
        }
        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
            DropdownMenuItem(onClick = {
                menuVisible = false
                runtime.copyShelfItem(item)
            }) { Text(runtime.strings.screenshotCopy) }
            DropdownMenuItem(onClick = {
                menuVisible = false
                runtime.openShelfItem(item)
            }) { Text(runtime.strings.screenshotPreview) }
            DropdownMenuItem(onClick = {
                menuVisible = false
                runtime.markupShelfItem(item)
            }) { Text(runtime.strings.screenshotMarkup) }
            DropdownMenuItem(onClick = {
                menuVisible = false
                runtime.removeShelfItem(item)
            }) { Text(runtime.strings.screenshotRemove) }
        }
    }
}

private fun loadImage(path: String): ImageBitmap? = runCatching {
    SkiaImage.makeFromEncoded(Files.readAllBytes(Path.of(path))).toComposeImageBitmap()
}.getOrNull()
