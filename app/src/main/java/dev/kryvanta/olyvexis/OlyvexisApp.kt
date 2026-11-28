package dev.kryvanta.olyvexis

import android.app.Application
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.display.CableSession
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OlyvexisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        InputSettings.ensureLoaded(this)
        DesktopController.initPrefs(this)
        ShizukuManager.init(this)
        // Restore global display settings if a previous cable session exited abnormally.
        CoroutineScope(Dispatchers.Main).launch {
            ShizukuManager.state.first { it == ShizukuState.Ready }
            CableSession.recoverLeftoverSettings(this@OlyvexisApp)
        }
    }
}
