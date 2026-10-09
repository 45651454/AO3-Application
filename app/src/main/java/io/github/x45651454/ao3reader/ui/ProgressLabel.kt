package io.github.x45651454.ao3reader.ui

/**
 * 列表页标题右侧的阅读进度文案。
 * 没开始读（≤0，或换算后不足 1%）不显示；≥99.5% 视为读完；其余向下取整百分比。
 * 加 0.0001 的 epsilon：Float 里 0.42 * 100 实际是 41.999998，直接截断会少 1%。
 */
internal fun progressLabel(fraction: Float): String? {
    if (fraction <= 0f) return null
    if (fraction >= 0.995f) return "已读完"
    val pct = (fraction * 100 + 0.0001f).toInt()
    return if (pct <= 0) null else "已读 $pct%"
}
