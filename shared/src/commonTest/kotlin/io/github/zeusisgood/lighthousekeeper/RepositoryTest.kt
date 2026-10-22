package io.github.zeusisgood.lighthousekeeper

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryTest {
    @Test
    fun roundTripKeepsOrderAndSanitizesAlias() {
        val devices = listOf(Device("AA:BB", "LHB-1", "左\t上"), Device("CC:DD", "LHB-2"))
        assertEquals(listOf(Device("AA:BB", "LHB-1", "左 上"), Device("CC:DD", "LHB-2")), decode(encode(devices)))
        assertEquals(emptyList(), decode(null))
        val v1 = listOf(Device("EE:FF", "HTC BS 3FCCDD", "", "AABBCCDD"))
        assertEquals(v1, decode(encode(v1)))
        assertEquals(listOf(Device("AA:BB", "LHB-1", "左")), decode("AA:BB\tLHB-1\t左")) // 0.2.0 までの3列
    }
}
