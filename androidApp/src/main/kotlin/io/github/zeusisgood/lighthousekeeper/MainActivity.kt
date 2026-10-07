package io.github.zeusisgood.lighthousekeeper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

private val BLUETOOTH = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    private val bluetoothGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keeper = (application as KeeperApp).keeper
        // 初回の説明画面から。許可・拒否どちらでも一覧へ（通知は任意）
        val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermission()
            lifecycleScope.launch { keeper.repo.setOnboarded() }
        }
        // 画面表示中はスキャンしっぱなし（権限がなければスキャン側が再試行し続ける）
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { keeper.scan.collect {} } }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            App(
                keeper, version, bluetoothGranted.value,
                onRequestPermissions = { request.launch(BLUETOOTH + Manifest.permission.POST_NOTIFICATIONS) },
                onOpenAppSettings = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                },
            )
        }
    }

    // 設定アプリから戻ったときに反映する
    override fun onResume() {
        super.onResume()
        refreshPermission()
    }

    private fun refreshPermission() {
        bluetoothGranted.value = BLUETOOTH.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
