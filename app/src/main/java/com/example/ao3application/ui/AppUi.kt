package com.example.ao3application.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ao3application.data.Repo
import com.example.ao3application.data.ThemeMode

sealed interface Screen {
    data object Browse : Screen
    data object Library : Screen
    data object Settings : Screen
    data class BrowseTag(val url: String, val label: String) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val id: Long) : Screen
}

private enum class NavMotion { Fade, Forward, Back }

@Composable
fun AppUi(
    repo: Repo,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val stack = remember { mutableStateOf(listOf<Screen>(Screen.Browse)) }
    val browseState = remember { BrowseUiState() }
    var motion by remember { mutableStateOf(NavMotion.Fade) }
    val push: (Screen) -> Unit = {
        motion = NavMotion.Forward
        stack.value = stack.value + it
    }
    val pop: () -> Unit = {
        if (stack.value.size > 1) {
            motion = NavMotion.Back
            stack.value = stack.value.dropLast(1)
        }
    }
    val switchTo: (Screen) -> Unit = {
        motion = NavMotion.Fade
        stack.value = listOf(it)
    }
    val current = stack.value.last()

    BackHandler(enabled = stack.value.size > 1) { pop() }

    Scaffold(
        bottomBar = {
            if (current is Screen.Browse || current is Screen.Library || current is Screen.Settings) {
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
                    NavigationBarItem(
                        selected = current is Screen.Settings,
                        onClick = { switchTo(Screen.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    when (motion) {
                        NavMotion.Fade ->
                            fadeIn(tween(220)) togetherWith fadeOut(tween(140))

                        NavMotion.Forward ->
                            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(280)) { -it / 6 } + fadeOut(tween(280)))

                        NavMotion.Back ->
                            (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(280)) { it / 6 } + fadeOut(tween(280)))
                    }
                },
                label = "screen",
            ) { screen ->
                when (screen) {
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

                    is Screen.Settings -> SettingsScreen(
                        repo = repo,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
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
}
