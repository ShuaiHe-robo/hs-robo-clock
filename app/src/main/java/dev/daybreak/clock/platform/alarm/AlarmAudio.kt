package dev.daybreak.clock.platform.alarm

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.daybreak.clock.R
import kotlinx.coroutines.*

/** Async preparation with a bounded deadline, then system and bundled fallbacks. */
class AlarmAudio(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var generation = 0
    private var preview = false
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var fallbackCallback: (() -> Unit)? = null
    private var focusGranted = false
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun play(uri: String?, locked: Boolean = false, preview: Boolean = false, fallback: () -> Unit = {}) {
        stop()
        this.preview = preview
        fallbackCallback = fallback
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAcceptsDelayedFocusGain(true).setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                focusGranted = change == AudioManager.AUDIOFOCUS_GAIN
                if (focusGranted) runCatching { player?.start() }
                else runCatching { player?.pause() }
            }.build()
        focus = request
        focusGranted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val candidates = buildList<Uri?> {
            if (!locked && uri != null) add(Uri.parse(uri))
            add(default)
            add(null) // Bundled PCM, available before first unlock.
        }
        attempt(candidates, 0, generation)
    }
    private fun attempt(candidates: List<Uri?>, index: Int, token: Int) {
        if (generation != token || index >= candidates.size) return
        player?.let { old -> worker.launch { runCatching { old.release() } } }
        val media = MediaPlayer()
        player = media
        var settled = false
        val timeout = Runnable {
            if (!settled && generation == token) { settled = true; fallbackCallback?.invoke(); attempt(candidates, index + 1, token) }
        }
        fun fail() {
            if (!settled && generation == token) {
                settled = true; handler.removeCallbacks(timeout); fallbackCallback?.invoke(); attempt(candidates, index + 1, token)
            }
        }
        try {
            media.setAudioAttributes(attributes)
            media.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            media.isLooping = !preview
            media.setOnPreparedListener {
                if (generation == token && !settled) {
                    settled = true; handler.removeCallbacks(timeout)
                    if (focusGranted) runCatching { it.start() }.onFailure { fallbackCallback?.invoke(); attempt(candidates, index + 1, token) }
                }
            }
            media.setOnErrorListener { _, _, _ ->
                if (settled && generation == token && player === media) { fallbackCallback?.invoke(); attempt(candidates, index + 1, token) }
                else fail()
                true
            }
            media.setOnCompletionListener { if (preview) stop() }
            // A remote document provider may block opening its descriptor. Never block the UI
            // or the deadline handler; a stale attempt is discarded when it eventually returns.
            worker.launch {
                try {
                    val candidate = candidates[index]
                    if (candidate != null) media.setDataSource(context, candidate)
                    else context.resources.openRawResourceFd(R.raw.backup_alarm).use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    handler.post {
                        if (generation == token && player === media && !settled) runCatching { media.prepareAsync() }.onFailure { fail() }
                        else worker.launch { runCatching { media.release() } }
                    }
                } catch (e: Exception) { handler.post { if (player === media) fail() } }
            }
            handler.postDelayed(timeout, 3000)
        } catch (e: Exception) { fail() }
    }
    fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        player?.let { old -> worker.launch { runCatching { old.release() } } }; player = null
        focusGranted = false
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null
    }
}
