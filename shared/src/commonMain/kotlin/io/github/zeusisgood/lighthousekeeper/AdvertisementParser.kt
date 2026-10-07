package io.github.zeusisgood.lighthousekeeper

/** Valve の Company ID。アドバタイズの Manufacturer Specific Data に付く */
const val VALVE_COMPANY_ID = 0x055D

/** アドバタイズの状態値（handoff「3. BLEプロトコル」の表と同じ） */
enum class PowerState {
    Sleep,    // 0x00
    Standby,  // 0x02（OFF扱い）
    Booting,  // 0x01（処理中。この値で止まることがある）
    On,       // 0x09 / 0x0B
    Unknown,  // 長さ不足・想定外の値
}

/**
 * Company ID を除いた Manufacturer Data（例: `00 02 01 01 0B 06 00`）から電源状態を取り出す。
 * 形式は1台でしか確認していないので、長さ不足や想定外の値は Unknown にする（保険・削減対象外）。
 */
fun parsePowerState(payload: ByteArray?): PowerState {
    if (payload == null || payload.size <= STATE_INDEX) return PowerState.Unknown
    return when (payload[STATE_INDEX].toInt() and 0xFF) {
        0x00 -> PowerState.Sleep
        0x02 -> PowerState.Standby
        0x01 -> PowerState.Booting
        0x09, 0x0B -> PowerState.On
        else -> PowerState.Unknown
    }
}

private const val STATE_INDEX = 4
