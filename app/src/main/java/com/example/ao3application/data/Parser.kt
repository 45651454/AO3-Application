package com.example.ao3application.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

object Parser {

    private val TAG_CATEGORIES =
        setOf("rating", "warning", "category", "fandom", "relationship", "character", "freeform")

    private val CONTENT_SAFELIST = Safelist()
        .addTags(
            "p", "br", "em", "i", "strong", "b", "u", "s", "del", "ins", "span",
            "blockquote", "hr", "h1", "h2", "h3", "h4", "h5", "h6",
            "ruby", "rt", "rp", "sup", "sub",
            "table", "thead", "tbody", "tr", "td", "th",
            "ul", "ol", "li", "dl", "dt", "dd", "pre", "code", "cite", "q", "img",
        )
        .addAttributes("img", "src", "alt")

    fun parseListing(html: String): ListingPage {
        val doc = Jsoup.parse(html)
        val works = doc.select("li.work.blurb").map(::parseBlurb)
        val next = doc.selectFirst("ol.pagination li.next a[href]")?.attr("href")
        return ListingPage(works, next)
    }

    private fun parseBlurb(li: Element): WorkSummary {
        val titleEl = li.selectFirst("h4.heading a[href^=\"/works/\"]")
        val id = li.id().removePrefix("work_").toLongOrNull()
            ?: titleEl?.attr("href")?.substringAfter("/works/")?.substringBefore("/")?.toLongOrNull()
            ?: 0L
        return WorkSummary(
            id = id,
            title = titleEl?.text().orEmpty(),
            author = li.selectFirst("a[rel=author]")?.text() ?: "Anonymous",
            rating = li.selectFirst(".required-tags span[class*=rating-] .text")?.text().orEmpty(),
            warning = li.selectFirst(".required-tags span[class*=warning-] .text")?.text().orEmpty(),
            category = li.selectFirst(".required-tags span[class*=category-] .text")?.text().orEmpty(),
            isComplete = li.selectFirst(".required-tags span.complete-yes") != null,
            fandoms = li.select("h5.fandoms a.tag").map { it.text() },
            tags = parseTagItems(li.select("ul.tags.commas li")),
            summary = paragraphsOf(li.selectFirst("blockquote.summary")),
            updated = li.selectFirst("p.datetime")?.text().orEmpty(),
            stats = parseStats(li.selectFirst("dl.stats")),
        )
    }

    fun parseWork(html: String, workId: Long): WorkDetail {
        val doc = Jsoup.parse(html)
        val chaptersEl = doc.selectFirst("#chapters")
            ?: throw WorkUnavailableException("无法获取正文（可能需要登录，或作品没有公开内容）")
        val title = doc.selectFirst("h2.title")?.text()?.trim().orEmpty()
        val authors = doc.select("h3.byline a[href^=\"/users/\"]").map { it.text() }
        val author = authors.joinToString(", ").ifEmpty {
            doc.selectFirst("h3.byline")?.text()?.trim().orEmpty().ifEmpty { "Anonymous" }
        }
        val tags = buildList {
            val meta = doc.selectFirst("dl.work.meta.group") ?: return@buildList
            for (dd in meta.select("dd")) {
                val category = dd.classNames().firstOrNull { it in TAG_CATEGORIES } ?: continue
                for (a in dd.select("a.tag")) add(Tag(a.text(), a.attr("href"), category))
            }
        }
        val stats = LinkedHashMap<String, String>()
        doc.selectFirst("dd.language")?.let { stats["language"] = it.text() }
        stats.putAll(parseStats(doc.selectFirst("dd.stats dl.stats")))
        val chapterDivs = chaptersEl.children().filter { it.hasClass("chapter") }
        val chapters = if (chapterDivs.isNotEmpty()) {
            chapterDivs.map { ch ->
                val content = ch.children().firstOrNull { it.hasClass("userstuff") }
                Chapter(
                    title = ch.selectFirst("h3.title")?.text()?.trim().orEmpty(),
                    html = sanitizeContent(content?.html().orEmpty()),
                )
            }
        } else {
            // 单章作品（x/1）：#chapters 下没有 .chapter 包装，正文 .userstuff 是直接子元素
            chaptersEl.children().filter { it.hasClass("userstuff") }
                .map { Chapter(title = "", html = sanitizeContent(it.html())) }
        }
        if (chapters.isEmpty() || chapters.all { it.html.isBlank() }) {
            // 仅登录可见/未公开的作品：#chapters 里没有章节内容，只有提示文字
            val notice = chaptersEl.text().trim().take(300)
            throw WorkUnavailableException(
                notice.ifBlank { "无法读取正文（可能需要登录，或作品尚未公开）" }
            )
        }
        return WorkDetail(
            id = workId,
            title = title,
            author = author,
            summary = paragraphsOf(doc.selectFirst("div.summary.module blockquote.userstuff")),
            tags = tags,
            stats = stats,
            chapters = chapters,
        )
    }

    fun sanitizeContent(html: String): String {
        val dirty = Jsoup.parseBodyFragment(html)
        dirty.select("script, style, .landmark").remove()
        dirty.outputSettings().prettyPrint(false)
        val clean = Cleaner(CONTENT_SAFELIST).clean(dirty)
        clean.outputSettings().prettyPrint(false)
        return clean.body().html().replace("src=\"/", "src=\"$AO3_BASE/")
    }

    fun paragraphsOf(el: Element?): String {
        if (el == null) return ""
        val ps = el.select("p")
        return if (ps.isEmpty()) el.text() else ps.joinToString("\n\n") { it.text() }
    }

    private fun parseTagItems(items: List<Element>): List<Tag> = items.mapNotNull { li ->
        val a = li.selectFirst("a.tag") ?: return@mapNotNull null
        Tag(a.text(), a.attr("href"), li.classNames().firstOrNull().orEmpty())
    }

    private fun parseStats(dl: Element?): Map<String, String> {
        if (dl == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (dt in dl.select("dt")) {
            val dd = dt.nextElementSibling() ?: continue
            if (dd.tagName() != "dd") continue
            val key = dt.classNames().firstOrNull() ?: dt.text().removeSuffix(":")
            out[key] = dd.text()
        }
        return out
    }
}
