package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ObsidianBorder

@Composable
fun TvMediaSeekBar(
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    onSeekToMs: (Long) -> Unit,
    onSeekDeltaMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
    stepMs: Long = 10_000L
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 5.dp,
        label = "seekTrackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isFocused) 10.dp else 7.dp,
        label = "seekThumbRadius"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            onSeekDeltaMs(-stepMs)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            onSeekDeltaMs(stepMs)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) accentColor.copy(alpha = 0.14f) else Color.Transparent,
        border = if (isFocused) BorderStroke(2.dp, accentColor) else BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val tapFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSeekToMs((tapFraction * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSeekToMs((dragFraction * safeDuration).toLong())
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val trackPx = trackHeight.toPx()
                val cy = size.height / 2f
                val corner = CornerRadius(trackPx / 2f, trackPx / 2f)

                // Background track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(0f, cy - trackPx / 2f),
                    size = Size(size.width, trackPx),
                    cornerRadius = corner
                )

                // Buffered indicator
                if (bufferedFraction > fraction) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.35f),
                        topLeft = Offset(0f, cy - trackPx / 2f),
                        size = Size(size.width * bufferedFraction, trackPx),
                        cornerRadius = corner
                    )
                }

                // Active progress track
                val activeW = size.width * fraction
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, cy - trackPx / 2f),
                    size = Size(activeW, trackPx),
                    cornerRadius = corner
                )

                // Scrubber thumb
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius.toPx(),
                    center = Offset(activeW.coerceIn(0f, size.width), cy)
                )
                drawCircle(
                    color = accentColor,
                    radius = (thumbRadius.toPx() * 0.55f),
                    center = Offset(activeW.coerceIn(0f, size.width), cy)
                )
            }
        }
    }
}
