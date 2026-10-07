package io.github.zeusisgood.lighthousekeeper

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** 登録デバイス。並び順＝登録順 */
data class Device(val address: String, val name: String, val alias: String = "") {
    val title get() = alias.ifBlank { name }
}

/** DataStore（Preferences）。登録デバイスとタイムアウト設定。登録デバイスは1行1台・タブ区切りの文字列1つで持つ */
class Repository(path: String) {
    private val store = PreferenceDataStoreFactory.createWithPath { path.toPath() }
    private val devicesKey = stringPreferencesKey("devices")
    private val timeoutKey = intPreferencesKey("timeoutSeconds")

    val devices = store.data.map { decode(it[devicesKey]) }

    /** 1台ごとのタイムアウト秒数（10〜180、デフォルト60） */
    val timeoutSeconds = store.data.map { it[timeoutKey] ?: 60 }
    suspend fun setTimeoutSeconds(seconds: Int) {
        store.edit { it[timeoutKey] = seconds.coerceIn(10, 180) }
    }

    suspend fun add(device: Device) = update { list -> if (list.any { it.address == device.address }) list else list + device }
    suspend fun rename(address: String, alias: String) = update { list -> list.map { if (it.address == address) it.copy(alias = alias) else it } }
    suspend fun remove(address: String) = update { list -> list.filter { it.address != address } }

    private suspend fun update(change: (List<Device>) -> List<Device>) {
        store.edit { prefs ->
            prefs[devicesKey] = encode(change(decode(prefs[devicesKey])))
        }
    }
}

internal fun decode(text: String?) = text.orEmpty().lines().filter { it.isNotBlank() }.map { line ->
    line.split('\t').let { Device(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
}

internal fun encode(devices: List<Device>) = devices.joinToString("\n") { d ->
    listOf(d.address, d.name, d.alias).joinToString("\t") { it.replace(Regex("[\t\n\r]"), " ") }
}
