package io.github.x45651454.ao3reader

import io.github.x45651454.ao3reader.ui.progressLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressLabelTest {

    /** 0 表示没开始读，不显示。 */
    @Test
    fun zeroMeansUnread() {
        assertNull(progressLabel(0f))
    }

    /** 异常负值同样当作未读，不显示。 */
    @Test
    fun negativeMeansUnread() {
        assertNull(progressLabel(-0.5f))
    }

    /** 不足 1% 向下取整后是 0%，不显示。 */
    @Test
    fun subOnePercentRoundsDownToNothing() {
        assertNull(progressLabel(0.004f))
    }

    /** 百分比向下取整：42.9% 显示 42%。 */
    @Test
    fun percentRoundsDown() {
        assertEquals("已读 42%", progressLabel(0.429f))
    }

    /** Float 精度：0.42f * 100 实际是 41.999998，不能截断成 41%。 */
    @Test
    fun floatPrecisionDoesNotLoseOnePercent() {
        assertEquals("已读 42%", progressLabel(0.42f))
    }

    /** 99.4% 未到读完阈值，仍显示百分比。 */
    @Test
    fun belowFinishThresholdStaysPercent() {
        assertEquals("已读 99%", progressLabel(0.994f))
    }

    /** ≥99.5% 视为读完。 */
    @Test
    fun thresholdCountsAsFinished() {
        assertEquals("已读完", progressLabel(0.995f))
    }

    /** 100% 读完。 */
    @Test
    fun fullCountsAsFinished() {
        assertEquals("已读完", progressLabel(1f))
    }
}
