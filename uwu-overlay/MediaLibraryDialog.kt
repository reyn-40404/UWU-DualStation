package com.uwu.dualstation.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class LocalVideo(val name: String, val uri: Uri, val size: Long, val modified: Long)

@Composable
fun MediaLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("uwu_media_library", 0) }
    var youtubeUri by remember { mutableStateOf(prefs.getString("youtube_tree", null)) }
    var bilibiliUri by remember { mutableStateOf(prefs.getString("bilibili_tree", null)) }
    var refreshKey by remember { mutableStateOf(0) }
    var youtubeVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var bilibiliVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    val youtubePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("youtube_tree", uri.toString()).apply()
            youtubeUri = uri.toString()
            refreshKey++
        } catch (_: SecurityException) {
            Toast.makeText(context, "無法保存資料夾權限，請重新選擇", Toast.LENGTH_LONG).show()
        }
    }
    val bilibiliPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("bilibili_tree", uri.toString()).apply()
            bilibiliUri = uri.toString()
            refreshKey++
        } catch (_: SecurityException) {
            Toast.makeText(context, "無法保存資料夾權限，請重新選擇", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(youtubeUri, bilibiliUri, refreshKey) {
        scanning = true
        val pair = withContext(Dispatchers.IO) {
            scanVideos(context, youtubeUri) to scanVideos(context, bilibiliUri)
        }
        youtubeVideos = pair.first
        bilibiliVideos = pair.second
        scanning = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("下載與緩存", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }
                Text(
                    "分別選擇兩個平台的影片資料夾。UWU 只讀取檔案，不會移動或刪除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        FolderSection("YouTube 資料夾", youtubeUri, youtubeVideos, { youtubePicker.launch(null) }) {
                            playVideo(context, it.uri)
                        }
                    }
                    item {
                        FolderSection("Bilibili 資料夾", bilibiliUri, bilibiliVideos, { bilibiliPicker.launch(null) }) {
                            playVideo(context, it.uri)
                        }
                    }
                    item {
                        Text(if (scanning) "正在掃描…" else "共找到 " + (youtubeVideos.size + bilibiliVideos.size) + " 個影片")
                        TextButton(onClick = { refreshKey++ }) { Text("重新掃描") }
                        Text(
                            "注意：YouTube 官方離線影片通常是加密且存放於 App 私有空間，UWU 無法讀取。Android 11 以上也會限制部分 Android/data 資料夾；Bilibili 私有快取可能同樣無法存取。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSection(
    title: String,
    selected: String?,
    videos: List<LocalVideo>,
    onChoose: () -> Unit,
    onOpen: (LocalVideo) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (selected == null) "尚未選擇資料夾" else "已設定資料夾", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onChoose) { Text(if (selected == null) "選擇資料夾" else "更換資料夾") }
            if (selected != null && videos.isEmpty()) Text("此資料夾目前沒有可讀取的影片", style = MaterialTheme.typography.bodySmall)
            videos.take(250).forEach { video ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(video) }.padding(vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(video.name, style = MaterialTheme.typography.bodyMedium)
                    val date = if (video.modified > 0) DateFormat.getDateTimeInstance().format(Date(video.modified)) else "日期未知"
                    Text(formatBytes(video.size) + " · " + date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (videos.size > 250) Text("僅顯示前 250 個影片", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun scanVideos(context: android.content.Context, rawUri: String?): List<LocalVideo> {
    if (rawUri.isNullOrBlank()) return emptyList()
    val root = try { DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) } catch (_: Exception) { null } ?: return emptyList()
    val found = mutableListOf<LocalVideo>()
    val queue = ArrayDeque<Pair<DocumentFile, Int>>()
    queue.add(root to 0)
    while (queue.isNotEmpty() && found.size < 1000) {
        val (folder, depth) = queue.removeFirst()
        val children = try { folder.listFiles() } catch (_: Exception) { emptyArray() }
        for (child in children) {
            if (child.isDirectory && depth < 8) queue.add(child to depth + 1)
            else if (child.isFile && isVideo(child)) {
                found.add(LocalVideo(child.name ?: "未命名影片", child.uri, child.length(), child.lastModified()))
            }
            if (found.size >= 1000) break
        }
    }
    return found.sortedByDescending { it.modified }
}

private fun isVideo(file: DocumentFile): Boolean {
    if (file.type.orEmpty().startsWith("video/")) return true
    val name = file.name.orEmpty().lowercase(Locale.ROOT)
    return listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv", ".m4v", ".ts", ".3gp").any(name::endsWith)
}

private fun playVideo(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "手機沒有可播放影片的 App", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "無法開啟這個影片檔案", Toast.LENGTH_LONG).show()
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
