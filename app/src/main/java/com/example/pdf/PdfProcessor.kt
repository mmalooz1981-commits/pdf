package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.CompressionLevel
import com.example.data.ImageFormatOption
import com.example.data.PageEditItem
import com.example.data.PageScopeMode
import com.example.data.PdfOperationResult
import com.example.data.SignaturePlacement
import com.example.data.WatermarkConfig
import com.example.data.WatermarkPosition
import com.example.data.WatermarkType
import com.example.data.formatFileSizeArabic
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PdfProcessor(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private fun normalizeDigitsAndCommas(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '،', ';', '؛' -> sb.append(',')
                '–', '—' -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parsePageRanges(rangesInput: String, totalPages: Int): List<IntRange> {
        val cleaned = normalizeDigitsAndCommas(rangesInput)
        val tokens = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "يرجى إدخال نطاق صفحات واحد على الأقل (مثال: 1-5، 8-12)" }

        return tokens.map { token ->
            val parts = token.split("-").map { it.trim() }.filter { it.isNotEmpty() }
            require(parts.size in 1..2) { "صيغة النطاق غير صحيحة: $token" }
            val start = parts[0].toIntOrNull()
                ?: throw IllegalArgumentException("رقم صفحة غير صالح: ${parts[0]}")
            val end = if (parts.size == 2) {
                parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("رقم صفحة غير صالح: ${parts[1]}")
            } else {
                start
            }
            require(start in 1..totalPages && end in start..totalPages) {
                "النطاق ($start-$end) خارج عدد صفحات الملف ($totalPages)"
            }
            start..end
        }
    }

    fun parseUniquePageNumbers(input: String, totalPages: Int): List<Int> {
        val ranges = parsePageRanges(input, totalPages)
        return ranges.flatMap { it.toList() }.distinct()
    }

    fun inspectPdf(file: File): Pair<Int, Boolean> {
        return try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.numberOfPages to doc.isEncrypted
            }
        } catch (e: InvalidPasswordException) {
            0 to true
        }
    }

    // 1. Advanced Split: Multi-Ranges
    fun splitByRanges(
        inputFile: File,
        rangesText: String,
        saveAsZip: Boolean,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        onProgress(0.05f, "جاري تحليل ملف PDF...")
        val outDir = PdfFileHelper.getOutputDir(context)
        val partFiles = mutableListOf<File>()
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            val ranges = parsePageRanges(rangesText, source.numberOfPages)
            ranges.forEachIndexed { idx, range ->
                onProgress(
                    0.1f + (0.7f * (idx + 1) / ranges.size),
                    "جاري إنشاء الجزء ${idx + 1} من ${ranges.size} (صفحات ${range.first}-${range.last})..."
                )
                val partFile = File(outDir, "${cleanBase}_Part_${idx + 1}_p${range.first}-${range.last}.pdf")
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { partDoc ->
                    for (pageNum in range) {
                        partDoc.importPage(source.getPage(pageNum - 1))
                    }
                    partDoc.save(partFile)
                }
                partFiles.add(partFile)
            }
        }

        if (partFiles.size == 1 && !saveAsZip) {
            onProgress(1f, "تم التقسيم بنجاح")
            val single = partFiles.first()
            return PdfOperationResult(
                primaryOutputFile = single,
                suggestedFileName = single.name,
                mimeType = "application/pdf",
                title = "تم استخراج النطاق بنجاح",
                summary = "تم إنشاء ملف PDF واحد بحجم ${formatFileSizeArabic(single.length())}",
                additionalFiles = partFiles
            )
        }

        onProgress(0.88f, "جاري ضغط الأجزاء في أرشيف ZIP...")
        val zipFile = File(outDir, "${cleanBase}_split_${System.currentTimeMillis()}.zip")
        zipFiles(partFiles, zipFile)
        onProgress(1f, "اكتمل التقسيم بنجاح")

        return PdfOperationResult(
            primaryOutputFile = zipFile,
            suggestedFileName = "${cleanBase}_مقسم.zip",
            mimeType = "application/zip",
            title = "تم تقسيم الملف إلى ${partFiles.size} أجزاء",
            summary = "تم إنشاء ${partFiles.size} ملفات PDF مستقلة بحجم إجمالي ${formatFileSizeArabic(zipFile.length())}. يمكنك حفظها كملف ZIP أو كملفات منفصلة في مجلد.",
            additionalFiles = partFiles,
            isPdfOutput = false
        )
    }

    // 2. Split Every Page
    fun splitEveryPage(
        inputFile: File,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val partFiles = mutableListOf<File>()

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            val total = source.numberOfPages
            for (i in 0 until total) {
                onProgress(
                    0.1f + (0.75f * (i + 1) / total),
                    "جاري فصل الصفحة ${i + 1} من $total..."
                )
                val partFile = File(outDir, "${cleanBase}_Page_${i + 1}.pdf")
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { singleDoc ->
                    singleDoc.importPage(source.getPage(i))
                    singleDoc.save(partFile)
                }
                partFiles.add(partFile)
            }
        }

        onProgress(0.9f, "جاري تجميع الصفحات في ملف ZIP...")
        val zipFile = File(outDir, "${cleanBase}_all_pages_${System.currentTimeMillis()}.zip")
        zipFiles(partFiles, zipFile)
        onProgress(1f, "اكتمل فصل جميع الصفحات")

        return PdfOperationResult(
            primaryOutputFile = zipFile,
            suggestedFileName = "${cleanBase}_كل_صفحة.zip",
            mimeType = "application/zip",
            title = "تم إنشاء ${partFiles.size} ملف مستقل",
            summary = "تم فصل كل صفحة في ملف PDF مستقل (${formatFileSizeArabic(zipFile.length())}).",
            additionalFiles = partFiles,
            isPdfOutput = false
        )
    }

    // 3. Split Equal Parts
    fun splitEqualParts(
        inputFile: File,
        partsCount: Int,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(partsCount >= 2) { "يجب أن يكون عدد الأجزاء 2 على الأقل" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val partFiles = mutableListOf<File>()

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            val total = source.numberOfPages
            val actualParts = min(partsCount, total)
            val pagesPerPart = ceil(total.toDouble() / actualParts).toInt()

            var currentStart = 0
            var partIndex = 1
            while (currentStart < total) {
                val endExclusive = min(currentStart + pagesPerPart, total)
                onProgress(
                    0.1f + (0.75f * endExclusive / total),
                    "جاري إنشاء الجزء $partIndex (صفحات ${currentStart + 1}-$endExclusive)..."
                )
                val partFile = File(outDir, "${cleanBase}_EqualPart_$partIndex.pdf")
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { partDoc ->
                    for (p in currentStart until endExclusive) {
                        partDoc.importPage(source.getPage(p))
                    }
                    partDoc.save(partFile)
                }
                partFiles.add(partFile)
                currentStart = endExclusive
                partIndex++
            }
        }

        onProgress(0.92f, "جاري إنشاء ملف ZIP...")
        val zipFile = File(outDir, "${cleanBase}_equal_parts_${System.currentTimeMillis()}.zip")
        zipFiles(partFiles, zipFile)
        onProgress(1f, "تم التقسيم المتساوي بنجاح")

        return PdfOperationResult(
            primaryOutputFile = zipFile,
            suggestedFileName = "${cleanBase}_أجزاء_متساوية.zip",
            mimeType = "application/zip",
            title = "تم التقسيم إلى ${partFiles.size} أجزاء متساوية",
            summary = "حجم الأرشيف الناتج: ${formatFileSizeArabic(zipFile.length())}",
            additionalFiles = partFiles,
            isPdfOutput = false
        )
    }

    // 4. Extract Specific Pages
    fun extractPages(
        inputFile: File,
        pageNumbers: List<Int>,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(pageNumbers.isNotEmpty()) { "يرجى اختيار صفحة واحدة على الأقل للاستخراج" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_extracted_${System.currentTimeMillis()}.pdf")

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { result ->
                pageNumbers.forEachIndexed { idx, pageNum ->
                    require(pageNum in 1..source.numberOfPages) {
                        "رقم الصفحة $pageNum غير موجود في الملف"
                    }
                    onProgress(
                        0.15f + (0.75f * (idx + 1) / pageNumbers.size),
                        "جاري استخراج الصفحة $pageNum..."
                    )
                    result.importPage(source.getPage(pageNum - 1))
                }
                result.save(outFile)
            }
        }

        onProgress(1f, "تم استخراج الصفحات بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_مستخرج.pdf",
            mimeType = "application/pdf",
            title = "تم استخراج ${pageNumbers.size} صفحة بنجاح",
            summary = "الحجم: ${formatFileSizeArabic(outFile.length())} • الصفحات: ${pageNumbers.joinToString("، ")}"
        )
    }

    // 5. Merge Unlimited PDFs
    fun mergePdfs(
        inputFiles: List<File>,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(inputFiles.size >= 2) { "يرجى اختيار ملفين PDF على الأقل للدمج" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val outFile = File(outDir, "merged_${System.currentTimeMillis()}.pdf")

        onProgress(0.15f, "جاري تجهيز ${inputFiles.size} ملفات للدمج...")
        val merger = PDFMergerUtility()
        inputFiles.forEachIndexed { idx, file ->
            merger.addSource(file)
            onProgress(0.15f + (0.4f * (idx + 1) / inputFiles.size), "جاري إضافة الملف ${idx + 1} من ${inputFiles.size}...")
        }

        onProgress(0.65f, "جاري دمج الصفحات وحفظ الملف النهائي...")
        FileOutputStream(outFile).use { fos ->
            merger.destinationStream = fos
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        }

        val pageCount = inspectPdf(outFile).first
        onProgress(1f, "تم الدمج بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "ملف_مدمج_${pageCount}_صفحة.pdf",
            mimeType = "application/pdf",
            title = "تم دمج ${inputFiles.size} ملفات بنجاح",
            summary = "إجمالي الصفحات: $pageCount صفحة • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 6. Organize Pages: Reorder + Rotate + Delete
    fun organizePages(
        inputFile: File,
        pageEdits: List<PageEditItem>,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val keptPages = pageEdits.filterNot { it.isDeleted }
        require(keptPages.isNotEmpty()) { "لا يمكن حذف جميع صفحات الملف" }

        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_organized_${System.currentTimeMillis()}.pdf")

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { result ->
                keptPages.forEachIndexed { idx, edit ->
                    onProgress(
                        0.1f + (0.8f * (idx + 1) / keptPages.size),
                        "جاري معالجة الصفحة ${idx + 1} من ${keptPages.size}..."
                    )
                    val imported = result.importPage(source.getPage(edit.originalPageIndex))
                    val existingRot = source.getPage(edit.originalPageIndex).rotation
                    imported.rotation = (existingRot + edit.rotationDegrees) % 360
                }
                result.save(outFile)
            }
        }

        val deletedCount = pageEdits.count { it.isDeleted }
        onProgress(1f, "تم حفظ التعديلات بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_منظم.pdf",
            mimeType = "application/pdf",
            title = "تم تنظيم الصفحات بنجاح",
            summary = "الصفحات المتبقية: ${keptPages.size} • المحذوفة: $deletedCount • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 7. Convert PDF to Images (PNG / JPG with DPI & Quality)
    suspend fun convertPdfToImages(
        inputFile: File,
        renderer: PdfPageRenderer,
        format: ImageFormatOption,
        dpi: Int,
        jpegQuality: Int,
        scopeMode: PageScopeMode,
        customPagesText: String,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val totalPages = renderer.getPageCount(inputFile)
        val targetPages = when (scopeMode) {
            PageScopeMode.ALL -> (1..totalPages).toList()
            PageScopeMode.SINGLE -> {
                val p = normalizeDigitsAndCommas(customPagesText).trim().toIntOrNull()
                    ?: throw IllegalArgumentException("يرجى إدخال رقم صفحة صحيح")
                require(p in 1..totalPages) { "رقم الصفحة يجب أن يكون بين 1 و $totalPages" }
                listOf(p)
            }
            PageScopeMode.CUSTOM_RANGE -> parseUniquePageNumbers(customPagesText, totalPages)
        }

        val outDir = File(PdfFileHelper.getOutputDir(context), "img_export_${System.currentTimeMillis()}").apply { mkdirs() }
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val exportedFiles = mutableListOf<File>()
        val compressFormat = if (format == ImageFormatOption.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        targetPages.forEachIndexed { idx, pageNum ->
            onProgress(
                0.08f + (0.78f * (idx + 1) / targetPages.size),
                "جاري تحويل الصفحة $pageNum (${idx + 1} من ${targetPages.size}) بدقة $dpi DPI..."
            )
            val bmp = renderer.renderPageAtDpi(inputFile, pageNum - 1, dpi)
            val imgFile = File(outDir, "${cleanBase}_page_${pageNum}.${format.extension}")
            FileOutputStream(imgFile).use { fos ->
                bmp.compress(compressFormat, jpegQuality.coerceIn(50, 100), fos)
            }
            bmp.recycle()
            exportedFiles.add(imgFile)
        }

        if (exportedFiles.size == 1) {
            val single = exportedFiles.first()
            onProgress(1f, "تم التحويل بنجاح")
            return PdfOperationResult(
                primaryOutputFile = single,
                suggestedFileName = single.name,
                mimeType = format.mimeType,
                title = "تم تحويل الصفحة إلى صورة ${format.extension.uppercase()}",
                summary = "الدقة: $dpi DPI • الحجم: ${formatFileSizeArabic(single.length())}",
                additionalFiles = exportedFiles,
                previewImageFiles = exportedFiles,
                isPdfOutput = false
            )
        }

        onProgress(0.92f, "جاري تجميع الصور في ملف ZIP...")
        val zipFile = File(PdfFileHelper.getOutputDir(context), "${cleanBase}_images_${System.currentTimeMillis()}.zip")
        zipFiles(exportedFiles, zipFile)
        onProgress(1f, "تم تحويل جميع الصفحات إلى صور")

        return PdfOperationResult(
            primaryOutputFile = zipFile,
            suggestedFileName = "${cleanBase}_صور_${format.extension}.zip",
            mimeType = "application/zip",
            title = "تم تحويل ${exportedFiles.size} صفحة إلى صور",
            summary = "الصيغة: ${format.extension.uppercase()} • الدقة: $dpi DPI • الحجم: ${formatFileSizeArabic(zipFile.length())}",
            additionalFiles = exportedFiles,
            previewImageFiles = exportedFiles,
            isPdfOutput = false
        )
    }

    // 8. Watermark (Arabic RTL Text & Image, Opacity, Rotation, 6 Positions including Tiled)
    fun addWatermark(
        inputFile: File,
        config: WatermarkConfig,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_watermarked_${System.currentTimeMillis()}.pdf")

        val watermarkBitmap: Bitmap = when (config.type) {
            WatermarkType.TEXT -> {
                require(config.text.isNotBlank()) { "يرجى إدخال نص العلامة المائية" }
                renderArabicTextWatermarkBitmap(config.text, config.fontSizeSp, config.colorArgb)
            }
            WatermarkType.IMAGE -> {
                val imgFile = config.imageFile
                    ?: throw IllegalArgumentException("يرجى اختيار صورة أو شعار للعلامة المائية")
                BitmapFactory.decodeFile(imgFile.absolutePath)
                    ?: throw IllegalArgumentException("تعذر قراءة صورة العلامة المائية")
            }
        }

        try {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val targetPages = when (config.pageScope) {
                    PageScopeMode.ALL -> (1..doc.numberOfPages).toSet()
                    PageScopeMode.SINGLE, PageScopeMode.CUSTOM_RANGE ->
                        parseUniquePageNumbers(config.customPagesText, doc.numberOfPages).toSet()
                }

                val pdImage = LosslessFactory.createFromImage(doc, watermarkBitmap)
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = config.opacity.coerceIn(0.05f, 1f)
                    strokingAlphaConstant = config.opacity.coerceIn(0.05f, 1f)
                }

                val total = doc.numberOfPages
                for (i in 0 until total) {
                    val pageNumber = i + 1
                    if (pageNumber !in targetPages) continue
                    onProgress(
                        0.1f + (0.8f * pageNumber / total),
                        "جاري تطبيق العلامة المائية على الصفحة $pageNumber من $total..."
                    )
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    val aspect = watermarkBitmap.height.toFloat() / watermarkBitmap.width.coerceAtLeast(1)

                    val drawWidth = if (config.type == WatermarkType.TEXT) {
                        (box.width * 0.58f).coerceAtMost(watermarkBitmap.width * 0.72f)
                    } else {
                        box.width * (config.imageScalePercent / 100f).coerceIn(0.1f, 0.85f)
                    }
                    val drawHeight = drawWidth * aspect
                    val angleRad = Math.toRadians((-config.rotationDegrees).toDouble())

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { stream ->
                        stream.setGraphicsStateParameters(gs)

                        if (config.position == WatermarkPosition.TILED) {
                            val tileW = drawWidth * 0.55f
                            val tileH = drawHeight * 0.55f
                            val stepX = max(tileW * 1.45f, box.width / 3f)
                            val stepY = max(tileH * 1.85f, box.height / 4f)
                            var cy = stepY * 0.6f
                            while (cy < box.height) {
                                var cx = stepX * 0.5f
                                while (cx < box.width) {
                                    drawRotatedImage(stream, pdImage, cx, cy, tileW, tileH, angleRad)
                                    cx += stepX
                                }
                                cy += stepY
                            }
                        } else {
                            val margin = 48f
                            val (cx, cy) = when (config.position) {
                                WatermarkPosition.CENTER -> (box.width / 2f) to (box.height / 2f)
                                WatermarkPosition.TOP_LEFT -> (margin + drawWidth / 2f) to (box.height - margin - drawHeight / 2f)
                                WatermarkPosition.TOP_RIGHT -> (box.width - margin - drawWidth / 2f) to (box.height - margin - drawHeight / 2f)
                                WatermarkPosition.BOTTOM_LEFT -> (margin + drawWidth / 2f) to (margin + drawHeight / 2f)
                                WatermarkPosition.BOTTOM_RIGHT -> (box.width - margin - drawWidth / 2f) to (margin + drawHeight / 2f)
                                WatermarkPosition.TILED -> (box.width / 2f) to (box.height / 2f)
                            }
                            drawRotatedImage(stream, pdImage, cx, cy, drawWidth, drawHeight, angleRad)
                        }
                    }
                }
                doc.save(outFile)
            }
        } finally {
            watermarkBitmap.recycle()
        }

        onProgress(1f, "تمت إضافة العلامة المائية بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_علامة_مائية.pdf",
            mimeType = "application/pdf",
            title = "تمت إضافة العلامة المائية بنجاح",
            summary = "تم حفظ نسخة جديدة دون تعديل الملف الأصلي • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 9. Multi-Signature Engine (Multiple signatures per page, position, scale, rotation)
    fun applySignatures(
        inputFile: File,
        placements: List<SignaturePlacement>,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(placements.isNotEmpty()) { "يرجى إضافة توقيع واحد على الأقل على الصفحة" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_signed_${System.currentTimeMillis()}.pdf")

        val decodedBitmaps = mutableMapOf<String, Bitmap>()
        try {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val pdImageMap = mutableMapOf<String, PDImageXObject>()
                for (placement in placements) {
                    if (!pdImageMap.containsKey(placement.signatureFilePath)) {
                        val bmp = BitmapFactory.decodeFile(placement.signatureFilePath)
                            ?: throw IllegalArgumentException("تعذر تحميل صورة التوقيع")
                        decodedBitmaps[placement.signatureFilePath] = bmp
                        pdImageMap[placement.signatureFilePath] = LosslessFactory.createFromImage(doc, bmp)
                    }
                }

                val total = doc.numberOfPages
                for (pageIdx in 0 until total) {
                    val pagePlacements = placements.filter { it.pageIndex == -1 || it.pageIndex == pageIdx }
                    if (pagePlacements.isEmpty()) continue
                    onProgress(
                        0.15f + (0.75f * (pageIdx + 1) / total),
                        "جاري ختم التوقيعات على الصفحة ${pageIdx + 1} من $total..."
                    )
                    val page = doc.getPage(pageIdx)
                    val box = page.mediaBox

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { stream ->
                        for (pl in pagePlacements) {
                            val pdImg = pdImageMap[pl.signatureFilePath] ?: continue
                            val bmp = decodedBitmaps[pl.signatureFilePath] ?: continue
                            val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                            val w = (box.width * pl.widthFraction).coerceIn(40f, box.width * 0.9f)
                            val h = w * aspect
                            val cx = box.width * pl.xFraction.coerceIn(0.05f, 0.95f)
                            // Convert Compose top-down yFraction to PDF bottom-up Y coordinate
                            val cy = box.height * (1f - pl.yFraction.coerceIn(0.05f, 0.95f))
                            val angleRad = Math.toRadians((-pl.rotationDegrees).toDouble())
                            drawRotatedImage(stream, pdImg, cx, cy, w, h, angleRad)
                        }
                    }
                }
                doc.save(outFile)
            }
        } finally {
            decodedBitmaps.values.forEach { it.recycle() }
        }

        onProgress(1f, "تمت إضافة التوقيعات بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_موقع.pdf",
            mimeType = "application/pdf",
            title = "تم توقيع المستند بنجاح",
            summary = "تم تطبيق ${placements.size} توقيع • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 10. Signature Background Removal (removes white/light paper background cleanly)
    fun removeLightBackground(source: Bitmap, threshold: Int = 215): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            if (a == 0) continue
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (luminance >= threshold) {
                pixels[i] = Color.TRANSPARENT
            } else if (luminance > threshold - 45) {
                val factor = (threshold - luminance) / 45f
                val newAlpha = (a * factor).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(newAlpha, r, g, b)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    // 11. Compress PDF (recompresses embedded heavy images + optimizes streams)
    fun compressPdf(
        inputFile: File,
        level: CompressionLevel,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_compressed_${System.currentTimeMillis()}.pdf")
        val originalSize = inputFile.length()

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val total = doc.numberOfPages
            for (i in 0 until total) {
                onProgress(
                    0.1f + (0.75f * (i + 1) / total),
                    "جاري ضغط عناصر الصفحة ${i + 1} من $total..."
                )
                val page: PDPage = doc.getPage(i)
                val resources = page.resources ?: continue
                for (name: COSName in resources.xObjectNames) {
                    val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                    if (xObj is PDImageXObject) {
                        runCatching {
                            val rawBmp = xObj.image ?: return@runCatching
                            val scaled = downscaleIfNeeded(rawBmp, level.maxDimPx)
                            val jpegBytes = ByteArrayOutputStream().use { baos ->
                                scaled.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                                baos.toByteArray()
                            }
                            val reEncoded = JPEGFactory.createFromByteArray(doc, jpegBytes)
                            resources.put(name, reEncoded)
                            if (scaled !== rawBmp) scaled.recycle()
                            rawBmp.recycle()
                        }
                    }
                }
            }
            onProgress(0.92f, "جاري حفظ الملف المضغوط...")
            doc.save(outFile)
        }

        val newSize = outFile.length()
        val savedPercent = if (originalSize > 0) {
            ((originalSize - newSize).toDouble() / originalSize * 100.0).toInt()
        } else 0
        val savingsText = if (savedPercent > 0) {
            "تم تقليل الحجم بنسبة $savedPercent% (${formatFileSizeArabic(originalSize)} ← ${formatFileSizeArabic(newSize)})"
        } else {
            "الملف محسن بالفعل • الحجم النهائي: ${formatFileSizeArabic(newSize)}"
        }

        onProgress(1f, "اكتمل الضغط")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_مضغوط.pdf",
            mimeType = "application/pdf",
            title = "اكتمل ضغط ملف PDF",
            summary = savingsText
        )
    }

    // 12. Protect PDF with Password (AES-128)
    fun protectPdf(
        inputFile: File,
        userPassword: String,
        ownerPassword: String,
        allowPrinting: Boolean,
        allowCopying: Boolean,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(userPassword.isNotBlank()) { "يرجى إدخال كلمة مرور لفتح الملف" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_protected_${System.currentTimeMillis()}.pdf")

        onProgress(0.25f, "جاري تشفير المستند...")
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val permission = AccessPermission().apply {
                setCanPrint(allowPrinting)
                setCanExtractContent(allowCopying)
                setCanModify(false)
            }
            val effectiveOwner = ownerPassword.ifBlank { userPassword }
            val policy = StandardProtectionPolicy(effectiveOwner, userPassword, permission).apply {
                encryptionKeyLength = 128
                permissions = permission
            }
            doc.protect(policy)
            onProgress(0.8f, "جاري حفظ النسخة المحمية...")
            doc.save(outFile)
        }

        onProgress(1f, "تمت حماية الملف بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_محمي.pdf",
            mimeType = "application/pdf",
            title = "تمت حماية الملف بكلمة مرور",
            summary = "تشفير AES-128 • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 13. Unlock Password-Protected PDF
    fun unlockPdf(
        inputFile: File,
        password: String,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(password.isNotBlank()) { "يرجى إدخال كلمة المرور الحالية للملف" }
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_unlocked_${System.currentTimeMillis()}.pdf")

        onProgress(0.3f, "جاري التحقق من كلمة المرور وفك الحماية...")
        try {
            PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                onProgress(0.8f, "جاري حفظ النسخة بدون كلمة مرور...")
                doc.save(outFile)
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException("كلمة المرور غير صحيحة. يرجى التأكد وإعادة المحاولة.")
        }

        onProgress(1f, "تم فك حماية الملف بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}_مفتوح.pdf",
            mimeType = "application/pdf",
            title = "تم فك حماية الملف بنجاح",
            summary = "تم حفظ نسخة غير محمية بكلمة مرور • الحجم: ${formatFileSizeArabic(outFile.length())}"
        )
    }

    // 14. Extract All Text & Embedded Images
    fun extractTextAndImages(
        inputFile: File,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        val outDir = File(PdfFileHelper.getOutputDir(context), "extracted_${System.currentTimeMillis()}").apply { mkdirs() }
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val extractedImages = mutableListOf<File>()
        val extractedTextBuilder = StringBuilder()

        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            val total = doc.numberOfPages
            for (i in 0 until total) {
                onProgress(
                    0.1f + (0.75f * (i + 1) / total),
                    "جاري استخراج النصوص والصور من الصفحة ${i + 1} من $total..."
                )
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val pageText = runCatching { stripper.getText(doc).trim() }.getOrDefault("")
                if (pageText.isNotEmpty()) {
                    extractedTextBuilder.append("──── صفحة ${i + 1} ────\n")
                    extractedTextBuilder.append(pageText).append("\n\n")
                }

                val resources = doc.getPage(i).resources ?: continue
                var imgIndexOnPage = 1
                for (name in resources.xObjectNames) {
                    val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                    if (xObj is PDImageXObject) {
                        runCatching {
                            val bmp = xObj.image ?: return@runCatching
                            val imgFile = File(outDir, "${cleanBase}_p${i + 1}_img${imgIndexOnPage++}.png")
                            FileOutputStream(imgFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            bmp.recycle()
                            extractedImages.add(imgFile)
                        }
                    }
                }
            }
        }

        val fullText = extractedTextBuilder.toString().ifBlank {
            "لم يتم العثور على نص قابل للنسخ المباشر (قد يكون الملف ممسوحاً ضوئياً — جرب أداة تحويل PDF إلى Word مع OCR السحابي)."
        }
        val textFile = File(outDir, "${cleanBase}_extracted_text.txt")
        textFile.writeText(fullText, Charsets.UTF_8)

        val allOutputs = listOf(textFile) + extractedImages
        val primaryOutput: File
        val suggestedName: String
        val mime: String
        if (extractedImages.isEmpty()) {
            primaryOutput = textFile
            suggestedName = "${cleanBase}_نص_مستخرج.txt"
            mime = "text/plain"
        } else {
            val zipFile = File(PdfFileHelper.getOutputDir(context), "${cleanBase}_text_images_${System.currentTimeMillis()}.zip")
            zipFiles(allOutputs, zipFile)
            primaryOutput = zipFile
            suggestedName = "${cleanBase}_نصوص_وصور.zip"
            mime = "application/zip"
        }

        onProgress(1f, "اكتمل الاستخراج بنجاح")
        return PdfOperationResult(
            primaryOutputFile = primaryOutput,
            suggestedFileName = suggestedName,
            mimeType = mime,
            title = "تم استخراج النصوص والصور",
            summary = "تم استخراج ${fullText.length} حرف و ${extractedImages.size} صورة مضمنة.",
            additionalFiles = allOutputs,
            previewImageFiles = extractedImages,
            previewText = fullText,
            isPdfOutput = false
        )
    }

    fun extractEmbeddedImagesByPage(inputFile: File, targetPages: List<Int>): Map<Int, List<ByteArray>> {
        val result = mutableMapOf<Int, MutableList<ByteArray>>()
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                for (pageNum in targetPages) {
                    if (pageNum !in 1..doc.numberOfPages) continue
                    val resources = doc.getPage(pageNum - 1).resources ?: continue
                    val list = mutableListOf<ByteArray>()
                    for (name in resources.xObjectNames) {
                        val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            runCatching {
                                val bmp = xObj.image ?: return@runCatching
                                if (bmp.width >= 48 && bmp.height >= 48) {
                                    val scaled = downscaleIfNeeded(bmp, 900)
                                    val bytes = ByteArrayOutputStream().use { baos ->
                                        scaled.compress(Bitmap.CompressFormat.PNG, 95, baos)
                                        baos.toByteArray()
                                    }
                                    list.add(bytes)
                                    if (scaled !== bmp) scaled.recycle()
                                }
                                bmp.recycle()
                            }
                        }
                    }
                    if (list.isNotEmpty()) result[pageNum] = list
                }
            }
        }
        return result
    }

    fun extractPlainTextByPage(inputFile: File, targetPages: List<Int>): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            for (pageNum in targetPages) {
                if (pageNum !in 1..doc.numberOfPages) continue
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                map[pageNum] = runCatching { stripper.getText(doc).trim() }.getOrDefault("")
            }
        }
        return map
    }

    private fun renderArabicTextWatermarkBitmap(
        text: String,
        fontSizeSp: Float,
        colorArgb: Int
    ): Bitmap {
        val typeface = runCatching {
            ResourcesCompat.getFont(context, R.font.cairo)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = (fontSizeSp * 2.2f).coerceIn(36f, 180f)
            this.typeface = typeface
        }

        val maxTextWidth = 900
        val measuredWidth = paint.measureText(text).toInt().coerceIn(120, maxTextWidth)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, measuredWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

        val padding = 24
        val bmpWidth = (layout.width + padding * 2).coerceAtLeast(64)
        val bmpHeight = (layout.height + padding * 2).coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    private fun drawRotatedImage(
        stream: PDPageContentStream,
        image: PDImageXObject,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        angleRad: Double
    ) {
        stream.saveGraphicsState()
        stream.transform(Matrix.getTranslateInstance(centerX, centerY))
        if (angleRad != 0.0) {
            stream.transform(Matrix.getRotateInstance(angleRad, 0f, 0f))
        }
        stream.drawImage(image, -width / 2f, -height / 2f, width, height)
        stream.restoreGraphicsState()
    }

    private fun downscaleIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest
        val newW = (src.width * ratio).toInt().coerceAtLeast(1)
        val newH = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun zipFiles(files: List<File>, destinationZip: File) {
        ZipOutputStream(FileOutputStream(destinationZip)).use { zip ->
            val buffer = ByteArray(16 * 1024)
            for (file in files) {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        zip.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
