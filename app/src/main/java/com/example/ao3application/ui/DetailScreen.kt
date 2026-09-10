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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ao3application.data.Repo
import com.example.ao3application.data.Tag
import com.example.ao3application.data.WorkDetail
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(workId, reload) {
        error = null
        detail = null
        try {
            val d = repo.work(workId)
            detail = d
            withContext(Dispatchers.IO) {
                isFavorite = repo.db.isFavorite(workId)
                progress = repo.db.progressFor(workId)
                favTagNames = repo.db.tagFavorites().map { it.name }.toSet()
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
                            Text(d.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "by ${d.author}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                TagSection(label, groupTags, favTagNames, onOpenTag, toggleTagFavorite)
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
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (isFavorite) {
                                repo.db.removeFavorite(workId)
                            } else {
                                repo.db.addFavorite(workId, d.title, d.author)
                            }
                        }
                        isFavorite = !isFavorite
                    }
                }) {
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
                Spacer(Modifier.weight(1f))
                Button(onClick = onRead) {
                    Text(if (progress > 0.01f) "继续阅读 ${(progress * 100).toInt()}%" else "开始阅读")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    label: String,
    tags: List<Tag>,
    favNames: Set<String>,
    onOpenTag: (Tag) -> Unit,
    onToggleFavorite: (Tag) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                TagChip(
                    name = tag.name,
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
private fun TagChip(name: String, saved: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (saved) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (saved) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (saved) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            name,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
