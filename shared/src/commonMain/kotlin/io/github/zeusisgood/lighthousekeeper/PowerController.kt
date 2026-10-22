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
/** Base Station 1.0 の電源（書き込み・読み戻しとも同じ Characteristic） */
private val V1_POWER = characteristicOf(V1_SERVICE, Uuid.parse("0000cb01-0000-1000-8000-00805f9b34fb"))

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

/** 1.0 で最後に受け付けを確認した操作。電源状態が読めないので「送信済み」として表示する（メモリ上のみ） */
data class Sent(val on: Boolean, val at: Instant)

fun PowerState.isTarget(on: Boolean) = if (on) this == PowerState.On else this == PowerState.Sleep

/**
 * ON/OFF の書き込み。1台ごとに独立して並列に走る（docs/architecture/02-power-flow.md）。
 * 接続リトライ・10秒で書き直し・タイムアウトは「確実に切り替わる」ための必須要件（削減対象外）
 */
class PowerController(private val scope: CoroutineScope, private val keeper: Keeper) {
    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks = _tasks.asStateFlow()
    private val _sent = MutableStateFlow<Map<String, Sent>>(emptyMap())
    val sent = _sent.asStateFlow()

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
        _sent.update { it - address }
        val v1Id = keeper.devices.value?.find { it.address == address }?.takeIf { it.isV1 }?.v1Id
        val timeout = keeper.timeoutSeconds.value.seconds // 1台ごと（設定で 10〜180 秒）
        jobs[address] = scope.launch {
            Log.add("$address ${on.label()} 開始（タイムアウト ${timeout.inWholeSeconds}秒）")
            val scanning = launch { keeper.scan.collect {} } // 画面を閉じても処理中はスキャンを続ける
            val ok = withTimeoutOrNull(timeout) {
                if (v1Id != null) writeV1(address, on, v1Id) else writeUntilConfirmed(address, on)
            } != null
            scanning.cancel()
            jobs.remove(address)
            if (ok) {
                Log.add("$address ${on.label()} 成功（${(Clock.System.now() - now).inWholeSeconds}秒）")
                _tasks.update { it - address }
                if (v1Id != null) _sent.update { it + (address to Sent(on, Clock.System.now())) }
            } else {
                Log.add("$address ${on.label()} タイムアウト → エラー")
                _tasks.update { it + (address to Task(on, now, failed = true)) }
            }
        }
    }

    /** Identify に 0x00 を書いて LED を点滅させる（失敗しても次の操作に影響しないので再試行は短く） */
    fun identify(address: String) {
        scope.launch {
            withTimeoutOrNull(15.seconds) { connectAndWrite(address, IDENTIFY, byteArrayOf(0x00), "点滅") }
                ?: Log.add("$address 点滅 失敗")
        }
    }

    private suspend fun writeUntilConfirmed(address: String, on: Boolean) {
        while (true) {
            val writtenAt = connectAndWrite(address, POWER, byteArrayOf(if (on) 0x01 else 0x00), on.label())
            val confirmed = withTimeoutOrNull(REWRITE_AFTER) {
                keeper.seen.first { val s = it[address]; s != null && s.at > writtenAt && s.raw.isTarget(on) }
            }
            if (confirmed != null) return
            Log.add("$address 10秒で目標にならず（${keeper.seen.value[address]?.raw ?: "受信なし"}）、書き直し")
        }
    }

    /**
     * 1.0: アドバタイズで確認できないので、書き込んだ同じ接続で読み戻して受け付けを確認する。
     * 不一致・読み戻し失敗は接続失敗と同じく2秒後に接続からやり直す（タイムアウトの範囲内）
     */
    private suspend fun writeV1(address: String, on: Boolean, id: String) {
        connectAndWrite(address, V1_POWER, v1Command(on, id), on.label()) { v1Accepted(on, it) }
    }

    /**
     * 接続 → 書き込み →（accepted があれば読み戻して確認）→ 即切断。失敗したら2秒おきに再試行。書き込んだ時刻を返す
     */
    private suspend fun connectAndWrite(
        address: String, characteristic: Characteristic, data: ByteArray, what: String,
        accepted: ((ByteArray) -> Boolean)? = null,
    ): Instant {
        var attempt = 0
        while (true) {
            attempt++
            val peripheral = keeper.advertisements[address]?.let { Peripheral(it) } ?: Peripheral(address.toIdentifier())
            try {
                val connect = suspend { withTimeout(CONNECT_TIMEOUT) { peripheral.connect() } }
                if (SERIAL_CONNECT) connectLock.withLock { connect() } else connect()
                peripheral.write(characteristic, data, WriteType.WithResponse)
                Log.add("$address $what 書き込み ${data.toHex()}（${attempt}回目）")
                if (accepted != null) {
                    val read = peripheral.read(characteristic)
                    Log.add("$address $what 読み戻し ${read.toHex()}")
                    check(accepted(read)) { "読み戻しが想定外" }
                }
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

private fun ByteArray.toHex() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
