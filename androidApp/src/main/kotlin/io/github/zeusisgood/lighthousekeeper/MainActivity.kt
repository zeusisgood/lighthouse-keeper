package io.github.zeusisgood.lighthousekeeper

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keeper = (application as KeeperApp).keeper
        // ponytail: 段階8で権限の説明画面に置き換える
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        // 画面表示中はスキャンしっぱなし
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { keeper.scan.collect {} } }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent { App(keeper, version) }
    }
}
