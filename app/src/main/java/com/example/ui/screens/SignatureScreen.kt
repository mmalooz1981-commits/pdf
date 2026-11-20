package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.SavedSignatureRecord
import com.example.data.SignaturePlacement
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.ToolTopBar
import java.util.UUID
import kotlin.math.roundToInt

private data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val widthPx: Float
)

@Composable
fun SignatureScreen(
    activePdf: ActivePdfDocument?,
    savedSignatures: List<SavedSignatureRecord>,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onSaveDrawnSignature: (Bitmap, String, Boolean, (SavedSignatureRecord) -> Unit) -> Unit,
    onImportSignatureUri: (android.net.Uri, Boolean, (SavedSignatureRecord) -> Unit) -> Unit,
    onDeleteSignature: (String) -> Unit,
    onApplySignatures: (List<SignaturePlacement>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Create/Manage, 1 = Place on PDF
    var removeImportBg by remember { mutableStateOf(true) }

    // Drawing Pad State
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var penColor by remember { mutableStateOf(Color(0xFF152238)) }
    var strokeWidth by remember { mutableFloatStateOf(9f) }
    var canvasWidthPx by remember { mutableFloatStateOf(900f) }
    var canvasHeightPx by remember { mutableFloatStateOf(380f) }

    // Placement Studio State
    var currentPageIndex by remember(activePdf?.localFile?.absolutePath) { mutableIntStateOf(0) }
    var pagePreviewBmp by remember(activePdf?.localFile?.absolutePath, currentPageIndex) { mutableStateOf<Bitmap?>(null) }
    val placements = remember(activePdf?.localFile?.absolutePath) { mutableStateListOf<SignaturePlacement>() }
    var selectedPlacementId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activePdf?.localFile?.absolutePath, currentPageIndex) {
        val doc = activePdf ?: return@LaunchedEffect
        if (!doc.isPasswordProtected && doc.pageCount > 0) {
            pagePreviewBmp = pageRenderer.renderPage(doc.localFile, currentPageIndex, 900, useCache = false)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportSignatureUri(uri, removeImportBg) { saved ->
                if (activePdf != null) {
                    val pl = SignaturePlacement(
                        id = UUID.randomUUID().toString(),
                        signatureFilePath = saved.filePath,
                        pageIndex = currentPageIndex
                    )
                    placements.add(pl)
                    selectedPlacementId = pl.id
                    selectedTab = 1
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "التوقيع الإلكتروني",
                subtitle = "رسم، استيراد، إزالة خلفية، وتوقيع متعدد",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("1. إنشاء وحفظ التوقيع") },
                    modifier = Modifier.testTag("signature_tab_create")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("2. وضع التوقيع على PDF (${placements.size})") },
                    modifier = Modifier.testTag("signature_tab_place")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedTab == 0) {
                    // Drawing Canvas Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ارسم توقيعك بالإصبع أو القلم", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(
                                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                                        enabled = strokes.isNotEmpty()
                                    ) {
                                        Icon(Icons.Filled.Undo, contentDescription = "تراجع")
                                    }
                                    IconButton(
                                        onClick = { strokes.clear() },
                                        enabled = strokes.isNotEmpty()
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "مسح")
                                    }
                                }
                            }

                            // Interactive Drawing Pad
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFFDF9))
                                    .border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .pointerInput(penColor, strokeWidth) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                activePoints = listOf(offset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                activePoints = activePoints + change.position
                                            },
                                            onDragEnd = {
                                                if (activePoints.isNotEmpty()) {
                                                    strokes.add(DrawnStroke(activePoints, penColor, strokeWidth))
                                                    activePoints = emptyList()
                                                }
                                            },
                                            onDragCancel = { activePoints = emptyList() }
                                        )
                                    }
                                    .testTag("signature_drawing_pad")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    canvasWidthPx = size.width
                                    canvasHeightPx = size.height

                                    fun drawStroke(pts: List<Offset>, color: Color, width: Float) {
                                        if (pts.isEmpty()) return
                                        if (pts.size == 1) {
                                            drawCircle(color, width / 2f, pts.first())
                                            return
                                        }
                                        val path = Path().apply {
                                            moveTo(pts.first().x, pts.first().y)
                                            for (i in 1 until pts.size) {
                                                lineTo(pts[i].x, pts[i].y)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = color,
                                            style = Stroke(
                                                width = width,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }

                                    strokes.forEach { drawStroke(it.points, it.color, it.widthPx) }
                                    drawStroke(activePoints, penColor, strokeWidth)
                                }

                                if (strokes.isEmpty() && activePoints.isEmpty()) {
                                    Text(
                                        text = "وقّع هنا...",
                                        color = Color(0x66544741),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            // Pen color & thickness
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(
                                    Color(0xFF152238),
                                    Color(0xFF000000),
                                    Color(0xFF8C1D2C),
                                    Color(0xFF1B5E20)
                                ).forEach { swatch ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(
                                                width = if (penColor == swatch) 3.dp else 1.dp,
                                                color = if (penColor == swatch) MaterialTheme.colorScheme.secondary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { penColor = swatch }
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = strokeWidth,
                                    onValueChange = { strokeWidth = it },
                                    valueRange = 4f..22f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    val bmp = renderStrokesToTransparentBitmap(
                                        strokes = strokes,
                                        widthPx = canvasWidthPx.toInt().coerceAtLeast(400),
                                        heightPx = canvasHeightPx.toInt().coerceAtLeast(180)
                                    )
                                    onSaveDrawnSignature(bmp, "توقيع ${savedSignatures.size + 1}", false) { saved ->
                                        strokes.clear()
                                        if (activePdf != null) {
                                            val pl = SignaturePlacement(
                                                id = UUID.randomUUID().toString(),
                                                signatureFilePath = saved.filePath,
                                                pageIndex = currentPageIndex
                                            )
                                            placements.add(pl)
                                            selectedPlacementId = pl.id
                                        }
                                        selectedTab = 1
                                    }
                                },
                                enabled = strokes.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_drawn_signature_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ التوقيع واستخدامه على المستند")
                            }
                        }
                    }

                    // Import Signature Image + Remove Background
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("أو استيراد توقيع من صورة", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("إزالة الخلفية البيضاء تلقائياً", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "يجعل خلفية الورقة شفافة لظهور التوقيع باحترافية",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = removeImportBg, onCheckedChange = { removeImportBg = it })
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_signature_image_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختيار صورة التوقيع من المعرض")
                            }
                        }
                    }

                    // Saved Signatures Library
                    if (savedSignatures.isNotEmpty()) {
                        Text("التوقيعات المحفوظة (${savedSignatures.size}):", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(savedSignatures, key = { it.id }) { sig ->
                                SavedSignatureChipCard(
                                    record = sig,
                                    onUse = {
                                        val pl = SignaturePlacement(
                                            id = UUID.randomUUID().toString(),
                                            signatureFilePath = sig.filePath,
                                            pageIndex = currentPageIndex
                                        )
                                        placements.add(pl)
                                        selectedPlacementId = pl.id
                                        selectedTab = 1
                                    },
                                    onDelete = { onDeleteSignature(sig.id) }
                                )
                            }
                        }
                    }
                } else {
                    // TAB 2: Interactive PDF Placement Studio
                    ActivePdfSelectorCard(
                        activePdf = activePdf,
                        onPickPdf = onPickPdf,
                        onCreateSamplePdf = onCreateSamplePdf
                    )

                    if (activePdf != null && !activePdf.isPasswordProtected && activePdf.pageCount > 0) {
                        // Add Saved Signature Strip
                        if (savedSignatures.isNotEmpty()) {
                            Text("اضغط لإضافة توقيع آخر إلى الصفحة:", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(savedSignatures, key = { it.id }) { sig ->
                                    OutlinedButton(
                                        onClick = {
                                            val pl = SignaturePlacement(
                                                id = UUID.randomUUID().toString(),
                                                signatureFilePath = sig.filePath,
                                                pageIndex = currentPageIndex,
                                                xFraction = 0.5f,
                                                yFraction = (0.4f + (placements.size % 3) * 0.16f).coerceAtMost(0.85f)
                                            )
                                            placements.add(pl)
                                            selectedPlacementId = pl.id
                                        }
                                    ) {
                                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(sig.name)
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Draw, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ارسم أو استورد توقيعاً أولاً")
                            }
                        }

                        // Page Selector
                        if (activePdf.pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الصفحة الحالية: ${currentPageIndex + 1} من ${activePdf.pageCount}", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                        enabled = currentPageIndex > 0
                                    ) { Text("السابقة") }
                                    OutlinedButton(
                                        onClick = { if (currentPageIndex < activePdf.pageCount - 1) currentPageIndex++ },
                                        enabled = currentPageIndex < activePdf.pageCount - 1
                                    ) { Text("التالية") }
                                }
                            }
                        }

                        // Interactive PDF Page with Draggable Signature Overlays
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.71f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        ) {
                            val density = LocalDensity.current
                            val boxWidthPx = with(density) { maxWidth.toPx() }
                            val boxHeightPx = with(density) { maxHeight.toPx() }

                            if (pagePreviewBmp != null && !pagePreviewBmp!!.isRecycled) {
                                Image(
                                    bitmap = pagePreviewBmp!!.asImageBitmap(),
                                    contentDescription = "معاينة الصفحة للتوقيع",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            val visiblePlacements = placements.filter { it.pageIndex == -1 || it.pageIndex == currentPageIndex }
                            visiblePlacements.forEach { placement ->
                                DraggableSignatureOverlay(
                                    placement = placement,
                                    isSelected = placement.id == selectedPlacementId,
                                    containerWidthPx = boxWidthPx,
                                    containerHeightPx = boxHeightPx,
                                    onSelect = { selectedPlacementId = placement.id },
                                    onDragDelta = { dx, dy ->
                                        val idx = placements.indexOfFirst { it.id == placement.id }
                                        if (idx >= 0) {
                                            val old = placements[idx]
                                            placements[idx] = old.copy(
                                                xFraction = (old.xFraction + dx / boxWidthPx).coerceIn(0.08f, 0.92f),
                                                yFraction = (old.yFraction + dy / boxHeightPx).coerceIn(0.08f, 0.92f)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // Controls for Currently Selected Placement
                        val selectedIdx = placements.indexOfFirst { it.id == selectedPlacementId }
                        if (selectedIdx >= 0) {
                            val selected = placements[selectedIdx]
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("تعديل التوقيع المحدد (اسحبه على الصفحة لتحريكه)", style = MaterialTheme.typography.titleSmall)
                                        IconButton(
                                            onClick = {
                                                placements.removeAt(selectedIdx)
                                                selectedPlacementId = placements.firstOrNull()?.id
                                            }
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "حذف التوقيع", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    Text("الحجم: ${(selected.widthFraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = selected.widthFraction,
                                        onValueChange = { placements[selectedIdx] = selected.copy(widthFraction = it) },
                                        valueRange = 0.12f..0.75f
                                    )

                                    Text("التدوير: ${selected.rotationDegrees.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = selected.rotationDegrees,
                                        onValueChange = { placements[selectedIdx] = selected.copy(rotationDegrees = it) },
                                        valueRange = -90f..90f
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = selected.pageIndex != -1,
                                            onClick = { placements[selectedIdx] = selected.copy(pageIndex = currentPageIndex) },
                                            label = { Text("هذه الصفحة فقط (${currentPageIndex + 1})") }
                                        )
                                        FilterChip(
                                            selected = selected.pageIndex == -1,
                                            onClick = { placements[selectedIdx] = selected.copy(pageIndex = -1) },
                                            label = { Text("جميع الصفحات") }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onApplySignatures(placements.toList()) },
                            enabled = placements.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("apply_signatures_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تطبيق ${placements.size} توقيع وحفظ نسخة جديدة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSignatureChipCard(
    record: SavedSignatureRecord,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    val bmp = remember(record.filePath) { BitmapFactory.decodeFile(record.filePath) }
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onUse)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = record.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1F1917)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableSignatureOverlay(
    placement: SignaturePlacement,
    isSelected: Boolean,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onSelect: () -> Unit,
    onDragDelta: (Float, Float) -> Unit
) {
    val bmp = remember(placement.signatureFilePath) {
        BitmapFactory.decodeFile(placement.signatureFilePath)
    } ?: return
    val density = LocalDensity.current
    val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
    val wPx = containerWidthPx * placement.widthFraction
    val hPx = wPx * aspect
    val leftPx = (containerWidthPx * placement.xFraction - wPx / 2f).roundToInt()
    val topPx = (containerHeightPx * placement.yFraction - hPx / 2f).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx, topPx) }
            .size(
                width = with(density) { wPx.toDp() },
                height = with(density) { hPx.toDp() }
            )
            .graphicsLayer(rotationZ = placement.rotationDegrees)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x668C1D2C),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onSelect)
            .pointerInput(placement.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "توقيع على الصفحة",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun renderStrokesToTransparentBitmap(
    strokes: List<DrawnStroke>,
    widthPx: Int,
    heightPx: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.widthPx
        if (stroke.points.size == 1) {
            paint.style = AndroidPaint.Style.FILL
            canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.widthPx / 2f, paint)
            paint.style = AndroidPaint.Style.STROKE
        } else {
            val path = AndroidPath().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bmp
}
