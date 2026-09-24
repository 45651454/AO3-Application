package io.github.x45651454.ao3reader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object ExportImport {

    private val json = Json { prettyPrint = true }

    /** 当前能读懂的备份格式版本，导出时也写这个值。 */
    private const val SUPPORTED_VERSION = 1L

    fun export(
        favorites: List<SavedWork>,
        history: List<HistoryEntry>,
        tagFavorites: List<TagFavorite>,
    ): String {
        val root = buildJsonObject {
            put("version", SUPPORTED_VERSION.toInt())
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("favorites") {
                for (f in favorites) addJsonObject {
                    put("id", f.id)
                    put("title", f.title)
                    put("author", f.author)
                    put("savedAt", f.savedAt)
                    // rating 是后加的字段：空值不写，旧版本应用读到也不受影响（它只认已知键）
                    if (f.rating.isNotEmpty()) put("rating", f.rating)
                    putJsonArray("tags") { for (t in f.tags) add(JsonPrimitive(t)) }
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
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("备份文件格式不正确：根节点不是 JSON 对象")
        // 老备份可能没有 version 字段，按 1 处理；过新的版本不硬解，提示升级
        val version = (root["version"] as? JsonPrimitive)?.longOrNull ?: 1L
        if (version > SUPPORTED_VERSION) {
            throw IllegalArgumentException("备份文件版本过新（v$version），请升级应用后再导入")
        }
        val favorites = (root["favorites"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            SavedWork(
                id = id,
                title = (o["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                author = (o["author"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                savedAt = (o["savedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
                tags = (o["tags"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                // 旧备份没有 rating 字段，缺省为空串（不显示徽章）
                rating = (o["rating"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
        val history = (root["history"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            HistoryEntry(
                id = id,
                title = (o["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                author = (o["author"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                lastReadAt = (o["lastReadAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
                progress = (o["progress"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: 0f,
            )
        }
        val tagFavorites = (root["tagFavorites"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            TagFavorite(
                name = name,
                href = (o["href"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                savedAt = (o["savedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
        return Triple(favorites, history, tagFavorites)
    }

    fun exportFrom(db: LibraryDb): String =
        export(db.favorites(), db.history(), db.tagFavorites())

    /** 整批导入包在一个事务里：任何一条失败整体回滚，不留半截数据。 */
    fun importInto(db: LibraryDb, text: String): Triple<Int, Int, Int> {
        val (favorites, history, tagFavorites) = parse(text)
        return db.runInTransaction {
            favorites.forEach(db::upsertFavorite)
            tagFavorites.forEach(db::importTagFavorite)
            val historyAdded = history.count(db::importHistoryIfNewer)
            Triple(favorites.size, historyAdded, tagFavorites.size)
        }
    }
}
