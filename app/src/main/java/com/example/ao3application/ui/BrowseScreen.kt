package com.example.ao3application.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ao3application.data.ListingPage
import com.example.ao3application.data.Repo
import com.example.ao3application.data.WorkSummary
import kotlinx.coroutines.launch

private val SORT_OPTIONS = listOf(
    "_score" to "最佳匹配",
    "kudos_count" to "Kudos",
    "revised_at" to "最近更新",
    "word_count" to "字数",
)

class BrowseUiState {
    var query by mutableStateOf("")
    var sort by mutableStateOf("_score")
    var completeOnly by mutableStateOf(false)
    var works by mutableStateOf(emptyList<WorkSummary>())
    var nextUrl by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    val listState = LazyListState()
}

@Composable
fun BrowseScreen(
    repo: Repo,
    tag: Screen.BrowseTag?,
    onOpenWork: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    state: BrowseUiState = remember { BrowseUiState() },
) {
    val s = state
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun load(fetch: suspend () -> ListingPage, append: Boolean) {
        scope.launch {
            s.loading = true
            s.error = null
            try {
                val page = fetch()
                s.works = if (append) s.works + page.works else page.works
                s.nextUrl = page.nextUrl
                if (!append) s.listState.scrollToItem(0)
            } catch (e: Exception) {
                s.error = e.message ?: "加载失败"
            } finally {
                s.loading = false
            }
        }
    }

    fun runSearch() {
        s.searched = true
        focusManager.clearFocus()
        load({ repo.search(s.query, s.sort, s.completeOnly) }, append = false)
    }

    LaunchedEffect(tag?.url) {
        if (tag != null) load({ repo.listing(tag.url) }, append = false)
    }

    Column(Modifier.fillMaxSize()) {
        if (tag != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Text(
                    tag.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            OutlinedTextField(
                value = s.query,
                onValueChange = { s.query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("搜索作品（关键词、标题、作者）") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { runSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SORT_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = s.sort == value,
                        onClick = {
                            s.sort = value
                            if (s.searched) runSearch()
                        },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = s.completeOnly,
                    onClick = {
                        s.completeOnly = !s.completeOnly
                        if (s.searched) runSearch()
                    },
                    label = { Text("仅完结") },
                )
            }
        }

        val err = s.error
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.loading && s.works.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                err != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (tag != null) load({ repo.listing(tag.url) }, false)
                        else if (s.searched) runSearch()
                    }) { Text("重试") }
                }

                s.works.isEmpty() -> Text(
                    if (s.searched || tag != null) "没有结果" else "输入关键词开始搜索，\n或从作品详情页点标签浏览",
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> LazyColumn(Modifier.fillMaxSize(), state = s.listState) {
                    items(s.works, key = { it.id }) { work ->
                        WorkCard(work, onClick = { onOpenWork(work.id) })
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (s.loading) {
                                CircularProgressIndicator()
                            } else if (s.nextUrl != null) {
                                Button(onClick = { load({ repo.listing(s.nextUrl!!) }, append = true) }) {
                                    Text("加载下一页")
                                }
                            } else {
                                Text("没有更多了", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkCard(work: WorkSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(work.title, style = MaterialTheme.typography.titleMedium)
        Text(
            "by ${work.author} · ${work.updated}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val badge = buildString {
            append(work.rating)
            if (work.warning.isNotEmpty()) append(" · ").append(work.warning)
            append(if (work.isComplete) " · 完结" else " · 连载中")
        }
        Text(
            badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (work.fandoms.isNotEmpty()) {
            Text(
                work.fandoms.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (work.tags.isNotEmpty()) {
            val names = work.tags.take(8).map { it.name }
            val suffix = if (work.tags.size > 8) " …" else ""
            Text(
                names.joinToString(" · ") + suffix,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (work.summary.isNotEmpty()) {
            Text(
                work.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val statsLine = buildList {
            work.stats["words"]?.let { add("$it 词") }
            work.stats["chapters"]?.let { add("$it 章") }
            work.stats["kudos"]?.let { add("Kudos $it") }
            work.stats["comments"]?.let { add("评论 $it") }
        }.joinToString(" · ")
        if (statsLine.isNotEmpty()) {
            Text(
                statsLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}
