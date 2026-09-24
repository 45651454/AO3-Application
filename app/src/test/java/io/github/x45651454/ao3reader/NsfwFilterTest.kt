package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.data.Rating
import io.github.x45651454.ao3reader.data.WorkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NsfwFilterTest {

    private fun work(id: Long, rating: String) = WorkSummary(
        id = id,
        title = "t$id",
        author = "a",
        rating = rating,
        warning = "",
        category = "",
        isComplete = true,
        fandoms = emptyList(),
        tags = emptyList(),
        summary = "",
        updated = "",
        stats = emptyMap(),
    )

    private val works = listOf(
        work(1, "Explicit"),
        work(2, "Mature"),
        work(3, "Teen And Up Audiences"),
        work(4, "General Audiences"),
        work(5, "Not Rated"),
        work(6, ""),
    )

    @Test
    fun showNsfwReturnsOriginalList() {
        assertSame(works, Rating.filterVisible(works, showNsfw = true))
    }

    @Test
    fun hideNsfwDropsExplicitAndMature() {
        val visible = Rating.filterVisible(works, showNsfw = false)
        assertEquals(listOf(3L, 4L, 5L, 6L), visible.map { it.id })
    }

    @Test
    fun hideNsfwKeepsUnratedAndUnknownRating() {
        val mixed = listOf(work(1, ""), work(2, "notrated"), work(3, "something else"))
        assertEquals(3, Rating.filterVisible(mixed, showNsfw = false).size)
    }

    @Test
    fun hideNsfwOnEmptyList() {
        assertEquals(emptyList<WorkSummary>(), Rating.filterVisible(emptyList(), showNsfw = false))
    }
}
