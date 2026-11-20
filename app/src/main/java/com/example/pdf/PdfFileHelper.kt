package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import com.example.R
import java.io.File
import java.io.FileOutputStream

object PdfFileHelper {

    fun getOutputDir(context: Context): File {
        return File(context.cacheDir, "outputs").apply { mkdirs() }
    }

    fun getSignaturesDir(context: Context): File {
        return File(context.filesDir, "signatures").apply { mkdirs() }
    }

    fun getWorkingCopiesDir(context: Context): File {
        return File(context.cacheDir, "working_pdfs").apply { mkdirs() }
    }

    fun queryFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path.orEmpty()).name.ifBlank { "document.pdf" }
        }
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
        }
        if (name.isNullOrBlank()) {
            name = DocumentFile.fromSingleUri(context, uri)?.name
        }
        return name?.ifBlank { "document.pdf" } ?: "document.pdf"
    }

    fun copyUriToTempFile(context: Context, uri: Uri, prefix: String = "src_", extension: String = ".pdf"): File {
        val dir = getWorkingCopiesDir(context)
        val outFile = File(dir, "${prefix}${System.currentTimeMillis()}$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalStateException("تعذر قراءة الملف المحدد. تأكد من صلاحية الوصول للملف.")
        return outFile
    }

    fun copyFileToDestinationUri(context: Context, sourceFile: File, destinationUri: Uri) {
        context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
            sourceFile.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalStateException("تعذر الحفظ في المسار المختار.")
    }

    fun saveMultipleFilesToTreeUri(
        context: Context,
        files: List<File>,
        treeUri: Uri,
        mimeType: String
    ): Int {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("تعذر الوصول إلى المجلد المحدد.")
        var savedCount = 0
        for (file in files) {
            val targetDoc = rootDoc.createFile(mimeType, file.name) ?: continue
            copyFileToDestinationUri(context, file, targetDoc.uri)
            savedCount++
        }
        return savedCount
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Generates a genuine multi-page Arabic/English PDF document on device so the user can
     * immediately test viewing, splitting, signing, watermarking, merging, and Word OCR conversion.
     */
    fun generateSampleArabicPdf(context: Context): File {
        val outFile = File(getWorkingCopiesDir(context), "نموذج_موعد_اللوز_تقرير.pdf")
        val pdfDocument = PdfDocument()
        val cairoTypeface = runCatching {
            ResourcesCompat.getFont(context, R.font.cairo)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD

        val pagesContent = listOf(
            Triple(
                "تقرير موعد اللوز السنوي لإدارة الوثائق",
                "الصفحة الأولى • ملخص تنفيذي وجدول الأداء",
                listOf(
                    "يقدم هذا المستند نموذجاً عملياً لاختبار جميع أدوات تطبيق موعد اللوز بي دي اف.",
                    "يدعم التطبيق عرض الملفات، التقسيم المتقدم بعدة نطاقات، الدمج، التوقيع الإلكتروني،",
                    "إضافة العلامات المائية العربية، والضغط والحماية بكلمة مرور، والتحويل الذكي إلى Word."
                )
            ),
            Triple(
                "تفاصيل المهام والعمليات المنجزة",
                "الصفحة الثانية • خطة العمل والجدول الزمني",
                listOf(
                    "المرحلة الأولى: تنظيم الصفحات وإعادة ترتيبها وتدويرها بكل سهولة.",
                    "المرحلة الثانية: تحويل الصفحات إلى صور PNG و JPG عالية الدقة.",
                    "المرحلة الثالثة: توقيع العقود والمراسلات الرسمية وحفظ التوقيع لإعادة الاستخدام."
                )
            ),
            Triple(
                "الملاحق والملاحظات الختامية",
                "الصفحة الثالثة • التوثيق والاعتماد الرسمي",
                listOf(
                    "يمكنك الآن تجربة تقسيم هذا المستند بنطاقات مثل 1-2 أو استخراج صفحة محددة،",
                    "كما يمكنك تجربة إضافة علامة مائية شفافة أو توقيع متعدد في أسفل الصفحة.",
                    "تم إنشاء هذا الملف محلياً على جهازك بدقة طباعة عالية."
                )
            )
        )

        val pageWidth = 595
        val pageHeight = 842

        pagesContent.forEachIndexed { idx, (title, subtitle, lines) ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Warm ivory background
            canvas.drawColor(Color.parseColor("#FFFDF9"))

            // Top burgundy header strip
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8C1D2C")
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 92f, headerPaint)

            // Gold accent line
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D4A373")
            }
            canvas.drawRect(0f, 92f, pageWidth.toFloat(), 98f, accentPaint)

            // Header brand text
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
                typeface = cairoTypeface
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("موعد اللوز بي دي اف • PDF MASTER", pageWidth - 36f, 56f, brandPaint)

            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2B1B17")
                textSize = 22f
                typeface = cairoTypeface
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(title, pageWidth - 36f, 155f, titlePaint)

            // Subtitle
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8A5A2B")
                textSize = 14f
                typeface = cairoTypeface
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(subtitle, pageWidth - 36f, 185f, subPaint)

            // Body paragraphs
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38302E")
                textSize = 14f
                typeface = cairoTypeface
                textAlign = Paint.Align.RIGHT
            }
            var y = 235f
            for (line in lines) {
                canvas.drawText(line, pageWidth - 36f, y, bodyPaint)
                y += 30f
            }

            // Sample summary table on page 1
            if (idx == 0) {
                val tableTop = 360f
                val rowH = 38f
                val left = 45f
                val right = pageWidth - 45f
                val col1 = right - 180f
                val col2 = left + 160f

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#C7B5AA")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f
                }
                val headerRowBg = Paint().apply { color = Color.parseColor("#F4E6E8") }
                canvas.drawRect(left, tableTop, right, tableTop + rowH, headerRowBg)

                val rows = listOf(
                    Triple("الميزة", "الحالة", "الدقة"),
                    Triple("تقسيم ودمج الملفات", "جاهز للاستخدام", "100%"),
                    Triple("التوقيع والعلامة المائية", "يدعم العربية RTL", "عالية"),
                    Triple("التحويل إلى Word OCR", "مدعوم بالذكاء السحابي", "احترافية")
                )

                rows.forEachIndexed { rIdx, (c1, c2, c3) ->
                    val top = tableTop + rIdx * rowH
                    val bottom = top + rowH
                    canvas.drawRect(left, top, right, bottom, borderPaint)
                    canvas.drawLine(col1, top, col1, bottom, borderPaint)
                    canvas.drawLine(col2, top, col2, bottom, borderPaint)

                    val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (rIdx == 0) Color.parseColor("#8C1D2C") else Color.parseColor("#2B1B17")
                        textSize = 13f
                        typeface = cairoTypeface
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText(c1, right - 12f, top + 25f, cellPaint)
                    canvas.drawText(c2, col1 - 12f, top + 25f, cellPaint)
                    canvas.drawText(c3, col2 - 12f, top + 25f, cellPaint)
                }
            }

            // Signature box on page 3
            if (idx == 2) {
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#D4A373")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRect(340f, 620f, 550f, 720f, boxPaint)
                canvas.drawText("مساحة التوقيع والاعتماد:", 540f, 610f, subPaint)
            }

            // Footer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7D6E66")
                textSize = 11f
                typeface = cairoTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("صفحة ${idx + 1} من ${pagesContent.size}  •  موعد اللوز بي دي اف", pageWidth / 2f, pageHeight - 30f, footerPaint)

            pdfDocument.finishPage(page)
        }

        FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return outFile
    }
}
