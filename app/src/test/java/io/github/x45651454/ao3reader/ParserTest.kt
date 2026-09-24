package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.Parser
import io.github.x45651454.ao3reader.data.Rating
import io.github.x45651454.ao3reader.data.WorkUnavailableException
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
    fun parseListingRatings() {
        val page = Parser.parseListing(fixture("fluff_list.html"))
        val ratings = page.works.map { it.rating }
        // fixture 里五档分级齐全，每篇都必须解析出分级文本
        assertEquals(20, ratings.size)
        assertTrue(ratings.all { it.isNotEmpty() })
        assertTrue("Explicit" in ratings)
        assertTrue("Mature" in ratings)
        assertTrue("Teen And Up Audiences" in ratings)
        assertTrue("General Audiences" in ratings)
        assertTrue("Not Rated" in ratings)
        // NSFW = Explicit + Mature，fixture 里 3 + 5 篇
        assertEquals(8, page.works.count { Rating.isNsfw(it.rating) })
    }

    @Test
    fun parseListingRatingFallsBackToTitleAttr() {
        // 容错：required-tags 里没有内层 .text 时退回 span 的 title 属性
        val html = """
            <html><body><ol class="work index group">
            <li id="work_42" class="work blurb group">
              <h4 class="heading"><a href="/works/42">No Text Span</a></h4>
              <ul class="required-tags">
                <li><span class="rating-mature rating" title="Mature"></span></li>
              </ul>
            </li>
            </ol></body></html>
        """.trimIndent()
        val page = Parser.parseListing(html)
        assertEquals(1, page.works.size)
        assertEquals("Mature", page.works.first().rating)
        assertTrue(Rating.isNsfw(page.works.first().rating))
    }

    @Test
    fun parseWorkPage() {
        val detail = Parser.parseWork(fixture("work_full.html"), 91583686L)
        assertEquals("Stolen Time", detail.title)
        assertEquals("outrageousboulefart", detail.author)
        assertEquals("Teen And Up Audiences", detail.rating)
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
    fun parseSingleChapterWorkText() {
        val detail = Parser.parseWork(fixture("work_single.html"), 63097L)
        assertEquals("Peter", detail.title)
        assertEquals("SecondSilk", detail.author)
        assertEquals(1, detail.chapters.size)
        assertTrue(detail.chapters.first().html.contains("Peter's mother sent him to the train"))
        assertFalse(detail.chapters.first().html.contains("Work Text"))
        assertEquals("1/1", detail.stats["chapters"])
        assertEquals("2010-02-16", detail.stats["published"])
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
