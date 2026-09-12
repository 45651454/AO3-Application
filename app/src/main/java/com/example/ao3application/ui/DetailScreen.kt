package com.example.ao3application.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ao3application.data.Repo
import com.example.ao3application.data.Tag
import com.example.ao3application.data.WorkDetail
import com.example.ao3application.ui.theme.canonicalTagCategory
import com.example.ao3application.ui.theme.tagColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val STAT_LABELS = listOf(
    "published" to "发布于",
    "updated" to "更新于",
    "words" to "字数",
    "chapters" to "章节",
    "language" to "语言",
    "kudos" to "Kudos",
    "comments" to "评论",
    "bookmarks" to "书签",
    "hits" to "点击",
)

private val TAG_GROUPS = listOf(
    setOf("rating") to "分级",
    setOf("warning", "warnings") to "警告",
    setOf("category", "categories") to "分类",
    setOf("fandom", "fandoms") to "同人圈",
    setOf("relationship", "relationships") to "关系",
    setOf("character", "characters") to "角色",
    setOf("freeform", "freeforms") to "标签",
)

@Composable
fun DetailScreen(
    repo: Repo,
    workId: Long,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onOpenTag: (Tag) -> Unit,
) {
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var isFavorite by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var favTagNames by remember { mutableStateOf(emptySet<String>()) }
    var savedTags by remember { mutableStateOf(emptyList<String>()) }
    var knownTags by remember { mutableStateOf(emptyList<String>()) }
    var downloaded by remember { mutableStateOf(false) }
    var offlineCopy by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(workId, reload) {
        error = null
        detail = null
        offlineCopy = false
        try {
            // 联网失败时退回下载的本地副本，只够显示标题/作者，但能继续进正文
            var fellBack = false
            val d = runCatching { repo.work(workId) }.getOrElse { e ->
                val local = withContext(Dispatchers.IO) { repo.db.downloadedWork(workId) }
                if (local == null) throw e else local.also { fellBack = true }
            }
            offlineCopy = fellBack
            detail = d
            withContext(Dispatchers.IO) {
                isFavorite = repo.db.isFavorite(workId)
                progress = repo.db.progressFor(workId)
                favTagNames = repo.db.tagFavorites().map { it.name }.toSet()
                savedTags = repo.db.favoriteTagsFor(workId)
                knownTags = repo.db.allFavoriteTags()
                downloaded = repo.db.isDownloaded(workId)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
    }

    val toggleTagFavorite: (Tag) -> Unit = { tag ->
        scope.launch {
            val removing = tag.name in favTagNames
            withContext(Dispatchers.IO) {
                if (removing) {
                    repo.db.removeTagFavorite(tag.name)
                } else {
                    repo.db.addTagFavorite(tag.name, tag.href)
                }
            }
            favTagNames = if (removing) favTagNames - tag.name else favTagNames + tag.name
            Toast.makeText(
                context,
                if (removing) "已取消收藏：${tag.name}" else "已收藏标签：${tag.name}",
                Toast.LENGTH_SHORT,
            ).show()
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
            Text("作品详情", style = MaterialTheme.typography.titleMedium)
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

                d == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    d.title,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                DownloadButton(
                                    repo = repo,
                                    detail = d,
                                    initiallyDownloaded = downloaded,
                                )
                            }
                            Text(
                                "by ${d.author}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (offlineCopy) {
                                Text(
                                    "离线副本 · 标签和简介需要联网查看",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val statsLine = STAT_LABELS
                                .mapNotNull { (key, label) -> d.stats[key]?.let { "$label $it" } }
                                .joinToString(" · ")
                            if (statsLine.isNotEmpty()) {
                                Text(
                                    statsLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (d.summary.isNotEmpty()) {
                                Text(d.summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    TAG_GROUPS.forEach { (keys, label) ->
                        val groupTags = d.tags.filter { it.category in keys }
                        if (groupTags.isNotEmpty()) {
                            item(key = label) {
                                TagSection(
                                    label = label,
                                    category = keys.first(),
                                    tags = groupTags,
                                    favNames = favTagNames,
                                    onOpenTag = onOpenTag,
                                    onToggleFavorite = toggleTagFavorite,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (d != null) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (isFavorite) {
                                            repo.db.removeFavorite(workId)
                                        } else {
                                            repo.db.addFavorite(workId, d.title, d.author)
                                        }
                                    }
                                    if (isFavorite) savedTags = emptyList()
                                    isFavorite = !isFavorite
                                }
                            },
                            onLongClickLabel = "按标签收藏",
                            onLongClick = { showTagPicker = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (savedTags.isNotEmpty()) {
                    Text(
                        savedTags.joinToString("、"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onRead) {
                    Text(if (progress > 0.01f) "继续阅读 ${(progress * 100).toInt()}%" else "开始阅读")
                }
            }

            if (showTagPicker) {
                TagPickerDialog(
                    relationshipTags = d.tags
                        .filter { canonicalTagCategory(it.category) == "relationship" }
                        .map { it.name }
                        .distinct(),
                    knownTags = knownTags,
                    initial = savedTags,
                    onDismiss = { showTagPicker = false },
                    onConfirm = { tags ->
                        showTagPicker = false
                        scope.launch {
                            val mine = withContext(Dispatchers.IO) {
                                repo.db.addFavorite(workId, d.title, d.author, tags)
                                repo.db.allFavoriteTags()
                            }
                            savedTags = tags
                            knownTags = mine
                            isFavorite = true
                            Toast.makeText(
                                context,
                                if (tags.isEmpty()) "已收藏" else "已收藏，标签：${tags.joinToString("、")}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    label: String,
    category: String,
    tags: List<Tag>,
    favNames: Set<String>,
    onOpenTag: (Tag) -> Unit,
    onToggleFavorite: (Tag) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = tagColors(category).content)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                TagChip(
                    name = tag.name,
                    category = tag.category.ifEmpty { category },
                    saved = tag.name in favNames,
                    onClick = { onOpenTag(tag) },
                    onLongClick = { onToggleFavorite(tag) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    name: String,
    category: String,
    saved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = tagColors(category)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (saved) MaterialTheme.colorScheme.secondaryContainer else c.container,
        contentColor = if (saved) MaterialTheme.colorScheme.onSecondaryContainer else c.content,
        border = if (saved) null else BorderStroke(1.dp, c.content.copy(alpha = 0.35f)),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            name,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** 长按收藏按钮弹出：勾选作品的关系标签、以前用过/建过的标签，或自己填（逗号分隔）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(
    relationshipTags: List<String>,
    knownTags: List<String>,
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    var custom by remember { mutableStateOf("") }
    // 作品自己的关系标签单独一组，"我的标签"里不重复列
    val mine = knownTags.filterNot { it in relationshipTags.toSet() }
    val typed = selected - relationshipTags.toSet() - mine.toSet()

    fun addCustom() {
        val parts = custom.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) selected = selected + parts
        custom = ""
    }

    val toggle: (String) -> Unit = { name ->
        selected = if (name in selected) selected - name else selected + name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按标签收藏") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (relationshipTags.isNotEmpty()) {
                    PickChips(
                        "作品的关系标签",
                        tagColors("relationship").content,
                        relationshipTags,
                        selected,
                        toggle,
                    )
                }
                if (mine.isNotEmpty()) {
                    PickChips("我的标签", MaterialTheme.colorScheme.primary, mine, selected, toggle)
                }

                Text("自定义标签", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("多个用逗号分隔") },
                        singleLine = true,
                    )
                    TextButton(onClick = { addCustom() }) { Text("添加") }
                }
                if (typed.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typed.forEach { name ->
                            FilterChip(
                                selected = true,
                                onClick = { toggle(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { Text("收藏") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一组可勾选标签。限高自己滚，别把下面的自定义输入挤出屏幕。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickChips(
    label: String,
    labelColor: Color,
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
    FlowRow(
        Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { name ->
            FilterChip(
                selected = name in selected,
                onClick = { onToggle(name) },
                label = { Text(name) },
            )
        }
    }
}
