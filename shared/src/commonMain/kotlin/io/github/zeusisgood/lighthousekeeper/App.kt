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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
/** 1.0 の「ON（送信済み）」。状態を確認できていないので ON より淡くする */
val SentOnColor = Color(0xFF86EFAC)
val OffColor = Color(0xFF64748B)
val BusyColor = Color(0xFF38BDF8)
val ErrorColor = Color(0xFFF87171)
val TextColor = Color(0xFFE5E7EB)

/** 画面遷移（docs/architecture/04-screen-flow.md） */
sealed interface Screen {
    data object List : Screen
    data object Add : Screen
    data class Device(val address: String) : Screen
    data object Settings : Screen
    data object Log : Screen
}

@Composable
fun App(
    keeper: Keeper,
    version: String,
    bluetoothGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val onboarded = keeper.onboarded.collectAsState().value
    PlatformBackHandler(enabled = screen != Screen.List) { screen = if (screen == Screen.Log) Screen.Settings else Screen.List }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background, onBackground = TextColor,
            surface = CardColor, onSurface = TextColor, primary = BusyColor,
        )
    ) {
        Box(Modifier.fillMaxSize().background(Background).safeDrawingPadding()) {
            when {
                onboarded == null -> {} // 読み込み中
                !onboarded -> PermissionScreen(onRequestPermissions)
                else -> when (val s = screen) {
                    Screen.List -> Column {
                        if (!bluetoothGranted) PermissionBanner(onOpenAppSettings)
                        ListScreen(keeper, onAdd = { screen = Screen.Add }, onSettings = { screen = Screen.Settings }, onDevice = { screen = Screen.Device(it) })
                    }
                    Screen.Add -> AddDeviceScreen(keeper, onRegistered = { screen = Screen.Device(it) }, onBack = { screen = Screen.List })
                    is Screen.Device -> DeviceScreen(keeper, s.address, onBack = { screen = Screen.List })
                    Screen.Settings -> SettingsScreen(keeper, version, onLog = { screen = Screen.Log }, onBack = { screen = Screen.List })
                    Screen.Log -> LogScreen(onBack = { screen = Screen.Settings })
                }
            }
        }
    }
}

/** 初回起動: 権限の説明（1枚）。許可・拒否どちらでも一覧へ進む */
@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        Text("Lighthouse Keeper", fontSize = 24.sp, color = TextColor)
        Text("ベースステーションの状態を読み取り、ON/スリープを切り替えるために次の権限を使います。", color = TextColor)
        Text("・付近のデバイス（Bluetooth）: 必須。位置情報は使いません", color = TextColor)
        Text("・通知: 任意。処理中と失敗を知らせます。許可しなくても切り替えは動きます", color = TextColor)
        BigButton("許可する", OnColor, Modifier.fillMaxWidth(), onClick = onRequest)
    }
}

/** Bluetooth 権限が拒否されているときに一覧の上に出す */
@Composable
private fun PermissionBanner(onOpenAppSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(CardColor).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("付近のデバイスの権限がないため、状態の取得と切り替えができません", color = ErrorColor, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenAppSettings) { Text("設定を開く") }
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
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text("←", fontSize = 22.sp, color = TextColor) }
        Text(title, fontSize = 20.sp, color = TextColor, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        actions()
    }
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(64.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = CardColor, contentColor = color),
    ) { Text(text, fontSize = 18.sp) }
}
