package io.github.zeusisgood.lighthousekeeper

import android.bluetooth.le.ScanSettings
import com.juul.kable.ScannerBuilder
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

actual fun formatTime(instant: Instant): String = timeFormat.format(instant.toJavaInstant())

actual fun platformLog(line: String) {
    android.util.Log.i("Keeper", line)
}

internal actual fun ScannerBuilder.platformScanSettings() {
    scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
}
