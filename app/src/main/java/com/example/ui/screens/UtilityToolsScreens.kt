package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ActivePdfDocument
import com.example.data.CompressionLevel
import com.example.ui.components.ActivePdfSelectorCard
import com.example.ui.components.ToolTopBar

@Composable
fun CompressPdfScreen(
    activePdf: ActivePdfDocument?,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onExecuteCompress: (CompressionLevel) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "ضغط ملف PDF",
                subtitle = "تقليل حجم الملف مع الحفاظ على جودة القراءة",
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

            if (activePdf != null && !activePdf.isPasswordProtected) {
                Text("اختر مستوى الضغط:", style = MaterialTheme.typography.titleMedium)

                CompressionLevel.entries.forEach { level ->
                    val isSelected = selectedLevel == level
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLevel = level }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedLevel = level }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.arabicLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    level.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onExecuteCompress(selectedLevel) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_compress_button")
                ) {
                    Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ضغط الملف الآن")
                }
            }
        }
    }
}

@Composable
fun SecurityPdfScreen(
    activePdf: ActivePdfDocument?,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onProtectPdf: (userPass: String, ownerPass: String, allowPrint: Boolean, allowCopy: Boolean) -> Unit,
    onUnlockPdf: (password: String) -> Unit
) {
    var selectedTab by remember(activePdf?.isPasswordProtected) {
        mutableIntStateOf(if (activePdf?.isPasswordProtected == true) 1 else 0)
    }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var unlockPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var allowPrinting by remember { mutableStateOf(true) }
    var allowCopying by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "الحماية وفك التشفير",
                subtitle = "تأمين ملفات PDF بكلمة مرور أو إزالة الحماية",
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
                    text = { Text("حماية بكلمة مرور") },
                    modifier = Modifier.testTag("security_tab_protect")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("فك حماية PDF") },
                    modifier = Modifier.testTag("security_tab_unlock")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ActivePdfSelectorCard(
                    activePdf = activePdf,
                    onPickPdf = onPickPdf,
                    onCreateSamplePdf = onCreateSamplePdf
                )

                if (activePdf != null) {
                    val visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                    if (selectedTab == 0) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("إعدادات التشفير (AES-128)", style = MaterialTheme.typography.titleMedium)

                                OutlinedTextField(
                                    value = userPassword,
                                    onValueChange = { userPassword = it },
                                    label = { Text("كلمة مرور فتح الملف (مطلوبة)") },
                                    visualTransformation = visualTransformation,
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = "إظهار أو إخفاء كلمة المرور"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("protect_user_password_input")
                                )

                                OutlinedTextField(
                                    value = ownerPassword,
                                    onValueChange = { ownerPassword = it },
                                    label = { Text("كلمة مرور المالك للتعديل (اختياري)") },
                                    visualTransformation = visualTransformation,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("السماح بطباعة المستند", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = allowPrinting, onCheckedChange = { allowPrinting = it })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("السماح بنسخ النصوص", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = allowCopying, onCheckedChange = { allowCopying = it })
                                }
                            }
                        }

                        Button(
                            onClick = { onProtectPdf(userPassword, ownerPassword, allowPrinting, allowCopying) },
                            enabled = userPassword.isNotBlank() && !activePdf.isPasswordProtected,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("execute_protect_button")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حماية وتشفير الملف")
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("فك حماية ملف تملك صلاحية تعديله", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "أدخل كلمة المرور الحالية لإنشاء نسخة مفتوحة بدون تشفير، دون المساس بالملف الأصلي.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = unlockPassword,
                                    onValueChange = { unlockPassword = it },
                                    label = { Text("كلمة المرور الحالية للملف") },
                                    visualTransformation = visualTransformation,
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = "إظهار أو إخفاء كلمة المرور"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unlock_password_input")
                                )
                            }
                        }

                        Button(
                            onClick = { onUnlockPdf(unlockPassword) },
                            enabled = unlockPassword.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("execute_unlock_button")
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فك الحماية وحفظ نسخة مفتوحة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractAssetsScreen(
    activePdf: ActivePdfDocument?,
    onNavigateBack: () -> Unit,
    onPickPdf: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onExecuteExtractAssets: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolTopBar(
                title = "استخراج النصوص والصور",
                subtitle = "استخرج كامل النصوص والصور المضمنة داخل PDF",
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

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ما الذي سيتم استخراجه؟", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "• جميع النصوص المقروءة في ملف TXT مع إمكانية النسخ المباشر والمعاينة الفورية.\n" +
                            "• جميع الصور المضمنة داخل صفحات PDF بدقتها الأصلية بصيغة PNG.\n" +
                            "• يتم حفظ النتائج في ملف نصي أو أرشيف ZIP أو في مجلد منفصل تختاره.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (activePdf != null && !activePdf.isPasswordProtected) {
                Button(
                    onClick = onExecuteExtractAssets,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_extract_assets_button")
                ) {
                    Icon(Icons.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استخراج النصوص والصور الآن")
                }
            }
        }
    }
}
