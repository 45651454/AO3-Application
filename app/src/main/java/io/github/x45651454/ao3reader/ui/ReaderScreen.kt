package io.github.x45651454.ao3reader.ui

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.x45651454.ao3reader.data.AO3_BASE
import io.github.x45651454.ao3reader.data.Repo
import io.github.x45651454.ao3reader.data.WorkDetail
import io.github.x45651454.ao3reader.ui.theme.LocalDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Entities

@Composable
fun ReaderScreen(repo: Repo, workId: Long, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var startFraction by remember { mutableStateOf(0f) }
    var startDownloaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current

    val restored = remember { mutableStateOf(false) }
    val lastFraction = remember { mutableStateOf(0f) }
    val lastSavedAt = remember { mutableStateOf(0L) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // 滚动恢复重试 Runnable 的引用，便于 onDispose 时移除；销毁标志做双重保险
    val restoreRunnableRef = remember { arrayOf<Runnable?>(null) }
    val destroyed = remember { mutableStateOf(false) }
    // 下次 onPageFinished 后要恢复到的进度：初次进入是历史进度，主题切换重载是当前进度
    val pendingRestoreFraction = remember { mutableStateOf(0f) }

    LaunchedEffect(workId, reload) {
        error = null
        ready = false
        try {
            // 下载过的直接读本地副本，断网也能看
            val local = withContext(Dispatchers.IO) { repo.db.downloadedWork(workId) }
            val d = local ?: repo.work(workId)
            startDownloaded = local != null
            val existing = withContext(Dispatchers.IO) {
                val p = repo.db.progressFor(workId)
                repo.db.recordRead(workId, d.title, d.author, p.coerceAtLeast(0f))
                p
            }
            startFraction = existing.coerceIn(0f, 1f)
            detail = d
            ready = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "加载失败"
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                detail?.title ?: "阅读",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val loaded = detail
            if (loaded != null) {
                DownloadButton(repo = repo, detail = loaded, initiallyDownloaded = startDownloaded)
            }
        }

        val d = detail
        val err = error
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                err != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { reload++ }) { Text("重试") }
                }

                d == null || !ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> {
                    val html = remember(d, dark) { readerHtml(d, dark) }
                    DisposableEffect(Unit) {
                        onDispose {
                            destroyed.value = true
                            webViewRef.value?.let { wv ->
                                // 移除队列中残留的恢复位置重试，避免销毁后仍回调 WebView
                                restoreRunnableRef[0]?.let { wv.removeCallbacks(it) }
                                restoreRunnableRef[0] = null
                                wv.destroy()
                            }
                            webViewRef.value = null
                            if (restored.value && lastFraction.value > 0f) {
                                runCatching { repo.db.updateProgress(workId, lastFraction.value) }
                            }
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                webViewRef.value = this
                                // onPageFinished 时 WebView 可能尚未布局（contentHeight 为 0），需等布局完成后再恢复位置
                                fun restorePosition(targetFraction: Float, attempt: Int) {
                                    if (destroyed.value || restored.value) return
                                    val max = maxScroll(this)
                                    if (max > 0) {
                                        scrollTo(0, (targetFraction * max).toInt())
                                        restored.value = true
                                    } else if (attempt < 20) {
                                        val retry = Runnable {
                                            restorePosition(targetFraction, attempt + 1)
                                        }
                                        restoreRunnableRef[0] = retry
                                        postDelayed(retry, 50)
                                    } else {
                                        restored.value = true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        if (restored.value || destroyed.value) return
                                        val target = pendingRestoreFraction.value
                                        if (target > 0.005f) {
                                            restorePosition(target, 0)
                                        } else {
                                            restored.value = true
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean = true
                                }
                                val webView = this@apply
                                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                    val max = maxScroll(webView)
                                    val fraction =
                                        if (max > 0) (scrollY / max).coerceIn(0f, 1f) else 0f
                                    lastFraction.value = fraction
                                    val now = System.currentTimeMillis()
                                    if (now - lastSavedAt.value > 2000) {
                                        lastSavedAt.value = now
                                        scope.launch(Dispatchers.IO) {
                                            repo.db.updateProgress(workId, fraction)
                                        }
                                    }
                                }
                                pendingRestoreFraction.value = startFraction
                                // tag 记录当前已加载的 html，供 update 块判断是否真正需要重载
                                tag = html
                                loadDataWithBaseURL(AO3_BASE, html, "text/html", "utf-8", null)
                            }
                        },
                        update = { webView ->
                            // 仅在 html 内容真的变化（如深/浅色切换）时重载，重组不会触发
                            if (webView.tag !== html && !destroyed.value) {
                                webView.tag = html
                                // 主题切换重载后恢复到当前阅读位置，而不是跳回开头/历史进度
                                pendingRestoreFraction.value =
                                    if (restored.value) lastFraction.value else startFraction
                                restored.value = false
                                webView.loadDataWithBaseURL(
                                    AO3_BASE,
                                    html,
                                    "text/html",
                                    "utf-8",
                                    null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun maxScroll(webView: WebView): Float {
    @Suppress("DEPRECATION")
    val scale = webView.scale
    return webView.contentHeight * scale - webView.height
}

private fun readerHtml(detail: WorkDetail, dark: Boolean): String {
    val bg = if (dark) "#121212" else "#FBF9F7"
    val fg = if (dark) "#E6E1E5" else "#1B1B1B"
    val muted = if (dark) "#9A9A9A" else "#6B6B6B"
    val css = buildString {
        append("body{background:$bg;color:$fg;font-size:18px;line-height:1.8;margin:16px;word-wrap:break-word;}")
        append("h2.ct{font-size:20px;margin:28px 0 12px;color:$muted;font-weight:600;}")
        append("a{color:#990000;}")
        append("blockquote{border-left:3px solid $muted;margin:8px 0;padding-left:12px;}")
        append("img{max-width:100%;height:auto;}")
        append("hr{border:none;border-top:1px solid $muted;opacity:.4;margin:24px 0;}")
        append("ruby rt{font-size:.6em;}sup,sub{font-size:.75em;}")
    }
    val sb = StringBuilder()
    sb.append("<!doctype html><html><head>")
    sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
    sb.append("<style>").append(css).append("</style></head><body>")
    val multi = detail.chapters.size > 1
    detail.chapters.forEachIndexed { i, chapter ->
        if (multi) {
            if (i > 0) sb.append("<hr>")
            if (chapter.title.isNotBlank()) {
                sb.append("<h2 class='ct'>").append(Entities.escape(chapter.title)).append("</h2>")
            }
        }
        sb.append(chapter.html)
    }
    sb.append("</body></html>")
    return sb.toString()
}
