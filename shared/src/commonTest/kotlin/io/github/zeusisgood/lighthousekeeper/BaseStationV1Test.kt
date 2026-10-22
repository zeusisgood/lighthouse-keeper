package io.github.zeusisgood.lighthousekeeper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 期待値は lighthouse_pm / lhctrl / OVR-Lighthouse-Manager のコマンドと同じバイト列 */
class BaseStationV1Test {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun commands() {
        assertEquals("12000000ffffffff" + "00".repeat(12), hex(v1Command(on = true, id = "AABBCCDD")))
        assertEquals("12020001ddccbbaa" + "00".repeat(12), hex(v1Command(on = false, id = "AABBCCDD")))
        assertEquals("12020001ffffffff" + "00".repeat(12), hex(v1Command(on = false, id = ""))) // ID なし
    }

    @Test
    fun readBack() {
        assertTrue(v1Accepted(true, bytes(0x00, 0x12, 0x00, 0x00, 0, 0)))
        assertTrue(v1Accepted(false, bytes(0x00, 0x12, 0x00, 0x01, 0, 0)))
        assertFalse(v1Accepted(true, bytes(0x00, 0x12, 0x00, 0x01)))
        assertFalse(v1Accepted(false, bytes(0x00, 0x12, 0x00, 0x00)))
        assertFalse(v1Accepted(true, bytes(0x01, 0x12, 0x00, 0x00))) // エラーコードあり
        assertFalse(v1Accepted(true, bytes(0x00, 0x12, 0x00)))
        assertFalse(v1Accepted(true, null))
    }

    @Test
    fun ids() {
        assertEquals("AABBCCDD", normalizeV1Id(" aabbccdd ", "HTC BS 3FCCDD"))
        assertEquals("AABBCCDD", normalizeV1Id("aabb", "HTC BS 3FCCDD"))
        assertNull(normalizeV1Id("aabbcc", "HTC BS 3FCCDD"))
        assertNull(normalizeV1Id("GGGGGGGG", "HTC BS 3FCCDD"))
        assertNull(normalizeV1Id("aabb", "HTC BS"))
    }
}
