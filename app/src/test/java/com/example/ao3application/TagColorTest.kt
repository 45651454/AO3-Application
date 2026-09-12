package com.example.ao3application

import com.example.ao3application.data.Parser
import com.example.ao3application.ui.theme.tagHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TagColorTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/ao3/$name")!!.bufferedReader(Charsets.UTF_8).readText()

    /** 列表页给复数分类、详情页给单数分类，两边都要查到颜色，否则标签退化成灰色。 */
    @Test
    fun everyParsedTagCategoryHasAColor() {
        val listing = Parser.parseListing(fixture("fluff_list.html"))
        val detail = Parser.parseWork(fixture("work_full.html"), 91583686L)
        val categories = listing.works.flatMap { it.tags }.map { it.category } +
            detail.tags.map { it.category }
        categories.distinct().forEach { assertNotNull("缺配色的分类: '$it'", tagHue(it)) }
    }

    @Test
    fun pluralListingCategoriesMapToTheSameColorAsSingular() {
        assertEquals(tagHue("relationship"), tagHue("relationships"))
        assertEquals(tagHue("character"), tagHue("characters"))
        assertEquals(tagHue("warning"), tagHue("warnings"))
        assertEquals(tagHue("freeform"), tagHue("freeforms"))
        assertEquals(tagHue("category"), tagHue("categories"))
        assertEquals(tagHue("fandom"), tagHue("fandoms"))
    }
}
