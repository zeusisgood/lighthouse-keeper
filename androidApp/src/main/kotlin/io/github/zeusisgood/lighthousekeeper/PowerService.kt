package io.github.zeusisgood.lighthousekeeper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 書き込み処理中にアプリを閉じても最後まで続けるための入れ物（処理の本体は PowerController）。
 * 処理中の通知を出し、終わったら消す。失敗した台だけスワイプで消せる通知を残す
 */
class PowerService : Service() {
    private val scope = MainScope()
    private var watching: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(PROGRESS_ID, progress("処理中…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            // Bluetooth 権限がないなど。処理自体は PowerController で続く（アプリを閉じると止まる可能性あり）
            Log.add("Foreground Service を開始できない: $e")
            stopSelf()
            return START_NOT_STICKY
        }
        if (watching == null) watching = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        val keeper = (application as KeeperApp).keeper
        val notifications = getSystemService(NotificationManager::class.java)
        val batch = mutableSetOf<String>()
        val done = keeper.power.tasks.first { tasks ->
            val busy = tasks.filterValues { !it.failed }
            batch += busy.keys
            if (busy.isNotEmpty()) {
                val verb = when {
                    busy.values.all { it.on } -> "起動中"
                    busy.values.none { it.on } -> "スリープ中"
                    else -> "切り替え中"
                }
                notifications.notify(PROGRESS_ID, progress("ベースステーションを$verb… ${batch.size}台中${batch.size - busy.size}台完了"))
            }
            busy.isEmpty()
        }
        // 失敗した台だけ、スワイプで消せる通知を残す（通知権限がなければ表示されないだけ）
        val titles = keeper.devices.value.orEmpty().associate { it.address to it.title }
        for (address in batch) {
            val task = done[address] ?: continue
            val what = if (task.on) "起動" else "スリープ"
            notifications.notify(address.hashCode(), failure("${titles[address] ?: address}: ${what}を確認できませんでした"))
        }
        watching = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
    }

    private fun progress(text: String) = Notification.Builder(this, CHANNEL_PROGRESS)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(text)
        .setContentIntent(openApp())
        .setOngoing(true)
        .build()

    private fun failure(text: String) = Notification.Builder(this, CHANNEL_FAILURE)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(text)
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_FAILURE = "failure"
        private const val PROGRESS_ID = 1
    }
}
