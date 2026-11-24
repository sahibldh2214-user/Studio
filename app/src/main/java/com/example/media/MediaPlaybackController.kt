package com.example.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.model.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoResizeModeLabel(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom")
}

data class StudioMediaState(
    val activeVideo: FileEntry? = null,
    val videoQueue: List<FileEntry> = emptyList(),
    val isVideoPlaying: Boolean = false,
    val isVideoBuffering: Boolean = false,
    val videoPositionMs: Long = 0L,
    val videoBufferedMs: Long = 0L,
    val videoDurationMs: Long = 0L,
    val videoSpeed: Float = 1.0f,
    val videoResizeMode: VideoResizeModeLabel = VideoResizeModeLabel.FIT,
    val videoError: String? = null,
    val lastVideoSeekFeedback: String? = null,

    val activeAudio: FileEntry? = null,
    val audioQueue: List<FileEntry> = emptyList(),
    val isAudioPlaying: Boolean = false,
    val isAudioBuffering: Boolean = false,
    val audioPositionMs: Long = 0L,
    val audioBufferedMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val audioShuffle: Boolean = false,
    val audioRepeatOne: Boolean = false,
    val audioError: String? = null,
    val lastAudioSeekFeedback: String? = null
)

class MediaPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(10_000L)
        .build()

    val audioPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(10_000L)
        .build()

    private var videoSession: MediaSession? = runCatching {
        MediaSession.Builder(appContext, videoPlayer).setId("vault_video_session").build()
    }.getOrNull()

    private var audioSession: MediaSession? = runCatching {
        MediaSession.Builder(appContext, audioPlayer).setId("vault_audio_session").build()
    }.getOrNull()

    private val _state = MutableStateFlow(StudioMediaState())
    val state: StateFlow<StudioMediaState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var videoSeekBannerJob: Job? = null
    private var audioSeekBannerJob: Job? = null

    init {
        videoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isVideoPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(isVideoBuffering = playbackState == Player.STATE_BUFFERING)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(
                        isVideoPlaying = false,
                        isVideoBuffering = false,
                        videoError = error.localizedMessage ?: "Video playback error"
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = videoPlayer.currentMediaItemIndex
                val entry = _state.value.videoQueue.getOrNull(idx)
                if (entry != null) _state.update { it.copy(activeVideo = entry, videoError = null) }
            }
        })

        audioPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isAudioPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(isAudioBuffering = playbackState == Player.STATE_BUFFERING)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(
                        isAudioPlaying = false,
                        isAudioBuffering = false,
                        audioError = error.localizedMessage ?: "Audio playback error"
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = audioPlayer.currentMediaItemIndex
                val entry = _state.value.audioQueue.getOrNull(idx)
                if (entry != null) _state.update { it.copy(activeAudio = entry, audioError = null) }
            }
        })

        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                _state.update { current ->
                    current.copy(
                        videoPositionMs = videoPlayer.currentPosition.coerceAtLeast(0L),
                        videoBufferedMs = videoPlayer.bufferedPosition.coerceAtLeast(0L),
                        videoDurationMs = videoPlayer.duration.coerceAtLeast(0L),
                        audioPositionMs = audioPlayer.currentPosition.coerceAtLeast(0L),
                        audioBufferedMs = audioPlayer.bufferedPosition.coerceAtLeast(0L),
                        audioDurationMs = audioPlayer.duration.coerceAtLeast(0L)
                    )
                }
                delay(250L)
            }
        }
    }

    fun playVideo(entry: FileEntry, playableUri: Uri, queue: List<Pair<FileEntry, Uri>>) {
        if (audioPlayer.isPlaying) audioPlayer.pause()

        val entries = queue.map { it.first }.ifEmpty { listOf(entry) }
        val uris = queue.map { it.second }.ifEmpty { listOf(playableUri) }
        val startIndex = entries.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)

        val mediaItems = entries.mapIndexed { idx, item ->
            MediaItem.Builder()
                .setUri(uris[idx])
                .setMediaId(item.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                .build()
        }

        videoPlayer.setMediaItems(mediaItems, startIndex, 0L)
        videoPlayer.prepare()
        videoPlayer.playWhenReady = true
        _state.update {
            it.copy(
                activeVideo = entry,
                videoQueue = entries,
                isVideoPlaying = true,
                videoError = null
            )
        }
    }

    fun playAudio(entry: FileEntry, playableUri: Uri, queue: List<Pair<FileEntry, Uri>>) {
        val entries = queue.map { it.first }.ifEmpty { listOf(entry) }
        val uris = queue.map { it.second }.ifEmpty { listOf(playableUri) }
        val startIndex = entries.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)

        val mediaItems = entries.mapIndexed { idx, item ->
            MediaItem.Builder()
                .setUri(uris[idx])
                .setMediaId(item.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                .build()
        }

        audioPlayer.setMediaItems(mediaItems, startIndex, 0L)
        audioPlayer.prepare()
        audioPlayer.playWhenReady = true
        _state.update {
            it.copy(
                activeAudio = entry,
                audioQueue = entries,
                isAudioPlaying = true,
                audioError = null
            )
        }
    }

    fun toggleVideoPlayPause() {
        if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play()
    }

    fun seekVideoBy(deltaMs: Long) {
        val dur = videoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val next = (videoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        videoPlayer.seekTo(next)
        _state.update { it.copy(videoPositionMs = next) }
        showVideoSeekBanner(deltaMs)
    }

    fun seekVideoTo(positionMs: Long) {
        val dur = videoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, dur)
        videoPlayer.seekTo(clamped)
        _state.update { it.copy(videoPositionMs = clamped) }
    }

    private fun showVideoSeekBanner(deltaMs: Long) {
        val label = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        _state.update { it.copy(lastVideoSeekFeedback = label) }
        videoSeekBannerJob?.cancel()
        videoSeekBannerJob = scope.launch {
            delay(1100L)
            _state.update { it.copy(lastVideoSeekFeedback = null) }
        }
    }

    fun nextVideo() {
        if (videoPlayer.hasNextMediaItem()) videoPlayer.seekToNextMediaItem()
    }

    fun prevVideo() {
        if (videoPlayer.hasPreviousMediaItem()) videoPlayer.seekToPreviousMediaItem()
    }

    fun cycleVideoSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        val idx = speeds.indexOf(_state.value.videoSpeed)
        val next = speeds[(idx + 1) % speeds.size]
        videoPlayer.playbackParameters = PlaybackParameters(next)
        _state.update { it.copy(videoSpeed = next) }
    }

    fun cycleVideoResizeMode() {
        val modes = VideoResizeModeLabel.entries
        val next = modes[(_state.value.videoResizeMode.ordinal + 1) % modes.size]
        _state.update { it.copy(videoResizeMode = next) }
    }

    fun toggleAudioPlayPause() {
        if (audioPlayer.isPlaying) audioPlayer.pause() else audioPlayer.play()
    }

    fun seekAudioBy(deltaMs: Long) {
        val dur = audioPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val next = (audioPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        audioPlayer.seekTo(next)
        _state.update { it.copy(audioPositionMs = next) }
        showAudioSeekBanner(deltaMs)
    }

    fun seekAudioTo(positionMs: Long) {
        val dur = audioPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, dur)
        audioPlayer.seekTo(clamped)
        _state.update { it.copy(audioPositionMs = clamped) }
    }

    private fun showAudioSeekBanner(deltaMs: Long) {
        val label = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        _state.update { it.copy(lastAudioSeekFeedback = label) }
        audioSeekBannerJob?.cancel()
        audioSeekBannerJob = scope.launch {
            delay(1100L)
            _state.update { it.copy(lastAudioSeekFeedback = null) }
        }
    }

    fun nextAudio() {
        if (audioPlayer.hasNextMediaItem()) audioPlayer.seekToNextMediaItem()
    }

    fun prevAudio() {
        if (audioPlayer.hasPreviousMediaItem()) audioPlayer.seekToPreviousMediaItem()
    }

    fun toggleAudioShuffle() {
        val next = !_state.value.audioShuffle
        audioPlayer.shuffleModeEnabled = next
        _state.update { it.copy(audioShuffle = next) }
    }

    fun toggleAudioRepeat() {
        val next = !_state.value.audioRepeatOne
        audioPlayer.repeatMode = if (next) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        _state.update { it.copy(audioRepeatOne = next) }
    }

    fun release() {
        tickerJob?.cancel()
        videoSeekBannerJob?.cancel()
        audioSeekBannerJob?.cancel()
        runCatching { videoSession?.release() }
        runCatching { audioSession?.release() }
        videoSession = null
        audioSession = null
        videoPlayer.release()
        audioPlayer.release()
    }

    companion object {
        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "00:00"
            val totalSec = ms / 1000
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
