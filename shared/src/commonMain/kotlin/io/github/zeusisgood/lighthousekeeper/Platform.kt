package io.github.zeusisgood.lighthousekeeper

import androidx.compose.runtime.Composable
import com.juul.kable.ScannerBuilder
import kotlin.time.Instant

/** 端末のタイムゾーンで HH:mm:ss */
expect fun formatTime(instant: Instant): String

expect fun platformLog(line: String)

/** 画面オフ時も動くよう低遅延スキャンにする（Android の ScanSettings） */
internal expect fun ScannerBuilder.platformScanSettings()

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
