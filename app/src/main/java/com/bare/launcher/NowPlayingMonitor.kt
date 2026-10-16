package com.bare.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Active media via [MediaSessionManager] + our [BareNotificationListener]
 * ComponentName (same notification access already used for WA/PR).
 * No extra permission beyond NLS. Works with TIDAL and other session apps.
 */
data class NowPlayingInfo(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val packageName: String,
) {
    val displayLine: String
        get() = if (artist.isNullOrBlank()) title else "$title — $artist"
}

class NowPlayingMonitor(
    private val context: Context,
    private val onChange: (NowPlayingInfo?) -> Unit,
) {
    private val msm: MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent =
        ComponentName(context, BareNotificationListener::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var active: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var started = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!started) return
            refreshFromSessions()
            mainHandler.postDelayed(this, 2_000L)
        }
    }

    fun start() {
        if (started) return
        started = true
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            mainHandler.post { bindBest(list) }
        }
        sessionsListener = listener
        try {
            msm?.addOnActiveSessionsChangedListener(listener, listenerComponent, mainHandler)
        } catch (e: SecurityException) {
            Log.d(TAG, "sessions listener: need notification access", e)
        } catch (e: Exception) {
            Log.d(TAG, "sessions listener failed", e)
        }
        refreshFromSessions()
        mainHandler.postDelayed(pollRunnable, 2_000L)
    }

    fun stop() {
        started = false
        mainHandler.removeCallbacks(pollRunnable)
        sessionsListener?.let {
            try {
                msm?.removeOnActiveSessionsChangedListener(it)
            } catch (_: Exception) {
            }
        }
        sessionsListener = null
        unbindController()
        onChange(null)
    }

    fun skipPrevious() {
        active?.transportControls?.skipToPrevious()
    }

    fun playPause() {
        val c = active ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun skipNext() {
        active?.transportControls?.skipToNext()
    }

    fun openMediaApp(): Boolean {
        val pkg = active?.packageName ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    private fun refreshFromSessions() {
        val list = try {
            msm?.getActiveSessions(listenerComponent).orEmpty()
        } catch (e: SecurityException) {
            Log.d(TAG, "getActiveSessions blocked — enable notification access", e)
            emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "getActiveSessions failed", e)
            emptyList()
        }
        bindBest(list)
    }

    private fun bindBest(controllers: List<MediaController>?) {
        val list = controllers.orEmpty()
        val best = pickBest(list)
        if (best?.sessionToken != active?.sessionToken) {
            unbindController()
            if (best != null) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        emit(active)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        emit(active)
                    }

                    override fun onSessionDestroyed() {
                        refreshFromSessions()
                    }
                }
                controllerCallback = cb
                best.registerCallback(cb, mainHandler)
                active = best
            }
        }
        emit(active)
    }

    private fun unbindController() {
        val c = active
        val cb = controllerCallback
        if (c != null && cb != null) {
            try {
                c.unregisterCallback(cb)
            } catch (_: Exception) {
            }
        }
        active = null
        controllerCallback = null
    }

    private fun pickBest(list: List<MediaController>): MediaController? {
        if (list.isEmpty()) return null
        fun hasMedia(c: MediaController): Boolean {
            val md = c.metadata ?: return false
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            return !title.isNullOrBlank()
        }
        fun isPlaying(c: MediaController): Boolean =
            c.playbackState?.state == PlaybackState.STATE_PLAYING

        // Prefer playing with metadata, then any playing, then paused-with-media.
        return list.firstOrNull { isPlaying(it) && hasMedia(it) }
            ?: list.firstOrNull { isPlaying(it) }
            ?: list.firstOrNull { hasMedia(it) && isActiveOrPaused(it) }
            ?: list.firstOrNull { hasMedia(it) }
    }

    private fun isActiveOrPaused(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        return s == PlaybackState.STATE_PLAYING ||
            s == PlaybackState.STATE_PAUSED ||
            s == PlaybackState.STATE_BUFFERING ||
            s == PlaybackState.STATE_CONNECTING
    }

    private fun emit(controller: MediaController?) {
        if (controller == null) {
            onChange(null)
            return
        }
        val md = controller.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            // Playing with no title yet — still hide until we have something to show.
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (!playing) {
                onChange(null)
                return
            }
            onChange(
                NowPlayingInfo(
                    title = controller.packageName.substringAfterLast('.'),
                    artist = null,
                    isPlaying = true,
                    packageName = controller.packageName,
                ),
            )
            return
        }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val state = controller.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING
        // Hide when stopped / none — only show playing or paused-with-media.
        if (state == PlaybackState.STATE_STOPPED ||
            state == PlaybackState.STATE_NONE ||
            state == PlaybackState.STATE_ERROR
        ) {
            onChange(null)
            return
        }
        if (!playing && state != PlaybackState.STATE_PAUSED &&
            state != PlaybackState.STATE_CONNECTING
        ) {
            onChange(null)
            return
        }
        onChange(
            NowPlayingInfo(
                title = title.trim(),
                artist = artist?.trim()?.takeIf { it.isNotEmpty() },
                isPlaying = playing,
                packageName = controller.packageName,
            ),
        )
    }

    companion object {
        private const val TAG = "NowPlaying"
    }
}
