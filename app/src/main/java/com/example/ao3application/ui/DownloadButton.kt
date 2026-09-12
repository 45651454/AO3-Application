package com.example.ao3application.ui

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.ao3application.data.Repo
import com.example.ao3application.data.WorkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载 / 已下载按钮，附带删除下载的确认框。详情页和阅读页共用，摆哪儿由调用方决定。
 * [initiallyDownloaded] 由调用方在加载时查一次传入。
 */
@Composable
internal fun DownloadButton(repo: Repo, detail: WorkDetail, initiallyDownloaded: Boolean) {
    var downloaded by remember(detail.id) { mutableStateOf(initiallyDownloaded) }
    var downloading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    TextButton(
        onClick = {
            if (downloaded) {
                confirmDelete = true
            } else {
                scope.launch {
                    downloading = true
                    withContext(Dispatchers.IO) { repo.db.saveDownload(detail) }
                    downloading = false
                    downloaded = true
                    Toast.makeText(context, "已下载，可离线阅读", Toast.LENGTH_SHORT).show()
                }
            }
        },
        enabled = !downloading,
    ) {
        Text(
            when {
                downloading -> "下载中…"
                downloaded -> "已下载"
                else -> "下载"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (downloaded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除下载") },
            text = { Text("删除后需要联网重新下载才能离线阅读。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.db.removeDownload(detail.id) }
                            downloaded = false
                            Toast.makeText(context, "已删除下载", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
