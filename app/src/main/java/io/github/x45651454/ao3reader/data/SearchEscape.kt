package io.github.x45651454.ao3reader.data

/**
 * AO3 搜索框背后走 Lucene query_string 语法，这些字符是运算符：
 * 标签名原文（如 "Podfic Length: 0-10 Minutes"、"Fluff!"）直接搜会被误解析，
 * 轻则 0 结果，重则 HTTP 400。逐字符加反斜杠转义，让搜索按字面文本匹配。
 */
private val LUCENE_SPECIALS = "+-=><!(){}[]^\"~*?:\\/|&".toSet()

fun escapeSearchQuery(query: String): String = buildString(query.length + 8) {
    for (c in query) {
        if (c in LUCENE_SPECIALS) append('\\')
        append(c)
    }
}
