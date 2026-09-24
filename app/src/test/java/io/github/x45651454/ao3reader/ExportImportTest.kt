package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.ExportImport
import io.github.x45651454.ao3reader.data.HistoryEntry
import io.github.x45651454.ao3reader.data.SavedWork
import io.github.x45651454.ao3reader.data.TagFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExportImportTest {

    @Test
    fun roundTrip() {
        val favorites = listOf(
            SavedWork(1L, "标题 \"quoted\" — unicode", "作者", 123456789L, listOf("A/B", "C & D")),
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
        assertEquals(listOf(SavedWork(1L, "T", "A", 5L)), importedFavorites)
        assertEquals(0, importedHistory.size)
        assertEquals(0, importedTagFavorites.size)
    }

    @Test
    fun parseSkipsNonStringTagElements() {
        // 对象/数组/null 这类坏元素跳过；数字/布尔是 JsonPrimitive，按原语义转成字符串保留
        val text = """
            {"version":1,"favorites":[
              {"id":1,"title":"T","author":"A","savedAt":5,"tags":["ok",42,{"x":1},["nested"],null,true,"也保留"]}
            ],"history":[]}
        """.trimIndent()
        val (importedFavorites, _, _) = ExportImport.parse(text)
        assertEquals(listOf(SavedWork(1L, "T", "A", 5L, listOf("ok", "42", "true", "也保留"))), importedFavorites)
    }

    @Test
    fun parseRejectsTooNewVersion() {
        val text = """{"version":2,"favorites":[],"history":[]}"""
        val e = assertThrows(IllegalArgumentException::class.java) { ExportImport.parse(text) }
        assertEquals("备份文件版本过新（v2），请升级应用后再导入", e.message)
    }

    @Test
    fun parseTreatsMissingVersionAsOne() {
        val text = """{"favorites":[{"id":1,"title":"T","author":"A","savedAt":5}],"history":[]}"""
        val (importedFavorites, _, _) = ExportImport.parse(text)
        assertEquals(listOf(SavedWork(1L, "T", "A", 5L)), importedFavorites)
    }

    @Test
    fun parseRejectsNonObjectRoot() {
        assertThrows(IllegalArgumentException::class.java) { ExportImport.parse("[1,2,3]") }
    }
}
