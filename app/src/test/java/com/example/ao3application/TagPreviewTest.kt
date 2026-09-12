package com.example.ao3application

import com.example.ao3application.data.Tag
import com.example.ao3application.ui.previewTags
import com.example.ao3application.ui.theme.canonicalTagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPreviewTest {

    private fun tag(category: String, name: String) = Tag(name, "/tags/x", category)

    /** 关系标签一多就会把角色挤出卡片，而这两类正是要上色的，配额必须保住角色。 */
    @Test
    fun charactersSurviveWhenRelationshipsAreMany() {
        val tags = List(10) { tag("relationships", "Rel $it") } +
            tag("characters", "Harry Potter") +
            tag("freeforms", "Angst")

        val shown = previewTags(tags)

        assertTrue(shown.any { it.category == "characters" && it.name == "Harry Potter" })
        assertTrue(shown.any { it.category == "freeforms" })
        assertTrue("卡片标签数应受限", shown.size <= 8)
    }

    /** 单复数写法的配额要算在同一类上（关系类是 4 个名额）。 */
    @Test
    fun pluralAndSingularShareTheSameQuota() {
        val tags = List(3) { tag("relationships", "A$it") } +
            List(3) { tag("relationship", "B$it") }

        assertEquals(4, previewTags(tags).size)
    }

    /** 卡片不显示警告，位置让给关系和角色；详情页另行完整展示。 */
    @Test
    fun warningsAreNotShownOnCards() {
        val tags = listOf(
            tag("warnings", "Major Character Death"),
            tag("warnings", "Rape/Non-Con"),
            tag("warning", "Underage"),
            tag("relationships", "A/B"),
            tag("characters", "A"),
        )

        val shown = previewTags(tags)

        assertTrue(shown.none { canonicalTagCategory(it.category) == "warning" })
        assertEquals(listOf("A/B", "A"), shown.map { it.name })
    }
}
