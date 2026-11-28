package dev.kryvanta.olyvexis.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dev.kryvanta.olyvexis.MainActivity
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service keeping the Wi-Fi streaming session alive when the user leaves the app.
 */
class StreamService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var shizukuJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Keep the device awake while streaming: when the phone screen turns off, Android also suspends
     * virtual displays and the desktop freezes.
     */
    @Suppress("DEPRECATION")
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "Olyvexis:stream")
    }

    /** Keep Wi-Fi in low-latency mode while streaming. */
    @Suppress("DEPRECATION")
    private val wifiLock by lazy {
        applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Olyvexis:wifi")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    StreamSession.stop()
                    stopCleanly()
                }
            }
            else -> {
                startInForeground(notification(getString(R.string.notif_starting)))
                if (!wakeLock.isHeld) wakeLock.acquire()
                runCatching { if (!wifiLock.isHeld) wifiLock.acquire() }
                registerNetworkWatcher()
                watchShizuku()
                monitorJob?.cancel()
                monitorJob = scope.launch {
                    StreamSession.start(applicationContext)
                    StreamSession.state.collectLatest { state ->
                        when (state) {
                            is StreamState.Running -> notify(
                                getString(R.string.notif_running, state.pin, state.url ?: getString(R.string.status_no_wifi)) +
                                    if (state.clients > 0) getString(R.string.notif_watching, state.clients) else "",
                            )
                            is StreamState.Failed, StreamState.Idle -> stopCleanly()
                            StreamState.Starting -> Unit
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun watchShizuku() {
        shizukuJob?.cancel()
        shizukuJob = scope.launch {
            ShizukuManager.state.collectLatest { s ->
                if (s == ShizukuState.NotRunning && StreamSession.state.value is StreamState.Running) {
                    StreamSession.stop()
                    stopCleanly()
                }
            }
        }
    }

    private fun registerNetworkWatcher() {
        if (networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = StreamSession.refreshIps(applicationContext)
            override fun onLost(network: Network) = StreamSession.refreshIps(applicationContext)
        }
        runCatching {
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        }
    }

    private fun stopCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        networkCallback = null
        if (wakeLock.isHeld) wakeLock.release()
        runCatching { if (wifiLock.isHeld) wifiLock.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.stream_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, StreamService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_desktop_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "stream"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.kryvanta.olyvexis.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StreamService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, StreamService::class.java).setAction(ACTION_STOP))
        }
    }
}
