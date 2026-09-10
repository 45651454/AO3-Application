package com.example.ao3application.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object ExportImport {

    private val json = Json { prettyPrint = true }

    fun export(
        favorites: List<SavedWork>,
        history: List<HistoryEntry>,
        tagFavorites: List<TagFavorite>,
    ): String {
        val root = buildJsonObject {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("favorites") {
                for (f in favorites) addJsonObject {
                    put("id", f.id)
                    put("title", f.title)
                    put("author", f.author)
                    put("savedAt", f.savedAt)
                }
            }
            putJsonArray("history") {
                for (h in history) addJsonObject {
                    put("id", h.id)
                    put("title", h.title)
                    put("author", h.author)
                    put("lastReadAt", h.lastReadAt)
                    put("progress", h.progress)
                }
            }
            putJsonArray("tagFavorites") {
                for (t in tagFavorites) addJsonObject {
                    put("name", t.name)
                    put("href", t.href)
                    put("savedAt", t.savedAt)
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun parse(text: String): Triple<List<SavedWork>, List<HistoryEntry>, List<TagFavorite>> {
        val root = Json.parseToJsonElement(text).jsonObject
        val favorites = (root["favorites"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            SavedWork(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                savedAt = o["savedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
        val history = (root["history"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            HistoryEntry(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lastReadAt = o["lastReadAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                progress = o["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
            )
        }
        val tagFavorites = (root["tagFavorites"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            TagFavorite(
                name = name,
                href = o["href"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                savedAt = o["savedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
        return Triple(favorites, history, tagFavorites)
    }

    fun exportFrom(db: LibraryDb): String =
        export(db.favorites(), db.history(), db.tagFavorites())

    fun importInto(db: LibraryDb, text: String): Triple<Int, Int, Int> {
        val (favorites, history, tagFavorites) = parse(text)
        favorites.forEach(db::upsertFavorite)
        tagFavorites.forEach(db::importTagFavorite)
        val historyAdded = history.count(db::importHistoryIfNewer)
        return Triple(favorites.size, historyAdded, tagFavorites.size)
    }
}
