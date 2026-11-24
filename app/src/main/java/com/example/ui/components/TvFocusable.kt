package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardFocused
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = ObsidianCard,
    focusedContainerColor: Color = ObsidianCardFocused,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focusScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> focusedContainerColor
            selected -> accentColor.copy(alpha = 0.16f)
            else -> containerColor
        },
        label = "focusBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> accentColor
            selected -> accentColor.copy(alpha = 0.65f)
            else -> ObsidianBorder
        },
        label = "focusBorder"
    )
    val borderWidth: Dp = if (isFocused) 3.dp else 1.dp

    Surface(
        modifier = modifier
            .scale(scale)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                            onLongClick?.invoke()
                            onLongClick != null
                        }
                        else -> false
                    }
                } else false
            }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { role = Role.Button }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource),
        shape = shape,
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = if (isFocused) 10.dp else 2.dp,
        shadowElevation = if (isFocused) 10.dp else 0.dp
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content(isFocused)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFocusPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            selected && isFocused -> accentColor
            selected -> accentColor.copy(alpha = 0.24f)
            isFocused -> ObsidianCardFocused
            else -> ObsidianCard
        },
        label = "pillBg"
    )
    val borderColor = when {
        isFocused -> accentColor
        selected -> accentColor.copy(alpha = 0.7f)
        else -> ObsidianBorder
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(if (isFocused) 3.dp else 1.dp, borderColor),
        shadowElevation = if (isFocused) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(isFocused)
        }
    }
}
