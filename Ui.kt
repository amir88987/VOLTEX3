package app.videogir

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import okhttp3.Request as OkRequest

// ───────────── رنگ‌ها: مشکی و قرمز ─────────────
private val Bg = Color(0xFF050505)
private val Surface1 = Color(0xFF111113)
private val Surface2 = Color(0xFF1B1B20)
private val Red = Color(0xFFFF2D3F)
private val RedDeep = Color(0xFFB0001A)
private val TextMain = Color(0xFFF5F5F7)
private val TextDim = Color(0xFF9A9AA3)
private val Line = Color(0xFF2C2C33)
private val Ok = Color(0xFF4ADE80)
private val Err = Color(0xFFFF8A8A)

private val RedGradient get() = Brush.horizontalGradient(listOf(Red, RedDeep))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red, onPrimary = Color.White,
            background = Bg, onBackground = TextMain,
            surface = Surface1, onSurface = TextMain,
            surfaceVariant = Surface2, onSurfaceVariant = TextDim,
            error = Err, outline = Line
        ),
        content = content
    )
}

// ───────────── کمکی‌ها ─────────────
private fun String.fa(): String =
    map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

private fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1048576.0
    val s = if (mb >= 1024) String.format(Locale.US, "%.1f گیگابایت", mb / 1024)
    else String.format(Locale.US, "%.1f مگابایت", mb)
    return s.replace('.', '٫').fa()
}

private fun fmtDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val t = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
    return t.fa()
}

// ───────────── ریشه برنامه + انیمیشن ورود ─────────────
@Composable
fun VoltexApp(vm: MainVm, onDownload: (VideoMeta, Option) -> Unit, showSplashFirst: Boolean) {
    AppTheme {
        var splash by rememberSaveable { mutableStateOf(showSplashFirst) }
        Box(Modifier.fillMaxSize().background(Bg)) {
            Root(vm, onDownload)
            if (splash) SplashScreen { splash = false }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val letters = "VOLTEX"
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val letterAnims = remember { letters.map { Animatable(0f) } }
    val line = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(450)) }
        launch { logoScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        delay(400)
        letterAnims.forEachIndexed { i, a ->
            launch {
                delay(i * 80L)
                a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
        delay(650)
        launch { line.animateTo(1f, tween(450)) }
        delay(200)
        launch { tag.animateTo(1f, tween(350)) }
        delay(700)
        exit.animateTo(0f, tween(300))
        onFinished()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = exit.value }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            // هاله قرمز پشت لوگو
            Box(
                Modifier
                    .size(360.dp)
                    .graphicsLayer { alpha = logoAlpha.value * pulse * 0.9f }
                    .background(
                        Brush.radialGradient(listOf(Red.copy(alpha = 0.45f), Color.Transparent)),
                        CircleShape
                    )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.voltex_logo), null,
                    Modifier.size(190.dp).graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
                )
                Spacer(Modifier.height(28.dp))
                Row {
                    letters.forEachIndexed { i, ch ->
                        Text(
                            ch.toString(),
                            color = if (ch == 'X') Red else Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 2.sp,
                            modifier = Modifier.graphicsLayer {
                                val a = letterAnims[i].value
                                alpha = a
                                translationX = (1f - a) * (if (i % 2 == 0) -90f else 90f)
                                translationY = (1f - a) * 30f
                                scaleX = 0.6f + 0.4f * a
                                scaleY = 0.6f + 0.4f * a
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .width((150f * line.value).dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Red, Color.Transparent)))
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "دانلودر ویدیو",
                    color = TextDim, fontSize = 14.sp,
                    modifier = Modifier.graphicsLayer { alpha = tag.value }
                )
            }
        }
    }
}

// ───────────── صفحه اصلی ─────────────
@Composable
private fun Root(vm: MainVm, onDownload: (VideoMeta, Option) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Red.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.85f, 0f),
                        radius = size.width * 1.1f
                    )
                )
            }
        ) {
            Scaffold(containerColor = Color.Transparent) { pad ->
                val dl by DownloadHub.state.collectAsStateWithLifecycle()
                val busy = dl is Dl.Running

                Column(Modifier.padding(pad).fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Header() }
                        item { InputCard(vm) }
                        when (val s = vm.ui) {
                            Ui.Idle -> item { HintCard() }
                            Ui.Loading -> item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Red)
                                }
                            }
                            is Ui.Error -> item { ErrorCard(s.message) }
                            is Ui.Ready -> {
                                item { MetaCard(s.data.meta) }
                                itemsIndexed(s.data.options) { index, opt ->
                                    Appear(index) {
                                        OptionRow(opt, enabled = !busy) { onDownload(s.data.meta, opt) }
                                    }
                                }
                            }
                        }
                    }
                    DownloadBar(dl)
                }
            }
        }
    }
}

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        a.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 40f
    }) { content() }
}

