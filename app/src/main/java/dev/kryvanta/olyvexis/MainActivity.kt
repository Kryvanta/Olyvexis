package dev.kryvanta.olyvexis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.display.CableSession
import dev.kryvanta.olyvexis.display.CableState
import dev.kryvanta.olyvexis.display.DisplayDetector
import dev.kryvanta.olyvexis.display.DisplayMode
import dev.kryvanta.olyvexis.display.VirtualDisplayTestScreen
import dev.kryvanta.olyvexis.input.ActiveInput
import dev.kryvanta.olyvexis.input.InputEvent
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import dev.kryvanta.olyvexis.stream.StreamService
import dev.kryvanta.olyvexis.stream.StreamSession
import dev.kryvanta.olyvexis.stream.StreamState
import dev.kryvanta.olyvexis.ui.OlyvexisTheme
import dev.kryvanta.olyvexis.ui.AppsSheet
import dev.kryvanta.olyvexis.ui.ControllerBanner
import dev.kryvanta.olyvexis.ui.ControllerScreen
import dev.kryvanta.olyvexis.ui.ControllerStatus
import dev.kryvanta.olyvexis.ui.DeskIcons
import dev.kryvanta.olyvexis.ui.LaptopSheet
import dev.kryvanta.olyvexis.ui.PadButton
import dev.kryvanta.olyvexis.ui.Phone
import dev.kryvanta.olyvexis.ui.SettingsSheet
import dev.kryvanta.olyvexis.ui.ShizukuSheet
import dev.kryvanta.olyvexis.ui.Welcome
import dev.kryvanta.olyvexis.ui.WelcomeScreen
import dev.kryvanta.olyvexis.ui.enterKey
import kotlinx.coroutines.launch

/**
 * Phone UI (inspired by iDesk): single trackpad controller screen with Apps, Laptop, Settings, and
 * Shizuku modal sheets. Automatically launches on external display when plugged in.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        InputSettings.ensureLoaded(this)
        setContent {
            OlyvexisTheme { Phone() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case the user just enabled Shizuku or granted permission.
        ShizukuManager.refresh()
    }
}

private enum class Sheet { None, Apps, Laptop, Settings, Shizuku }

@Composable
private fun Phone() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuManager.state.collectAsState()
    val cable by CableSession.state.collectAsState()
    val stream by StreamSession.state.collectAsState()
    val windows by DesktopController.windows.collectAsState()
    val displayMode by remember { DisplayDetector(context).modes() }.collectAsState(initial = DisplayMode.VirtualOverWifi)
    var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
    var welcome by rememberSaveable { mutableStateOf(!Welcome.seen(context)) }
    var preview by rememberSaveable { mutableStateOf(false) }
    /** Display ID already auto-started in this connection so stopping doesn't immediately relaunch. */
    var autoStartedFor by remember { mutableIntStateOf(-1) }

    val external = displayMode as? DisplayMode.External
    val cableRunning = cable as? CableState.Running
    val streamRunning = stream as? StreamState.Running
    val desktopRunning = cableRunning != null || streamRunning != null

    // Auto-start desktop when an external display is connected.
    LaunchedEffect(external?.displayId, shizuku) {
        val ext = external ?: run { autoStartedFor = -1; return@LaunchedEffect }
        if (shizuku == ShizukuState.Ready && cable is CableState.Idle && autoStartedFor != ext.displayId) {
            autoStartedFor = ext.displayId
            CableSession.start(context, ext)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        StreamService.start(context) // Stream still runs even if notification permission is denied.
    }
    val startStream = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            StreamService.start(context)
        }
    }

    if (welcome) {
        WelcomeScreen(onFinish = {
            Welcome.markSeen(context)
            welcome = false
        })
        return
    }
    if (preview) {
        VirtualDisplayTestScreen(onExit = { preview = false })
        return
    }

    val status = when {
        cableRunning != null -> ControllerStatus(true, stringResource(R.string.status_external_connected, cableRunning.name), windows = windows.size)
        streamRunning != null -> ControllerStatus(
            true,
            stringResource(R.string.status_laptop_url, streamRunning.url ?: stringResource(R.string.status_no_wifi)),
            stringResource(
                R.string.status_pin_clients,
                streamRunning.pin,
                if (streamRunning.clients == 0) stringResource(R.string.status_no_viewers)
                else stringResource(R.string.status_viewers_count, streamRunning.clients),
            ),
            windows.size,
        )
        external != null -> ControllerStatus(false, stringResource(R.string.status_external_seen, external.name), windows = windows.size)
        else -> ControllerStatus(false, stringResource(R.string.status_no_display), windows = windows.size)
    }

    val banners = buildList<@Composable () -> Unit> {
        if (shizuku != ShizukuState.Ready) add {
            ControllerBanner(stringResource(R.string.banner_shizuku_needed), action = stringResource(R.string.banner_guide)) { sheet = Sheet.Shizuku }
        }
        cableRunning?.vendorDesktop?.let { vendor ->
            add { ControllerBanner(stringResource(R.string.banner_vendor_desktop, vendor)) }
        }
        (cable as? CableState.Failed)?.let { f ->
            add {
                ControllerBanner(f.message, color = Phone.red, action = stringResource(R.string.action_retry)) {
                    external?.let { scope.launch { CableSession.start(context, it) } }
                }
            }
        }
    }

    val buttons = buildList {
        add(PadButton(DeskIcons.AppsGrid, stringResource(R.string.btn_apps)) { sheet = Sheet.Apps })
        if (external != null) {
            add(
                PadButton(DeskIcons.Monitor, stringResource(R.string.btn_display), enabled = shizuku == ShizukuState.Ready, active = cableRunning != null) {
                    scope.launch {
                        if (cableRunning != null) CableSession.stop(context) else CableSession.start(context, external)
                    }
                },
            )
        }
        if (cableRunning == null) {
            add(PadButton(DeskIcons.Wifi, stringResource(R.string.btn_laptop), enabled = shizuku == ShizukuState.Ready, active = streamRunning != null) { sheet = Sheet.Laptop })
        }
        add(PadButton(DeskIcons.Settings, stringResource(R.string.btn_settings)) { sheet = Sheet.Settings })
    }

    ControllerScreen(
        status = status,
        banners = banners,
        buttons = buttons,
        inputReady = desktopRunning,
        onInput = ActiveInput::handle,
        onSendText = { text, enter ->
            if (text.isNotEmpty()) ActiveInput.handle(InputEvent.Text(text))
            if (enter) enterKey(ActiveInput::handle)
        },
    )

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Apps -> AppsSheet(
            desktopRunning = desktopRunning,
            onOpen = {
                DesktopController.open(it)
                sheet = Sheet.None
            },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Laptop -> LaptopSheet(
            state = stream,
            onStart = startStream,
            onStop = { scope.launch { StreamSession.stop() } },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Settings -> SettingsSheet(
            shizuku = shizuku,
            onShizuku = { sheet = Sheet.Shizuku },
            onPreview = {
                sheet = Sheet.None
                preview = true
            },
            onGuide = {
                sheet = Sheet.None
                welcome = true
            },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Shizuku -> ShizukuSheet(shizuku, onDismiss = { sheet = Sheet.None })
    }
}
