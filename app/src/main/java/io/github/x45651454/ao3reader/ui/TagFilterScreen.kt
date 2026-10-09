package io.github.x45651454.ao3reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x45651454.ao3reader.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 收藏的标签筛选页：单选语义，整页取代原来列表顶部的 FilterChip 行。
 * 「全部收藏」「未打标签」两个固定项 + 注册表里全部收藏标签；
 * 标签后的数字是该标签下的收藏数（收藏本就不大，从 DB 读一次直接数）。
 * 筛选状态由 AppUi 持有，选中后通过 [onSelect] 回调并 pop。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterScreen(
    repo: Repo,
    currentTag: String?,
    untaggedOnly: Boolean,
    onSelect: (tag: String?, untaggedOnly: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    // null 表示还在加载，区分于「注册表为空」
    var allTags by remember { mutableStateOf<List<String>?>(null) }
    var totalCount by remember { mutableStateOf(0) }
    var untaggedCount by remember { mutableStateOf(0) }
    var tagCounts by remember { mutableStateOf(emptyMap<String, Int>()) }

    LaunchedEffect(Unit) {
        val (tags, favorites) = withContext(Dispatchers.IO) {
            repo.db.allFavoriteTags() to repo.db.favorites()
        }
        allTags = tags
        totalCount = favorites.size
        untaggedCount = favorites.count { it.tags.isEmpty() }
        tagCounts = favorites.flatMap { it.tags }.groupingBy { it }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("按标签筛选") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val tags = allTags
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "all") {
                TagFilterRow(
                    label = "全部收藏",
                    count = if (tags == null) null else totalCount,
                    selected = currentTag == null && !untaggedOnly,
                    onClick = { onSelect(null, false) },
                )
            }
            item(key = "untagged") {
                TagFilterRow(
                    label = "未打标签",
                    count = if (tags == null) null else untaggedCount,
                    selected = untaggedOnly,
                    onClick = { onSelect(null, true) },
                )
            }
            when {
                tags == null -> item(key = "loading") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                tags.isEmpty() -> item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有收藏标签，长按收藏可新建",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> items(tags, key = { it }) { name ->
                    TagFilterRow(
                        label = name,
                        count = tagCounts[name] ?: 0,
                        selected = !untaggedOnly && currentTag == name,
                        onClick = { onSelect(name, false) },
                    )
                }
            }
        }
    }
}

/** 整行可点的单选行；选中项用主题色文字 + 右侧勾。 */
@Composable
private fun TagFilterRow(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    "$count",
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "当前筛选",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
