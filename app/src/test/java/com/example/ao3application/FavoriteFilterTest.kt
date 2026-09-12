package com.example.ao3application

import com.example.ao3application.data.SavedWork
import com.example.ao3application.ui.filterFavorites
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteFilterTest {

    private val tagged = SavedWork(1L, "有标签", "A", 1L, listOf("甜文", "Harry/Reader"))
    private val untagged = SavedWork(2L, "没标签", "B", 2L)
    private val favorites = listOf(tagged, untagged)

    @Test
    fun noFilterReturnsEverything() {
        assertEquals(favorites, filterFavorites(favorites, null, untaggedOnly = false))
    }

    @Test
    fun tagFilterMatchesAnyOfTheWorksTags() {
        assertEquals(listOf(tagged), filterFavorites(favorites, "甜文", untaggedOnly = false))
        assertEquals(listOf(tagged), filterFavorites(favorites, "Harry/Reader", untaggedOnly = false))
    }

    @Test
    fun untaggedOnlyReturnsWorksSavedWithoutTags() {
        assertEquals(listOf(untagged), filterFavorites(favorites, null, untaggedOnly = true))
    }

    /** 切到「未标注」时残留的标签筛选不该再生效。 */
    @Test
    fun untaggedOnlyWinsOverAStaleTag() {
        assertEquals(listOf(untagged), filterFavorites(favorites, "甜文", untaggedOnly = true))
    }

    @Test
    fun unknownTagMatchesNothing() {
        assertEquals(emptyList<SavedWork>(), filterFavorites(favorites, "不存在", untaggedOnly = false))
    }
}
