package io.github.x45651454.ao3reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.x45651454.ao3reader.data.SettingsStore
import io.github.x45651454.ao3reader.data.ThemeMode
import io.github.x45651454.ao3reader.ui.AppUi
import io.github.x45651454.ao3reader.ui.theme.AO3ApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AO3ApplicationTheme(darkTheme = dark) {
                AppUi(
                    repo = (application as App).repo,
                    themeMode = themeMode,
                    onThemeModeChange = {
                        settings.themeMode = it
                        themeMode = it
                    },
                )
            }
        }
    }
}
