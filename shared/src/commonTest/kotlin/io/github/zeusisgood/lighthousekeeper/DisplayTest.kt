package io.github.zeusisgood.lighthousekeeper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DisplayTest {
    private val t = Instant.fromEpochSeconds(1_000)
    private fun seen(p: PowerState) = Seen("LHB-1", p, p, t)

    @Test
    fun fromAdvertisement() {
        assertEquals(Display.Off, display(seen(PowerState.Sleep), t))
        assertEquals(Display.Off, display(seen(PowerState.Standby), t))
        assertEquals(Display.On, display(seen(PowerState.On), t))
        assertEquals(Display.Unknown, display(seen(PowerState.Unknown), t))
        assertEquals(Display.Unknown, display(null, t))
    }

    @Test
    fun staleBecomesUnknown() {
        assertEquals(Display.On, display(seen(PowerState.On), t + 10.seconds))
        assertEquals(Display.Unknown, display(seen(PowerState.On), t + 11.seconds))
    }

    @Test
    fun withTask() {
        val busy = Task(on = true, startedAt = t)
        val failed = busy.copy(failed = true)
        assertEquals(Display.Busy, display(seen(PowerState.Booting), busy, t))
        assertEquals(Display.Busy, display(seen(PowerState.On), busy, t)) // 成功判定は PowerController が行う
        assertEquals(Display.Error, display(seen(PowerState.Booting), failed, t))
        assertEquals(Display.Error, display(seen(PowerState.On), failed, t + 11.seconds)) // 古い受信では解除しない
        assertEquals(Display.On, display(seen(PowerState.On), failed, t)) // 目標値を受信したらエラー解除
        assertEquals(Display.Error, display(seen(PowerState.Standby), failed.copy(on = false), t)) // OFF成功は 0x00 のみ
        assertEquals(Display.Off, display(seen(PowerState.Sleep), failed.copy(on = false), t))
    }
}
