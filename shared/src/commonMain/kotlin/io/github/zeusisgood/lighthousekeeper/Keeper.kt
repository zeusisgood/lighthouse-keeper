package io.github.zeusisgood.lighthousekeeper

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** 最後に受信したアドバタイズ。power は 0x01（Booting）を受けても直前の値のまま、raw は受信値そのまま */
data class Seen(val name: String?, val power: PowerState, val raw: PowerState, val at: Instant)

enum class Display { Unknown, Off, On, Busy, Error }

/** この時間アドバタイズを受信しなければ「不明」 */
val STALE_AFTER = 10.seconds

/** docs/architecture/03-state-machine.md */
fun display(seen: Seen?, task: Task?, now: Instant): Display {
    val fresh = seen != null && now - seen.at <= STALE_AFTER
    return when {
        task == null -> display(seen, now)
        !task.failed -> Display.Busy
        // エラーは次の操作か、もともとの目標値を受信するまで保持
        fresh && seen.raw.isTarget(task.on) -> display(seen, now)
        else -> Display.Error
    }
}

fun display(seen: Seen?, now: Instant): Display = when {
    seen == null || now - seen.at > STALE_AFTER -> Display.Unknown
    else -> when (seen.power) {
        PowerState.Sleep, PowerState.Standby -> Display.Off
        PowerState.On -> Display.On
        PowerState.Booting, PowerState.Unknown -> Display.Unknown
    }
}

/** アプリ全体で1つ。スキャン結果（＝状態の唯一の情報源）を持つ */
class Keeper(scope: CoroutineScope) {
    val power = PowerController(scope, this)

    // 接続時に使う（static random アドレスの種別をスキャン結果から引き継ぐため）。スキャンもMainで動くのでロック不要
    private val advertisements = mutableMapOf<String, Advertisement>()
    fun lastAdvertisement(address: String) = advertisements[address]

    private val _seen = MutableStateFlow<Map<String, Seen>>(emptyMap())
    val seen = _seen.asStateFlow()

    /**
     * 常時スキャン。購読者（表示中の画面・処理中の書き込み）がいる間ずっと開始したまま。
     * 購読が切れても5秒は止めない（start/stop の回数制限対策）
     */
    val scan: SharedFlow<Unit> = Scanner {
        filters { match { manufacturerData = listOf(Filter.ManufacturerData(VALVE_COMPANY_ID, byteArrayOf())) } }
        platformScanSettings()
    }.advertisements
        .onEach { ad ->
            advertisements[ad.identifier.toString()] = ad
            onAdvertisement(ad.identifier.toString(), ad.name, ad.manufacturerData(VALVE_COMPANY_ID))
        }
        .map { }
        .retryWhen { e, _ ->
            // 権限なし・Bluetooth OFF など。回数制限（30秒で5回）に掛からない間隔で再開する
            Log.add("スキャン失敗、7秒後に再開: $e")
            delay(7.seconds)
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000))

    private fun onAdvertisement(address: String, name: String?, payload: ByteArray?) {
        val parsed = parsePowerState(payload)
        _seen.update { all ->
            val prev = all[address]
            val power = if (parsed == PowerState.Booting) prev?.power ?: PowerState.Unknown else parsed
            if (prev?.raw != parsed) Log.add("$address 状態 ${prev?.raw ?: "-"} → $parsed")
            all + (address to Seen(name ?: prev?.name, power, parsed, Clock.System.now()))
        }
    }
}
