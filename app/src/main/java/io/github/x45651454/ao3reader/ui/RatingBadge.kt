package io.github.x45651454.ao3reader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.x45651454.ao3reader.data.Rating
import io.github.x45651454.ao3reader.ui.theme.LocalDarkTheme
import io.github.x45651454.ao3reader.ui.theme.TagColors

/**
 * 分级徽章：Explicit 显示红色「NSFW」，Mature 显示橙色「Mature」，其余（含未知）不显示。
 * 配色公式与 tagColors 一致（HSL 按深浅主题换算明度），保证两种主题下对比度都够。
 */
@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    val level = Rating.levelOf(rating)
    val label = Rating.badgeLabel(rating) ?: return
    val (hue, sat) = when (level) {
        Rating.Level.EXPLICIT -> 355f to 0.72f // 红，同 warning 标签
        else -> 28f to 0.90f                   // 橙，Mature
    }
    val c = if (LocalDarkTheme.current) {
        TagColors(Color.hsl(hue, sat * 0.40f, 0.20f), Color.hsl(hue, sat, 0.80f))
    } else {
        TagColors(Color.hsl(hue, sat * 0.45f, 0.93f), Color.hsl(hue, sat, 0.32f))
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = c.container,
        contentColor = c.content,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
