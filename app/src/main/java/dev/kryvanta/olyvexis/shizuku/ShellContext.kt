package dev.kryvanta.olyvexis.shizuku

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process

/**
 * Context giả cho tiến trình shell: hệ thống kiểm tra tên gói khớp với uid người gọi
 * (vd. DisplayManagerService.createVirtualDisplay), nên phải khai là `com.android.shell`.
 * Cách làm giống FakeContext của scrcpy.
 */
class ShellContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName() = SHELL_PACKAGE

    override fun getOpPackageName() = SHELL_PACKAGE

    @SuppressLint("NewApi")
    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(SHELL_PACKAGE).build()

    override fun getDeviceId() = 0 // Context.DEVICE_ID_DEFAULT

    override fun getApplicationContext(): Context = this

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"

        fun supportsAttribution() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
