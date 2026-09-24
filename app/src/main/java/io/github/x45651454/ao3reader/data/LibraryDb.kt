package io.github.x45651454.ao3reader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

private const val TAG_FAVORITES_TABLE =
    "CREATE TABLE tag_favorites (name TEXT PRIMARY KEY, href TEXT NOT NULL, saved_at INTEGER NOT NULL)"

// 收藏时给作品打的标签，用于书库按标签筛选
private const val FAVORITE_TAGS_TABLE =
    "CREATE TABLE favorite_tags (work_id INTEGER NOT NULL, tag TEXT NOT NULL, " +
        "PRIMARY KEY (work_id, tag))"

// 离线下载：正文按章拆开存，避免把 HTML 塞进一个字段里再自己拆
// v6 起冗余 rating，书库「已下载」和离线详情页可以显示分级徽章
private const val DOWNLOADS_TABLE =
    "CREATE TABLE downloads (work_id INTEGER PRIMARY KEY, title TEXT NOT NULL, " +
        "author TEXT NOT NULL, downloaded_at INTEGER NOT NULL, rating TEXT NOT NULL DEFAULT '')"

// schema v5 起正文 HTML 落到 filesDir 下的文件，表里只存元数据和相对路径，
// 避免单行超过 CursorWindow 约 2MB 直接崩溃
private const val DOWNLOAD_CHAPTERS_TABLE =
    "CREATE TABLE download_chapters (work_id INTEGER NOT NULL, idx INTEGER NOT NULL, " +
        "title TEXT NOT NULL, path TEXT NOT NULL, PRIMARY KEY (work_id, idx))"

// v4 的旧结构，仅 onUpgrade 迁移用
private const val LEGACY_DOWNLOAD_CHAPTERS_TABLE =
    "CREATE TABLE download_chapters (work_id INTEGER NOT NULL, idx INTEGER NOT NULL, " +
        "title TEXT NOT NULL, html TEXT NOT NULL, PRIMARY KEY (work_id, idx))"

class LibraryDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "library.db", null, 6) {

