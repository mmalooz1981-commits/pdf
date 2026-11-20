package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivePdfDocument
import com.example.data.PageScopeMode
import com.example.data.WatermarkConfig
import com.example.data.WatermarkPosition
import com.example.data.WatermarkType
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.ToolTopBar
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkScreen(
    activePdf: ActivePdfDocument?,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onImportWatermarkImage: (android.net.Uri, (File) -> Unit) -> Unit,
    onApplyWatermark: (WatermarkConfig) -> Unit
) {
    var config by remember { mutableStateOf(WatermarkConfig()) }
    var previewPageBmp by remember(activePdf?.localFile?.absolutePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(activePdf?.localFile?.absolutePath) {
        val doc = activePdf ?: return@LaunchedEffect
        if (!doc.isPasswordProtected && doc.pageCount > 0) {
            previewPageBmp = pageRenderer.renderPage(doc.localFile, 0, 720, useCache = true)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportWatermarkImage(uri) { file ->
                config = config.copy(type = WatermarkType.IMAGE, imageFile = file)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "العلامة المائية الاحترافية",
                subtitle = "نص عربي RTL أو شعار شفاف بجميع المواضع",
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
                // Live Watermark Preview Canvas
                Text("معاينة فورية على الصفحة الأولى:", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(245.dp)
                            .aspectRatio(0.71f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        if (previewPageBmp != null && !previewPageBmp!!.isRecycled) {
                            Image(
                                bitmap = previewPageBmp!!.asImageBitmap(),
                                contentDescription = "معاينة العلامة المائية",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        WatermarkLiveOverlay(config = config)
                    }
                }

                // Type Switch: Text vs Image
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = config.type == WatermarkType.TEXT,
                                onClick = { config = config.copy(type = WatermarkType.TEXT) },
                                label = { Text("علامة نصية (عربي/إنجليزي)") },
                                modifier = Modifier.testTag("watermark_type_text")
                            )
                            FilterChip(
                                selected = config.type == WatermarkType.IMAGE,
                                onClick = { config = config.copy(type = WatermarkType.IMAGE) },
                                label = { Text("علامة مصورة (شعار)") },
                                modifier = Modifier.testTag("watermark_type_image")
                            )
                        }

                        if (config.type == WatermarkType.TEXT) {
                            OutlinedTextField(
                                value = config.text,
                                onValueChange = { config = config.copy(text = it) },
                                label = { Text("نص العلامة المائية") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("watermark_text_input")
                            )

                            Text("حجم الخط: ${config.fontSizeSp.roundToInt()} pt", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = config.fontSizeSp,
                                onValueChange = { config = config.copy(fontSizeSp = it) },
                                valueRange = 18f..72f
                            )

                            Text("لون العلامة:", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(
                                    Color(0xFF8C1D2C),
                                    Color(0xFF555555),
                                    Color(0xFF1E6091),
                                    Color(0xFF2E7D4F),
                                    Color(0xFF8A5A2B),
                                    Color(0xFF000000)
                                ).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (config.colorArgb == color.toArgb()) 3.dp else 1.dp,
                                                color = if (config.colorArgb == color.toArgb()) MaterialTheme.colorScheme.secondary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { config = config.copy(colorArgb = color.toArgb()) }
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_watermark_image_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (config.imageFile == null) "اختيار صورة أو شعار من المعرض"
                                    else "تم اختيار الشعار (اضغط للتغيير)"
                                )
                            }

                            Text("حجم الشعار: ${config.imageScalePercent.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = config.imageScalePercent,
                                onValueChange = { config = config.copy(imageScalePercent = it) },
                                valueRange = 15f..75f
                            )
                        }

                        // Opacity & Rotation
                        Text("نسبة الشفافية: ${(config.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = config.opacity,
                            onValueChange = { config = config.copy(opacity = it) },
                            valueRange = 0.08f..1f
                        )

                        Text("زاوية التدوير: ${config.rotationDegrees.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = config.rotationDegrees,
                            onValueChange = { config = config.copy(rotationDegrees = it) },
                            valueRange = -90f..90f
                        )

                        // Position chips
                        Text("موضع العلامة:", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WatermarkPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = config.position == pos,
                                    onClick = { config = config.copy(position = pos) },
                                    label = { Text(pos.arabicLabel) }
                                )
                            }
                        }

                        // Page Scope
                        Text("نطاق التطبيق:", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = config.pageScope == PageScopeMode.ALL,
                                onClick = { config = config.copy(pageScope = PageScopeMode.ALL) },
                                label = { Text("جميع الصفحات (${activePdf.pageCount})") }
                            )
                            FilterChip(
                                selected = config.pageScope == PageScopeMode.CUSTOM_RANGE,
                                onClick = { config = config.copy(pageScope = PageScopeMode.CUSTOM_RANGE) },
                                label = { Text("صفحات محددة") }
                            )
                        }
                        if (config.pageScope == PageScopeMode.CUSTOM_RANGE) {
                            OutlinedTextField(
                                value = config.customPagesText,
                                onValueChange = { config = config.copy(customPagesText = it) },
                                label = { Text("أرقام الصفحات (مثال: 1-2، 4)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = { onApplyWatermark(config) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_watermark_button")
                ) {
                    Icon(Icons.Filled.BrandingWatermark, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة العلامة المائية وحفظ نسخة جديدة")
                }
            }
        }
    }
}

@Composable
private fun WatermarkLiveOverlay(config: WatermarkConfig) {
    val logoBmp = remember(config.imageFile?.absolutePath) {
        config.imageFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    @Composable
    fun SingleWatermarkMark(scaleFactor: Float = 1f) {
        Box(
            modifier = Modifier
                .alpha(config.opacity.coerceIn(0.08f, 1f))
                .graphicsLayer(rotationZ = config.rotationDegrees)
        ) {
            if (config.type == WatermarkType.TEXT) {
                Text(
                    text = config.text.ifBlank { "علامة مائية" },
                    color = Color(config.colorArgb),
                    fontSize = (config.fontSizeSp * 0.34f * scaleFactor).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            } else if (logoBmp != null) {
                Image(
                    bitmap = logoBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size((84 * (config.imageScalePercent / 40f) * scaleFactor).dp)
                )
            }
        }
    }

    if (config.position == WatermarkPosition.TILED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(2) { SingleWatermarkMark(scaleFactor = 0.62f) }
                }
            }
        }
    } else {
        val alignment = when (config.position) {
            WatermarkPosition.CENTER -> Alignment.Center
            WatermarkPosition.TOP_RIGHT -> Alignment.TopStart // In RTL, Start = Right
            WatermarkPosition.TOP_LEFT -> Alignment.TopEnd
            WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomStart
            WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomEnd
            WatermarkPosition.TILED -> Alignment.Center
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = alignment
        ) {
            SingleWatermarkMark()
        }
    }
}
