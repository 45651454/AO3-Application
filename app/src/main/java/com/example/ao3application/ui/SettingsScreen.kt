package com.example.ao3application.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ao3application.data.ExportImport
import com.example.ao3application.data.Repo
import com.example.ao3application.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val THEME_LABELS = mapOf(
    ThemeMode.SYSTEM to "跟随系统",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.DARK to "深色",
)

@Composable
fun SettingsScreen(
    repo: Repo,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(ExportImport.exportFrom(repo.db).toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "导出完成" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                    ExportImport.importInto(repo.db, text)
                }
            }
            result.onSuccess { (favCount, histCount, tagCount) ->
                Toast.makeText(
                    context,
                    "导入完成：收藏 $favCount 条，新增历史 $histCount 条，标签 $tagCount 条",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "设置",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        SectionTitle("外观")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(THEME_LABELS.getValue(mode)) },
                )
            }
        }

        SectionTitle("数据")
        BackupAction(
            title = "导出备份",
            detail = "把收藏（含标签）、阅读历史和收藏的标签保存成 JSON 文件。",
            action = "导出",
            onClick = { exportLauncher.launch("ao3-backup.json") },
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        BackupAction(
            title = "导入备份",
            detail = "从 JSON 文件恢复。同 id 的收藏会被文件里的内容覆盖。",
            action = "导入",
            onClick = {
                importLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                )
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun BackupAction(title: String, detail: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onClick) { Text(action) }
    }
}
