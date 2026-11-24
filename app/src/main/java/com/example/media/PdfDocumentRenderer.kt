package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfDocumentSession(
    private val context: Context
) {
    private val mutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pageCache = LinkedHashMap<Int, Bitmap>(8, 0.75f, true)

    var pageCount: Int = 0
        private set

    suspend fun open(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                closeInternal()
                val pfd = if (uri.scheme == "file") {
                    ParcelFileDescriptor.open(File(uri.path ?: error("Invalid file path")), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")
                        ?: error("Unable to open PDF descriptor")
                }
                val pdfRenderer = PdfRenderer(pfd)
                descriptor = pfd
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
                pageCount
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1440): Bitmap? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val r = renderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= r.pageCount) return@withContext null

                pageCache[pageIndex]?.let { cached ->
                    if (!cached.isRecycled) return@withContext cached
                }

                r.openPage(pageIndex).use { page ->
                    val width = targetWidthPx.coerceIn(640, 2048)
                    val height = ((width.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (pageCache.size >= 6) {
                        val eldestKey = pageCache.keys.firstOrNull()
                        if (eldestKey != null) {
                            pageCache.remove(eldestKey)
                        }
                    }
                    pageCache[pageIndex] = bitmap
                    bitmap
                }
            }
        }

    suspend fun close() {
        mutex.withLock { closeInternal() }
    }

    private fun closeInternal() {
        pageCache.clear()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
        pageCount = 0
    }
}
