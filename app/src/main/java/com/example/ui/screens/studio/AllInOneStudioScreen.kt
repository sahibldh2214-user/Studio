package com.example.ui.screens.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.media.MediaPlaybackController
import com.example.media.StudioMediaState
import com.example.model.FileCategoryFilter
import com.example.model.FileEntry
import com.example.ui.PdfViewerState
import com.example.ui.StudioPaneMode
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.AmberVault
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.TextSecondary

@Composable
fun AllInOneStudioScreen(
    paneMode: StudioPaneMode,
    mediaController: MediaPlaybackController,
    mediaState: StudioMediaState,
    pdfState: PdfViewerState,
    onChangePaneMode: (StudioPaneMode) -> Unit,
    onBrowseCategory: (FileCategoryFilter) -> Unit,
    onPickDeviceVideo: () -> Unit,
    onPickDeviceAudio: () -> Unit,
    onOpenQueueFile: (FileEntry) -> Unit,
    onPrevPdfPage: () -> Unit,
    onNextPdfPage: () -> Unit,
    onCyclePdfZoom: () -> Unit,
    onToggleVideoFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header + Studio Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "All-in-One Media & PDF Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Unified Video Theatre, Hi-Fi Music Deck & PDF Reader in a single workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioPaneMode.entries.forEach { mode ->
                val (icon, accent) = when (mode) {
                    StudioPaneMode.ALL_IN_ONE -> Icons.Filled.Dashboard to ElectricCyan
                    StudioPaneMode.VIDEO -> Icons.Filled.Movie to ElectricCyan
                    StudioPaneMode.MUSIC -> Icons.Filled.MusicNote to IndigoAccent
                    StudioPaneMode.PDF -> Icons.Filled.PictureAsPdf to AmberVault
                }
                VaultFocusPill(
                    onClick = { onChangePaneMode(mode) },
                    selected = paneMode == mode,
                    accentColor = accent,
                    modifier = Modifier.testTag("studio_mode_${mode.name}"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(mode.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (paneMode) {
                StudioPaneMode.ALL_IN_ONE -> {
                    // Widescreen TV 3-Panel All-in-One Cockpit: Video Theatre + Music Deck + PDF Reader
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VideoTheatrePane(
                            controller = mediaController,
                            mediaState = mediaState,
                            onPickVideoFromFiles = { onBrowseCategory(FileCategoryFilter.VIDEO) },
                            onPickDeviceVideo = onPickDeviceVideo,
                            onToggleFullscreen = onToggleVideoFullscreen,
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                        )
                        MusicDeckPane(
                            controller = mediaController,
                            mediaState = mediaState,
                            onPickMusicFromFiles = { onBrowseCategory(FileCategoryFilter.AUDIO) },
                            onSelectQueueItem = onOpenQueueFile,
                            onPickDeviceAudio = onPickDeviceAudio,
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                        )
                        PdfReaderPane(
                            pdfState = pdfState,
                            onPrevPage = onPrevPdfPage,
                            onNextPage = onNextPdfPage,
                            onCycleZoom = onCyclePdfZoom,
                            onPickPdfFromFiles = { onBrowseCategory(FileCategoryFilter.PDF) },
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                        )
                    }
                }

                StudioPaneMode.VIDEO -> {
                    VideoTheatrePane(
                        controller = mediaController,
                        mediaState = mediaState,
                        onPickVideoFromFiles = { onBrowseCategory(FileCategoryFilter.VIDEO) },
                        onPickDeviceVideo = onPickDeviceVideo,
                        onToggleFullscreen = onToggleVideoFullscreen,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                StudioPaneMode.MUSIC -> {
                    MusicDeckPane(
                        controller = mediaController,
                        mediaState = mediaState,
                        onPickMusicFromFiles = { onBrowseCategory(FileCategoryFilter.AUDIO) },
                        onSelectQueueItem = onOpenQueueFile,
                        onPickDeviceAudio = onPickDeviceAudio,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                StudioPaneMode.PDF -> {
                    PdfReaderPane(
                        pdfState = pdfState,
                        onPrevPage = onPrevPdfPage,
                        onNextPage = onNextPdfPage,
                        onCycleZoom = onCyclePdfZoom,
                        onPickPdfFromFiles = { onBrowseCategory(FileCategoryFilter.PDF) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
