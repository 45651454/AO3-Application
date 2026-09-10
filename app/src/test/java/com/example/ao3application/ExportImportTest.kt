package com.example.ao3application

import com.example.ao3application.data.ExportImport
import com.example.ao3application.data.HistoryEntry
import com.example.ao3application.data.SavedWork
import com.example.ao3application.data.TagFavorite
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportImportTest {

    @Test
    fun roundTrip() {
        val favorites = listOf(
            SavedWork(1L, "标题 \"quoted\" — unicode", "作者", 123456789L),
            SavedWork(2L, "Plain", "anon", 42L),
        )
        val history = listOf(HistoryEntry(2L, "Work", "Author", 987654321L, 0.42f))
        val tagFavorites = listOf(TagFavorite("Angst", "/tags/Angst/works", 555L))
        val text = ExportImport.export(favorites, history, tagFavorites)
        val (importedFavorites, importedHistory, importedTagFavorites) = ExportImport.parse(text)
        assertEquals(favorites, importedFavorites)
        assertEquals(history, importedHistory)
        assertEquals(tagFavorites, importedTagFavorites)
    }

    @Test
    fun parseOldBackupWithoutTagFavorites() {
        val text = """{"version":1,"favorites":[{"id":1,"title":"T","author":"A","savedAt":5}],"history":[]}"""
        val (importedFavorites, importedHistory, importedTagFavorites) = ExportImport.parse(text)
        assertEquals(1, importedFavorites.size)
        assertEquals(0, importedHistory.size)
        assertEquals(0, importedTagFavorites.size)
    }
}
