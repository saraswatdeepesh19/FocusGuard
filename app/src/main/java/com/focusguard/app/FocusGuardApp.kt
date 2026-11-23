package com.focusguard.app

import android.app.Application
import com.focusguard.app.util.Notifications
import com.focusguard.app.work.UsageSnapshotWorker
import kotlinx.coroutines.launch

class FocusGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        UsageSnapshotWorker.schedule(this)
        // Anonymous, random, stays on the device. Lets v2 attach history to an account later.
        container.appScope.launch { container.settings.ensureInstallId() }
    }
}
