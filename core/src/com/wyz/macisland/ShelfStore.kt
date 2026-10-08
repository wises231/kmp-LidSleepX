package com.wyz.macisland.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ShelfStore(
    val path: Path = defaultShelfPath(),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun load(): ShelfStoreState {
        if (!Files.exists(path)) return ShelfStoreState()
        return runCatching {
            json.decodeFromString<ShelfStoreState>(Files.readString(path, StandardCharsets.UTF_8))
        }.getOrElse { ShelfStoreState() }.normalized()
    }

    fun save(state: ShelfStoreState): ShelfStoreState {
        val normalized = state.normalized()
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(
            temporary,
            json.encodeToString(ShelfStoreState.serializer(), normalized),
            StandardCharsets.UTF_8,
        )
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return normalized
    }

    fun clear(): Boolean = runCatching {
        Files.deleteIfExists(path)
        Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + ".tmp"))
    }.isSuccess

    companion object {
        const val MAX_ITEMS = 12

        fun defaultShelfPath(): Path = Path.of(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "MacIsland",
            "Screenshots",
            "shelf.json",
        )

        fun normalizeItems(
            items: List<ShelfItem>,
            limit: Int = MAX_ITEMS,
            exists: (Path) -> Boolean = { Files.exists(it) },
        ): List<ShelfItem> {
            val seen = LinkedHashSet<String>()
            return items.asSequence()
                .map(ShelfItem::normalized)
                .filter { it.path.isNotBlank() }
                .filter { item -> seen.add(canonicalPath(item.path)) }
                .filter { exists(Path.of(it.path)) }
                .toList()
                .takeLast(limit.coerceAtLeast(0))
        }

        fun canonicalPath(value: String): String =
            runCatching { Path.of(value).toAbsolutePath().normalize().toString() }.getOrDefault(value)
    }
}

private fun ShelfStoreState.normalized(): ShelfStoreState = copy(
    items = ShelfStore.normalizeItems(items),
)
