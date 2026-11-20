package com.example.data

import java.io.File

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class PdfToolRoute(val route: String) {
    HOME("home"),
    VIEWER("viewer"),
    SPLIT("split"),
    TO_IMAGES("to_images"),
    SIGNATURE("signature"),
    WATERMARK("watermark"),
    TO_WORD("to_word"),
    MERGE("merge"),
    ORGANIZE("organize"),
    COMPRESS("compress"),
    SECURITY("security"),
    EXTRACT_ASSETS("extract_assets")
}

data class ActivePdfDocument(
    val localFile: File,
    val displayName: String,
    val sourceUri: String?,
    val sizeBytes: Long,
    val pageCount: Int,
    val isPasswordProtected: Boolean = false
)

data class RecentPdfRecord(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedMillis: Long
)

data class SavedSignatureRecord(
    val id: String,
    val name: String,
    val filePath: String,
    val createdAtMillis: Long
)

data class PdfOperationResult(
    val primaryOutputFile: File,
    val suggestedFileName: String,
    val mimeType: String,
    val title: String,
    val summary: String,
    val additionalFiles: List<File> = emptyList(),
    val previewImageFiles: List<File> = emptyList(),
    val previewText: String? = null,
    val isPdfOutput: Boolean = mimeType == "application/pdf"
)

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(val progress: Float, val stage: String) : ProcessingState
    data class Success(val result: PdfOperationResult) : ProcessingState
    data class Error(val message: String) : ProcessingState
}

enum class SplitMode {
    MULTI_RANGES,
    EVERY_PAGE,
    EQUAL_PARTS,
    EXTRACT_PAGES
}

enum class ImageFormatOption(val extension: String, val mimeType: String, val label: String) {
    PNG("png", "image/png", "PNG (جودة عالية بدون فقدان)"),
    JPEG("jpg", "image/jpeg", "JPG (حجم أصغر)")
}

enum class PageScopeMode {
    ALL,
    SINGLE,
    CUSTOM_RANGE
}

enum class WatermarkType {
    TEXT,
    IMAGE
}

enum class WatermarkPosition(val arabicLabel: String) {
    CENTER("الوسط"),
    TOP_RIGHT("أعلى اليمين"),
    TOP_LEFT("أعلى اليسار"),
    BOTTOM_RIGHT("أسفل اليمين"),
    BOTTOM_LEFT("أسفل اليسار"),
    TILED("تكرار على كامل الصفحة")
}

data class WatermarkConfig(
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "سري للغاية - موعد اللوز",
    val fontSizeSp: Float = 42f,
    val colorArgb: Int = 0xFF8C1D2C.toInt(),
    val opacity: Float = 0.35f,
    val rotationDegrees: Float = -35f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val imageFile: File? = null,
    val imageScalePercent: Float = 35f,
    val pageScope: PageScopeMode = PageScopeMode.ALL,
    val customPagesText: String = "1"
)

data class SignaturePlacement(
    val id: String,
    val signatureFilePath: String,
    val pageIndex: Int, // 0-based; -1 means apply to ALL pages
    val xFraction: Float = 0.62f, // 0..1 from left edge of PDF page
    val yFraction: Float = 0.78f, // 0..1 from top edge of PDF page
    val widthFraction: Float = 0.28f, // fraction of page width
    val rotationDegrees: Float = 0f
)

enum class CompressionLevel(val arabicLabel: String, val description: String, val jpegQuality: Int, val maxDimPx: Int) {
    LOW("ضغط خفيف", "يحافظ على أعلى دقة للصور", 85, 1800),
    MEDIUM("ضغط متوازن (موصى به)", "توازن ممتاز بين الحجم والوضوح", 65, 1300),
    HIGH("أقصى ضغط", "أصغر حجم ممكن للمشاركة السريعة", 42, 950)
}

data class PageEditItem(
    val originalPageIndex: Int, // 0-based
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val isDeleted: Boolean = false,
    val isSelectedForExtract: Boolean = false
)

data class MergeCandidate(
    val id: String,
    val localFile: File,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int
)

fun formatFileSizeArabic(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes بايت"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f ك.ب", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.2f م.ب", bytes / (1024.0 * 1024.0))
    }
}
