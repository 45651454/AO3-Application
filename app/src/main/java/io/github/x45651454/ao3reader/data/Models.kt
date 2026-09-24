package io.github.x45651454.ao3reader.data

data class Tag(val name: String, val href: String, val category: String = "")

data class WorkSummary(
    val id: Long,
    val title: String,
    val author: String,
    val rating: String,
    val warning: String,
    val category: String,
    val isComplete: Boolean,
    val fandoms: List<String>,
    val tags: List<Tag>,
    val summary: String,
    val updated: String,
    val stats: Map<String, String>,
)

data class Chapter(val title: String, val html: String)

data class WorkDetail(
    val id: Long,
    val title: String,
    val author: String,
    val summary: String,
    val tags: List<Tag>,
    val stats: Map<String, String>,
    val chapters: List<Chapter>,
)

data class ListingPage(val works: List<WorkSummary>, val nextUrl: String?)

data class SavedWork(
    val id: Long,
    val title: String,
    val author: String,
    val savedAt: Long,
    val tags: List<String> = emptyList(),
)

data class TagFavorite(val name: String, val href: String, val savedAt: Long)

data class HistoryEntry(
    val id: Long,
    val title: String,
    val author: String,
    val lastReadAt: Long,
    val progress: Float,
)

class WorkUnavailableException(message: String) : Exception(message)
