package io.github.x45651454.ao3reader.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x45651454.ao3reader.data.ListingPage
import io.github.x45651454.ao3reader.data.Rating
import io.github.x45651454.ao3reader.data.Repo
import io.github.x45651454.ao3reader.data.Tag
import io.github.x45651454.ao3reader.data.WorkSummary
import io.github.x45651454.ao3reader.ui.theme.canonicalTagCategory
import io.github.x45651454.ao3reader.ui.theme.tagColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SORT_OPTIONS = listOf(
    "_score" to "最佳匹配",
    "kudos_count" to "Kudos",
    "revised_at" to "最近更新",
    "word_count" to "字数",
)

/**
 * 卡片上每类标签的名额，同时充当白名单：不在表里的分类一律不显示（警告、分级、同人圈都在此列）。
 * 关系和角色名额最大，保证它们不会被其他标签挤掉——详情页仍然完整展示警告。
 */
private val CARD_TAG_QUOTA = mapOf(
    "relationship" to 4,
    "character" to 4,
    "freeform" to 3,
    "category" to 1,
)

internal fun previewTags(tags: List<Tag>): List<Tag> {
    val used = mutableMapOf<String, Int>()
    return tags.filter { tag ->
        val key = canonicalTagCategory(tag.category)
        val max = CARD_TAG_QUOTA[key] ?: 0
        val n = used[key] ?: 0
        if (n < max) {
            used[key] = n + 1
            true
        } else {
            false
        }
    }.take(8)
}

class BrowseUiState(val listState: LazyListState = LazyListState()) {
    var query by mutableStateOf("")
    var sort by mutableStateOf("_score")
    var completeOnly by mutableStateOf(false)
    var works by mutableStateOf(emptyList<WorkSummary>())
    var nextUrl by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
}

/**
 * BrowseUiState 的保存方案：查询词、排序、筛选、是否已搜索、分页 nextUrl、滚动位置
 * 和已加载的结果列表全部编成 Bundle 兼容类型（String/Boolean/Long/ArrayList/HashMap），
 * 旋转或进程重建后原样恢复，不重新请求网络，滚动位置也保得住。
 * loading/error 是瞬时状态，不保存。
 */
val BrowseUiStateSaver: Saver<BrowseUiState, Any> = Saver(
    save = { s ->
        arrayListOf<Any?>(
            s.query,
            s.sort,
            s.completeOnly,
            s.searched,
            s.nextUrl,
            arrayListOf(s.listState.firstVisibleItemIndex, s.listState.firstVisibleItemScrollOffset),
            ArrayList(s.works.map(::workToSaveable)),
        )
    },
    restore = ::browseStateFromSaveable,
)

@Suppress("UNCHECKED_CAST")
private fun browseStateFromSaveable(value: Any): BrowseUiState {
    val v = value as List<*>
    val scroll = v[5] as List<Int>
    return BrowseUiState(LazyListState(scroll[0], scroll[1])).apply {
        query = v[0] as String
        sort = v[1] as String
        completeOnly = v[2] as Boolean
        searched = v[3] as Boolean
        nextUrl = v[4] as String?
        works = (v[6] as List<List<Any?>>).map(::workFromSaveable)
    }
}

private fun workToSaveable(w: WorkSummary): ArrayList<Any?> = arrayListOf(
    w.id, w.title, w.author, w.rating, w.warning, w.category, w.isComplete,
    ArrayList(w.fandoms),
    ArrayList(w.tags.map { arrayListOf(it.name, it.href, it.category) }),
    w.summary, w.updated,
    HashMap(w.stats),
)

@Suppress("UNCHECKED_CAST")
private fun workFromSaveable(v: List<Any?>): WorkSummary = WorkSummary(
    id = (v[0] as Number).toLong(),
    title = v[1] as String,
    author = v[2] as String,
    rating = v[3] as String,
    warning = v[4] as String,
    category = v[5] as String,
    isComplete = v[6] as Boolean,
    fandoms = v[7] as List<String>,
    tags = (v[8] as List<List<String>>).map { Tag(it[0], it[1], it[2]) },
    summary = v[9] as String,
    updated = v[10] as String,
    stats = v[11] as Map<String, String>,
)

@Composable
fun BrowseScreen(
    repo: Repo,
    tag: Screen.BrowseTag?,
    onOpenWork: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    state: BrowseUiState = rememberSaveable(saver = BrowseUiStateSaver) { BrowseUiState() },
    showNsfw: Boolean = true,
) {
    val s = state
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 只在渲染层过滤：state.works 与 Saver 里始终是原始未过滤数据，
    // 开关切换时重组即可立即生效，不动网络请求和分页（nextUrl）。
    val visibleWorks = remember(s.works, showNsfw) { Rating.filterVisible(s.works, showNsfw) }

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
                if (e is CancellationException) throw e
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
                    items(visibleWorks, key = { it.id }) { work ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkCard(work: WorkSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                work.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            RatingBadge(work.rating)
        }
        Text(
            "by ${work.author} · ${work.updated}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val badge = listOfNotNull(
            work.rating.ifEmpty { null },
            if (work.isComplete) "完结" else "连载中",
        ).joinToString(" · ")
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
        val shownTags = previewTags(work.tags)
        if (shownTags.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shownTags.forEach { tag ->
                    val c = tagColors(tag.category)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = c.container,
                        contentColor = c.content,
                    ) {
                        Text(
                            tag.name,
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (work.tags.size > shownTags.size) {
                    Text(
                        "+${work.tags.size - shownTags.size}",
                        Modifier.padding(vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
