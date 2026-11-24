package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.FileEntry
import com.example.model.FileTypeClassifier
import com.example.model.MediaKind
import com.example.model.SourceType
import com.example.ui.theme.AmberVault
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaKindStyle(
    val icon: ImageVector,
    val accent: Color,
    val badgeLabel: String
)

fun styleForMediaKind(kind: MediaKind): MediaKindStyle = when (kind) {
    MediaKind.FOLDER -> MediaKindStyle(Icons.Filled.Folder, ElectricCyan, "FOLDER")
    MediaKind.VIDEO -> MediaKindStyle(Icons.Filled.VideoFile, Color(0xFF38BDF8), "VIDEO")
    MediaKind.AUDIO -> MediaKindStyle(Icons.Filled.AudioFile, IndigoAccent, "MUSIC")
    MediaKind.PDF -> MediaKindStyle(Icons.Filled.PictureAsPdf, AmberVault, "PDF")
    MediaKind.IMAGE -> MediaKindStyle(Icons.Filled.Image, Color(0xFFA855F7), "PHOTO")
    MediaKind.ARCHIVE -> MediaKindStyle(Icons.Filled.FolderZip, EmeraldLive, "ARCHIVE")
    MediaKind.APK -> MediaKindStyle(Icons.Filled.Android, Color(0xFF84CC16), "APK")
    MediaKind.DOCUMENT -> MediaKindStyle(Icons.Filled.Description, Color(0xFF94A3B8), "DOC")
    MediaKind.OTHER -> MediaKindStyle(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF64748B), "FILE")
}

@Composable
fun FileGridCard(
    entry: FileEntry,
    onOpen: () -> Unit,
    onMoreActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = styleForMediaKind(entry.mediaKind)
    VaultFocusCard(
        onClick = onOpen,
        onLongClick = onMoreActions,
        accentColor = style.accent,
        modifier = modifier.testTag("file_grid_card_${entry.name}"),
        contentPadding = PaddingValues(14.dp)
    ) { isFocused ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.accent.copy(alpha = if (isFocused) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.mediaKind == MediaKind.IMAGE &&
                    (entry.sourceType == SourceType.LOCAL_VAULT || entry.sourceType == SourceType.SAF_MOUNT)
                ) {
                    AsyncImage(
                        model = entry.uriString,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = style.badgeLabel,
                        tint = style.accent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = style.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.accent
                    )
                }

                IconButton(
                    onClick = onMoreActions,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .focusProperties { canFocus = false }
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "File options for ${entry.name}",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val subtitle = if (entry.isDirectory) {
                if (entry.sourceType == SourceType.LOCAL_VAULT) "${entry.sizeBytes} items" else "Directory"
            } else {
                FileTypeClassifier.formatSize(entry.sizeBytes)
            }
            Text(
                text = subtitle,
                style = MonoTextStyle,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileListRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onMoreActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = styleForMediaKind(entry.mediaKind)
    VaultFocusCard(
        onClick = onOpen,
        onLongClick = onMoreActions,
        accentColor = style.accent,
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_list_row_${entry.name}"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = style.badgeLabel,
                    tint = style.accent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val dateText = formatTimestamp(entry.lastModifiedMs)
                val sizeText = if (entry.isDirectory) "Folder" else FileTypeClassifier.formatSize(entry.sizeBytes)
                Text(
                    text = "$sizeText  ·  $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onMoreActions,
                modifier = Modifier
                    .size(48.dp)
                    .focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Options for ${entry.name}",
                    tint = TextSecondary
                )
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
}
