package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** ③ 設定 */
@Composable
fun SettingsScreen(keeper: Keeper, version: String, onLog: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saved by keeper.timeoutSeconds.collectAsState()
    var seconds by remember(saved) { mutableStateOf(saved.toFloat()) }
    Column(Modifier.fillMaxSize()) {
        TopBar("設定", onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("タイムアウト（1台ごと）: ${seconds.roundToInt()}秒", color = TextColor)
            Slider(
                value = seconds, onValueChange = { seconds = it },
                valueRange = 10f..180f, steps = 16, // 10秒刻み
                onValueChangeFinished = { scope.launch { keeper.repo.setTimeoutSeconds(seconds.roundToInt()) } },
            )
            BigButton("ログを見る", TextColor, Modifier.fillMaxWidth(), onClick = onLog)
            Text("バージョン $version", color = OffColor)
        }
    }
}

/** 接続・書き込み・リトライ・状態遷移の記録（新しい順） */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val lines by Log.lines.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("ログ", onBack = onBack)
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(lines.asReversed()) {
                Text(it, color = TextColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
