package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.escapeSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEscapeTest {

    @Test
    fun plainTextUnchanged() {
        assertEquals("Fluff", escapeSearchQuery("Fluff"))
        assertEquals("hello world", escapeSearchQuery("hello world"))
    }

    @Test
    fun colonAndDashEscaped() {
        assertEquals(
            "Podfic Length\\: 0\\-10 Minutes",
            escapeSearchQuery("Podfic Length: 0-10 Minutes"),
        )
    }

    @Test
    fun exclamationAndPipeEscaped() {
        assertEquals("Fluff\\!", escapeSearchQuery("Fluff!"))
        assertEquals("A \\| B", escapeSearchQuery("A | B"))
    }

    @Test
    fun slashQuoteBackslashEscaped() {
        assertEquals("Alpha\\/Beta", escapeSearchQuery("Alpha/Beta"))
        assertEquals("\\\"quoted\\\"", escapeSearchQuery("\"quoted\""))
        assertEquals("a\\\\b", escapeSearchQuery("a\\b"))
    }

    @Test
    fun cjkAndSpacesUntouched() {
        assertEquals("原神", escapeSearchQuery("原神"))
        assertEquals("魔道 祖师", escapeSearchQuery("魔道 祖师"))
    }
}
