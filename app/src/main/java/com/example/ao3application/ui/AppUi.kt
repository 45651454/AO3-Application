package com.example.ao3application.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ao3application.data.Repo

sealed interface Screen {
    data object Browse : Screen
    data object Library : Screen
    data class BrowseTag(val url: String, val label: String) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val id: Long) : Screen
}

@Composable
fun AppUi(repo: Repo) {
    val stack = remember { mutableStateOf(listOf<Screen>(Screen.Browse)) }
    val browseState = remember { BrowseUiState() }
    val push: (Screen) -> Unit = { stack.value = stack.value + it }
    val pop: () -> Unit = { if (stack.value.size > 1) stack.value = stack.value.dropLast(1) }
    val switchTo: (Screen) -> Unit = { stack.value = listOf(it) }
    val current = stack.value.last()

    BackHandler(enabled = stack.value.size > 1) { pop() }

    Scaffold(
        bottomBar = {
            if (current is Screen.Browse || current is Screen.Library) {
                NavigationBar {
                    NavigationBarItem(
                        selected = current is Screen.Browse,
                        onClick = { switchTo(Screen.Browse) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("浏览") },
                    )
                    NavigationBarItem(
                        selected = current is Screen.Library,
                        onClick = { switchTo(Screen.Library) },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        label = { Text("书库") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val screen = current) {
                is Screen.Browse -> BrowseScreen(
                    repo = repo,
                    tag = null,
                    onOpenWork = { push(Screen.Detail(it)) },
                    state = browseState,
                )

                is Screen.Library -> LibraryScreen(
                    repo = repo,
                    onOpenWork = { push(Screen.Detail(it)) },
                    onOpenTag = { tag -> push(Screen.BrowseTag(tag.href, tag.name)) },
                )

                is Screen.BrowseTag -> BrowseScreen(
                    repo = repo,
                    tag = screen,
                    onOpenWork = { push(Screen.Detail(it)) },
                    onBack = pop,
                )

                is Screen.Detail -> DetailScreen(
                    repo = repo,
                    workId = screen.id,
                    onBack = pop,
                    onRead = { push(Screen.Reader(screen.id)) },
                    onOpenTag = { tag -> push(Screen.BrowseTag(tag.href, tag.name)) },
                )

                is Screen.Reader -> ReaderScreen(
                    repo = repo,
                    workId = screen.id,
                    onBack = pop,
                )
            }
        }
    }
}
