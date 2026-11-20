package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivePdfDocument
import com.example.data.CompressionLevel
import com.example.data.ImageFormatOption
import com.example.data.MergeCandidate
import com.example.data.PageEditItem
import com.example.data.PageScopeMode
import com.example.data.PdfOperationResult
import com.example.data.PreferencesRepository
import com.example.data.ProcessingState
import com.example.data.RecentPdfRecord
import com.example.data.SavedSignatureRecord
import com.example.data.SignaturePlacement
import com.example.data.SplitMode
import com.example.data.ThemeMode
import com.example.data.WatermarkConfig
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfPageRenderer
import com.example.pdf.PdfProcessor
import com.example.pdf.PdfToWordEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "PdfMasterViewModel"

class PdfMasterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = PreferencesRepository(appContext)
    val pageRenderer = PdfPageRenderer()
    private val pdfProcessor = PdfProcessor(appContext)
    private val pdfToWordEngine = PdfToWordEngine(appContext, pdfProcessor, pageRenderer)

    val themeMode: StateFlow<ThemeMode> = prefsRepo.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val recentFiles: StateFlow<List<RecentPdfRecord>> = prefsRepo.recentFilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedSignatures: StateFlow<List<SavedSignatureRecord>> = prefsRepo.savedSignaturesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activePdf = MutableStateFlow<ActivePdfDocument?>(null)
    val activePdf: StateFlow<ActivePdfDocument?> = _activePdf.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _mergeCandidates = MutableStateFlow<List<MergeCandidate>>(emptyList())
    val mergeCandidates: StateFlow<List<MergeCandidate>> = _mergeCandidates.asStateFlow()

    private var activeJob: Job? = null

    fun cycleThemeMode() {
        viewModelScope.launch {
            val next = when (themeMode.value) {
                ThemeMode.SYSTEM -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.SYSTEM
            }
            prefsRepo.setThemeMode(next)
        }
    }

    fun dismissProcessingState() {
        _processingState.value = ProcessingState.Idle
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _processingState.value = ProcessingState.Idle
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun showStatusMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun clearRecentHistory() {
        viewModelScope.launch { prefsRepo.clearRecentFiles() }
    }

    fun removeRecentRecord(uriString: String) {
        viewModelScope.launch { prefsRepo.removeRecentFile(uriString) }
    }

    fun loadPdfFromUri(uri: Uri, onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Running(0.15f, "جاري فتح ملف PDF...")
            try {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val doc = withContext(Dispatchers.IO) {
                    val displayName = PdfFileHelper.queryFileName(appContext, uri)
                    val localCopy = PdfFileHelper.copyUriToTempFile(appContext, uri)
                    val (pages, isEncrypted) = pdfProcessor.inspectPdf(localCopy)
                    val actualPages = if (!isEncrypted && pages == 0) {
                        runCatching { pageRenderer.getPageCount(localCopy) }.getOrDefault(0)
                    } else pages

                    ActivePdfDocument(
                        localFile = localCopy,
                        displayName = displayName,
                        sourceUri = uri.toString(),
                        sizeBytes = localCopy.length(),
                        pageCount = actualPages,
                        isPasswordProtected = isEncrypted
                    )
                }

                _activePdf.value = doc
                prefsRepo.addRecentFile(
                    RecentPdfRecord(
                        uriString = uri.toString(),
                        displayName = doc.displayName,
                        sizeBytes = doc.sizeBytes,
                        pageCount = doc.pageCount,
                        lastOpenedMillis = System.currentTimeMillis()
                    )
                )
                _processingState.value = ProcessingState.Idle
                onLoaded?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load PDF from URI", e)
                _processingState.value = ProcessingState.Error(
                    e.localizedMessage ?: "تعذر فتح ملف PDF المحدد."
                )
            }
        }
    }

    fun loadLocalPdfFile(file: File, displayName: String, onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    val (pages, isEncrypted) = pdfProcessor.inspectPdf(file)
                    ActivePdfDocument(
                        localFile = file,
                        displayName = displayName,
                        sourceUri = Uri.fromFile(file).toString(),
                        sizeBytes = file.length(),
                        pageCount = pages,
                        isPasswordProtected = isEncrypted
                    )
                }
                _activePdf.value = doc
                prefsRepo.addRecentFile(
                    RecentPdfRecord(
                        uriString = Uri.fromFile(file).toString(),
                        displayName = doc.displayName,
                        sizeBytes = doc.sizeBytes,
                        pageCount = doc.pageCount,
                        lastOpenedMillis = System.currentTimeMillis()
                    )
                )
                onLoaded?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load local PDF", e)
                _processingState.value = ProcessingState.Error(e.localizedMessage ?: "تعذر تحميل الملف.")
            }
        }
    }

    fun createAndLoadSamplePdf(onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Running(0.4f, "جاري إنشاء ملف عربي تجريبي (3 صفحات)...")
            try {
                val sampleFile = withContext(Dispatchers.IO) {
                    PdfFileHelper.generateSampleArabicPdf(appContext)
                }
                _processingState.value = ProcessingState.Idle
                loadLocalPdfFile(sampleFile, sampleFile.name, onLoaded)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create sample PDF", e)
                _processingState.value = ProcessingState.Error("تعذر إنشاء الملف التجريبي: ${e.localizedMessage}")
            }
        }
    }

    // --- Split & Extract ---
    fun runSplitOperation(
        mode: SplitMode,
        rangesText: String,
        partsCount: Int,
        selectedExtractPages: List<Int>,
        saveAsZip: Boolean
    ) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري تجهيز عملية التقسيم...") { onProgress ->
            when (mode) {
                SplitMode.MULTI_RANGES -> pdfProcessor.splitByRanges(
                    inputFile = doc.localFile,
                    rangesText = rangesText,
                    saveAsZip = saveAsZip,
                    baseName = doc.displayName,
                    onProgress = onProgress
                )
                SplitMode.EVERY_PAGE -> pdfProcessor.splitEveryPage(
                    inputFile = doc.localFile,
                    baseName = doc.displayName,
                    onProgress = onProgress
                )
                SplitMode.EQUAL_PARTS -> pdfProcessor.splitEqualParts(
                    inputFile = doc.localFile,
                    partsCount = partsCount,
                    baseName = doc.displayName,
                    onProgress = onProgress
                )
                SplitMode.EXTRACT_PAGES -> {
                    val pages = if (selectedExtractPages.isNotEmpty()) {
                        selectedExtractPages.sorted()
                    } else {
                        pdfProcessor.parseUniquePageNumbers(rangesText, doc.pageCount)
                    }
                    pdfProcessor.extractPages(
                        inputFile = doc.localFile,
                        pageNumbers = pages,
                        baseName = doc.displayName,
                        onProgress = onProgress
                    )
                }
            }
        }
    }

    // --- PDF to Images ---
    fun runPdfToImages(
        format: ImageFormatOption,
        dpi: Int,
        jpegQuality: Int,
        scopeMode: PageScopeMode,
        customPagesText: String
    ) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري تحويل صفحات PDF إلى صور...") { onProgress ->
            pdfProcessor.convertPdfToImages(
                inputFile = doc.localFile,
                renderer = pageRenderer,
                format = format,
                dpi = dpi,
                jpegQuality = jpegQuality,
                scopeMode = scopeMode,
                customPagesText = customPagesText,
                baseName = doc.displayName,
                onProgress = onProgress
            )
        }
    }

    // --- Watermark ---
    fun importImageToTempFile(uri: Uri, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    PdfFileHelper.copyUriToTempFile(appContext, uri, prefix = "wm_", extension = ".png")
                }
                onReady(file)
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("تعذر استيراد الصورة: ${e.localizedMessage}")
            }
        }
    }

    fun runWatermark(config: WatermarkConfig) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري تطبيق العلامة المائية...") { onProgress ->
            pdfProcessor.addWatermark(
                inputFile = doc.localFile,
                config = config,
                baseName = doc.displayName,
                onProgress = onProgress
            )
        }
    }

    // --- Signatures ---
    fun saveSignatureBitmap(
        bitmap: Bitmap,
        name: String,
        removeBackground: Boolean,
        onSaved: (SavedSignatureRecord) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    val processed = if (removeBackground) {
                        pdfProcessor.removeLightBackground(bitmap)
                    } else {
                        bitmap
                    }
                    val id = UUID.randomUUID().toString()
                    val sigFile = File(PdfFileHelper.getSignaturesDir(appContext), "sig_$id.png")
                    FileOutputStream(sigFile).use { fos ->
                        processed.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    if (processed !== bitmap) processed.recycle()
                    SavedSignatureRecord(
                        id = id,
                        name = name.ifBlank { "توقيعي" },
                        filePath = sigFile.absolutePath,
                        createdAtMillis = System.currentTimeMillis()
                    )
                }
                prefsRepo.addSavedSignature(record)
                onSaved(record)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save signature", e)
                _processingState.value = ProcessingState.Error("تعذر حفظ التوقيع: ${e.localizedMessage}")
            }
        }
    }

    fun importSignatureImageUri(
        uri: Uri,
        removeBackground: Boolean,
        onSaved: (SavedSignatureRecord) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: throw IllegalArgumentException("تعذر قراءة صورة التوقيع")
                }
                saveSignatureBitmap(
                    bitmap = bmp,
                    name = "توقيع مستورد",
                    removeBackground = removeBackground,
                    onSaved = onSaved
                )
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error(e.localizedMessage ?: "تعذر استيراد التوقيع")
            }
        }
    }

    fun deleteSavedSignature(id: String) {
        viewModelScope.launch {
            prefsRepo.deleteSavedSignature(id)
        }
    }

    fun runApplySignatures(placements: List<SignaturePlacement>) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري ختم التوقيعات على المستند...") { onProgress ->
            pdfProcessor.applySignatures(
                inputFile = doc.localFile,
                placements = placements,
                baseName = doc.displayName,
                onProgress = onProgress
            )
        }
    }

    // --- PDF to Word (AI Cloud OCR + Local Mode) ---
    fun runPdfToWord(
        scopeMode: PageScopeMode,
        customPagesText: String,
        useCloudAiOcr: Boolean,
        includeImages: Boolean,
        includeHeaderFooter: Boolean
    ) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري تهيئة محرك التحويل إلى Word...") { onProgress ->
            val targetPages = when (scopeMode) {
                PageScopeMode.ALL -> (1..doc.pageCount).toList()
                PageScopeMode.SINGLE, PageScopeMode.CUSTOM_RANGE ->
                    pdfProcessor.parseUniquePageNumbers(customPagesText, doc.pageCount)
            }
            pdfToWordEngine.convertPdfToDocx(
                inputFile = doc.localFile,
                baseName = doc.displayName,
                targetPages = targetPages,
                useCloudAiOcr = useCloudAiOcr,
                includeImages = includeImages,
                includeHeaderFooter = includeHeaderFooter,
                onProgress = onProgress
            )
        }
    }

    // --- Merge PDFs ---
    fun addMergeFiles(uris: List<Uri>) {
        viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val name = PdfFileHelper.queryFileName(appContext, uri)
                        val copy = PdfFileHelper.copyUriToTempFile(appContext, uri, prefix = "merge_")
                        val (pages, _) = pdfProcessor.inspectPdf(copy)
                        MergeCandidate(
                            id = UUID.randomUUID().toString(),
                            localFile = copy,
                            displayName = name,
                            sizeBytes = copy.length(),
                            pageCount = pages
                        )
                    }
                }
                _mergeCandidates.value = _mergeCandidates.value + added
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("تعذر إضافة أحد الملفات: ${e.localizedMessage}")
            }
        }
    }

    fun addActivePdfToMerge() {
        val doc = _activePdf.value ?: return
        val candidate = MergeCandidate(
            id = UUID.randomUUID().toString(),
            localFile = doc.localFile,
            displayName = doc.displayName,
            sizeBytes = doc.sizeBytes,
            pageCount = doc.pageCount
        )
        _mergeCandidates.value = _mergeCandidates.value + candidate
    }

    fun moveMergeCandidate(fromIndex: Int, toIndex: Int) {
        val list = _mergeCandidates.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _mergeCandidates.value = list
    }

    fun removeMergeCandidate(id: String) {
        _mergeCandidates.value = _mergeCandidates.value.filterNot { it.id == id }
    }

    fun clearMergeCandidates() {
        _mergeCandidates.value = emptyList()
    }

    fun runMergePdfs() {
        val files = _mergeCandidates.value.map { it.localFile }
        launchPdfTask("جاري دمج ملفات PDF...") { onProgress ->
            pdfProcessor.mergePdfs(files, onProgress)
        }
    }

    // --- Organize Pages ---
    fun runOrganizePages(edits: List<PageEditItem>) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري إعادة بناء الصفحات...") { onProgress ->
            pdfProcessor.organizePages(doc.localFile, edits, doc.displayName, onProgress)
        }
    }

    // --- Compress ---
    fun runCompressPdf(level: CompressionLevel) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري ضغط ملف PDF...") { onProgress ->
            pdfProcessor.compressPdf(doc.localFile, level, doc.displayName, onProgress)
        }
    }

    // --- Protect / Unlock ---
    fun runProtectPdf(
        userPassword: String,
        ownerPassword: String,
        allowPrinting: Boolean,
        allowCopying: Boolean
    ) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري حماية ملف PDF...") { onProgress ->
            pdfProcessor.protectPdf(
                inputFile = doc.localFile,
                userPassword = userPassword,
                ownerPassword = ownerPassword,
                allowPrinting = allowPrinting,
                allowCopying = allowCopying,
                baseName = doc.displayName,
                onProgress = onProgress
            )
        }
    }

    fun runUnlockPdf(password: String) {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري فك حماية الملف...") { onProgress ->
            pdfProcessor.unlockPdf(
                inputFile = doc.localFile,
                password = password,
                baseName = doc.displayName,
                onProgress = onProgress
            )
        }
    }

    // --- Extract Text & Images ---
    fun runExtractTextAndImages() {
        val doc = _activePdf.value ?: return
        launchPdfTask("جاري استخراج النصوص والصور...") { onProgress ->
            pdfProcessor.extractTextAndImages(doc.localFile, doc.displayName, onProgress)
        }
    }

    // --- Save / Share Outputs ---
    fun saveResultToUri(result: PdfOperationResult, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PdfFileHelper.copyFileToDestinationUri(appContext, result.primaryOutputFile, destinationUri)
                }
                _statusBannerMessage.value = "تم حفظ الملف (${result.suggestedFileName}) بنجاح في الموقع المختار."
            } catch (e: Exception) {
                Log.e(TAG, "Save to URI failed", e)
                _statusBannerMessage.value = "فشل الحفظ: ${e.localizedMessage}"
            }
        }
    }

    fun saveMultipleResultsToFolder(result: PdfOperationResult, treeUri: Uri) {
        viewModelScope.launch {
            try {
                val files = result.additionalFiles.ifEmpty { listOf(result.primaryOutputFile) }
                val itemMime = when (files.firstOrNull()?.extension?.lowercase()) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "txt" -> "text/plain"
                    else -> "application/pdf"
                }
                val count = withContext(Dispatchers.IO) {
                    PdfFileHelper.saveMultipleFilesToTreeUri(appContext, files, treeUri, itemMime)
                }
                _statusBannerMessage.value = "تم حفظ $count ملف منفصل في المجلد المختار بنجاح."
            } catch (e: Exception) {
                Log.e(TAG, "Folder save failed", e)
                _statusBannerMessage.value = "فشل حفظ الملفات في المجلد: ${e.localizedMessage}"
            }
        }
    }

    fun shareResult(result: PdfOperationResult) {
        runCatching {
            PdfFileHelper.shareFile(
                context = appContext,
                file = result.primaryOutputFile,
                mimeType = result.mimeType,
                chooserTitle = "مشاركة ${result.suggestedFileName}"
            )
        }.onFailure { e ->
            _statusBannerMessage.value = "تعذر فتح نافذة المشاركة: ${e.localizedMessage}"
        }
    }

    fun shareActivePdf() {
        val doc = _activePdf.value ?: return
        runCatching {
            PdfFileHelper.shareFile(
                context = appContext,
                file = doc.localFile,
                mimeType = "application/pdf",
                chooserTitle = "مشاركة ${doc.displayName}"
            )
        }.onFailure { e ->
            _statusBannerMessage.value = "تعذر مشاركة الملف: ${e.localizedMessage}"
        }
    }

    private fun launchPdfTask(
        initialStage: String,
        block: suspend ((Float, String) -> Unit) -> PdfOperationResult
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _processingState.value = ProcessingState.Running(0.05f, initialStage)
            try {
                val result = withContext(Dispatchers.IO) {
                    block { progress, stage ->
                        _processingState.value = ProcessingState.Running(
                            progress.coerceIn(0f, 1f),
                            stage
                        )
                    }
                }
                _processingState.value = ProcessingState.Success(result)
            } catch (e: CancellationException) {
                _processingState.value = ProcessingState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF operation failed", e)
                _processingState.value = ProcessingState.Error(
                    e.localizedMessage ?: "حدث خطأ غير متوقع أثناء معالجة الملف."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pageRenderer.clearCache()
    }
}
