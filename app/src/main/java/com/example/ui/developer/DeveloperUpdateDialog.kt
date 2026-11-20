package com.example.ui.developer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.CafeCyanPrimary
import com.example.ui.theme.CafeGold
import com.example.ui.theme.CafeInputBg
import com.example.ui.theme.CafeInputBorder
import com.example.ui.theme.CafeSurface
import com.example.ui.theme.CafeTextPrimary
import com.example.ui.theme.CafeTextSecondary

@Composable
fun DeveloperUpdateDialog(
    currentConfig: AdminAppUpdateConfig,
    onDismiss: () -> Unit,
    onPublish: (versionCode: Int, versionName: String, downloadUrl: String, notes: String, isForce: Boolean) -> Unit,
    onDisableUpdate: () -> Unit
) {
    val context = LocalContext.current
    val fallbackUrl = currentConfig.downloadUrl.ifBlank { BuildConfig.DEFAULT_UPDATE_URL }
    val suggestedCode = if (currentConfig.latestVersionCode > 0) {
        currentConfig.latestVersionCode.toString()
    } else if (BuildConfig.VERSION_CODE > 1) {
        BuildConfig.VERSION_CODE.toString()
    } else {
        ""
    }
    val suggestedName = currentConfig.latestVersionName.ifBlank { BuildConfig.VERSION_NAME }

    var versionCodeStr by remember(currentConfig) { mutableStateOf(suggestedCode) }
    var versionNameStr by remember(currentConfig) { mutableStateOf(suggestedName) }
    var downloadUrlStr by remember(currentConfig) { mutableStateOf(fallbackUrl) }
    var releaseNotesStr by remember(currentConfig) { mutableStateOf(currentConfig.releaseNotes) }
    var isForceUpdate by remember(currentConfig) { mutableStateOf(currentConfig.isForceUpdate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CafeSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = CafeCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إدارة تحديثات تطبيق الكاشير",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = CafeTextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentConfig.isUpdateActive) Color(0xFF0A2E1A) else Color(0xFF261D04),
                    border = BorderStroke(
                        1.dp,
                        if (currentConfig.isUpdateActive) Color(0xFF4CAF50) else CafeGold.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (currentConfig.isUpdateActive) {
                                "الحالة: إشعار التحديث مفعّل للنسخة ${currentConfig.latestVersionName} (كود ${currentConfig.latestVersionCode})"
                            } else {
                                "الحالة: إشعار التحديث متوقف حالياً (مثالي أثناء فحصك للنسخة على هاتفك قبل إطلاقها)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentConfig.isUpdateActive) Color(0xFF81C784) else CafeGold
                        )
                    }
                }

                // Helpful workflow hint for testing before release
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF041A1F),
                    border = BorderStroke(1.dp, Color(0xFF0E4752)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "طريقة العمل الموصى بها:\n1) نزّل النسخة الجديدة من GitHub وافحصها على هاتفك أولاً.\n2) اقرأ رقم الكود من (إعدادات التطبيق) ثم اكتبه هنا واضغط «نشر التحديث».",
                        fontSize = 11.sp,
                        color = CafeTextSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = versionCodeStr,
                        onValueChange = { input -> versionCodeStr = input.filter { it.isDigit() } },
                        label = { Text("كود الإصدار (رقم)", color = CafeTextSecondary, fontSize = 11.sp) },
                        placeholder = { Text("مثال: 115", color = CafeTextSecondary.copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dev_update_code_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CafeInputBg,
                            unfocusedContainerColor = CafeInputBg,
                            focusedBorderColor = CafeCyanPrimary,
                            unfocusedBorderColor = CafeInputBorder,
                            focusedTextColor = CafeTextPrimary,
                            unfocusedTextColor = CafeTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = versionNameStr,
                        onValueChange = { versionNameStr = it },
                        label = { Text("اسم الإصدار", color = CafeTextSecondary, fontSize = 11.sp) },
                        placeholder = { Text("1.0.15", color = CafeTextSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dev_update_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CafeInputBg,
                            unfocusedContainerColor = CafeInputBg,
                            focusedBorderColor = CafeCyanPrimary,
                            unfocusedBorderColor = CafeInputBorder,
                            focusedTextColor = CafeTextPrimary,
                            unfocusedTextColor = CafeTextPrimary
                        )
                    )
                }

                OutlinedTextField(
                    value = downloadUrlStr,
                    onValueChange = { downloadUrlStr = it },
                    label = { Text("رابط تحميل النسخة (GitHub Release / الموقع)", color = CafeTextSecondary, fontSize = 11.sp) },
                    placeholder = { Text("https://github.com/.../releases/download/v1.0/Q-Cafe-Cashier.apk", color = CafeTextSecondary.copy(alpha = 0.5f), fontSize = 10.sp) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dev_update_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CafeInputBg,
                        unfocusedContainerColor = CafeInputBg,
                        focusedBorderColor = CafeCyanPrimary,
                        unfocusedBorderColor = CafeInputBorder,
                        focusedTextColor = CafeTextPrimary,
                        unfocusedTextColor = CafeTextPrimary
                    )
                )

                if (downloadUrlStr.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val cleanUrl = downloadUrlStr.trim()
                            if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "أدخل رابطاً يبدأ بـ https://", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CafeCyanPrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CafeCyanPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختبار فتح رابط التحميل في المتصفح", fontSize = 11.sp, color = CafeCyanPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = releaseNotesStr,
                    onValueChange = { releaseNotesStr = it },
                    label = { Text("ملاحظات التحديث لأصحاب الكافتيريات (اختياري)", color = CafeTextSecondary, fontSize = 11.sp) },
                    placeholder = { Text("مثال: إضافة المساعد الذكي، استدعاء النادل، وتحسين السرعة", color = CafeTextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dev_update_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CafeInputBg,
                        unfocusedContainerColor = CafeInputBg,
                        focusedBorderColor = CafeCyanPrimary,
                        unfocusedBorderColor = CafeInputBorder,
                        focusedTextColor = CafeTextPrimary,
                        unfocusedTextColor = CafeTextPrimary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CafeInputBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isForceUpdate) "تحديث إجباري (إلزامي)" else "تحديث اختياري (يمكن تأجيله)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isForceUpdate) CafeGold else CafeTextPrimary
                        )
                        Text(
                            text = if (isForceUpdate) "لن يتمكن صاحب الكافتيريا من استخدام النسخة القديمة قبل التحديث" else "يظهر زر (لاحقاً) بجانب زر التحديث",
                            fontSize = 10.sp,
                            color = CafeTextSecondary
                        )
                    }
                    Switch(
                        checked = isForceUpdate,
                        onCheckedChange = { isForceUpdate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CafeGold)
                    )
                }

                if (currentConfig.isUpdateActive) {
                    OutlinedButton(
                        onClick = onDisableUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dev_disable_update_btn"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إيقاف إشعار التحديث مؤقتاً", fontSize = 12.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedCode = versionCodeStr.toIntOrNull()
                    if (parsedCode == null || parsedCode <= 0) {
                        Toast.makeText(context, "يرجى كتابة رقم كود الإصدار الجديد", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!downloadUrlStr.trim().startsWith("http")) {
                        Toast.makeText(context, "يرجى إدخال رابط تحميل صحيح يبدأ بـ https://", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onPublish(
                        parsedCode,
                        versionNameStr.trim().ifBlank { "1.0.$parsedCode" },
                        downloadUrlStr.trim(),
                        releaseNotesStr.trim(),
                        isForceUpdate
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CafeCyanPrimary,
                    contentColor = Color(0xFF032125)
                ),
                modifier = Modifier.testTag("dev_publish_update_btn")
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("نشر التحديث الآن", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = CafeTextSecondary)
            }
        }
    )
}
