package com.example.ao3application.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ao3application.data.ExportImport
import com.example.ao3application.data.HistoryEntry
import com.example.ao3application.data.Repo
import com.example.ao3application.data.SavedWork
import com.example.ao3application.data.TagFavorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏筛选。[untaggedOnly] 只留收藏时没打标签的；否则按 [tag] 过滤，[tag] 为 null 表示全部。
 * 两者分开传参而不是用一个字符串哨兵，免得跟用户真起的同名标签撞车。
 */
internal fun filterFavorites(
    favorites: List<SavedWork>,
    tag: String?,
    untaggedOnly: Boolean,
): List<SavedWork> = when {
    untaggedOnly -> favorites.filter { it.tags.isEmpty() }
    tag == null -> favorites
    else -> favorites.filter { tag in it.tags }
}

@Composable
fun LibraryScreen(
    repo: Repo,
    onOpenWork: (Long) -> Unit,
    onOpenTag: (TagFavorite) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var favorites by remember { mutableStateOf(emptyList<SavedWork>()) }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var tagFavorites by remember { mutableStateOf(emptyList<TagFavorite>()) }
    var downloads by remember { mutableStateOf(emptyList<SavedWork>()) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var untaggedOnly by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingFavorite by remember { mutableStateOf<SavedWork?>(null) }
    var pendingHistory by remember { mutableStateOf<HistoryEntry?>(null) }
    var pendingTag by remember { mutableStateOf<TagFavorite?>(null) }
    var pendingDownload by remember { mutableStateOf<SavedWork?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            favorites = repo.db.favorites()
            history = repo.db.history()
            tagFavorites = repo.db.tagFavorites()
            downloads = repo.db.downloads()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(ExportImport.exportFrom(repo.db).toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "导出完成" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                    ExportImport.importInto(repo.db, text)
                }
            }
            result.onSuccess { (favCount, histCount, tagCount) ->
                Toast.makeText(
                    context,
                    "导入完成：收藏 $favCount 条，新增历史 $histCount 条，标签 $tagCount 条",
                    Toast.LENGTH_LONG,
                ).show()
                refresh++
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("书库", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("导出备份") },
                    onClick = {
                        menuOpen = false
                        exportLauncher.launch("ao3-backup.json")
                    },
                )
                DropdownMenuItem(
                    text = { Text("导入备份") },
                    onClick = {
                        menuOpen = false
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                        )
                    },
                )
            }
        }

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("收藏 (${favorites.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("历史 (${history.size})") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("标签 (${tagFavorites.size})") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("已下载 (${downloads.size})") })
        }

        if (tab == 0) {
            if (favorites.isEmpty()) {
                EmptyHint("还没有收藏。\n在作品详情页点 ♡ 收藏，长按 ♡ 可按标签收藏。")
            } else {
                val allTags = favorites.flatMap { it.tags }.distinct().sorted()
                val hasUntagged = favorites.any { it.tags.isEmpty() }
                val active = tagFilter?.takeIf { it in allTags }
                val showUntagged = untaggedOnly && hasUntagged
                val shown = filterFavorites(favorites, active, showUntagged)

                if (allTags.isNotEmpty() || hasUntagged) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = active == null && !showUntagged,
                            onClick = {
                                tagFilter = null
                                untaggedOnly = false
                            },
                            label = { Text("全部") },
                        )
                        if (hasUntagged) {
                            FilterChip(
                                selected = showUntagged,
                                onClick = {
                                    untaggedOnly = !untaggedOnly
                                    tagFilter = null
                                },
                                label = { Text("未标注") },
                            )
                        }
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = active == tag,
                                onClick = {
                                    tagFilter = if (active == tag) null else tag
                                    untaggedOnly = false
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }

                if (shown.isEmpty()) {
                    EmptyHint(
                        if (showUntagged) "收藏里没有未标注标签的作品。"
                        else "没有标签「$active」的收藏。"
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { ListHint() }
                        items(shown, key = { it.id }) { fav ->
                            LibraryRow(
                                title = fav.title,
                                subtitle = "by ${fav.author} · ${fmtTime(fav.savedAt)}",
                                progress = null,
                                tags = fav.tags,
                                onClick = { onOpenWork(fav.id) },
                                onLongClick = { pendingFavorite = fav },
                            )
                        }
                    }
                }
            }
        } else if (tab == 1) {
            if (history.isEmpty()) {
                EmptyHint("还没有阅读记录。\n打开作品开始阅读后会自动记录。")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ListHint() }
                    items(history, key = { it.id }) { entry ->
                        val progressText =
                            if (entry.progress > 0.01f) " · 已读 ${(entry.progress * 100).toInt()}%" else ""
                        LibraryRow(
                            title = entry.title,
                            subtitle = "by ${entry.author}$progressText · ${fmtTime(entry.lastReadAt)}",
                            progress = entry.progress,
                            onClick = { onOpenWork(entry.id) },
                            onLongClick = { pendingHistory = entry },
                        )
                    }
                }
            }
        } else if (tab == 2) {
            if (tagFavorites.isEmpty()) {
                EmptyHint("还没有收藏的标签。\n在作品详情页长按标签即可收藏。")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ListHint() }
                    items(tagFavorites, key = { it.name }) { tag ->
                        LibraryRow(
                            title = tag.name,
                            subtitle = "收藏于 ${fmtTime(tag.savedAt)}",
                            progress = null,
                            onClick = { onOpenTag(tag) },
                            onLongClick = { pendingTag = tag },
                        )
                    }
                }
            }
        } else {
            if (downloads.isEmpty()) {
                EmptyHint("还没有下载的文章。\n在阅读页右上角点「下载」即可离线保存。")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ListHint() }
                    items(downloads, key = { it.id }) { work ->
                        LibraryRow(
                            title = work.title,
                            subtitle = "by ${work.author} · 下载于 ${fmtTime(work.savedAt)}",
                            progress = null,
                            onClick = { onOpenWork(work.id) },
                            onLongClick = { pendingDownload = work },
                        )
                    }
                }
            }
        }
    }

    pendingFavorite?.let { fav ->
        ConfirmDelete(
            title = "删除收藏",
            text = "确定从收藏中删除《${fav.title}》吗？",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.db.removeFavorite(fav.id) }
                    pendingFavorite = null
                    refresh++
                }
            },
            onDismiss = { pendingFavorite = null },
        )
    }
    pendingHistory?.let { entry ->
        ConfirmDelete(
            title = "删除记录",
            text = "确定删除《${entry.title}》的阅读记录吗？",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.db.deleteHistory(entry.id) }
                    pendingHistory = null
                    refresh++
                }
            },
            onDismiss = { pendingHistory = null },
        )
    }
    pendingTag?.let { tag ->
        ConfirmDelete(
            title = "删除标签",
            text = "确定删除收藏的标签「${tag.name}」吗？",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.db.removeTagFavorite(tag.name) }
                    pendingTag = null
                    refresh++
                }
            },
            onDismiss = { pendingTag = null },
        )
    }
    pendingDownload?.let { work ->
        ConfirmDelete(
            title = "删除下载",
            text = "确定删除《${work.title}》的下载吗？删除后需要联网重新下载才能离线阅读。",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.db.removeDownload(work.id) }
                    pendingDownload = null
                    refresh++
                }
            },
            onDismiss = { pendingDownload = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    progress: Float?,
    tags: List<String> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            tag,
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        if (progress != null && progress > 0.01f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListHint() {
    Text(
        "长按条目可删除",
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConfirmDelete(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
