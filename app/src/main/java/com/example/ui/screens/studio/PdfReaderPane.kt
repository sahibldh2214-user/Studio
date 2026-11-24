package com.example.ui.screens.studio

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.PdfViewerState
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.AmberVault
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary

@Composable
fun PdfReaderPane(
    pdfState: PdfViewerState,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onCycleZoom: () -> Unit,
    onPickPdfFromFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_PAGE_DOWN -> { onNextPage(); true }
                    KeyEvent.KEYCODE_PAGE_UP -> { onPrevPage(); true }
                    else -> false
                }
            } else false
        },
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        val activePdf = pdfState.activePdf
        if (activePdf == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = "PDF Reader",
                        tint = AmberVault,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "TV PDF Document Reader",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Read local, PC-transferred, LAN, or Cloud PDFs with D-pad page flip & 10-foot zoom.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    VaultFocusPill(
                        onClick = onPickPdfFromFiles,
                        selected = true,
                        accentColor = AmberVault,
                        modifier = Modifier.testTag("pick_pdf_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = AmberVault, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse PDFs", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePdf.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page ${pdfState.currentPage + 1} / ${pdfState.pageCount.coerceAtLeast(1)}  ·  Zoom ${(pdfState.zoomScale * 100).toInt()}%",
                            style = MonoTextStyle,
                            color = AmberVault
                        )
                    }

                    VaultFocusPill(
                        onClick = onPrevPage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("pdf_prev_page")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = onNextPage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("pdf_next_page")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    VaultFocusPill(
                        onClick = onCycleZoom,
                        selected = pdfState.zoomScale > 1.05f,
                        accentColor = AmberVault,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("pdf_zoom_button")
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom PDF", tint = AmberVault, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${(pdfState.zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0E131D)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pdfState.isLoading -> CircularProgressIndicator(color = AmberVault)
                        pdfState.errorMessage != null -> Text(
                            text = pdfState.errorMessage,
                            color = CoralAlert,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        pdfState.renderedBitmap != null -> {
                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(8.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Image(
                                    bitmap = pdfState.renderedBitmap.asImageBitmap(),
                                    contentDescription = "PDF Page ${pdfState.currentPage + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(
                                        fraction = (0.85f * pdfState.zoomScale).coerceAtMost(1.0f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
