package com.example.ui.screens.studio

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.MediaPlaybackController
import com.example.media.StudioMediaState
import com.example.model.FileEntry
import com.example.ui.components.TvMediaSeekBar
import com.example.ui.components.VaultFocusCard
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicDeckPane(
    controller: MediaPlaybackController,
    mediaState: StudioMediaState,
    onPickMusicFromFiles: () -> Unit,
    onSelectQueueItem: (FileEntry) -> Unit,
    onPickDeviceAudio: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val activeAudio = mediaState.activeAudio
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Surface(
        modifier = modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && activeAudio != null) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { controller.toggleAudioPlayPause(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { controller.audioPlayer.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { controller.audioPlayer.pause(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { controller.seekAudioBy(10_000L); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { controller.seekAudioBy(-10_000L); true }
                    else -> false
                }
            } else false
        },
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        if (activeAudio == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Music Player",
                        tint = IndigoAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hi-Fi Music Deck",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Play MP3, FLAC, WAV, AAC & OGG with TV remote D-pad play/pause & ±10s seek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultFocusPill(
                            onClick = onPickMusicFromFiles,
                            selected = true,
                            accentColor = IndigoAccent,
                            modifier = Modifier.testTag("pick_music_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Vault", style = MaterialTheme.typography.labelLarge)
                        }
                        VaultFocusPill(
                            onClick = onPickDeviceAudio,
                            accentColor = IndigoAccent,
                            modifier = Modifier.testTag("pick_device_audio_button")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Audio", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.linearGradient(listOf(IndigoAccent, ElectricCyan)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Music Track",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeAudio.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusText = mediaState.lastAudioSeekFeedback?.let { "Seek $it" }
                            ?: if (mediaState.isAudioBuffering) "Buffering..."
                            else if (mediaState.isAudioPlaying) "Playing · Audio Deck"
                            else "Paused"
                        Text(
                            text = statusText,
                            style = MonoTextStyle,
                            color = IndigoAccent
                        )
                    }
                    VaultFocusPill(
                        onClick = onPickDeviceAudio,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = "Pick audio file", tint = IndigoAccent, modifier = Modifier.size(16.dp))
                    }
                }

                mediaState.audioError?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = CoralAlert)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Animated frequency visualizer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 38.dp else 60.dp)
                ) {
                    val bars = 24
                    val gap = 6.dp.toPx()
                    val barW = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(3f)
                    for (i in 0 until bars) {
                        val amplitude = if (mediaState.isAudioPlaying) {
                            (0.32f + 0.65f * ((sin(phase + i * 0.55f) + 1f) * 0.5f))
                        } else {
                            0.18f
                        }
                        val barH = size.height * amplitude
                        val x = i * (barW + gap)
                        val y = size.height - barH
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(ElectricCyan, IndigoAccent)),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // TV-Remote Compatible SeekBar (Left/Right D-pad ±10s + Touch Scrub)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(MediaPlaybackController.formatDuration(mediaState.audioPositionMs), style = MonoTextStyle, color = TextSecondary)
                    TvMediaSeekBar(
                        positionMs = mediaState.audioPositionMs,
                        bufferedMs = mediaState.audioBufferedMs,
                        durationMs = mediaState.audioDurationMs,
                        accentColor = IndigoAccent,
                        onSeekToMs = { controller.seekAudioTo(it) },
                        onSeekDeltaMs = { controller.seekAudioBy(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("audio_seek_bar")
                    )
                    Text(MediaPlaybackController.formatDuration(mediaState.audioDurationMs), style = MonoTextStyle, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultFocusPill(
                        onClick = { controller.toggleAudioShuffle() },
                        selected = mediaState.audioShuffle,
                        accentColor = IndigoAccent,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (mediaState.audioShuffle) IndigoAccent else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    VaultFocusPill(
                        onClick = { controller.prevAudio() },
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = { controller.seekAudioBy(-10_000L) },
                        contentPadding = PaddingValues(10.dp),
                        modifier = Modifier.testTag("audio_rewind_10s")
                    ) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 10s", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    VaultFocusPill(
                        onClick = { controller.toggleAudioPlayPause() },
                        selected = true,
                        accentColor = IndigoAccent,
                        modifier = Modifier.testTag("music_play_pause_button"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (mediaState.isAudioPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (mediaState.isAudioPlaying) "Pause music" else "Play music",
                            tint = IndigoAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (mediaState.isAudioPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelLarge)
                    }
                    VaultFocusPill(
                        onClick = { controller.seekAudioBy(10_000L) },
                        contentPadding = PaddingValues(10.dp),
                        modifier = Modifier.testTag("audio_forward_10s")
                    ) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Forward 10s", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    VaultFocusPill(
                        onClick = { controller.nextAudio() },
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = { controller.toggleAudioRepeat() },
                        selected = mediaState.audioRepeatOne,
                        accentColor = IndigoAccent,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.RepeatOne, contentDescription = "Repeat track", tint = if (mediaState.audioRepeatOne) IndigoAccent else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                if (!compact && mediaState.audioQueue.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Up Next (${mediaState.audioQueue.size} tracks)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(mediaState.audioQueue, key = { it.id }) { track ->
                            val isCurrent = track.id == activeAudio.id
                            VaultFocusCard(
                                onClick = { onSelectQueueItem(track) },
                                selected = isCurrent,
                                accentColor = IndigoAccent,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = track.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) IndigoAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
