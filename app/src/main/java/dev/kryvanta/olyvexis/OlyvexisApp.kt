package dev.kryvanta.olyvexis

import android.app.Application
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.display.CableSession
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OlyvexisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        InputSettings.ensureLoaded(this)
        DesktopController.initPrefs(this)
        ShizukuManager.init(this)
        // Lần chạy trước có thể đã bị tắt hẳn khi desktop đang chạy qua cáp (vd. cài lại app), để
        // sót force_desktop_mode_on_external_displays trên máy: trả lại ngay khi có Shizuku.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            ShizukuManager.state.first { it == ShizukuState.Ready }
            CableSession.recoverLeftoverSettings(this@OlyvexisApp)
        }
    }
}
