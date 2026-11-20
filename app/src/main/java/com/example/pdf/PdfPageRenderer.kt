package com.example.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfPageRenderer {

    private val renderMutex = Mutex()

    // Keep thumbnails bounded to ~16MB in memory so large PDFs never trigger OOM.
    private val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun clearCache() {
        thumbnailCache.evictAll()
    }

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 900,
        useCache: Boolean = true
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${pageIndex}_$targetWidthPx"
        if (useCache) {
            thumbnailCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }
        }

        renderMutex.withLock {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                            val width = targetWidthPx.coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            if (useCache && targetWidthPx <= 600) {
                                thumbnailCache.put(cacheKey, bitmap)
                            }
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    suspend fun renderPageAtDpi(
        file: File,
        pageIndex: Int,
        dpi: Int
    ): Bitmap = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(pageIndex in 0 until renderer.pageCount) {
                        "رقم الصفحة غير صالح: ${pageIndex + 1}"
                    }
                    renderer.openPage(pageIndex).use { page ->
                        val scale = dpi / 72f
                        val width = (page.width * scale).toInt().coerceIn(200, 3200)
                        val height = (page.height * scale).toInt().coerceIn(200, 4400)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )
                        bitmap
                    }
                }
            }
        }
    }
}
