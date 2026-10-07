package io.github.zeusisgood.lighthousekeeper

import android.app.Application
import kotlinx.coroutines.MainScope

class KeeperApp : Application() {
    val keeper by lazy { Keeper(MainScope()) }
}
