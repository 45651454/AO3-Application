package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.normalizeFavoriteTagName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteTagNameTest {

    @Test
    fun blankNamesAreRejected() {
        assertNull(normalizeFavoriteTagName(""))
        assertNull(normalizeFavoriteTagName("   "))
        assertNull(normalizeFavoriteTagName(" \t\n "))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("甜文", normalizeFavoriteTagName("  甜文  "))
        assertEquals("Harry/Reader", normalizeFavoriteTagName("\tHarry/Reader\n"))
    }

    @Test
    fun innerWhitespaceIsKept() {
        assertEquals("Harry Potter", normalizeFavoriteTagName("Harry Potter"))
    }
}
