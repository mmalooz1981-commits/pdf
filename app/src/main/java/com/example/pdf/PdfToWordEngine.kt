package com.example.pdf

import android.content.Context
import android.util.Log
import com.example.data.PdfOperationResult
import com.example.data.formatFileSizeArabic
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "PdfToWordEngine"

class PdfToWordEngine(
    private val context: Context,
    private val pdfProcessor: PdfProcessor,
    private val pageRenderer: PdfPageRenderer
) {

    private val pageLayoutSchema = Schema.obj(
        properties = mapOf(
            "isRtl" to Schema.boolean(description = "True if the page is primarily Arabic or RTL."),
            "headerText" to Schema.string(description = "Header text at the top of the page if present.", nullable = true),
            "footerText" to Schema.string(description = "Footer text at the bottom of the page if present.", nullable = true),
            "blocks" to Schema.array(
                description = "Ordered visual blocks from top to bottom preserving original document structure.",
                items = Schema.obj(
                    properties = mapOf(
                        "type" to Schema.enumeration(
                            values = listOf("heading1", "heading2", "paragraph", "list_item", "table"),
                            description = "Structural block type."
                        ),
                        "text" to Schema.string(description = "Text content for non-table blocks.", nullable = true),
                        "alignment" to Schema.enumeration(
                            values = listOf("right", "center", "left"),
                            description = "Visual text alignment on the page."
                        ),
                        "colorHex" to Schema.string(description = "Approximate 6-char hex color without #, e.g. 8C1D2C.", nullable = true),
                        "fontSizePt" to Schema.integer(description = "Approximate font size in pt (10 to 26)."),
                        "isBold" to Schema.boolean(description = "Whether the text appears bold."),
                        "isRtl" to Schema.boolean(description = "True if this block is Arabic/RTL."),
                        "tableRows" to Schema.array(
                            description = "2D array of cell strings when type is table.",
                            items = Schema.array(items = Schema.string()),
                            nullable = true
                        )
                    ),
                    optionalProperties = listOf("text", "colorHex", "tableRows")
                )
            )
        ),
        optionalProperties = listOf("headerText", "footerText")
    )

    // Lazy initialization per Firebase AI Logic contract
    private val ocrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pageLayoutSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun convertPdfToDocx(
        inputFile: File,
        baseName: String,
        targetPages: List<Int>,
        useCloudAiOcr: Boolean,
        includeImages: Boolean,
        includeHeaderFooter: Boolean,
        onProgress: (Float, String) -> Unit
    ): PdfOperationResult {
        require(targetPages.isNotEmpty()) { "يرجى تحديد صفحة واحدة على الأقل للتحويل" }

        onProgress(0.05f, "جاري تجهيز الصفحات واستخراج الصور المضمنة...")
        val embeddedImagesMap = if (includeImages) {
            pdfProcessor.extractEmbeddedImagesByPage(inputFile, targetPages)
        } else {
            emptyMap()
        }

        val docxPages = mutableListOf<DocxPageModel>()
        val previewSummary = StringBuilder()

        if (useCloudAiOcr) {
            targetPages.forEachIndexed { idx, pageNum ->
                onProgress(
                    0.12f + (0.76f * idx / targetPages.size),
                    "جاري تحليل الصفحة $pageNum (${idx + 1} من ${targetPages.size}) عبر محرك OCR السحابي..."
                )
                val bitmap = pageRenderer.renderPage(
                    file = inputFile,
                    pageIndex = pageNum - 1,
                    targetWidthPx = 1100,
                    useCache = false
                ) ?: throw IllegalStateException("تعذر تصيير الصفحة $pageNum للتحليل البصري")

                try {
                    val promptText = """
                        You are a high-precision document layout & Arabic/English OCR converter for PDF to Microsoft Word (DOCX).
                        Analyze this PDF page image carefully and transcribe ALL text accurately without summarizing or omitting anything.
                        Preserve:
                        - Exact Arabic and English wording, numbers, and punctuation (including scanned text via OCR).
                        - Exact top-to-bottom reading order.
                        - Headings (heading1 / heading2), paragraphs, bulleted list items, and full multi-row tables with every cell intact.
                        - Page header (headerText) and footer (footerText) if visible.
                        - Text alignment (right for Arabic, center for centered titles, left for English), approximate fontSizePt, bold weight, and dominant text colorHex.
                        Return strictly valid JSON matching the schema.
                    """.trimIndent()

                    val response = withTimeout(60_000L) {
                        ocrModel.generateContent(
                            content {
                                image(bitmap)
                                text(promptText)
                            }
                        )
                    }

                    val jsonText = response.text
                    if (jsonText.isNullOrBlank()) {
                        throw IllegalStateException("أعاد محرك التحويل السحابي استجابة فارغة للصفحة $pageNum.")
                    }

                    val parsedPage = parsePageJson(
                        pageNumber = pageNum,
                        jsonText = jsonText,
                        embeddedImages = embeddedImagesMap[pageNum].orEmpty()
                    )
                    docxPages.add(parsedPage)
                    appendPreview(previewSummary, parsedPage)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Cloud OCR timed out on page $pageNum", e)
                    throw IllegalStateException("انتهت مهلة التحويل السحابي (60 ثانية) عند الصفحة $pageNum. يرجى المحاولة مجدداً أو اختيار نطاق صفحات أصغر.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Cloud OCR failed on page $pageNum", e)
                    throw IllegalStateException(
                        "تعذر إتمام التحويل السحابي للصفحة $pageNum: ${e.localizedMessage ?: "خطأ في الاتصال بالخدمة"}"
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        } else {
            // Local fast structured text + embedded image conversion
            onProgress(0.25f, "جاري استخراج النصوص والفقرات محلياً...")
            val textByPage = pdfProcessor.extractPlainTextByPage(inputFile, targetPages)
            targetPages.forEachIndexed { idx, pageNum ->
                onProgress(
                    0.3f + (0.55f * (idx + 1) / targetPages.size),
                    "جاري تنسيق الصفحة $pageNum من ${targetPages.size}..."
                )
                val rawText = textByPage[pageNum].orEmpty()
                val pageIsRtl = containsArabic(rawText)
                val blocks = rawText
                    .split(Regex("\\n\\s*\\n"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { blockIdx, para ->
                        val singleLine = para.replace("\n", " ").trim()
                        val isHeading = blockIdx == 0 && singleLine.length <= 90
                        val blockRtl = containsArabic(singleLine) || pageIsRtl
                        DocxBlock(
                            type = if (isHeading) "heading1" else "paragraph",
                            text = singleLine,
                            alignment = if (blockRtl) "right" else "left",
                            fontSizePt = if (isHeading) 18 else 12,
                            isBold = isHeading,
                            isRtl = blockRtl
                        )
                    }

                val model = DocxPageModel(
                    pageNumber = pageNum,
                    isRtl = pageIsRtl,
                    blocks = blocks,
                    embeddedImagesPng = embeddedImagesMap[pageNum].orEmpty()
                )
                docxPages.add(model)
                appendPreview(previewSummary, model)
            }
        }

        onProgress(0.92f, "جاري بناء ملف Word (.docx) مع الجداول والصور والتنسيق العربي...")
        val outDir = PdfFileHelper.getOutputDir(context)
        val cleanBase = baseName.removeSuffix(".pdf").ifBlank { "document" }
        val outFile = File(outDir, "${cleanBase}_${System.currentTimeMillis()}.docx")
        DocxBuilder.buildDocx(
            pages = docxPages,
            outputFile = outFile,
            includeHeaderFooter = includeHeaderFooter
        )

        val totalTables = docxPages.sumOf { p -> p.blocks.count { it.type == "table" } }
        val totalImages = docxPages.sumOf { it.embeddedImagesPng.size }
        val modeLabel = if (useCloudAiOcr) "محرك OCR السحابي" else "المحرك المحلي"

        onProgress(1f, "تم إنشاء ملف Word بنجاح")
        return PdfOperationResult(
            primaryOutputFile = outFile,
            suggestedFileName = "${cleanBase}.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            title = "تم تحويل PDF إلى Word بنجاح",
            summary = "$modeLabel • ${docxPages.size} صفحة • $totalTables جدول • $totalImages صورة • الحجم: ${formatFileSizeArabic(outFile.length())}",
            previewText = previewSummary.toString().ifBlank { "تم إنشاء مستند Word جاهز للحفظ والمشاركة." },
            isPdfOutput = false
        )
    }

    private fun parsePageJson(
        pageNumber: Int,
        jsonText: String,
        embeddedImages: List<ByteArray>
    ): DocxPageModel {
        val obj = JSONObject(jsonText)
        val pageRtl = obj.optBoolean("isRtl", true)
        val header = obj.optString("headerText", "").takeIf { it.isNotBlank() && it != "null" }
        val footer = obj.optString("footerText", "").takeIf { it.isNotBlank() && it != "null" }
        val blocksJson = obj.optJSONArray("blocks")
        val blocks = mutableListOf<DocxBlock>()

        if (blocksJson != null) {
            for (i in 0 until blocksJson.length()) {
                val b = blocksJson.optJSONObject(i) ?: continue
                val type = b.optString("type", "paragraph")
                val text = b.optString("text", "").takeIf { it != "null" }.orEmpty()
                val alignment = b.optString("alignment", if (pageRtl) "right" else "left")
                val colorHex = b.optString("colorHex", "").takeIf { it.isNotBlank() && it != "null" }
                val fontSize = b.optInt("fontSizePt", 12)
                val isBold = b.optBoolean("isBold", false)
                val blockRtl = b.optBoolean("isRtl", pageRtl)

                val tableRows = mutableListOf<List<String>>()
                val rowsArr = b.optJSONArray("tableRows")
                if (rowsArr != null) {
                    for (r in 0 until rowsArr.length()) {
                        val rowArr = rowsArr.optJSONArray(r) ?: continue
                        val rowCells = List(rowArr.length()) { c -> rowArr.optString(c, "") }
                        if (rowCells.isNotEmpty()) tableRows.add(rowCells)
                    }
                }

                blocks.add(
                    DocxBlock(
                        type = type,
                        text = text,
                        alignment = alignment,
                        colorHex = colorHex,
                        fontSizePt = fontSize,
                        isBold = isBold,
                        isRtl = blockRtl,
                        tableRows = tableRows
                    )
                )
            }
        }

        return DocxPageModel(
            pageNumber = pageNumber,
            headerText = header,
            footerText = footer,
            isRtl = pageRtl,
            blocks = blocks,
            embeddedImagesPng = embeddedImages
        )
    }

    private fun appendPreview(sb: StringBuilder, page: DocxPageModel) {
        sb.append("════ صفحة ${page.pageNumber} ════\n")
        if (!page.headerText.isNullOrBlank()) {
            sb.append("[رأس الصفحة] ${page.headerText}\n")
        }
        for (block in page.blocks) {
            when (block.type) {
                "table" -> {
                    sb.append("[جدول (${block.tableRows.size} صفوف)]\n")
                    block.tableRows.forEach { row ->
                        sb.append(" | ").append(row.joinToString(" | ")).append(" |\n")
                    }
                }
                "heading1" -> sb.append("\n■ ${block.text}\n")
                "heading2" -> sb.append("▪ ${block.text}\n")
                "list_item" -> sb.append("• ${block.text}\n")
                else -> if (block.text.isNotBlank()) sb.append("${block.text}\n")
            }
        }
        if (page.embeddedImagesPng.isNotEmpty()) {
            sb.append("[+${page.embeddedImagesPng.size} صورة مضمنة]\n")
        }
        if (!page.footerText.isNullOrBlank()) {
            sb.append("[تذييل] ${page.footerText}\n")
        }
        sb.append("\n")
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }
}
