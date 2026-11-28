package dev.kryvanta.olyvexis.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.shizuku.ShizukuException
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import dev.kryvanta.olyvexis.ui.DeskColors
import dev.kryvanta.olyvexis.ui.DeskIcons
import dev.kryvanta.olyvexis.ui.PrimaryActionButton
import dev.kryvanta.olyvexis.ui.SecondaryActionButton
import kotlinx.coroutines.launch

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"

private val wirelessStepRes = listOf(
    R.string.shizuku_step_1,
    R.string.shizuku_step_2,
    R.string.shizuku_step_3,
    R.string.shizuku_step_4,
    R.string.shizuku_step_5,
    R.string.shizuku_step_6,
)

/** Content for the Shizuku setup step, varying by [state]. */
@Composable
fun ShizukuStepContent(state: ShizukuState) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            ShizukuState.NotRunning -> {
                Text(
                    stringResource(R.string.shizuku_not_running),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wirelessStepRes.forEachIndexed { idx, stepRes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DeskColors.SurfaceElevated)
                                .border(1.dp, DeskColors.BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(DeskColors.AccentContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${idx + 1}", color = DeskColors.AccentBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(stringResource(stepRes), style = MaterialTheme.typography.bodySmall, color = DeskColors.TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeskColors.WarningContainer)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DeskIcons.Info, contentDescription = null, tint = DeskColors.Warning, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.shizuku_reboot_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeskColors.TextPrimary,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isInstalled(context, SHIZUKU_PACKAGE)) {
                        SecondaryActionButton(stringResource(R.string.shizuku_open), onClick = { openShizuku(context) }, modifier = Modifier.weight(1f))
                    } else {
                        SecondaryActionButton(stringResource(R.string.shizuku_download), onClick = { openUrl(context, SHIZUKU_DOWNLOAD) }, modifier = Modifier.weight(1f))
                    }
                    SecondaryActionButton(stringResource(R.string.shizuku_dev_options), onClick = { openDeveloperOptions(context) }, modifier = Modifier.weight(1f))
                }
                PrimaryActionButton(
                    text = stringResource(R.string.shizuku_recheck),
                    icon = DeskIcons.Refresh,
                    onClick = ShizukuManager::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ShizukuState.Outdated -> {
                Text(stringResource(R.string.shizuku_outdated), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionButton(stringResource(R.string.shizuku_update), onClick = { openUrl(context, SHIZUKU_DOWNLOAD) }, modifier = Modifier.weight(1f))
                    PrimaryActionButton(stringResource(R.string.shizuku_recheck), icon = DeskIcons.Refresh, onClick = ShizukuManager::refresh, modifier = Modifier.weight(1f))
                }
            }
            ShizukuState.NoPermission -> {
                Text(stringResource(R.string.shizuku_no_perm), style = MaterialTheme.typography.bodyMedium)
                PrimaryActionButton(
                    text = stringResource(R.string.shizuku_grant),
                    icon = DeskIcons.CheckCircle,
                    onClick = ShizukuManager::requestPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ShizukuState.PermissionDenied -> {
                Text(
                    stringResource(R.string.shizuku_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeskColors.Warning,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionButton(stringResource(R.string.shizuku_open), onClick = { openShizuku(context) }, modifier = Modifier.weight(1f))
                    PrimaryActionButton(stringResource(R.string.shizuku_recheck), icon = DeskIcons.Refresh, onClick = ShizukuManager::refresh, modifier = Modifier.weight(1f))
                }
            }
            ShizukuState.Ready -> {
                val isRoot = ShizukuManager.serverUid() == 0
                if (isRoot) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeskColors.WarningContainer)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(DeskIcons.Warning, contentDescription = null, tint = DeskColors.Warning, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.shizuku_ready_root),
                            style = MaterialTheme.typography.bodySmall,
                            color = DeskColors.TextPrimary,
                        )
                    }
                } else {
                    Text(stringResource(R.string.shizuku_ready_shell), style = MaterialTheme.typography.bodyMedium)
                }
                ShellTest()
            }
        }
    }
}

/** Runs a quick settings read command to verify ShellService is working. */
@Composable
private fun ShellTest() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    val command = "settings get global enable_freeform_support"

    SecondaryActionButton(
        text = stringResource(if (running) R.string.shizuku_testing else R.string.shizuku_test_cmd),
        enabled = !running,
        onClick = {
            running = true
            scope.launch {
                output = try {
                    val r = ShizukuManager.runCommand(command)
                    buildString {
                        append("$ $command\n")
                        if (r.stdout.isNotEmpty()) append(r.stdout).append('\n')
                        if (r.stderr.isNotEmpty()) append(r.stderr).append('\n')
                        append("exit=${r.exitCode}")
                    }
                } catch (e: ShizukuException) {
                    "Lỗi: ${e.message}" + (e.cause?.let { "\n$it" } ?: "")
                }
                running = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    output?.let {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF06080D))
                .border(1.dp, DeskColors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DeskColors.AccentBright)
        }
    }
}

private fun isInstalled(context: Context, pkg: String) =
    context.packageManager.getLaunchIntentForPackage(pkg) != null

private fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else openUrl(context, SHIZUKU_DOWNLOAD)
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openDeveloperOptions(context: Context) {
    val tries = listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_DEVICE_INFO_SETTINGS)
    for (action in tries) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
