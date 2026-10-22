package io.github.zeusisgood.lighthousekeeper

import kotlin.uuid.Uuid

/*
 * Base Station 1.0（HTC Vive）。実機では未検証で、lighthouse_pm / lhctrl / LighthouseRedox などの調査結果に基づく（docs/handoff.md「3. BLEプロトコル」）。
 * 電源状態はアドバタイズに出ないので、書き込んだ直後に同じ Characteristic を読み戻して受け付けを確認する
 */

/** 名前は `HTC BS xyABCD`（xy = MAC 末尾1バイト、ABCD = 本体IDの下位4桁） */
const val V1_NAME_PREFIX = "HTC BS"

/** アドバタイズに載る Service（スキャンフィルター用）。書き込み先は同じ Service の 0xCB01 */
val V1_SERVICE: Uuid = Uuid.parse("0000cb00-0000-1000-8000-00805f9b34fb")

fun isV1Name(name: String) = name.startsWith(V1_NAME_PREFIX)

/**
 * 20バイトのコマンド。`12 [cc] [タイムアウト秒 BE 2バイト] [ID LE 4バイト] 00…`
 * - ON: `12 00 00 00 FFFFFFFF`（無期限で稼働。起動は ID なしでも通る）
 * - OFF: `12 02 00 01 [ID]`（1秒後にスタンバイ）。資料では本物の ID が必須だが、lhctrl の実測では
 *   ID なし（FFFFFFFF）でも「指定秒数で止まる」とあるので、ID が空なら FFFFFFFF で送る（推測・未検証）
 */
fun v1Command(on: Boolean, id: String): ByteArray {
    val bytes = ByteArray(20)
    bytes[0] = 0x12
    val idValue = if (on || id.isBlank()) 0xFFFFFFFFL else id.toLong(16)
    if (!on) {
        bytes[1] = 0x02
        bytes[3] = 0x01
    }
    for (i in 0..3) bytes[4 + i] = (idValue shr (8 * i)).toByte()
    return bytes
}

/**
 * 読み戻した値 `[エラーコード] 12 [タイムアウト秒 BE 2バイト] 00…` が、送ったコマンドを受け付けた形か。
 * ON は無期限（0x0000）、OFF は 1秒。長さ不足・想定外は不一致（保険・削減対象外）
 */
fun v1Accepted(on: Boolean, read: ByteArray?): Boolean {
    if (read == null || read.size < 4) return false
    val timeout = ((read[2].toInt() and 0xFF) shl 8) or (read[3].toInt() and 0xFF)
    return read[0] == 0x00.toByte() && read[1] == 0x12.toByte() && timeout == (if (on) 0 else 1)
}

/**
 * 入力された本体ID（背面ラベル）を8桁の大文字16進にする。4桁だけなら名前の末尾4桁（下位）を補う。不正なら null
 */
fun normalizeV1Id(input: String, name: String): String? {
    val hex = Regex("[0-9A-F]+")
    val id = input.trim().uppercase()
    val hint = name.takeLast(4).uppercase().takeIf { it.length == 4 && hex.matches(it) }
    return when {
        id.length == 8 && hex.matches(id) -> id
        id.length == 4 && hex.matches(id) && hint != null -> id + hint
        else -> null
    }
}
