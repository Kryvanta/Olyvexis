package dev.kryvanta.olyvexis.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.R
import kotlinx.coroutines.launch

/** Màn hướng dẫn lần đầu (giống WelcomeView của iDesk): mỗi trang một chủ đề, vài dòng ngắn. */
object Welcome {
    private const val PREFS = "welcome"
    fun seen(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
}

private enum class Mark { Check, Number, Cannot, Caveat }

private class WelcomePage(
    val icon: ImageVector,
    val titleRes: Int,
    val lines: List<Pair<Mark, Int>>,
)

private val pages = listOf(
    WelcomePage(
        DeskIcons.Monitor, R.string.welcome_p1_title,
        listOf(
            Mark.Check to R.string.welcome_p1_l1,
            Mark.Check to R.string.welcome_p1_l2,
            Mark.Caveat to R.string.welcome_p1_l3,
            Mark.Check to R.string.welcome_p1_l4,
        ),
    ),
    WelcomePage(
        DeskIcons.Usb, R.string.welcome_p2_title,
        listOf(
            Mark.Number to R.string.welcome_p2_l1,
            Mark.Number to R.string.welcome_p2_l2,
            Mark.Number to R.string.welcome_p2_l3,
            Mark.Caveat to R.string.welcome_p2_l4,
        ),
    ),
    WelcomePage(
        DeskIcons.Touchpad, R.string.welcome_p3_title,
        listOf(
            Mark.Check to R.string.welcome_p3_l1,
            Mark.Check to R.string.welcome_p3_l2,
            Mark.Check to R.string.welcome_p3_l3,
        ),
    ),
    WelcomePage(
        DeskIcons.Keyboard, R.string.welcome_p4_title,
        listOf(
            Mark.Check to R.string.welcome_p4_l1,
            Mark.Check to R.string.welcome_p4_l2,
            Mark.Check to R.string.welcome_p4_l3,
            Mark.Check to R.string.welcome_p4_l4,
        ),
    ),
)

@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    BackHandler(onBack = onFinish)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i -> PageView(pages[i]) }

        // Chấm trang
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f)),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                colors = ButtonDefaults.buttonColors(containerColor = Phone.accent, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(if (last) R.string.welcome_start else R.string.welcome_next), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!last) {
                Text(
                    stringResource(R.string.welcome_skip),
                    color = Phone.secondary,
                    fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinish).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun PageView(page: WelcomePage) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = Phone.accent,
                modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(page.titleRes),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            page.lines.forEachIndexed { i, (mark, lineRes) ->
                Row(verticalAlignment = Alignment.Top) {
                    MarkIcon(mark, i)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(lineRes), color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun MarkIcon(mark: Mark, index: Int) {
    val (bg, label) = when (mark) {
        Mark.Check -> Phone.accent to "✓"
        Mark.Number -> Phone.accent to "${index + 1}"
        Mark.Cannot -> Phone.orange to "✕"
        Mark.Caveat -> Color(0xFF48484A) to "i"
    }
    Box(
        Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
