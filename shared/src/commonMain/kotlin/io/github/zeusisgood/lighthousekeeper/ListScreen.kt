package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Instant

@Composable
fun ListScreen(keeper: Keeper, onAdd: () -> Unit, onSettings: () -> Unit, onDevice: (String) -> Unit) {
    val devices = keeper.devices.collectAsState().value ?: return
    val seen by keeper.seen.collectAsState()
    val tasks by keeper.power.tasks.collectAsState()
    val now by rememberNow()

    Column(Modifier.fillMaxSize()) {
        TopBar("Lighthouse Keeper") {
            TextButton(onClick = onAdd) { Text("＋", fontSize = 22.sp, color = TextColor) }
            TextButton(onClick = onSettings) { Text("⚙", fontSize = 22.sp, color = TextColor) }
        }
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                BigButton("デバイスを追加", BusyColor, Modifier.fillMaxWidth(), onClick = onAdd)
            }
            return@Column
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("全部ON", OnColor, Modifier.weight(1f)) { devices.forEach { keeper.power.set(it.address, true) } }
                BigButton("全部OFF", OffColor, Modifier.weight(1f)) { devices.forEach { keeper.power.set(it.address, false) } }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(devices, key = { it.address }) { device ->
                    DeviceCard(
                        device, seen[device.address], tasks[device.address], now,
                        onToggle = { keeper.power.set(device.address, it) },
                        onClick = { onDevice(device.address) },
                    )
                }
            }
        }
    }
}

/** 一覧のカード。詳細画面では onClick なしで状態表示とトグルに使う */
@Composable
fun DeviceCard(device: Device, seen: Seen?, task: Task?, now: Instant, onToggle: (Boolean) -> Unit, onClick: (() -> Unit)? = null) {
    val display = display(seen, task, now)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.title, fontSize = 20.sp, color = TextColor)
                if (device.alias.isNotBlank()) Text(device.name, fontSize = 12.sp, color = OffColor)
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
