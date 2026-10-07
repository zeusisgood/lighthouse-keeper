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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/** ② デバイス追加。スキャンは画面表示中ずっと動いているので、ここでは結果を絞り込むだけ */
@Composable
fun AddDeviceScreen(keeper: Keeper, onRegistered: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val seen by keeper.seen.collectAsState()
    val registered = keeper.devices.collectAsState().value.orEmpty().map { it.address }.toSet()
    val found = seen.mapNotNull { (address, s) -> s.name?.takeIf { it.startsWith("LHB-") }?.let { address to it } }.sortedBy { it.second }

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
                        scope.launch {
                            keeper.repo.add(Device(address, name))
                            Log.add("$address 登録（$name）")
                            onRegistered(address)
                        }
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
}

/** 登録直後の別名入力（スキップ可）。「点滅させる」でどの実機かを確認できる */
@Composable
fun AliasScreen(keeper: Keeper, address: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val device = keeper.devices.collectAsState().value?.find { it.address == address }
    var alias by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("別名を付ける")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(device?.name ?: address, color = OffColor)
            OutlinedTextField(alias, { alias = it }, label = { Text("別名（例: 左）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            BigButton("点滅させる", BusyColor, Modifier.fillMaxWidth()) { keeper.power.identify(address) }
            BigButton("保存", OnColor, Modifier.fillMaxWidth()) {
                scope.launch {
                    keeper.repo.rename(address, alias.trim())
                    onDone()
                }
            }
            BigButton("スキップ", OffColor, Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}
