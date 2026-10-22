package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** ② デバイス追加。スキャンは画面表示中ずっと動いているので、ここでは結果を絞り込むだけ。1.0 は登録前に本体IDを聞く */
@Composable
fun AddDeviceScreen(keeper: Keeper, onRegistered: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val seen by keeper.seen.collectAsState()
    val registered = keeper.devices.collectAsState().value.orEmpty().map { it.address }.toSet()
    val found = seen.mapNotNull { (address, s) -> s.name?.takeIf { it.startsWith("LHB-") || isV1Name(it) }?.let { address to it } }.sortedBy { it.second }
    var askId by remember { mutableStateOf<Pair<String, String>?>(null) } // 1.0 の (address, name)
    fun register(device: Device) {
        scope.launch {
            keeper.repo.add(device)
            Log.add("${device.address} 登録（${device.name}${if (device.isV1) "、ID ${device.v1Id}" else ""}）")
            onRegistered(device.address)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("デバイスを追加", onBack = onBack)
        if (found.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("探しています…", color = OffColor) }
            return@Column
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(found, key = { it.first }) { (address, name) ->
                val done = address in registered
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !done) {
                        if (isV1Name(name)) askId = address to name else register(Device(address, name))
                    },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, fontSize = 18.sp, color = if (done) OffColor else TextColor)
                        Text(if (done) "登録済み" else address, fontSize = 12.sp, color = OffColor)
                    }
                }
            }
        }
    }

    askId?.let { (address, name) ->
        var input by remember { mutableStateOf("") }
        val id = normalizeV1Id(input, name)
        AlertDialog(
            onDismissRequest = { askId = null },
            title = { Text("本体IDを入力") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Base Station 1.0 のスリープには、背面ラベルの ID（8桁の英数字）が必要です。先頭4桁だけでも構いません（末尾4桁 ${name.takeLast(4)} は名前から補います）")
                    Text("1.0 の対応は実機で未検証です", color = OffColor, fontSize = 12.sp)
                    OutlinedTextField(input, { input = it }, label = { Text("ID") }, singleLine = true, isError = input.isNotBlank() && id == null)
                }
            },
            confirmButton = {
                TextButton(enabled = id != null, onClick = {
                    askId = null
                    register(Device(address, name, v1Id = id!!))
                }) { Text("登録") }
            },
            dismissButton = { TextButton(onClick = { askId = null }) { Text("キャンセル") } },
        )
    }
}
