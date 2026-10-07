package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

val Background = Color(0xFF121417)
val CardColor = Color(0xFF1E2228)
val OnColor = Color(0xFF4ADE80)
val OffColor = Color(0xFF64748B)
val BusyColor = Color(0xFF38BDF8)
val ErrorColor = Color(0xFFF87171)
val TextColor = Color(0xFFE5E7EB)

@Composable
fun App(keeper: Keeper) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background, onBackground = TextColor,
            surface = Background, onSurface = TextColor, primary = BusyColor,
        )
    ) {
        Box(Modifier.fillMaxSize().background(Background).safeDrawingPadding()) {
            ListScreen(keeper)
        }
    }
}

/** 1秒ごとに進む現在時刻（「不明」への切り替えと経過秒数の表示用） */
@Composable
fun rememberNow() = produceState(Clock.System.now()) {
    while (true) {
        delay(1_000)
        value = Clock.System.now()
    }
}

@Composable
private fun ListScreen(keeper: Keeper) {
    val seen by keeper.seen.collectAsState()
    val tasks by keeper.power.tasks.collectAsState()
    val now by rememberNow()
    // ponytail: 段階5で登録済みデバイスの一覧に置き換える。今はスキャンで見えた全台
    val devices = seen.entries.filter { it.value.name?.startsWith("LHB-") == true }.sortedBy { it.value.name }.map { it.key }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("全部ON", OnColor, Modifier.weight(1f)) { devices.forEach { keeper.power.set(it, true) } }
            BigButton("全部OFF", OffColor, Modifier.weight(1f)) { devices.forEach { keeper.power.set(it, false) } }
        }
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ベースステーションを探しています…", color = OffColor)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices, key = { it }) { address ->
                val s = seen[address]
                DeviceCard(
                    title = s?.name ?: address, subtitle = address, seen = s, task = tasks[address], now = now,
                    onToggle = { keeper.power.set(address, it) },
                )
            }
        }
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardColor, contentColor = color),
    ) { Text(text, fontSize = 18.sp) }
}

@Composable
private fun DeviceCard(title: String, subtitle: String, seen: Seen?, task: Task?, now: Instant, onToggle: (Boolean) -> Unit) {
    val display = display(seen, task, now)
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, color = TextColor)
                Text(subtitle, fontSize = 12.sp, color = OffColor)
                Text("最終確認 ${seen?.let { formatTime(it.at) } ?: "-"}", fontSize = 12.sp, color = OffColor)
            }
            val (label, color) = when (display) {
                Display.On -> "ON" to OnColor
                Display.Off -> "OFF" to OffColor
                Display.Busy -> "処理中（${(now - task!!.startedAt).inWholeSeconds}秒）" to BusyColor
                Display.Error -> "エラー" to ErrorColor
                Display.Unknown -> "不明" to OffColor
            }
            Text(label, color = color, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
            Switch(
                // 処理中・エラーは目標側に倒しておく
                checked = if (task != null && display != Display.On && display != Display.Off) task.on else display == Display.On,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = OnColor),
            )
        }
    }
}
