package com.example.ao3application.data

import okhttp3.HttpUrl.Companion.toHttpUrl

class Repo(private val client: Ao3Client, val db: LibraryDb) {

    private val workCache = object : LinkedHashMap<Long, WorkDetail>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, WorkDetail>?): Boolean =
            size > 5
    }

    suspend fun listing(url: String): ListingPage {
        val page = Parser.parseListing(client.getHtml(absoluteUrl(url)))
        return page.copy(nextUrl = page.nextUrl?.let(::absoluteUrl))
    }

    suspend fun search(query: String, sortColumn: String, completeOnly: Boolean): ListingPage {
        val builder = (AO3_BASE + "/works/search").toHttpUrl().newBuilder()
        if (query.isNotBlank()) builder.addQueryParameter("work_search[query]", query)
        if (sortColumn != "_score") {
            builder.addQueryParameter("work_search[sort_column]", sortColumn)
            builder.addQueryParameter("work_search[sort_direction]", "desc")
        }
        if (completeOnly) builder.addQueryParameter("work_search[complete]", "T")
        builder.addQueryParameter("commit", "Search")
        return listing(builder.build().toString())
    }

    suspend fun work(id: Long): WorkDetail {
        synchronized(workCache) { workCache[id] }?.let { return it }
        val detail = Parser.parseWork(client.getHtml("$AO3_BASE/works/$id?view_full_work=true"), id)
        synchronized(workCache) { workCache[id] = detail }
        return detail
    }

    companion object {
        fun absoluteUrl(href: String): String =
            if (href.startsWith("http")) href else AO3_BASE + href
    }
}
