package io.github.x45651454.ao3reader.data

/**
 * AO3 作品分级的判定逻辑（纯函数，方便单测）。
 *
 * 输入既可能是列表/详情页解析出的显示文本（"Teen And Up Audiences"），
 * 也可能是 required-tags 上的 slug（"teen-audience"），两种都认；
 * 认不出来的一律 UNKNOWN，不显示徽章，绝不抛异常。
 */
object Rating {

    enum class Level { EXPLICIT, MATURE, TEEN, GENERAL, NOT_RATED, UNKNOWN }

    fun levelOf(rating: String?): Level {
        val normalized = rating
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
        return when (normalized) {
            "explicit" -> Level.EXPLICIT
            "mature" -> Level.MATURE
            "teen and up audiences", "teen-audience" -> Level.TEEN
            "general audiences", "general-audience" -> Level.GENERAL
            "not rated", "notrated" -> Level.NOT_RATED
            else -> Level.UNKNOWN
        }
    }

    /** Explicit 和 Mature 视为 NSFW。 */
    fun isNsfw(rating: String?): Boolean =
        levelOf(rating).let { it == Level.EXPLICIT || it == Level.MATURE }

    /**
     * 浏览列表的可见性过滤（纯函数）：showNsfw 关闭时去掉 Explicit / Mature 作品，
     * 开启时原样返回。未分级 / 认不出分级的作品始终保留。
     */
    fun filterVisible(works: List<WorkSummary>, showNsfw: Boolean): List<WorkSummary> =
        if (showNsfw) works else works.filter { !isNsfw(it.rating) }

    /** 需要显示徽章时返回徽章文字，否则 null。 */
    fun badgeLabel(rating: String?): String? = when (levelOf(rating)) {
        Level.EXPLICIT -> "NSFW"
        Level.MATURE -> "Mature"
        else -> null
    }
}
