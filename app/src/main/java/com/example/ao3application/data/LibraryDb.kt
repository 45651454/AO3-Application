package com.example.ao3application.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val TAG_FAVORITES_TABLE =
    "CREATE TABLE tag_favorites (name TEXT PRIMARY KEY, href TEXT NOT NULL, saved_at INTEGER NOT NULL)"

// 收藏时给作品打的标签，用于书库按标签筛选
private const val FAVORITE_TAGS_TABLE =
    "CREATE TABLE favorite_tags (work_id INTEGER NOT NULL, tag TEXT NOT NULL, " +
        "PRIMARY KEY (work_id, tag))"

// 离线下载：正文按章拆开存，避免把 HTML 塞进一个字段里再自己拆
private const val DOWNLOADS_TABLE =
    "CREATE TABLE downloads (work_id INTEGER PRIMARY KEY, title TEXT NOT NULL, " +
        "author TEXT NOT NULL, downloaded_at INTEGER NOT NULL)"

private const val DOWNLOAD_CHAPTERS_TABLE =
    "CREATE TABLE download_chapters (work_id INTEGER NOT NULL, idx INTEGER NOT NULL, " +
        "title TEXT NOT NULL, html TEXT NOT NULL, PRIMARY KEY (work_id, idx))"

class LibraryDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "library.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favorites (" +
                "work_id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, saved_at INTEGER NOT NULL)"
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
            db.execSQL(DOWNLOAD_CHAPTERS_TABLE)
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
    ) {
        writableDatabase.insertWithOnConflict(
            "favorites", null,
            ContentValues().apply {
                put("work_id", id)
                put("title", title)
                put("author", author)
                put("saved_at", savedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        setFavoriteTags(id, tags)
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
        val db = writableDatabase
        db.beginTransaction()
        try {
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
            .rawQuery("SELECT work_id, title, author, saved_at FROM favorites ORDER BY saved_at DESC", null)
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
        addFavorite(saved.id, saved.title, saved.author, saved.tags, saved.savedAt)

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
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "downloads", null,
                ContentValues().apply {
                    put("work_id", detail.id)
                    put("title", detail.title)
                    put("author", detail.author)
                    put("downloaded_at", at)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.delete("download_chapters", "work_id = ?", arrayOf(detail.id.toString()))
            detail.chapters.forEachIndexed { idx, chapter ->
                db.insertWithOnConflict(
                    "download_chapters", null,
                    ContentValues().apply {
                        put("work_id", detail.id)
                        put("idx", idx)
                        put("title", chapter.title)
                        put("html", chapter.html)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun isDownloaded(workId: Long): Boolean =
        readableDatabase
            .rawQuery("SELECT 1 FROM downloads WHERE work_id = ?", arrayOf(workId.toString()))
            .use { it.moveToFirst() }

    /** 只有阅读需要的字段（标题/作者/正文）；标签、统计这些详情页才用，不落盘。 */
    fun downloadedWork(workId: Long): WorkDetail? {
        val head = readableDatabase
            .rawQuery(
                "SELECT title, author FROM downloads WHERE work_id = ?",
                arrayOf(workId.toString()),
            )
            .use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
            ?: return null
        val chapters = readableDatabase
            .rawQuery(
                "SELECT title, html FROM download_chapters WHERE work_id = ? ORDER BY idx",
                arrayOf(workId.toString()),
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(Chapter(c.getString(0), c.getString(1)))
                }
            }
        return WorkDetail(
            id = workId,
            title = head.first,
            author = head.second,
            summary = "",
            tags = emptyList(),
            stats = emptyMap(),
            chapters = chapters,
        )
    }

    fun removeDownload(workId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("download_chapters", "work_id = ?", arrayOf(workId.toString()))
            db.delete("downloads", "work_id = ?", arrayOf(workId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun downloads(): List<SavedWork> =
        readableDatabase
            .rawQuery(
                "SELECT work_id, title, author, downloaded_at FROM downloads ORDER BY downloaded_at DESC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(SavedWork(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
                    }
                }
            }
}
