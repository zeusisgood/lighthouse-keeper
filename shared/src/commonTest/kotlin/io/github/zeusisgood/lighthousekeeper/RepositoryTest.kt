package io.github.zeusisgood.lighthousekeeper

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryTest {
    @Test
    fun roundTripKeepsOrderAndSanitizesAlias() {
        val devices = listOf(Device("AA:BB", "LHB-1", "左\t上"), Device("CC:DD", "LHB-2"))
        assertEquals(listOf(Device("AA:BB", "LHB-1", "左 上"), Device("CC:DD", "LHB-2")), decode(encode(devices)))
        assertEquals(emptyList(), decode(null))
    }
}
