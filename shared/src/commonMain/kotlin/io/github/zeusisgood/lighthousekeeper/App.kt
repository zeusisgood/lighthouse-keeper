package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
    val now by rememberNow()
    // ponytail: 段階5で登録済みデバイスの一覧に置き換える。今はスキャンで見えた全台
    val devices = seen.entries.filter { it.value.name?.startsWith("LHB-") == true }.sortedBy { it.value.name }
    if (devices.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ベースステーションを探しています…", color = OffColor)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(devices, key = { it.key }) { (address, s) ->
            DeviceCard(title = s.name ?: address, subtitle = address, display = display(s, now), seen = s)
        }
    }
}

@Composable
private fun DeviceCard(title: String, subtitle: String, display: Display, seen: Seen?) {
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
                Display.Unknown -> "不明" to OffColor
            }
            Text(label, color = color, fontSize = 18.sp)
        }
    }
}