    private val filesDir: File = context.applicationContext.filesDir

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favorites (" +
                "work_id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, " +
                "saved_at INTEGER NOT NULL, rating TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE history (" +
                "work_id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, " +
                "last_read_at INTEGER NOT NULL, progress REAL NOT NULL)"
        )
        db.execSQL(TAG_FAVORITES_TABLE)
        db.execSQL(FAVORITE_TAGS_TABLE)
        db.execSQL(DOWNLOADS_TABLE)
        db.execSQL(DOWNLOAD_CHAPTERS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(TAG_FAVORITES_TABLE)
        if (oldVersion < 3) db.execSQL(FAVORITE_TAGS_TABLE)
        if (oldVersion < 4) {
            db.execSQL(DOWNLOADS_TABLE)
            db.execSQL(LEGACY_DOWNLOAD_CHAPTERS_TABLE)
        }
        if (oldVersion < 5) migrateDownloadChaptersToFiles(db)
        if (oldVersion < 6) {
            // favorites 自 v1 就存在，直接加列；downloads 是 v4 才有的，
            // 从 <4 升上来时刚用新结构建过表（已含 rating），不能再 ALTER 否则列重复
            db.execSQL("ALTER TABLE favorites ADD COLUMN rating TEXT NOT NULL DEFAULT ''")
            if (oldVersion >= 4) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN rating TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    /** v4 → v5：把 download_chapters.html 逐行写到文件，再按新结构重建表。单行失败只丢那一章。 */
    private fun migrateDownloadChaptersToFiles(db: SQLiteDatabase) {
        data class LegacyChapter(val workId: Long, val idx: Int, val title: String, val html: String)

        val rows = mutableListOf<LegacyChapter>()
        db.rawQuery("SELECT work_id, idx, title, html FROM download_chapters", null).use { c ->
            while (c.moveToNext()) {
                rows.add(LegacyChapter(c.getLong(0), c.getInt(1), c.getString(2), c.getString(3)))
            }
        }
        db.execSQL("DROP TABLE download_chapters")
        db.execSQL(DOWNLOAD_CHAPTERS_TABLE)
        for (row in rows) {
            val path = chapterRelativePath(row.workId, row.idx)
            try {
                val file = File(filesDir, path)
                file.parentFile?.mkdirs()
                file.writeText(row.html)
                db.insertWithOnConflict(
                    "download_chapters", null,
                    ContentValues().apply {
                        put("work_id", row.workId)
                        put("idx", row.idx)
                        put("title", row.title)
                        put("path", path)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            } catch (e: Exception) {
                // 单行迁移失败：清掉半成品文件，跳过该章，不影响其余下载记录
                runCatching { File(filesDir, path).delete() }
            }
        }
    }

    private fun chapterRelativePath(workId: Long, idx: Int): String = "downloads/$workId/$idx.html"

    private fun downloadDir(workId: Long): File = File(filesDir, "downloads/$workId")

    /** 把多个写操作包进同一个事务，避免中途崩溃留下半截数据。 */
    fun <T> runInTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    fun isFavorite(workId: Long): Boolean =
        readableDatabase
            .rawQuery("SELECT 1 FROM favorites WHERE work_id = ?", arrayOf(workId.toString()))
            .use { it.moveToFirst() }

    fun addFavorite(
        id: Long,
        title: String,
        author: String,
        tags: List<String> = emptyList(),
        savedAt: Long = System.currentTimeMillis(),
        rating: String = "",
    ) {
        runInTransaction {
            writableDatabase.insertWithOnConflict(
                "favorites", null,
                ContentValues().apply {
                    put("work_id", id)
                    put("title", title)
                    put("author", author)
                    put("saved_at", savedAt)
                    put("rating", rating)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            replaceFavoriteTags(writableDatabase, id, tags)
        }
    }

    fun removeFavorite(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("favorite_tags", "work_id = ?", arrayOf(id.toString()))
            db.delete("favorites", "work_id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 整组替换某个作品的标签。 */
    fun setFavoriteTags(workId: Long, tags: List<String>) {
        runInTransaction { replaceFavoriteTags(writableDatabase, workId, tags) }
    }

    private fun replaceFavoriteTags(db: SQLiteDatabase, workId: Long, tags: List<String>) {
        db.delete("favorite_tags", "work_id = ?", arrayOf(workId.toString()))
        for (tag in tags.map(String::trim).filter(String::isNotEmpty).distinct()) {
            db.insertWithOnConflict(
                "favorite_tags", null,
                ContentValues().apply {
                    put("work_id", workId)
                    put("tag", tag)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun favoriteTagsFor(workId: Long): List<String> =
        readableDatabase
            .rawQuery("SELECT tag FROM favorite_tags WHERE work_id = ? ORDER BY tag", arrayOf(workId.toString()))
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0))
                }
            }

    fun favoriteTagsByWork(): Map<Long, List<String>> {
        val out = mutableMapOf<Long, MutableList<String>>()
        readableDatabase.rawQuery("SELECT work_id, tag FROM favorite_tags ORDER BY tag", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getString(1))
        }
        return out
    }

    /** 书库筛选用的标签列表，按使用次数从多到少。 */
    fun allFavoriteTags(): List<String> =
        readableDatabase
            .rawQuery(
                "SELECT tag, COUNT(*) AS c FROM favorite_tags GROUP BY tag ORDER BY c DESC, tag ASC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0))
                }
            }

    fun favorites(): List<SavedWork> {
        val tagsByWork = favoriteTagsByWork()
        return readableDatabase
            .rawQuery("SELECT work_id, title, author, saved_at, rating FROM favorites ORDER BY saved_at DESC", null)
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        add(
                            SavedWork(
                                id,
                                c.getString(1),
                                c.getString(2),
                                c.getLong(3),
                                tagsByWork[id].orEmpty(),
                                c.getString(4),
                            )
                        )
                    }
                }
            }
    }

    fun recordRead(id: Long, title: String, author: String, progress: Float) {
        writableDatabase.insertWithOnConflict(
            "history", null,
            ContentValues().apply {
                put("work_id", id)
                put("title", title)
                put("author", author)
                put("last_read_at", System.currentTimeMillis())
                put("progress", progress)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun updateProgress(id: Long, progress: Float) {
        writableDatabase.execSQL(
            "UPDATE history SET progress = ?, last_read_at = ? WHERE work_id = ?",
            arrayOf<Any>(progress, System.currentTimeMillis(), id),
        )
    }

    fun progressFor(id: Long): Float =
        readableDatabase
            .rawQuery("SELECT progress FROM history WHERE work_id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.getFloat(0) else -1f }

    fun history(): List<HistoryEntry> =
        readableDatabase
            .rawQuery(
                "SELECT work_id, title, author, last_read_at, progress FROM history ORDER BY last_read_at DESC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(HistoryEntry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getFloat(4)))
                    }
                }
            }

    fun deleteHistory(id: Long) {
        writableDatabase.delete("history", "work_id = ?", arrayOf(id.toString()))
    }

    fun upsertFavorite(saved: SavedWork) =
        addFavorite(saved.id, saved.title, saved.author, saved.tags, saved.savedAt, saved.rating)

    fun tagFavorites(): List<TagFavorite> =
        readableDatabase
            .rawQuery("SELECT name, href, saved_at FROM tag_favorites ORDER BY saved_at DESC", null)
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(TagFavorite(c.getString(0), c.getString(1), c.getLong(2)))
                    }
                }
            }

    fun addTagFavorite(name: String, href: String, savedAt: Long = System.currentTimeMillis()) {
        writableDatabase.insertWithOnConflict(
            "tag_favorites", null,
            ContentValues().apply {
                put("name", name)
                put("href", href)
                put("saved_at", savedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun removeTagFavorite(name: String) {
        writableDatabase.delete("tag_favorites", "name = ?", arrayOf(name))
    }

    fun importTagFavorite(tag: TagFavorite) = addTagFavorite(tag.name, tag.href, tag.savedAt)

    fun importHistoryIfNewer(entry: HistoryEntry): Boolean {
        readableDatabase
            .rawQuery("SELECT last_read_at FROM history WHERE work_id = ?", arrayOf(entry.id.toString()))
            .use { c ->
                if (c.moveToFirst() && c.getLong(0) >= entry.lastReadAt) return false
            }
        writableDatabase.insertWithOnConflict(
            "history", null,
            ContentValues().apply {
                put("work_id", entry.id)
                put("title", entry.title)
                put("author", entry.author)
                put("last_read_at", entry.lastReadAt)
                put("progress", entry.progress)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return true
    }

    fun saveDownload(detail: WorkDetail, at: Long = System.currentTimeMillis()) {
        val dir = downloadDir(detail.id)
        // 先整体清掉旧文件再重写，避免旧版本章节数更多时残留孤儿文件
        dir.deleteRecursively()
        dir.mkdirs()
        val paths = detail.chapters.mapIndexed { idx, chapter ->
            val path = chapterRelativePath(detail.id, idx)
            File(filesDir, path).writeText(chapter.html)
            path
        }
        runInTransaction {
            writableDatabase.insertWithOnConflict(
                "downloads", null,
                ContentValues().apply {
                    put("work_id", detail.id)
                    put("title", detail.title)
                    put("author", detail.author)
                    put("downloaded_at", at)
                    put("rating", detail.rating)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            writableDatabase.delete("download_chapters", "work_id = ?", arrayOf(detail.id.toString()))
            detail.chapters.forEachIndexed { idx, chapter ->
                writableDatabase.insertWithOnConflict(
                    "download_chapters", null,
                    ContentValues().apply {
                        put("work_id", detail.id)
                        put("idx", idx)
                        put("title", chapter.title)
                        put("path", paths[idx])
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    fun isDownloaded(workId: Long): Boolean =
        readableDatabase
            .rawQuery("SELECT 1 FROM downloads WHERE work_id = ?", arrayOf(workId.toString()))
            .use { it.moveToFirst() }

    /** 只有阅读需要的字段（标题/作者/分级/正文）；标签、统计这些详情页才用，不落盘。 */
    fun downloadedWork(workId: Long): WorkDetail? {
        val head = readableDatabase
            .rawQuery(
                "SELECT title, author, rating FROM downloads WHERE work_id = ?",
                arrayOf(workId.toString()),
            )
            .use { if (it.moveToFirst()) Triple(it.getString(0), it.getString(1), it.getString(2)) else null }
            ?: return null
        val rows = readableDatabase
            .rawQuery(
                "SELECT title, path FROM download_chapters WHERE work_id = ? ORDER BY idx",
                arrayOf(workId.toString()),
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0) to c.getString(1))
                }
            }
        // 正文文件可能被手动删掉，缺任何一章都当作未下载，回退到在线加载
        val chapters = rows.map { (title, path) ->
            val file = File(filesDir, path)
            if (!file.isFile) return null
            val html = runCatching { file.readText() }.getOrNull() ?: return null
            Chapter(title, html)
        }
        return WorkDetail(
            id = workId,
            title = head.first,
            author = head.second,
            rating = head.third,
            summary = "",
            tags = emptyList(),
            stats = emptyMap(),
            chapters = chapters,
        )
    }

    fun removeDownload(workId: Long) {
        runInTransaction {
            writableDatabase.delete("download_chapters", "work_id = ?", arrayOf(workId.toString()))
            writableDatabase.delete("downloads", "work_id = ?", arrayOf(workId.toString()))
        }
        // 正文文件删不掉不影响记录已清除，失败最多留个孤儿目录
        downloadDir(workId).deleteRecursively()
    }

    fun downloads(): List<SavedWork> =
        readableDatabase
            .rawQuery(
                "SELECT work_id, title, author, downloaded_at, rating FROM downloads ORDER BY downloaded_at DESC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(SavedWork(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), rating = c.getString(4)))
                    }
                }
            }
}
