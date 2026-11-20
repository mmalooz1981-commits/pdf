package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActivePdfDocument
import com.example.data.PdfToolRoute
import com.example.data.RecentPdfRecord
import com.example.data.ThemeMode
import com.example.data.formatFileSizeArabic
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentBronze
import com.example.ui.theme.AccentCobalt
import com.example.ui.theme.AccentCrimson
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentOlive
import com.example.ui.theme.AccentPlum
import com.example.ui.theme.AccentSlate
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.AccentTerracotta

private data class HomeToolItem(
    val route: PdfToolRoute,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badge: String? = null
)

private val AllPdfTools = listOf(
    HomeToolItem(
        route = PdfToolRoute.VIEWER,
        title = "فتح وعرض PDF",
        subtitle = "معاينة احترافية وتكبير وتنقل سريع",
        icon = Icons.Filled.PictureAsPdf,
        accent = AccentCrimson
    ),
    HomeToolItem(
        route = PdfToolRoute.SPLIT,
        title = "تقسيم واستخراج",
        subtitle = "نطاقات متعددة، كل صفحة، أو أجزاء متساوية",
        icon = Icons.Filled.ContentCut,
        accent = AccentTerracotta
    ),
    HomeToolItem(
        route = PdfToolRoute.TO_IMAGES,
        title = "تحويل إلى صور",
        subtitle = "تصدير JPG و PNG بدقة وجودة مخصصة",
        icon = Icons.Filled.Image,
        accent = AccentTeal
    ),
    HomeToolItem(
        route = PdfToolRoute.SIGNATURE,
        title = "التوقيع الإلكتروني",
        subtitle = "رسم، استيراد، إزالة خلفية، وتوقيع متعدد",
        icon = Icons.Filled.Draw,
        accent = AccentIndigo
    ),
    HomeToolItem(
        route = PdfToolRoute.WATERMARK,
        title = "العلامة المائية",
        subtitle = "نص عربي RTL أو شعار مع شفافية وتدوير",
        icon = Icons.Filled.BrandingWatermark,
        accent = AccentPlum
    ),
    HomeToolItem(
        route = PdfToolRoute.TO_WORD,
        title = "تحويل إلى Word",
        subtitle = "OCR سحابي ذكي للجداول والعربية DOCX",
        icon = Icons.Filled.Description,
        accent = AccentCobalt,
        badge = "OCR"
    ),
    HomeToolItem(
        route = PdfToolRoute.MERGE,
        title = "دمج ملفات PDF",
        subtitle = "دمج عدد غير محدود من الملفات بترتيبك",
        icon = Icons.Filled.MergeType,
        accent = AccentEmerald
    ),
    HomeToolItem(
        route = PdfToolRoute.ORGANIZE,
        title = "تنظيم الصفحات",
        subtitle = "إعادة ترتيب، تدوير، وحذف الصفحات",
        icon = Icons.Filled.GridView,
        accent = AccentAmber
    ),
    HomeToolItem(
        route = PdfToolRoute.COMPRESS,
        title = "ضغط PDF",
        subtitle = "تقليل حجم الملف مع الحفاظ على الوضوح",
        icon = Icons.Filled.Compress,
        accent = AccentBronze
    ),
    HomeToolItem(
        route = PdfToolRoute.SECURITY,
        title = "الحماية والتشفير",
        subtitle = "قفل بكلمة مرور أو فك حماية الملفات",
        icon = Icons.Filled.Lock,
        accent = AccentSlate
    ),
    HomeToolItem(
        route = PdfToolRoute.EXTRACT_ASSETS,
        title = "استخراج نصوص وصور",
        subtitle = "استخراج المحتوى النصي والصور المضمنة",
        icon = Icons.Filled.TextSnippet,
        accent = AccentOlive
    )
)

@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    activePdf: ActivePdfDocument?,
    recentFiles: List<RecentPdfRecord>,
    onCycleTheme: () -> Unit,
    onPickPdfAndOpenViewer: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onSelectTool: (PdfToolRoute) -> Unit,
    onOpenRecentFile: (RecentPdfRecord) -> Unit,
    onClearRecentFiles: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val gridColumns = when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Editorial Banner
                item(span = { GridItemSpan(gridColumns) }) {
                    HeroBannerHeader(
                        themeMode = themeMode,
                        onCycleTheme = onCycleTheme,
                        onPickPdf = onPickPdfAndOpenViewer,
                        onCreateSamplePdf = onCreateSamplePdf
                    )
                }

                // Active Document Quick Strip (if loaded)
                if (activePdf != null) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        ActiveFileQuickStrip(
                            activePdf = activePdf,
                            onOpenViewer = { onSelectTool(PdfToolRoute.VIEWER) }
                        )
                    }
                }

                // Section Title: Tools
                item(span = { GridItemSpan(gridColumns) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أدوات إدارة وتحرير PDF",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "${AllPdfTools.size} أداة",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 11 Core Tool Cards
                items(AllPdfTools, key = { it.route.route }) { tool ->
                    ToolGridCard(
                        tool = tool,
                        onClick = { onSelectTool(tool.route) }
                    )
                }

                // Recent Files Section
                item(span = { GridItemSpan(gridColumns) }) {
                    RecentFilesSection(
                        recentFiles = recentFiles,
                        onOpenRecent = onOpenRecentFile,
                        onClearAll = onClearRecentFiles
                    )
                }

                item(span = { GridItemSpan(gridColumns) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroBannerHeader(
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit
) {
    val themeIcon = when (themeMode) {
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
        ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
    }
    val themeDesc = when (themeMode) {
        ThemeMode.LIGHT -> "الوضع النهاري"
        ThemeMode.DARK -> "الوضع الليلي"
        ThemeMode.SYSTEM -> "تلقائي حسب النظام"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner_1795165380078),
                contentDescription = "خلفية تطبيق موعد اللوز بي دي اف",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Directional gradient scrim for crystal-clear Arabic typography readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xD8210B10),
                                Color(0xBB3A1019),
                                Color(0x66210B10)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Color(0x38E8B87D),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x66E8B87D))
                        ) {
                            Text(
                                text = "PDF MASTER",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFE5C2),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "موعد اللوز بي دي اف",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "المحترف لإدارة وتحرير وتحويل ملفات PDF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xE6FFF4EA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onCycleTheme,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0x33FFFFFF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(imageVector = themeIcon, contentDescription = themeDesc)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickPdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFF8F2),
                            contentColor = Color(0xFF7B1524)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("hero_open_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح ملف PDF", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onCreateSamplePdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x3BFFFFFF),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("hero_sample_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("ملف تجريبي")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFileQuickStrip(
    activePdf: ActivePdfDocument,
    onOpenViewer: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenViewer)
            .testTag("active_pdf_quick_strip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "الملف المفتوح حالياً: ${activePdf.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${activePdf.pageCount} صفحة • ${formatFileSizeArabic(activePdf.sizeBytes)} • اضغط للمعاينة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: HomeToolItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .testTag("tool_card_${tool.route.route}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(tool.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = tool.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (tool.badge != null) {
                    Surface(
                        color = tool.accent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = tool.badge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentFilesSection(
    recentFiles: List<RecentPdfRecord>,
    onOpenRecent: (RecentPdfRecord) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "الملفات الأخيرة",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (recentFiles.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_recent_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مسح السجل")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentFiles.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "سجل الملفات فارغ حالياً",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "كل ملف PDF تفتحه سيظهر هنا للوصول السريع لاحقاً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentFiles.forEach { record ->
                    Card(
                        onClick = { onOpenRecent(record) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${record.pageCount} صفحة • ${formatFileSizeArabic(record.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
