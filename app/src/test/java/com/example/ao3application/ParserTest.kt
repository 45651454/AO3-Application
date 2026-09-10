package com.example.ao3application

import com.example.ao3application.data.Parser
import com.example.ao3application.data.WorkUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/ao3/$name")!!.bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun parseListingPage() {
        val page = Parser.parseListing(fixture("fluff_list.html"))
        assertEquals(20, page.works.size)
        val first = page.works.first()
        assertEquals(91583686L, first.id)
        assertEquals("Stolen Time", first.title)
        assertEquals("outrageousboulefart", first.author)
        assertEquals("Teen And Up Audiences", first.rating)
        assertFalse(first.isComplete)
        assertEquals("53,338", first.stats["words"])
        assertEquals("28", first.stats["kudos"])
        assertTrue(first.fandoms.isNotEmpty())
        assertTrue(first.tags.any { it.category == "relationships" && it.name == "Higuruma Hiromi/Reader" })
        assertTrue(first.summary.contains("A list"))
        assertNotNull(page.nextUrl)
        assertTrue(page.nextUrl!!.contains("page=2"))
    }

    @Test
    fun parseWorkPage() {
        val detail = Parser.parseWork(fixture("work_full.html"), 91583686L)
        assertEquals("Stolen Time", detail.title)
        assertEquals("outrageousboulefart", detail.author)
        assertEquals(11, detail.chapters.size)
        assertEquals("Chapter 1", detail.chapters.first().title)
        assertTrue(detail.chapters.first().html.contains("Attend the gala"))
        assertFalse(detail.chapters.first().html.contains("Chapter Text"))
        assertTrue(detail.chapters.first().html.contains("<p>"))
        assertTrue(detail.tags.any { it.category == "rating" && it.name == "Teen And Up Audiences" })
        assertTrue(detail.tags.any { it.category == "relationship" && it.name == "Higuruma Hiromi/Reader" })
        assertTrue(detail.tags.any { it.category == "freeform" && it.name == "Angst" })
        assertEquals("2026-08-29", detail.stats["published"])
        assertEquals("11/?", detail.stats["chapters"])
        assertTrue(detail.summary.contains("A list"))
    }

    @Test
    fun parseWorkWithoutReadableChaptersThrowsWithNotice() {
        val html = """
            <html><body>
            <div id="chapters" class="userstuff module">
            <p class="message">Sorry, this work is only available to registered users of the Archive.</p>
            </div>
            </body></html>
        """.trimIndent()
        val e = assertThrows(WorkUnavailableException::class.java) { Parser.parseWork(html, 1L) }
        assertTrue(e.message!!.contains("registered users"))
    }
}
