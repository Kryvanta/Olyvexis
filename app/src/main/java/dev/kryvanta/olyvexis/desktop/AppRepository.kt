package dev.kryvanta.olyvexis.desktop

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Danh sách app, icon và ghim ứng dụng cho desktop. */
class AppRepository(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun launchableApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = null, // Icon nạp lười qua AppIcons.
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Theo dõi cài/gỡ/cập nhật app để menu ứng dụng tự động làm mới. */
    fun observeLaunchableApps(): Flow<List<LaunchableApp>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(launchableApps())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        trySend(launchableApps())
        context.applicationContext.registerReceiver(receiver, filter)
        awaitClose {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
    }

    /**
     * Danh sách app được ghim (lưu trong SharedPreferences; lần đầu lấy các app phổ biến).
     */
    fun pinnedApps(all: List<LaunchableApp>): List<LaunchableApp> {
        val byPackage = all.groupBy { it.component.packageName }
        val saved = savedPinnedPackages()
        if (saved != null) {
            return saved.mapNotNull { pkg -> byPackage[pkg]?.firstOrNull() }
        }
        val defaults = PINNED_CANDIDATES.mapNotNull { group -> group.firstNotNullOfOrNull { byPackage[it]?.first() } }
        savePinnedPackages(defaults.map { it.component.packageName })
        return defaults
    }

    fun isPinned(packageName: String): Boolean =
        savedPinnedPackages()?.contains(packageName) == true

    fun togglePin(packageName: String, all: List<LaunchableApp>): List<LaunchableApp> {
        val current = (savedPinnedPackages() ?: pinnedApps(all).map { it.component.packageName }).toMutableList()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        savePinnedPackages(current)
        return pinnedApps(all)
    }

    private fun savedPinnedPackages(): List<String>? {
        val raw = prefs.getString(KEY_PINNED, null) ?: return null
        return raw.split('|').filter { it.isNotBlank() }
    }

    private fun savePinnedPackages(packages: List<String>) {
        prefs.edit().putString(KEY_PINNED, packages.joinToString("|")).apply()
    }

    private companion object {
        const val PREFS = "desktop_pins"
        const val KEY_PINNED = "pinned_packages"

        val PINNED_CANDIDATES = listOf(
            // Tệp
            listOf("com.sec.android.app.myfiles", "com.hihonor.filemanager", "com.huawei.filemanager",
                "com.mi.android.globalFileexplorer", "com.android.fileexplorer", "com.coloros.filemanager",
                "com.google.android.apps.nbu.files", "com.google.android.documentsui"),
            // Trình duyệt
            listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.microsoft.emmx"),
            // Ảnh
            listOf("com.sec.android.gallery3d", "com.hihonor.photos", "com.huawei.photos", "com.miui.gallery",
                "com.coloros.gallery3d", "com.google.android.apps.photos"),
            // Cài đặt
            listOf("com.android.settings"),
        )
    }
}

/** Bộ nhớ đệm icon app dạng ImageBitmap cho Compose. */
object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(256)

    /** Gọi trên luồng nền: nạp icon từ PackageManager có thể chậm. */
    fun load(context: Context, app: LaunchableApp, sizePx: Int = 96): ImageBitmap? {
        val key = app.component.flattenToString()
        cache.get(key)?.let { return it }
        return runCatching {
            context.packageManager.getActivityIcon(app.component).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }

    fun cached(app: LaunchableApp): ImageBitmap? = cache.get(app.component.flattenToString())
}

@androidx.compose.runtime.Composable
fun rememberAppIcon(app: LaunchableApp): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = app.component.flattenToString()
    val state = androidx.compose.runtime.remember(key) {
        androidx.compose.runtime.mutableStateOf(AppIcons.cached(app))
    }
    androidx.compose.runtime.LaunchedEffect(key) {
        if (state.value == null) {
            state.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppIcons.load(context, app)
            }
        }
    }
    return state.value
}
