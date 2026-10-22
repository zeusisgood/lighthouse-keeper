package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** デバイス詳細。登録直後と一覧のカードタップの両方から開く。別名・点滅（2.0 のみ）・個別ON/OFF・このデバイスのログ・削除 */
@Composable
fun DeviceScreen(keeper: Keeper, address: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val device = keeper.devices.collectAsState().value?.find { it.address == address } ?: return
    val seen by keeper.seen.collectAsState()
    val tasks by keeper.power.tasks.collectAsState()
    val sent by keeper.power.sent.collectAsState()
    val lines by Log.lines.collectAsState()
    val now by rememberNow()
    var alias by remember { mutableStateOf(device.alias) }
    var confirmRemove by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf(false) }
    val task = tasks[address]

    Column(Modifier.fillMaxSize()) {
        TopBar(device.title, onBack = onBack) {
            TextButton(onClick = { confirmRemove = true }) { Text("削除", color = ErrorColor) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceCard(device, seen[address], task, sent[address], now, onToggle = { keeper.power.set(address, it) })
            Text(if (device.isV1) "$address ・ ID ${device.v1Id.ifBlank { "なし" }} ・ 1.0（未検証）" else address, fontSize = 12.sp, color = OffColor)
            if (device.isV1 && device.v1Id.isBlank() && task?.failed == true && !task.on) {
                Text("スリープできない場合は、本体IDを入れると直るかもしれません", color = ErrorColor, fontSize = 12.sp)
            }
            if (device.isV1) BigButton(if (device.v1Id.isBlank()) "本体IDを入力" else "本体IDを変更", TextColor, Modifier.fillMaxWidth()) { editId = true }
            OutlinedTextField(alias, { alias = it }, label = { Text("別名（例: 左）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!device.isV1) BigButton("点滅させる", BusyColor, Modifier.fillMaxWidth()) { keeper.power.identify(address) } // 1.0 には Identify がない
            BigButton("保存", OnColor, Modifier.fillMaxWidth()) {
                scope.launch {
                    keeper.repo.rename(address, alias.trim())
                    onBack()
                }
            }
            Text("ログ（状態遷移・接続・書き込み）", color = TextColor)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(lines.filter { address in it }.asReversed()) {
                Text(it.replace("$address ", ""), color = TextColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }

    if (editId) V1IdDialog(device.name, device.v1Id, confirm = "保存", onDismiss = { editId = false }) {
        editId = false
        scope.launch {
            keeper.repo.setV1Id(address, it)
            Log.add("$address 本体ID ${it.ifBlank { "なし" }}")
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("${device.title} を削除しますか？") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    keeper.repo.remove(address)
                    Log.add("$address 削除")
                    onBack()
                }
            }) { Text("削除", color = ErrorColor) }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("キャンセル") } },
    )
}
