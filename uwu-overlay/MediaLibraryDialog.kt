package com.uwu.dualstation.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
private data class FolderStats(val bytes: Long = 0L, val files: Int = 0)

@Composable
fun MediaLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("uwu_media_library", 0) }
    var youtubeUri by remember { mutableStateOf(prefs.getString("youtube_tree", null)) }
    var bilibiliUri by remember { mutableStateOf(prefs.getString("bilibili_tree", null)) }
    var refreshKey by remember { mutableStateOf(0) }
    var youtubeVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var bilibiliVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var youtubeStats by remember { mutableStateOf(FolderStats()) }
    var bilibiliStats by remember { mutableStateOf(FolderStats()) }
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
        val result = withContext(Dispatchers.IO) {
            scanFolder(context, youtubeUri) to scanFolder(context, bilibiliUri)
        }
        youtubeVideos = result.first.first
        youtubeStats = result.first.second
        bilibiliVideos = result.second.first
        bilibiliStats = result.second.second
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
                    "分別選擇兩個平台的影片資料夾。封面由本機影片擷取；空間統計只計算可讀取的檔案。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        FolderSection("YouTube 資料夾", youtubeUri, youtubeVideos, youtubeStats, { youtubePicker.launch(null) }) {
                            playVideo(context, it.uri)
                        }
                    }
                    item {
                        FolderSection("Bilibili 資料夾", bilibiliUri, bilibiliVideos, bilibiliStats, { bilibiliPicker.launch(null) }) {
                            playVideo(context, it.uri)
                        }
                    }
                    item {
                        Text(if (scanning) "正在掃描資料夾與統計容量…" else "共找到 ${youtubeVideos.size + bilibiliVideos.size} 個影片")
                        TextButton(onClick = { refreshKey++ }) { Text("重新掃描") }
                        Text(
                            "注意：YouTube 官方離線影片通常是加密且存放於 App 私有空間，UWU 無法讀取。Android 11 以上也會限制部分 Android/data 資料夾；Bilibili 私有快取可能同樣無法存取。資料夾統計最多掃描 5,000 個檔案。",
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
    stats: FolderStats,
    onChoose: () -> Unit,
    onOpen: (LocalVideo) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (selected == null) "尚未選擇資料夾" else "已設定資料夾", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onChoose) { Text(if (selected == null) "選擇資料夾" else "更換資料夾") }
            if (selected != null) {
                Text("可讀取檔案：${stats.files} 個 · 佔用約 ${formatBytes(stats.bytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (videos.isEmpty()) Text("此資料夾目前沒有可讀取的影片", style = MaterialTheme.typography.bodySmall)
            }
            videos.take(80).forEachIndexed { index, video ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(video) }.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoThumbnail(video)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(video.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        val date = if (video.modified > 0) DateFormat.getDateTimeInstance().format(Date(video.modified)) else "日期未知"
                        Text(formatBytes(video.size) + " · " + date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (videos.size > 80) Text("影片清單僅顯示前 80 個；容量統計仍會掃描資料夾內可讀取檔案。", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VideoThumbnail(video: LocalVideo) {
    val context = LocalContext.current
    var bitmap by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.uri) {
        bitmap = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, video.uri)
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (frame != null && (frame.width > 320 || frame.height > 180)) {
                    val ratio = minOf(320f / frame.width, 180f / frame.height)
                    Bitmap.createScaledBitmap(frame, (frame.width * ratio).toInt().coerceAtLeast(1), (frame.height * ratio).toInt().coerceAtLeast(1), true)
                } else frame
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }
    }
    Box(
        modifier = Modifier.size(width = 104.dp, height = 62.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "影片縮圖", modifier = Modifier.size(width = 104.dp, height = 62.dp), contentScale = ContentScale.Crop)
        } else {
            Text("影片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun scanFolder(context: android.content.Context, rawUri: String?): Pair<List<LocalVideo>, FolderStats> {
    if (rawUri.isNullOrBlank()) return emptyList<LocalVideo>() to FolderStats()
    val root = try { DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) } catch (_: Exception) { null } ?: return emptyList<LocalVideo>() to FolderStats()
    val found = mutableListOf<LocalVideo>()
    val queue = ArrayDeque<Pair<DocumentFile, Int>>()
    queue.add(root to 0)
    var totalBytes = 0L
    var fileCount = 0
    while (queue.isNotEmpty() && fileCount < 5000) {
        val (folder, depth) = queue.removeFirst()
        val children = try { folder.listFiles() } catch (_: Exception) { emptyArray() }
        for (child in children) {
            if (child.isDirectory && depth < 8) {
                queue.add(child to depth + 1)
            } else if (child.isFile) {
                val size = try { child.length().coerceAtLeast(0L) } catch (_: Exception) { 0L }
                totalBytes += size
                fileCount++
                if (found.size < 1000 && isVideo(child)) {
                    found.add(LocalVideo(child.name ?: "未命名影片", child.uri, size, child.lastModified()))
                }
            }
            if (fileCount >= 5000) break
        }
    }
    return found.sortedByDescending { it.modified } to FolderStats(totalBytes, fileCount)
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
