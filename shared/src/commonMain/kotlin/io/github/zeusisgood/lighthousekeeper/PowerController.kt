package io.github.zeusisgood.lighthousekeeper

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.toIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val SERVICE = Uuid.parse("00001523-1212-efde-1523-785feabcd124")
private val POWER = characteristicOf(SERVICE, Uuid.parse("00001525-1212-efde-1523-785feabcd124"))
private val IDENTIFY = characteristicOf(SERVICE, Uuid.parse("00008421-1212-efde-1523-785feabcd124"))

/** 接続失敗時の再試行間隔 */
private val CONNECT_RETRY_INTERVAL = 2.seconds
/** connect() がこれ以上かかったら失敗扱い（Android の既定は約30秒で、タイムアウト内の再試行回数が減るため） */
private val CONNECT_TIMEOUT = 10.seconds
/** 書き込み後この時間で目標値にならなければ書き直す */
private val REWRITE_AFTER = 10.seconds

/** 内部デバッグフラグ: 並列接続で不安定な端末向けに「接続の確立だけ」直列にする（保険・削減対象外。ユーザー設定にはしない） */
internal const val SERIAL_CONNECT = false

/** 処理中 or 失敗した台。成功したら消える */
data class Task(val on: Boolean, val startedAt: Instant, val failed: Boolean = false)

fun PowerState.isTarget(on: Boolean) = if (on) this == PowerState.On else this == PowerState.Sleep

/**
 * ON/OFF の書き込み。1台ごとに独立して並列に走る（docs/architecture/02-power-flow.md）。
 * 接続リトライ・10秒で書き直し・タイムアウトは「確実に切り替わる」ための必須要件（削減対象外）
 */
class PowerController(private val scope: CoroutineScope, private val keeper: Keeper) {
    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks = _tasks.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val connectLock = Mutex()

    /** 処理中に逆の操作をされたら、その台はキャンセルして新しい目標でやり直す */
    fun set(address: String, on: Boolean) {
        jobs.remove(address)?.cancel()
        val now = Clock.System.now()
        val seen = keeper.seen.value[address]
        if (seen != null && now - seen.at <= STALE_AFTER && seen.raw.isTarget(on)) {
            Log.add("$address すでに${on.label()}、書き込みなし")
            _tasks.update { it - address }
            return
        }
        _tasks.update { it + (address to Task(on, now)) }
        val timeout = keeper.timeoutSeconds.value.seconds // 1台ごと（設定で 10〜180 秒）
        jobs[address] = scope.launch {
            Log.add("$address ${on.label()} 開始（タイムアウト ${timeout.inWholeSeconds}秒）")
            val scanning = launch { keeper.scan.collect {} } // 画面を閉じても処理中はスキャンを続ける
            val ok = withTimeoutOrNull(timeout) { writeUntilConfirmed(address, on) } != null
            scanning.cancel()
            jobs.remove(address)
            if (ok) {
                Log.add("$address ${on.label()} 成功（${(Clock.System.now() - now).inWholeSeconds}秒）")
                _tasks.update { it - address }
            } else {
                Log.add("$address ${on.label()} タイムアウト → エラー")
                _tasks.update { it + (address to Task(on, now, failed = true)) }
            }
        }
    }

    /** Identify に 0x00 を書いて LED を点滅させる（失敗しても次の操作に影響しないので再試行は短く） */
    fun identify(address: String) {
        scope.launch {
            withTimeoutOrNull(15.seconds) { connectAndWrite(address, IDENTIFY, 0x00, "点滅") }
                ?: Log.add("$address 点滅 失敗")
        }
    }

    private suspend fun writeUntilConfirmed(address: String, on: Boolean) {
        while (true) {
            val writtenAt = connectAndWrite(address, POWER, if (on) 0x01 else 0x00, on.label())
            val confirmed = withTimeoutOrNull(REWRITE_AFTER) {
                keeper.seen.first { val s = it[address]; s != null && s.at > writtenAt && s.raw.isTarget(on) }
            }
            if (confirmed != null) return
            Log.add("$address 10秒で目標にならず（${keeper.seen.value[address]?.raw ?: "受信なし"}）、書き直し")
        }
    }

    /** 接続 → 書き込み → 即切断。接続・書き込みに失敗したら2秒おきに再試行。書き込んだ時刻を返す */
    private suspend fun connectAndWrite(address: String, characteristic: Characteristic, value: Int, what: String): Instant {
        var attempt = 0
        while (true) {
            attempt++
            val peripheral = keeper.advertisements[address]?.let { Peripheral(it) } ?: Peripheral(address.toIdentifier())
            try {
                val connect = suspend { withTimeout(CONNECT_TIMEOUT) { peripheral.connect() } }
                if (SERIAL_CONNECT) connectLock.withLock { connect() } else connect()
                peripheral.write(characteristic, byteArrayOf(value.toByte()), WriteType.WithResponse)
                Log.add("$address $what 書き込み 0x${value.toString(16).padStart(2, '0')}（${attempt}回目）")
                return Clock.System.now()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive() // 自分がキャンセル・タイムアウトされたなら抜ける
                Log.add("$address 接続/書き込み失敗（${attempt}回目）: $e、2秒後に再試行")
            } finally {
                withContext(NonCancellable) {
                    withTimeoutOrNull(5.seconds) { peripheral.disconnect() }
                    peripheral.close()
                }
            }
            delay(CONNECT_RETRY_INTERVAL)
        }
    }
}

private fun Boolean.label() = if (this) "ON" else "OFF"