@Composable
private fun Header() {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.voltex_logo), null,
            Modifier.size(52.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "VOLTEX",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color.White, Red)),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 3.sp
                )
            )
            Text("دانلودر ویدیو یوتیوب", color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GradientButton(
    text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) RedGradient else Brush.horizontalGradient(listOf(Surface2, Surface2)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else TextDim, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InputCard(vm: MainVm) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://youtu.be/…", color = TextDim) },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.load() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Red,
                unfocusedBorderColor = Line,
                cursorColor = Red,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Red.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable {
                        clipboard.getText()?.text?.let {
                            vm.input = Extractor.findUrl(it) ?: it
                            vm.load()
                        }
                    }
                    .padding(vertical = 14.dp, horizontal = 18.dp)
            ) { Text("چسباندن", color = Red, fontWeight = FontWeight.Bold) }
            GradientButton(
                "دریافت کیفیت‌ها",
                enabled = vm.input.isNotBlank() && vm.ui != Ui.Loading,
                modifier = Modifier.weight(1f)
            ) { vm.load() }
        }
    }
}

@Composable
private fun HintCard() {
    val steps = listOf(
        "لینک ویدیو را بچسبان و «دریافت کیفیت‌ها» را بزن.",
        "کیفیت دلخواه را انتخاب کن.",
        "فایل در پوشه Movies/VOLTEX ذخیره می‌شود."
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("چطور کار می‌کند؟", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        steps.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(RedGradient),
                    contentAlignment = Alignment.Center
                ) { Text("${i + 1}".fa(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Spacer(Modifier.width(12.dp))
                Text(t, color = TextDim, fontSize = 14.sp)
            }
        }
        Text(
            "نکته: در برنامه یوتیوب دکمه «اشتراک‌گذاری» را بزن و VOLTEX را انتخاب کن.",
            color = Red, fontSize = 13.sp
        )
        Text("برای اتصال به یوتیوب فیلترشکن را روشن کن.", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Red.copy(alpha = 0.10f))
            .border(1.dp, Red.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) { Text(message, color = Err, fontSize = 14.sp) }
}

@Composable
private fun MetaCard(meta: VideoMeta) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
    ) {
        val bmp by produceState<ImageBitmap?>(null, meta.thumb) {
            value = meta.thumb?.let { loadBitmap(it) }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Surface2)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                )
            )
            val dur = fmtDuration(meta.durationSec)
            if (dur.isNotEmpty()) {
                Text(
                    dur,
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                meta.title, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (meta.uploader.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta.uploader, color = TextDim, fontSize = 13.sp)
            }
        }
    }
}

private suspend fun loadBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        Http.client.newCall(OkRequest.Builder().url(url).build()).execute().use { r ->
            r.body?.byteStream()?.let { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun OptionRow(opt: Option, enabled: Boolean, onClick: () -> Unit) {
    val height = opt.label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val hd = height >= 720
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.dp, if (enabled && hd) Red.copy(alpha = 0.45f) else Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled && hd) RedGradient else Brush.horizontalGradient(listOf(Surface2, Surface2)))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                opt.label,
                color = if (enabled && hd) Color.White else if (enabled) Red else TextDim,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(opt.detail, fontSize = 13.sp, color = TextMain)
            val size = fmtSize(opt.size)
            if (size.isNotEmpty()) Text(size, fontSize = 12.sp, color = TextDim)
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (enabled) RedGradient else Brush.horizontalGradient(listOf(Surface2, Surface2))),
            contentAlignment = Alignment.Center
        ) {
            Text("↓", color = if (enabled) Color.White else TextDim, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadBar(dl: Dl) {
    val ctx = LocalContext.current
    val top = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    when (dl) {
        Dl.Idle -> Unit
        is Dl.Running -> Column(
            Modifier
                .fillMaxWidth()
                .clip(top)
                .background(Surface1)
                .border(1.dp, Line, top)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(dl.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            val barMod = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            if (dl.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { dl.progress }, modifier = barMod, color = Red, trackColor = Surface2
                )
            } else {
                LinearProgressIndicator(modifier = barMod, color = Red, trackColor = Surface2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pct = if (dl.progress >= 0f) " · ${(dl.progress * 100).toInt()}٪".fa() else ""
                Text(dl.phase + pct, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { ctx.startService(DownloadService.cancelIntent(ctx)) }) {
                    Text("لغو", color = Err, fontWeight = FontWeight.Bold)
                }
            }
        }
        is Dl.Done -> Row(
            Modifier
                .fillMaxWidth()
                .clip(top)
                .background(Surface1)
                .border(1.dp, Ok.copy(alpha = 0.4f), top)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("✓ ذخیره شد", color = Ok, fontWeight = FontWeight.Bold)
                Text(dl.where, color = TextDim, fontSize = 12.sp)
            }
            TextButton(onClick = { DownloadHub.state.value = Dl.Idle }) { Text("باشه", color = Red) }
        }
        is Dl.Failed -> Row(
            Modifier
                .fillMaxWidth()
                .clip(top)
                .background(Surface1)
                .border(1.dp, Red.copy(alpha = 0.5f), top)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dl.message, color = Err, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { DownloadHub.state.value = Dl.Idle }) { Text("باشه", color = Red) }
        }
    }
}
