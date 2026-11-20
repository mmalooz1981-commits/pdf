package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.PageEditItem
import com.example.pdf.PdfPageRenderer
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.PdfPageThumbnailCard
import com.example.ui.components.ToolTopBar

@Composable
fun OrganizePagesScreen(
    activePdf: ActivePdfDocument?,
    pageRenderer: PdfPageRenderer,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onSaveOrganizedPdf: (List<PageEditItem>) -> Unit
) {
    val pageEdits = remember(activePdf?.localFile?.absolutePath) { mutableStateListOf<PageEditItem>() }

    LaunchedEffect(activePdf?.localFile?.absolutePath, activePdf?.pageCount) {
        pageEdits.clear()
        val count = activePdf?.pageCount ?: 0
        for (i in 0 until count) {
            pageEdits.add(PageEditItem(originalPageIndex = i))
        }
    }

    val keptCount = pageEdits.count { !it.isDeleted }
    val deletedCount = pageEdits.count { it.isDeleted }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "تنظيم وإعادة ترتيب الصفحات",
                subtitle = "اسحب مطولاً لإعادة الترتيب، أو دوّر واحذف الصفحات",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActivePdfSelectorCard(
                activePdf = activePdf,
                onPickPdf = onPickPdf,
                onCreateSamplePdf = onCreateSamplePdf
            )

            if (activePdf != null && !activePdf.isPasswordProtected && pageEdits.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المتبقية: $keptCount صفحة • المحذوفة: $deletedCount",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "ضغط مطول للسحب",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(pageEdits, key = { _, item -> item.originalPageIndex }) { index, item ->
                        OrganizePageCard(
                            file = activePdf.localFile,
                            item = item,
                            displayPosition = index + 1,
                            canMoveBack = index > 0,
                            canMoveForward = index < pageEdits.lastIndex,
                            renderer = pageRenderer,
                            onMoveLeft = {
                                if (index > 0) {
                                    val moved = pageEdits.removeAt(index)
                                    pageEdits.add(index - 1, moved)
                                }
                            },
                            onMoveRight = {
                                if (index < pageEdits.lastIndex) {
                                    val moved = pageEdits.removeAt(index)
                                    pageEdits.add(index + 1, moved)
                                }
                            },
                            onRotate = {
                                pageEdits[index] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
                            },
                            onToggleDelete = {
                                pageEdits[index] = item.copy(isDeleted = !item.isDeleted)
                            }
                        )
                    }
                }

                Button(
                    onClick = { onSaveOrganizedPdf(pageEdits.toList()) },
                    enabled = keptCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_organize_button")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ المستند المنظم ($keptCount صفحة)")
                }
            }
        }
    }
}

@Composable
private fun OrganizePageCard(
    file: java.io.File,
    item: PageEditItem,
    displayPosition: Int,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    renderer: PdfPageRenderer,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onToggleDelete: () -> Unit
) {
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDeleted) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isDragging) 2.dp else 1.dp,
            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(displayPosition) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        dragAccumX = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        dragAccumX = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        // RTL aware horizontal drag swap threshold
                        if (dragAccumX > 90f) {
                            onMoveLeft()
                            dragAccumX = 0f
                        } else if (dragAccumX < -90f) {
                            onMoveRight()
                            dragAccumX = 0f
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .alpha(if (item.isDeleted) 0.35f else 1f),
                contentAlignment = Alignment.Center
            ) {
                PdfPageThumbnailCard(
                    file = file,
                    pageIndex = item.originalPageIndex,
                    renderer = renderer,
                    targetWidthPx = 220,
                    badgeText = "#$displayPosition",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = item.rotationDegrees.toFloat())
                )

                if (item.isDeleted) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "سيتم حذفها",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "أصلية: ${item.originalPageIndex + 1}" + if (item.rotationDegrees != 0) " • ${item.rotationDegrees}°" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onMoveLeft, enabled = canMoveBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "تقديم", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRotate, enabled = !item.isDeleted, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "تدوير 90 درجة", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (item.isDeleted) Icons.Filled.RestoreFromTrash else Icons.Filled.Delete,
                        contentDescription = if (item.isDeleted) "استعادة" else "حذف",
                        tint = if (item.isDeleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onMoveRight, enabled = canMoveForward, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "تأخير", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
