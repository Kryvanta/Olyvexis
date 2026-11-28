package dev.kryvanta.olyvexis.display

import android.content.ComponentName
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
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
import dev.kryvanta.olyvexis.ui.DeskIcons
import dev.kryvanta.olyvexis.ui.Phone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEST_WIDTH = 1280
private const val TEST_HEIGHT = 720
private const val TEST_DPI = 160

/** Keeps the virtual display alive across activity recreations. */
class VirtualDisplayTestViewModel : ViewModel() {
    val controller = VirtualDisplayController()
    var status by mutableStateOf("…")

    override fun onCleared() {
        DesktopController.closeAll()
        CoroutineScope(Dispatchers.IO).launch { withContext(NonCancellable) { controller.release() } }
    }
}

/** Previews the desktop directly on the phone using TextureView (avoids SurfaceView layer offset). */
@Composable
fun VirtualDisplayTestScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: VirtualDisplayTestViewModel = viewModel()
    val controller = vm.controller
    val repo = remember { AppRepository(context) }
    val apps by remember { repo.observeLaunchableApps() }.collectAsState(initial = remember { repo.launchableApps() })
    var status by vm::status

    val exit: () -> Unit = {
        scope.launch {
            DesktopController.closeAll()
            controller.release()
            status = "…"
            onExit()
        }
    }
    BackHandler(onBack = exit)

    Column(
        Modifier
            .fillMaxSize()
            .background(Phone.background)
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                Icon(DeskIcons.ArrowBack, contentDescription = null, tint = Phone.icon, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_title), color = Phone.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = Phone.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // TextureView sits directly in the Compose tree, centered in a 16:9 container.
        BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val aspect = TEST_WIDTH.toFloat() / TEST_HEIGHT
            val previewW = minOf(maxWidth, 640.dp)
            val previewH = previewW / aspect
            Box(
                Modifier
                    .width(previewW)
                    .height(previewH)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            setTransform(Matrix())
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
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                private var surface: Surface? = null

                                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                    texture.setDefaultBufferSize(TEST_WIDTH, TEST_HEIGHT)
                                    setTransform(Matrix())
                                    val s = Surface(texture).also { surface = it }
                                    scope.launch {
                                        status = try {
                                            val (id, created) = controller.attach(TEST_WIDTH, TEST_HEIGHT, TEST_DPI, s)
                                            if (created) {
                                                val r = controller.launch(
                                                    ComponentName(context, DesktopActivity::class.java),
                                                    freeform = false,
                                                )
                                                if (!r.isSuccess || r.stdout.contains("Error")) {
                                                    "Error: ${r.stdout} ${r.stderr}".trim()
                                                } else {
                                                    "#$id · ${TEST_WIDTH}×${TEST_HEIGHT}"
                                                }
                                            } else {
                                                "#$id · ${TEST_WIDTH}×${TEST_HEIGHT}"
                                            }
                                        } catch (e: Exception) {
                                            "Error: ${e.message}"
                                        }
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                                    texture.setDefaultBufferSize(TEST_WIDTH, TEST_HEIGHT)
                                    setTransform(Matrix())
                                }

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    scope.launch { runCatching { controller.setSurface(null) } }
                                    surface?.release()
                                    surface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
                )
            }
        }

        Text(stringResource(R.string.preview_touch_hint), color = Phone.secondary, fontSize = 12.sp)

        LazyVerticalGrid(columns = GridCells.Adaptive(78.dp), modifier = Modifier.weight(1f)) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                val bmp = rememberAppIcon(app)
                Column(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (controller.displayId != null) {
                                DesktopController.open(app)
                                status = app.label
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (bmp != null) {
                        Image(bmp, contentDescription = app.label, modifier = Modifier.size(44.dp))
                    } else {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Phone.field))
                    }
                    Text(app.label, fontSize = 11.sp, color = Phone.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
