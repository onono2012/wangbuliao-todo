package com.wangbuliao.todo

import com.wangbuliao.todo.util.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** RepeatRule 纯函数单测：每天/每周/每月滚动、月末夹取、边界与防御 */
class RepeatRuleTest {

    private fun cal(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, min, 0)
        }.timeInMillis

    @Test
    fun `daily rolls one day forward when remindAt is future`() {
        val from = cal(2026, 9, 13, 8, 0)
        val now = cal(2026, 9, 12, 20, 0) // from 还未到
        val next = RepeatRule.next(from, RepeatRule.DAILY, now)
        assertEquals(cal(2026, 9, 14, 8, 0), next)
    }

    @Test
    fun `daily skips multiple missed days until after now`() {
        val from = cal(2026, 9, 10, 8, 0)
        val now = cal(2026, 9, 13, 12, 0) // 错过 3 天多
        val next = RepeatRule.next(from, RepeatRule.DAILY, now)
        assertEquals(cal(2026, 9, 14, 8, 0), next)
    }

    @Test
    fun `weekly rolls exactly seven days`() {
        val from = cal(2026, 9, 13, 9, 0)
        val now = cal(2026, 9, 13, 9, 1) // 刚过 1 分钟
        val next = RepeatRule.next(from, RepeatRule.WEEKLY, now)
        assertEquals(cal(2026, 9, 20, 9, 0), next)
    }

    @Test
    fun `monthly normal day`() {
        val from = cal(2026, 9, 15, 10, 30)
        val now = cal(2026, 9, 15, 10, 31)
        val next = RepeatRule.next(from, RepeatRule.MONTHLY, now)
        assertEquals(cal(2026, 10, 15, 10, 30), next)
    }

    @Test
    fun `monthly clamps day 31 to shorter february`() {
        val from = cal(2026, 1, 31, 8, 0)
        val now = cal(2026, 1, 31, 8, 1)
        val next = RepeatRule.next(from, RepeatRule.MONTHLY, now)
        // 2026 非闰年：1月31日 +1月 = 2月28日
        assertEquals(cal(2026, 2, 28, 8, 0), next)
    }

    @Test
    fun `monthly clamps to leap february 29`() {
        val from = cal(2028, 1, 31, 8, 0)
        val now = cal(2028, 1, 31, 8, 1)
        val next = RepeatRule.next(from, RepeatRule.MONTHLY, now)
        // 2028 闰年：1月31日 +1月 = 2月29日
        assertEquals(cal(2028, 2, 29, 8, 0), next)
    }

    @Test
    fun `monthly year wrap december to january`() {
        val from = cal(2026, 12, 20, 7, 0)
        val now = cal(2026, 12, 20, 7, 1)
        val next = RepeatRule.next(from, RepeatRule.MONTHLY, now)
        assertEquals(cal(2027, 1, 20, 7, 0), next)
    }

    @Test
    fun `none repeat returns zero`() {
        assertEquals(0L, RepeatRule.next(cal(2026, 9, 13), RepeatRule.NONE, cal(2026, 9, 12)))
    }

    @Test
    fun `invalid repeat returns zero`() {
        assertEquals(0L, RepeatRule.next(cal(2026, 9, 13), 99, cal(2026, 9, 12)))
    }

    @Test
    fun `zero or negative remindAt returns zero`() {
        assertEquals(0L, RepeatRule.next(0L, RepeatRule.DAILY, System.currentTimeMillis()))
        assertEquals(0L, RepeatRule.next(-1L, RepeatRule.WEEKLY, System.currentTimeMillis()))
    }

    @Test
    fun `boundary exactly equal to now still rolls forward`() {
        val from = cal(2026, 9, 13, 8, 0)
        // now 恰好等于下一次触发点：必须再推一天（严格晚于 now）
        val next = RepeatRule.next(from, RepeatRule.DAILY, cal(2026, 9, 14, 8, 0))
        assertEquals(cal(2026, 9, 15, 8, 0), next)
    }

    @Test
    fun `long overdue daily catches up beyond now`() {
        val from = cal(2025, 1, 1, 6, 0)
        val now = cal(2026, 9, 13, 12, 0)
        val next = RepeatRule.next(from, RepeatRule.DAILY, now)
        assertTrue(next > now)
        // 落在 now 之后的第一个 06:00
        assertEquals(cal(2026, 9, 14, 6, 0), next)
    }

    @Test
    fun `labels`() {
        assertEquals("每天", RepeatRule.label(RepeatRule.DAILY))
        assertEquals("每周", RepeatRule.label(RepeatRule.WEEKLY))
        assertEquals("每月", RepeatRule.label(RepeatRule.MONTHLY))
        assertEquals("", RepeatRule.label(RepeatRule.NONE))
        assertEquals("", RepeatRule.label(-3))
    }
}
