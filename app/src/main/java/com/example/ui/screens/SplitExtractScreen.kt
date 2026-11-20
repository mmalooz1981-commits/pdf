package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.SplitMode
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.PdfPageThumbnailCard
import com.example.ui.components.ToolTopBar
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitExtractScreen(
    activePdf: ActivePdfDocument?,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onExecuteSplit: (
        mode: SplitMode,
        rangesText: String,
        partsCount: Int,
        selectedExtractPages: List<Int>,
        saveAsZip: Boolean
    ) -> Unit
) {
    var mode by remember { mutableStateOf(SplitMode.MULTI_RANGES) }
    var rangesText by remember(activePdf?.localFile?.absolutePath) {
        val defaultRange = if ((activePdf?.pageCount ?: 0) >= 3) "1-2، 3" else "1"
        mutableStateOf(defaultRange)
    }
    var partsCount by remember { mutableIntStateOf(2) }
    var saveAsZip by remember { mutableStateOf(true) }
    val selectedPages = remember(activePdf?.localFile?.absolutePath) { mutableStateListOf<Int>() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "تقسيم واستخراج الصفحات",
                subtitle = "نطاقات متعددة، كل صفحة، أو أجزاء متساوية",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ActivePdfSelectorCard(
                activePdf = activePdf,
                onPickPdf = onPickPdf,
                onCreateSamplePdf = onCreateSamplePdf
            )

            if (activePdf != null && !activePdf.isPasswordProtected && activePdf.pageCount > 0) {
                // Live Page Preview Strip
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (mode == SplitMode.EXTRACT_PAGES) {
                            "اضغط على الصفحات لتحديدها (${selectedPages.size} محددة):"
                        } else {
                            "معاينة صفحات الملف (${activePdf.pageCount} صفحة):"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(activePdf.pageCount) { idx ->
                            val pageNum = idx + 1
                            val isSelected = selectedPages.contains(pageNum)
                            PdfPageThumbnailCard(
                                file = activePdf.localFile,
                                pageIndex = idx,
                                renderer = pageRenderer,
                                targetWidthPx = 220,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .height(124.dp)
                                    .aspectRatio(0.71f)
                                    .clickable {
                                        if (mode == SplitMode.EXTRACT_PAGES) {
                                            if (isSelected) selectedPages.remove(pageNum)
                                            else selectedPages.add(pageNum)
                                        }
                                    }
                            )
                        }
                    }
                }

                // Mode Selection
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("اختر طريقة التقسيم", style = MaterialTheme.typography.titleMedium)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = mode == SplitMode.MULTI_RANGES,
                                onClick = { mode = SplitMode.MULTI_RANGES },
                                label = { Text("نطاقات متعددة") },
                                modifier = Modifier.testTag("split_mode_ranges")
                            )
                            FilterChip(
                                selected = mode == SplitMode.EVERY_PAGE,
                                onClick = { mode = SplitMode.EVERY_PAGE },
                                label = { Text("تقسيم كل صفحة") },
                                modifier = Modifier.testTag("split_mode_every")
                            )
                            FilterChip(
                                selected = mode == SplitMode.EQUAL_PARTS,
                                onClick = { mode = SplitMode.EQUAL_PARTS },
                                label = { Text("تقسيم متساوٍ") },
                                modifier = Modifier.testTag("split_mode_equal")
                            )
                            FilterChip(
                                selected = mode == SplitMode.EXTRACT_PAGES,
                                onClick = { mode = SplitMode.EXTRACT_PAGES },
                                label = { Text("استخراج صفحات") },
                                modifier = Modifier.testTag("split_mode_extract")
                            )
                        }

                        when (mode) {
                            SplitMode.MULTI_RANGES -> {
                                OutlinedTextField(
                                    value = rangesText,
                                    onValueChange = { rangesText = it },
                                    label = { Text("نطاقات الصفحات (مثال: 1-5، 8-12، 15-20)") },
                                    supportingText = {
                                        Text("ينشئ ملف PDF مستقل لكل نطاق مفصول بفاصلة (إجمالي الصفحات: ${activePdf.pageCount})")
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("split_ranges_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("تجميع الملفات في أرشيف ZIP", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "يمكنك أيضاً حفظ الأجزاء كملفات منفصلة في مجلد بعد التنفيذ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(checked = saveAsZip, onCheckedChange = { saveAsZip = it })
                                }
                            }

                            SplitMode.EVERY_PAGE -> {
                                Text(
                                    text = "سيتم إنشاء ${activePdf.pageCount} ملف PDF مستقل (ملف واحد لكل صفحة) وتجهيزها للحفظ كملف ZIP أو كملفات منفصلة داخل مجلد تختاره.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SplitMode.EQUAL_PARTS -> {
                                val maxParts = activePdf.pageCount.coerceAtLeast(2)
                                Text(
                                    text = "عدد الأجزاء المتساوية: $partsCount أجزاء",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (maxParts > 2) {
                                    Slider(
                                        value = partsCount.coerceIn(2, maxParts).toFloat(),
                                        onValueChange = { partsCount = it.roundToInt() },
                                        valueRange = 2f..maxParts.toFloat(),
                                        steps = (maxParts - 3).coerceAtLeast(0)
                                    )
                                } else {
                                    Text(
                                        text = "سيتم التقسيم إلى جزأين متساويين.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SplitMode.EXTRACT_PAGES -> {
                                OutlinedTextField(
                                    value = if (selectedPages.isNotEmpty()) {
                                        selectedPages.sorted().joinToString("، ")
                                    } else rangesText,
                                    onValueChange = {
                                        selectedPages.clear()
                                        rangesText = it
                                    },
                                    label = { Text("الصفحات المطلوب استخراجها في ملف واحد") },
                                    supportingText = {
                                        Text("اضغط على الصور المصغرة أعلاه أو اكتب أرقام الصفحات مثل: 1، 3-5")
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onExecuteSplit(
                            mode,
                            rangesText,
                            partsCount,
                            selectedPages.toList(),
                            saveAsZip
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_split_button")
                ) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (mode) {
                            SplitMode.MULTI_RANGES -> "تقسيم حسب النطاقات"
                            SplitMode.EVERY_PAGE -> "تقسيم كل صفحة على حدة"
                            SplitMode.EQUAL_PARTS -> "تقسيم إلى $partsCount أجزاء متساوية"
                            SplitMode.EXTRACT_PAGES -> "استخراج الصفحات المحددة"
                        }
                    )
                }
            }
        }
    }
}
