package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.ImageFormatOption
import com.example.data.PageScopeMode
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.PdfPageThumbnailCard
import com.example.ui.components.ToolTopBar
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToImagesScreen(
    activePdf: ActivePdfDocument?,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onExecuteConvert: (
        format: ImageFormatOption,
        dpi: Int,
        jpegQuality: Int,
        scope: PageScopeMode,
        customPagesText: String
    ) -> Unit
) {
    var format by remember { mutableStateOf(ImageFormatOption.PNG) }
    var dpi by remember { mutableIntStateOf(150) }
    var jpegQuality by remember { mutableIntStateOf(90) }
    var scopeMode by remember { mutableStateOf(PageScopeMode.ALL) }
    var customPagesText by remember { mutableStateOf("1") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "تحويل PDF إلى صور",
                subtitle = "تصدير JPG / PNG مع تحكم بالدقة والجودة",
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
                // Preview strip
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(activePdf.pageCount.coerceAtMost(20)) { idx ->
                        PdfPageThumbnailCard(
                            file = activePdf.localFile,
                            pageIndex = idx,
                            renderer = pageRenderer,
                            targetWidthPx = 200,
                            modifier = Modifier
                                .height(112.dp)
                                .aspectRatio(0.71f)
                        )
                    }
                }

                // Format & DPI settings
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("صيغة الصور والدقة", style = MaterialTheme.typography.titleMedium)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageFormatOption.entries.forEach { opt ->
                                FilterChip(
                                    selected = format == opt,
                                    onClick = { format = opt },
                                    label = { Text(opt.label) },
                                    modifier = Modifier.testTag("img_format_${opt.extension}")
                                )
                            }
                        }

                        Text("الدقة (DPI): $dpi نقطة/بوصة", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100 to "عادي (100)", 150 to "ممتاز (150)", 220 to "عالي (220)", 300 to "طباعة (300)").forEach { (d, label) ->
                                FilterChip(
                                    selected = dpi == d,
                                    onClick = { dpi = d },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (format == ImageFormatOption.JPEG) {
                            Text("جودة ضغط JPG: $jpegQuality%", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = jpegQuality.toFloat(),
                                onValueChange = { jpegQuality = it.roundToInt() },
                                valueRange = 55f..100f
                            )
                        }
                    }
                }

                // Page Scope
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("الصفحات المطلوب تحويلها", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = scopeMode == PageScopeMode.ALL,
                                onClick = { scopeMode = PageScopeMode.ALL },
                                label = { Text("جميع الصفحات (${activePdf.pageCount})") }
                            )
                            FilterChip(
                                selected = scopeMode == PageScopeMode.SINGLE,
                                onClick = { scopeMode = PageScopeMode.SINGLE },
                                label = { Text("صفحة واحدة") }
                            )
                            FilterChip(
                                selected = scopeMode == PageScopeMode.CUSTOM_RANGE,
                                onClick = { scopeMode = PageScopeMode.CUSTOM_RANGE },
                                label = { Text("نطاق محدد") }
                            )
                        }

                        if (scopeMode != PageScopeMode.ALL) {
                            OutlinedTextField(
                                value = customPagesText,
                                onValueChange = { customPagesText = it },
                                label = {
                                    Text(
                                        if (scopeMode == PageScopeMode.SINGLE) "رقم الصفحة (1 إلى ${activePdf.pageCount})"
                                        else "نطاق الصفحات (مثال: 1-3، 5)"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onExecuteConvert(format, dpi, jpegQuality, scopeMode, customPagesText)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_pdf_to_images_button")
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحويل إلى صور ${format.extension.uppercase()} ومعاينتها")
                }
            }
        }
    }
}
