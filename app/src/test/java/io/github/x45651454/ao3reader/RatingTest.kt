package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {

    @Test
    fun levelOfDisplayText() {
        assertEquals(Rating.Level.EXPLICIT, Rating.levelOf("Explicit"))
        assertEquals(Rating.Level.MATURE, Rating.levelOf("Mature"))
        assertEquals(Rating.Level.TEEN, Rating.levelOf("Teen And Up Audiences"))
        assertEquals(Rating.Level.GENERAL, Rating.levelOf("General Audiences"))
        assertEquals(Rating.Level.NOT_RATED, Rating.levelOf("Not Rated"))
    }

    @Test
    fun levelOfSlug() {
        assertEquals(Rating.Level.EXPLICIT, Rating.levelOf("explicit"))
        assertEquals(Rating.Level.MATURE, Rating.levelOf("mature"))
        assertEquals(Rating.Level.TEEN, Rating.levelOf("teen-audience"))
        assertEquals(Rating.Level.GENERAL, Rating.levelOf("general-audience"))
        assertEquals(Rating.Level.NOT_RATED, Rating.levelOf("notrated"))
    }

    @Test
    fun levelOfToleratesCaseAndWhitespace() {
        assertEquals(Rating.Level.EXPLICIT, Rating.levelOf("  EXPLICIT  "))
        assertEquals(Rating.Level.TEEN, Rating.levelOf("teen  and   up audiences"))
    }

    @Test
    fun levelOfUnknown() {
        assertEquals(Rating.Level.UNKNOWN, Rating.levelOf(null))
        assertEquals(Rating.Level.UNKNOWN, Rating.levelOf(""))
        assertEquals(Rating.Level.UNKNOWN, Rating.levelOf("something else"))
    }

    @Test
    fun isNsfwOnlyExplicitAndMature() {
        assertTrue(Rating.isNsfw("Explicit"))
        assertTrue(Rating.isNsfw("Mature"))
        assertFalse(Rating.isNsfw("Teen And Up Audiences"))
        assertFalse(Rating.isNsfw("General Audiences"))
        assertFalse(Rating.isNsfw("Not Rated"))
        assertFalse(Rating.isNsfw(null))
        assertFalse(Rating.isNsfw(""))
    }

    @Test
    fun badgeLabel() {
        assertEquals("NSFW", Rating.badgeLabel("Explicit"))
        assertEquals("Mature", Rating.badgeLabel("Mature"))
        assertNull(Rating.badgeLabel("Teen And Up Audiences"))
        assertNull(Rating.badgeLabel("General Audiences"))
        assertNull(Rating.badgeLabel("Not Rated"))
        assertNull(Rating.badgeLabel(null))
    }
}
