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
}
