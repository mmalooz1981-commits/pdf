package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.PageScopeMode
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.ToolTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToWordScreen(
    activePdf: ActivePdfDocument?,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onExecuteConvertToWord: (
        scopeMode: PageScopeMode,
        customPagesText: String,
        useCloudAiOcr: Boolean,
        includeImages: Boolean,
        includeHeaderFooter: Boolean
    ) -> Unit
) {
    var useCloudAiOcr by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(true) }
    var includeHeaderFooter by remember { mutableStateOf(true) }
    var scopeMode by remember { mutableStateOf(PageScopeMode.ALL) }
    var customPagesText by remember { mutableStateOf("1-3") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "تحويل PDF إلى Word",
                subtitle = "تصدير DOCX مع OCR سحابي وتنسيق عربي RTL",
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

            // Engine Highlight Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "محرك تحويل ذكي مع OCR للملفات الممسوحة ضوئياً",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "يحافظ على العناوين والأحجام والألوان، الجداول، الصور المضمنة، الرؤوس والتذييلات، الهوامش، واتجاه النص العربي RTL بدقة عالية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (activePdf != null && !activePdf.isPasswordProtected && activePdf.pageCount > 0) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("إعدادات التحويل إلى DOCX", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("محرك OCR السحابي الذكي (موصى به)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "يقرأ الصفحات الممسوحة ضوئياً ويستعيد الجداول والتنسيق البصري",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useCloudAiOcr,
                                onCheckedChange = { useCloudAiOcr = it },
                                modifier = Modifier.testTag("word_cloud_ocr_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("استخراج وتضمين الصور الأصلية", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "إدراج الصور الموجودة داخل ملف PDF في مستند Word",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = includeImages, onCheckedChange = { includeImages = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("حفظ رؤوس الصفحات وتذييلاتها", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "إدراج الرأس والتذييل تلقائياً داخل ملف DOCX",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = includeHeaderFooter, onCheckedChange = { includeHeaderFooter = it })
                        }

                        Text("نطاق الصفحات للتحويل:", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = scopeMode == PageScopeMode.ALL,
                                onClick = { scopeMode = PageScopeMode.ALL },
                                label = { Text("جميع الصفحات (${activePdf.pageCount})") }
                            )
                            FilterChip(
                                selected = scopeMode == PageScopeMode.CUSTOM_RANGE,
                                onClick = { scopeMode = PageScopeMode.CUSTOM_RANGE },
                                label = { Text("نطاق محدد") }
                            )
                        }
                        if (scopeMode == PageScopeMode.CUSTOM_RANGE) {
                            OutlinedTextField(
                                value = customPagesText,
                                onValueChange = { customPagesText = it },
                                label = { Text("أرقام الصفحات (مثال: 1-3، 5)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onExecuteConvertToWord(
                            scopeMode,
                            customPagesText,
                            useCloudAiOcr,
                            includeImages,
                            includeHeaderFooter
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_pdf_to_word_button")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (useCloudAiOcr) "تحويل إلى Word عبر OCR السحابي"
                        else "تحويل محلي سريع إلى Word (.docx)"
                    )
                }
            }
        }
    }
}
