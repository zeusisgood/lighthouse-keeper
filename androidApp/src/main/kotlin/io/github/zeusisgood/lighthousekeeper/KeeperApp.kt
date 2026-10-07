package io.github.zeusisgood.lighthousekeeper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KeeperApp : Application() {
    private val scope = MainScope()
    val keeper by lazy { Keeper(scope, filesDir.resolve("keeper.preferences_pb").path) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(PowerService.CHANNEL_PROGRESS, "処理中", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(PowerService.CHANNEL_FAILURE, "失敗", NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
        // 処理中の台が出たら Foreground Service を開始（操作は画面からなので前面から開始できる）
        scope.launch {
            keeper.power.tasks.map { tasks -> tasks.values.any { !it.failed } }
                .distinctUntilChanged()
                .filter { it }
                .collect { startForegroundService(Intent(this@KeeperApp, PowerService::class.java)) }
        }
    }
}
