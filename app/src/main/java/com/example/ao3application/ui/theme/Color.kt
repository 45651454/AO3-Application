package com.example.ao3application.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// AO3 标签分类 -> 色相/饱和度。明度按当前主题算，保证深浅色下都够对比度。
// ponytail: 只按分类上色，不按标签名。要更细就扩这张表。
private val TAG_HUES = mapOf(
    "rating" to (42f to 0.90f),        // 琥珀
    "warning" to (355f to 0.72f),      // 红
    "category" to (190f to 0.85f),     // 青
    "fandom" to (232f to 0.62f),       // 靛蓝
    "relationship" to (330f to 0.70f), // 品红
    "character" to (152f to 0.58f),    // 绿
    "freeform" to (272f to 0.62f),     // 紫
)

/** 列表页给的分类是复数（relationships），详情页是单数（relationship）。 */
internal fun canonicalTagCategory(category: String): String = when (category) {
    "categories" -> "category"
    "fandoms" -> "fandom"
    else -> category.removeSuffix("s")
}

/** 未配色分类返回 null，调用方自行退化。 */
internal fun tagHue(category: String): Pair<Float, Float>? =
    TAG_HUES[canonicalTagCategory(category)]

data class TagColors(val container: Color, val content: Color)

@Composable
fun tagColors(category: String): TagColors {
    val (hue, sat) = tagHue(category) ?: return TagColors(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return if (isSystemInDarkTheme()) {
        TagColors(Color.hsl(hue, sat * 0.40f, 0.20f), Color.hsl(hue, sat, 0.80f))
    } else {
        TagColors(Color.hsl(hue, sat * 0.45f, 0.93f), Color.hsl(hue, sat, 0.32f))
    }
}