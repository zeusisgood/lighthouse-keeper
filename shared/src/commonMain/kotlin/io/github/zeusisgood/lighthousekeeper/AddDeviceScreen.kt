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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
