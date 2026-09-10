package com.example.ao3application.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val TAG_FAVORITES_TABLE =
    "CREATE TABLE tag_favorites (name TEXT PRIMARY KEY, href TEXT NOT NULL, saved_at INTEGER NOT NULL)"

class LibraryDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "library.db", null, 2) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(TAG_FAVORITES_TABLE)
    }

    fun isFavorite(workId: Long): Boolean =
        readableDatabase
            .rawQuery("SELECT 1 FROM favorites WHERE work_id = ?", arrayOf(workId.toString()))
            .use { it.moveToFirst() }

    fun addFavorite(id: Long, title: String, author: String, savedAt: Long = System.currentTimeMillis()) {
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
    }

    fun removeFavorite(id: Long) {
        writableDatabase.delete("favorites", "work_id = ?", arrayOf(id.toString()))
    }

    fun favorites(): List<SavedWork> =
        readableDatabase
            .rawQuery("SELECT work_id, title, author, saved_at FROM favorites ORDER BY saved_at DESC", null)
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(SavedWork(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
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
        addFavorite(saved.id, saved.title, saved.author, saved.savedAt)

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
}
