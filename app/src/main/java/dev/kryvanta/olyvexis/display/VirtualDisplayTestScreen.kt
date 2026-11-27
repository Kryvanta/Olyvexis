package dev.kryvanta.olyvexis.display

import android.content.ComponentName
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import dev.kryvanta.olyvexis.ui.Phone
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.desktop.AppRepository
import dev.kryvanta.olyvexis.desktop.DesktopActivity
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.desktop.rememberAppIcon
import dev.kryvanta.olyvexis.ui.DeskColors
import dev.kryvanta.olyvexis.ui.DeskIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TEST_WIDTH = 1280
private const val TEST_HEIGHT = 720
private const val TEST_DPI = 160

/**
 * Giữ màn hình ảo qua các lần activity bị tạo lại.
 */
class VirtualDisplayTestViewModel : ViewModel() {
    val controller = VirtualDisplayController()
    var status by mutableStateOf("Đang khởi tạo màn hình ảo…")

    override fun onCleared() {
        DesktopController.closeAll()
        CoroutineScope(Dispatchers.IO).launch { withContext(NonCancellable) { controller.release() } }
    }
}

/**
 * Xem trước desktop ngay trên điện thoại với khung mô phỏng màn hình và thanh mở nhanh app.
 */
@Composable
fun VirtualDisplayTestScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: VirtualDisplayTestViewModel = viewModel()
    val controller = vm.controller
    val repo = remember { AppRepository(context) }
    val apps by remember { repo.observeLaunchableApps() }.collectAsState(initial = remember { repo.launchableApps() })
    var status by vm::status
    var desktopReady by remember { mutableStateOf(false) }
    val active = remember { AtomicBoolean(true) }
    val attachJobs = remember { mutableListOf<Job>() }
    DisposableEffect(Unit) { onDispose { active.set(false) } }

    val exit: () -> Unit = {
        if (active.getAndSet(false)) scope.launch {
            attachJobs.toList().joinAll()
            DesktopController.closeAll()
            controller.release()
            desktopReady = false
            status = "Đang khởi tạo màn hình ảo…"
            onExit()
        }
    }
    BackHandler(onBack = exit)

    // Giống màn hình chính (kiểu iDesk): nền đen, tiêu đề một dòng, khung desktop, lưới app.
    Column(
        Modifier
            .fillMaxSize()
            .background(Phone.background)
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Phone.field)
                    .clickable(onClick = exit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DeskIcons.ArrowBack, contentDescription = "Quay lại", tint = Phone.icon, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_title), color = Phone.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = Phone.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Khung desktop: chạm vào đây là chạm lên desktop.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(3.dp),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TEST_WIDTH.toFloat() / TEST_HEIGHT)
                    .clip(RoundedCornerShape(13.dp)),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.setFixedSize(TEST_WIDTH, TEST_HEIGHT)
                        setOnTouchListener { v, ev ->
                            val id = controller.displayId
                            if (id != null && v.width > 0 && v.height > 0) {
                                val copy = MotionEvent.obtain(ev)
                                copy.transform(Matrix().apply { setScale(TEST_WIDTH.toFloat() / v.width, TEST_HEIGHT.toFloat() / v.height) })
                                DesktopController.forwardToDisplay(id, copy)
                                copy.recycle()
                            }
                            true
                        }
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                if (!active.get()) return
                                attachJobs += scope.launch {
                                    desktopReady = false
                                    status = try {
                                        val (id, created) =
                                            controller.attach(TEST_WIDTH, TEST_HEIGHT, TEST_DPI, holder.surface)
                                        if (!active.get()) return@launch
                                        if (created) {
                                            val r = controller.launch(
                                                ComponentName(context, DesktopActivity::class.java),
                                                freeform = false,
                                            )
                                            if (!r.isSuccess || r.stdout.contains("Error")) {
                                                controller.release()
                                                "Lỗi mở desktop: ${r.stdout} ${r.stderr}".trim()
                                            } else {
                                                desktopReady = true
                                                "Màn hình #$id · ${TEST_WIDTH}×${TEST_HEIGHT}"
                                            }
                                        } else {
                                            desktopReady = true
                                            "Màn hình #$id"
                                        }
                                    } catch (e: Exception) {
                                        controller.release()
                                        "Lỗi: ${e.message}"
                                    }
                                }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                if (active.get()) scope.launch {
                                    desktopReady = false
                                    runCatching { controller.setSurface(null) }
                                }
                            }
                        })
                    }
                },
            )
        }

        Text(stringResource(R.string.preview_touch_hint), color = Phone.secondary, fontSize = 12.sp)

        LazyVerticalGrid(columns = GridCells.Adaptive(78.dp), modifier = Modifier.weight(1f)) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                val bmp = rememberAppIcon(app)
                Column(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = desktopReady) {
                            if (controller.displayId != null) {
                                DesktopController.open(app)
                                status = "Đã mở ${app.label}"
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val b = bmp
                    if (b != null) {
                        Image(b, contentDescription = app.label, modifier = Modifier.size(44.dp))
                    } else {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Phone.field))
                    }
                    Text(app.label, fontSize = 11.sp, color = Phone.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
