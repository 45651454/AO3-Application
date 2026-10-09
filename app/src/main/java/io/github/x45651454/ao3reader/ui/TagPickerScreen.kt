package io.github.x45651454.ao3reader.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x45651454.ao3reader.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 按标签收藏：整页取代原来的 TagPickerDialog。
 * 主体是注册表里全部「我的标签」，顶部保留本作品的关系标签建议；
 * 「新建」的标签写进注册表，即使本次没勾选下次打开也还在。
 * 作品的标题/作者/分级由详情页传进来，不收藏过的作品不必再回查 DB。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerScreen(
    repo: Repo,
    workId: Long,
    title: String,
    author: String,
    rating: String,
    workTags: List<String>,
    onBack: () -> Unit,
) {
    var allTags by remember { mutableStateOf(emptyList<String>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loaded by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(workId) {
        val (tags, initial) = withContext(Dispatchers.IO) {
            repo.db.allFavoriteTags() to repo.db.favoriteTagsFor(workId)
        }
        allTags = tags
        selected = initial.toSet()
        loaded = true
    }

    val toggle: (String) -> Unit = { name ->
        selected = if (name in selected) selected - name else selected + name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("按标签收藏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showCreate = true }) { Text("新建") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = loaded && !saving,
                        onClick = {
                            scope.launch {
                                saving = true
                                val tags = selected.toList().sorted()
                                withContext(Dispatchers.IO) {
                                    repo.db.addFavorite(workId, title, author, tags, rating = rating)
                                }
                                Toast.makeText(
                                    context,
                                    if (tags.isEmpty()) "已收藏" else "已收藏，标签：${tags.joinToString("、")}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onBack()
                            }
                        },
                    ) { Text("添加收藏") }
                }
            }
        },
    ) { padding ->
        if (!loaded) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 已在「本作品标签」里列出的名字，「我的标签」里不重复列
            val mine = allTags.filterNot { it in workTags.toSet() }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (workTags.isNotEmpty()) {
                    item(key = "header_work") { TagGroupHeader("本作品标签") }
                    items(workTags, key = { "work:$it" }) { name ->
                        TagSelectRow(name, checked = name in selected, onToggle = { toggle(name) })
                    }
                }
                item(key = "header_mine") { TagGroupHeader("我的标签") }
                if (allTags.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "还没有收藏标签，点右上角「新建」创建一个",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(mine, key = { "mine:$it" }) { name ->
                    TagSelectRow(name, checked = name in selected, onToggle = { toggle(name) })
                }
            }
        }
    }

    if (showCreate) {
        CreateTagDialog(
            onDismiss = { showCreate = false },
            onConfirm = { raw ->
                scope.launch {
                    val name = withContext(Dispatchers.IO) { repo.db.registerFavoriteTag(raw) }
                    if (name != null) {
                        showCreate = false
                        if (name !in allTags) allTags = (allTags + name).sortedWith(String.CASE_INSENSITIVE_ORDER)
                        selected = selected + name
                    } else {
                        Toast.makeText(context, "标签名不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun TagGroupHeader(label: String) {
    Text(
        label,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 整行可点 toggle，右侧 Checkbox 只展示状态。 */
@Composable
private fun TagSelectRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun CreateTagDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("标签名") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
