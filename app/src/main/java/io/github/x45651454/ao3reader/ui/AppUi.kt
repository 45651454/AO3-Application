package io.github.x45651454.ao3reader.ui

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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.x45651454.ao3reader.data.Repo
import io.github.x45651454.ao3reader.data.ThemeMode

sealed interface Screen {
    data object Browse : Screen
    data object Library : Screen
    data object Settings : Screen
    data class BrowseTag(val url: String, val label: String) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val id: Long) : Screen
}

private enum class NavMotion { Fade, Forward, Back }

/**
 * 导航栈的保存方案：Screen 全部编成 Bundle 兼容类型（String / ArrayList），
 * 旋转或进程重建后栈不塌回首页。data object 编成固定字符串，带参的编成 ArrayList。
 */
private fun screenToSaveable(screen: Screen): Any = when (screen) {
    Screen.Browse -> "browse"
    Screen.Library -> "library"
    Screen.Settings -> "settings"
    is Screen.BrowseTag -> arrayListOf("tag", screen.url, screen.label)
    is Screen.Detail -> arrayListOf("detail", screen.id)
    is Screen.Reader -> arrayListOf("reader", screen.id)
}

private fun screenFromSaveable(value: Any): Screen? = when (value) {
    "browse" -> Screen.Browse
    "library" -> Screen.Library
    "settings" -> Screen.Settings
    is List<*> -> when (value.firstOrNull()) {
        "tag" -> Screen.BrowseTag(value[1] as String, value[2] as String)
        "detail" -> Screen.Detail((value[1] as Number).toLong())
        "reader" -> Screen.Reader((value[1] as Number).toLong())
        else -> null
    }

    else -> null
}

@Composable
fun AppUi(
    repo: Repo,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showNsfw: Boolean,
    onShowNsfwChange: (Boolean) -> Unit,
) {
    val stack = rememberSaveable(
        stateSaver = listSaver<List<Screen>, Any>(
            save = { screens -> screens.map(::screenToSaveable) },
            restore = { saved ->
                saved.mapNotNull(::screenFromSaveable).ifEmpty { listOf(Screen.Browse) }
            },
        ),
    ) { mutableStateOf(listOf(Screen.Browse)) }
    val browseState = rememberSaveable(saver = BrowseUiStateSaver) { BrowseUiState() }
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
                        showNsfw = showNsfw,
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
                        showNsfw = showNsfw,
                        onShowNsfwChange = onShowNsfwChange,
                    )

                    is Screen.BrowseTag -> BrowseScreen(
                        repo = repo,
                        tag = screen,
                        onOpenWork = { push(Screen.Detail(it)) },
                        onBack = pop,
                        showNsfw = showNsfw,
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
