package io.github.zeusisgood.lighthousekeeper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/** 不具合調査用のログ（保険・削減対象外）。接続・書き込み・リトライ・状態遷移を記録する */
object Log {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()

    // ponytail: メモリ上のみ・最新1000行。プロセスが落ちると消える。足りなければファイルへ書き出す
    fun add(message: String) {
        val line = "${formatTime(Clock.System.now())} $message"
        platformLog(line)
        _lines.update { (it + line).takeLast(1000) }
    }
}
