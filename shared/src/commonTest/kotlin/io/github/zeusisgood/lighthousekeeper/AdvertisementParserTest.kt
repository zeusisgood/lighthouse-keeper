package io.github.zeusisgood.lighthousekeeper

import kotlin.test.Test
import kotlin.test.assertEquals

class AdvertisementParserTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun realDevice() {
        assertEquals(PowerState.Sleep, parsePowerState(bytes(0x00, 0x02, 0x01, 0x01, 0x00, 0x06, 0x00)))
        assertEquals(PowerState.On, parsePowerState(bytes(0x00, 0x02, 0x01, 0x01, 0x0B, 0x06, 0x00)))
    }

    @Test
    fun allKnownValues() {
        val expected = mapOf(
            0x00 to PowerState.Sleep, 0x01 to PowerState.Booting, 0x02 to PowerState.Standby,
            0x09 to PowerState.On, 0x0B to PowerState.On,
        )
        for (v in 0..0xFF) {
            assertEquals(expected[v] ?: PowerState.Unknown, parsePowerState(bytes(0, 2, 1, 1, v, 6, 0)), "value $v")
        }
    }

    @Test
    fun tooShortOrMissing() {
        assertEquals(PowerState.Unknown, parsePowerState(null))
        assertEquals(PowerState.Unknown, parsePowerState(bytes()))
        assertEquals(PowerState.Unknown, parsePowerState(bytes(0x00, 0x02, 0x01, 0x01)))
        assertEquals(PowerState.On, parsePowerState(bytes(0x00, 0x02, 0x01, 0x01, 0x09)))
    }
}
