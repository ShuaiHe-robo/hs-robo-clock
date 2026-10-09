package dev.daybreak.clock.platform.alarm

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SelectedRingtone(val uri: String, val name: String, val source: String)
class RingtoneSelection(private val context: Context) {
    suspend fun file(uri: Uri): SelectedRingtone {
        val existing = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            check(context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) { "文件授权无法持久保存，请重新选择或使用默认铃声" }
            validate(uri)
            val name = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "所选音频"
            }
            return SelectedRingtone(uri.toString(), name, "FILE")
        } catch (e: Exception) {
            if (!existing) runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            throw IllegalArgumentException("音频无法读取或解码，请重新选择或使用默认铃声", e)
        }
    }
    suspend fun validate(uri: Uri) = withContext(Dispatchers.Main.immediate) {
        val player = MediaPlayer()
        try {
            withTimeout(4000) {
                suspendCancellableCoroutine { continuation ->
                    player.setOnPreparedListener { if (continuation.isActive) continuation.resume(Unit) }
                    player.setOnErrorListener { _, _, _ ->
                        if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException("无法解码音频"))
                        true
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        try { player.setDataSource(context, uri); if (continuation.isActive) player.prepareAsync() }
                        catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }
                }
            }
        } finally { CoroutineScope(Dispatchers.IO).launch { runCatching { player.release() } } }
    }
}
