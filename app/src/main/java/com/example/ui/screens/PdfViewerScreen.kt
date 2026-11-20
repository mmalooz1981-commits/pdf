package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.PdfToolRoute
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.PdfPageThumbnailCard
import com.example.ui.components.ToolTopBar
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    activePdf: ActivePdfDocument?,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onJumpToTool: (PdfToolRoute) -> Unit
) {
    var currentPage by remember(activePdf?.localFile?.absolutePath) { mutableIntStateOf(0) }
    var zoomScale by remember(activePdf?.localFile?.absolutePath, currentPage) { mutableFloatStateOf(1f) }
    var panOffset by remember(activePdf?.localFile?.absolutePath, currentPage) { mutableStateOf(Offset.Zero) }
    var pageBitmap by remember(activePdf?.localFile?.absolutePath, currentPage) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(false) }

    LaunchedEffect(activePdf?.localFile?.absolutePath, currentPage) {
        val doc = activePdf ?: return@LaunchedEffect
        if (doc.isPasswordProtected || doc.pageCount <= 0) return@LaunchedEffect
        isLoadingPage = true
        pageBitmap = pageRenderer.renderPage(
            file = doc.localFile,
            pageIndex = currentPage.coerceIn(0, doc.pageCount - 1),
            targetWidthPx = 1240,
            useCache = false
        )
        isLoadingPage = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = activePdf?.displayName ?: "عارض PDF الاحترافي",
                subtitle = activePdf?.let {
                    if (it.isPasswordProtected) "ملف مشفر - افتح أداة فك الحماية"
                    else "صفحة ${currentPage + 1} من ${it.pageCount}"
                },
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onPickPdf,
                        modifier = Modifier.testTag("viewer_pick_pdf")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "فتح ملف آخر")
                    }
                    if (activePdf != null) {
                        IconButton(
                            onClick = onSharePdf,
                            modifier = Modifier.testTag("viewer_share_pdf")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "مشاركة الملف")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (activePdf == null || activePdf.isPasswordProtected || activePdf.pageCount <= 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ActivePdfSelectorCard(
                    activePdf = activePdf,
                    onPickPdf = onPickPdf,
                    onCreateSamplePdf = onCreateSamplePdf
                )
                if (activePdf?.isPasswordProtected == true) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AssistChip(
                        onClick = { onJumpToTool(PdfToolRoute.SECURITY) },
                        label = { Text("الذهاب لفك حماية الملف بكلمة المرور") }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Quick Action Bar for Current Document
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = { onJumpToTool(PdfToolRoute.SPLIT) },
                            label = { Text("تقسيم") },
                            leadingIcon = { Icon(Icons.Filled.ContentCut, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onJumpToTool(PdfToolRoute.SIGNATURE) },
                            label = { Text("توقيع") },
                            leadingIcon = { Icon(Icons.Filled.Draw, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onJumpToTool(PdfToolRoute.WATERMARK) },
                            label = { Text("علامة مائية") },
                            leadingIcon = { Icon(Icons.Filled.BrandingWatermark, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onJumpToTool(PdfToolRoute.TO_WORD) },
                            label = { Text("إلى Word") },
                            leadingIcon = { Icon(Icons.Filled.Description, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onJumpToTool(PdfToolRoute.ORGANIZE) },
                            label = { Text("تنظيم") },
                            leadingIcon = { Icon(Icons.Filled.GridView, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                // Main Pinch-Zoomable Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .pointerInput(currentPage) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = newScale
                                panOffset = if (newScale > 1f) {
                                    Offset(
                                        x = (panOffset.x + pan.x).coerceIn(-600f, 600f),
                                        y = (panOffset.y + pan.y).coerceIn(-800f, 800f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPage) {
                        CircularProgressIndicator()
                    } else if (pageBitmap != null && !pageBitmap!!.isRecycled) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "صفحة ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                        )
                    }

                    // Zoom Controls Overlay
                    Surface(
                        color = Color(0xBB1E1816),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.5f).coerceAtLeast(1f)
                                    if (zoomScale == 1f) panOffset = Offset.Zero
                                }
                            ) {
                                Icon(Icons.Filled.ZoomOut, contentDescription = "تصغير", tint = Color.White)
                            }
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.5f).coerceAtMost(4f) }
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "تكبير", tint = Color.White)
                            }
                        }
                    }
                }

                // Bottom Page Strip & Navigation Controls
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (activePdf.pageCount > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(activePdf.pageCount) { idx ->
                                    PdfPageThumbnailCard(
                                        file = activePdf.localFile,
                                        pageIndex = idx,
                                        renderer = pageRenderer,
                                        targetWidthPx = 150,
                                        isSelected = idx == currentPage,
                                        modifier = Modifier
                                            .height(74.dp)
                                            .aspectRatio(0.71f)
                                            .clickable { currentPage = idx }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0,
                                modifier = Modifier.testTag("viewer_prev_page")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الصفحة السابقة")
                            }

                            if (activePdf.pageCount > 1) {
                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { currentPage = it.roundToInt() },
                                    valueRange = 0f..(activePdf.pageCount - 1).toFloat(),
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            Text(
                                text = "${currentPage + 1} / ${activePdf.pageCount}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            IconButton(
                                onClick = { if (currentPage < activePdf.pageCount - 1) currentPage++ },
                                enabled = currentPage < activePdf.pageCount - 1,
                                modifier = Modifier.testTag("viewer_next_page")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الصفحة التالية")
                            }
                        }
                    }
                }
            }
        }
    }
}
