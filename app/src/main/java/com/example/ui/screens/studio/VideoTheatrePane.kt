package com.example.ui.screens.studio

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.media.MediaPlaybackController
import com.example.media.StudioMediaState
import com.example.media.VideoResizeModeLabel
import com.example.ui.components.TvMediaSeekBar
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoTheatrePane(
    controller: MediaPlaybackController,
    mediaState: StudioMediaState,
    onPickVideoFromFiles: () -> Unit,
    onPickDeviceVideo: () -> Unit = {},
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) {
    val activeVideo = mediaState.activeVideo
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playPauseFocusRequester = remember { FocusRequester() }

    fun peekControls() {
        controlsVisible = true
        lastInteractionMs = System.currentTimeMillis()
    }

    // Auto-hide video HUD overlay after 4 seconds while playing; keep visible when paused
    LaunchedEffect(mediaState.isVideoPlaying, controlsVisible, lastInteractionMs) {
        if (mediaState.isVideoPlaying && controlsVisible) {
            delay(4000L)
            if (mediaState.isVideoPlaying) {
                controlsVisible = false
            }
        } else if (!mediaState.isVideoPlaying) {
            controlsVisible = true
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        if (activeVideo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "Video Theatre",
                        tint = ElectricCyan,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Video Theatre Ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Media3 ExoPlayer with TV remote D-pad play/pause, ±10s seek scrubbing, and speed controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultFocusPill(
                            onClick = onPickVideoFromFiles,
                            selected = true,
                            accentColor = ElectricCyan,
                            modifier = Modifier.testTag("pick_video_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Vault", style = MaterialTheme.typography.labelLarge)
                        }
                        VaultFocusPill(
                            onClick = onPickDeviceVideo,
                            accentColor = ElectricCyan,
                            modifier = Modifier.testTag("pick_device_video_button")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Video", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeVideo.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeVideo.sourceType.name} · ${mediaState.videoSpeed}x · ${mediaState.videoResizeMode.label}",
                            style = MonoTextStyle,
                            color = ElectricCyan
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaultFocusPill(
                            onClick = onPickDeviceVideo,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Open device video", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", style = MaterialTheme.typography.labelMedium)
                        }
                        VaultFocusPill(
                            onClick = onPickVideoFromFiles,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Switch video", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue (${mediaState.videoQueue.size})", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Video Surface with TV Remote D-Pad Key Handling & Auto-Hide HUD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (isFullscreen) Modifier else Modifier.aspectRatio(16f / 9f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            peekControls()
                        }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_SPACE,
                                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                        controller.toggleVideoPlayPause()
                                        peekControls()
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                        controller.videoPlayer.play()
                                        peekControls()
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                        controller.videoPlayer.pause()
                                        peekControls()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                        controller.seekVideoBy(10_000L)
                                        peekControls()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                        controller.seekVideoBy(-10_000L)
                                        peekControls()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        peekControls()
                                        false // allow D-pad traversal to controls below
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                keepScreenOn = true
                                player = controller.videoPlayer
                            }
                        },
                        update = { playerView ->
                            playerView.player = controller.videoPlayer
                            playerView.resizeMode = when (mediaState.videoResizeMode) {
                                VideoResizeModeLabel.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                VideoResizeModeLabel.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                VideoResizeModeLabel.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        }
                    )

                    if (mediaState.isVideoBuffering) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    mediaState.lastVideoSeekFeedback?.let { seekBadge ->
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = seekBadge,
                                style = MaterialTheme.typography.headlineSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }

                    mediaState.videoError?.let { err ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = CoralAlert.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Peek / Pause HUD Overlay inside the video frame
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (mediaState.isVideoPlaying) "D-Pad Center: Pause · Left/Right: ±10s" else "Paused · Press Center to Resume",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "${MediaPlaybackController.formatDuration(mediaState.videoPositionMs)} / ${MediaPlaybackController.formatDuration(mediaState.videoDurationMs)}",
                                    style = MonoTextStyle,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // TV-Remote Compatible SeekBar (Left/Right D-pad ±10s + Touch Scrub)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = MediaPlaybackController.formatDuration(mediaState.videoPositionMs),
                        style = MonoTextStyle,
                        color = TextSecondary
                    )
                    TvMediaSeekBar(
                        positionMs = mediaState.videoPositionMs,
                        bufferedMs = mediaState.videoBufferedMs,
                        durationMs = mediaState.videoDurationMs,
                        accentColor = ElectricCyan,
                        onSeekToMs = {
                            controller.seekVideoTo(it)
                            peekControls()
                        },
                        onSeekDeltaMs = {
                            controller.seekVideoBy(it)
                            peekControls()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .testTag("video_seek_bar")
                    )
                    Text(
                        text = MediaPlaybackController.formatDuration(mediaState.videoDurationMs),
                        style = MonoTextStyle,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Custom Play/Pause/Seek Transport Controls (FlowRow wraps cleanly in 3-pane TV cockpit)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultFocusPill(
                        onClick = { controller.prevVideo(); peekControls() },
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous video", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = { controller.seekVideoBy(-10_000L); peekControls() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("video_rewind_10s")
                    ) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 10 seconds", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-10s", style = MaterialTheme.typography.labelMedium)
                    }
                    VaultFocusPill(
                        onClick = { controller.toggleVideoPlayPause(); peekControls() },
                        selected = true,
                        accentColor = ElectricCyan,
                        modifier = Modifier
                            .focusRequester(playPauseFocusRequester)
                            .testTag("video_play_pause_button"),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (mediaState.isVideoPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (mediaState.isVideoPlaying) "Pause video" else "Play video",
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (mediaState.isVideoPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelLarge)
                    }
                    VaultFocusPill(
                        onClick = { controller.seekVideoBy(10_000L); peekControls() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("video_forward_10s")
                    ) {
                        Text("+10s", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.FastForward, contentDescription = "Forward 10 seconds", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = { controller.nextVideo(); peekControls() },
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next video", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = { controller.cycleVideoSpeed(); peekControls() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = "Playback speed", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${mediaState.videoSpeed}x", style = MaterialTheme.typography.labelMedium)
                    }
                    VaultFocusPill(
                        onClick = { controller.cycleVideoResizeMode(); peekControls() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect ratio", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(mediaState.videoResizeMode.label, style = MaterialTheme.typography.labelMedium)
                    }
                    if (onToggleFullscreen != null) {
                        VaultFocusPill(
                            onClick = { onToggleFullscreen(); peekControls() },
                            selected = isFullscreen,
                            accentColor = ElectricCyan,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("video_fullscreen_toggle")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen TV mode",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isFullscreen) "Exit" else "Full", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
