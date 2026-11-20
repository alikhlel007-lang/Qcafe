package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.model.AppUpdateConfig
import com.example.ui.theme.CafeCyanPrimary
import com.example.ui.theme.CafeGold
import com.example.ui.theme.CafeSurface
import com.example.ui.theme.CafeTextPrimary
import com.example.ui.theme.CafeTextSecondary

@Composable
fun AppUpdateDialog(
    config: AppUpdateConfig,
    onDismissOptional: () -> Unit
) {
    val context = LocalContext.current

    if (config.isForceUpdate) {
        BackHandler(enabled = true) {
            // Mandatory update blocks back navigation until user taps Update
        }
    }

    Dialog(
        onDismissRequest = {
            if (!config.isForceUpdate) {
                onDismissOptional()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !config.isForceUpdate,
            dismissOnClickOutside = !config.isForceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD902090B)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CafeSurface),
                border = BorderStroke(1.8.dp, if (config.isForceUpdate) CafeGold else CafeCyanPrimary),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        CafeCyanPrimary.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "تحديث متوفر",
                            tint = CafeCyanPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (config.isForceUpdate) "تحديث إلزامي للتطبيق" else "يتوفر تحديث جديد للتطبيق",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CafeTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFF06252B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CafeCyanPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = CafeGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val versionLabel = config.latestVersionName.ifBlank { "كود ${config.latestVersionCode}" }
                            Text(
                                text = "الإصدار الجديد: $versionLabel (الحالي: ${BuildConfig.VERSION_NAME})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CafeGold
                            )
                        }
                    }

                    if (config.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF04171A),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF0E3D44))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "ما الجديد في هذا التحديث:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CafeCyanPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = config.releaseNotes,
                                    fontSize = 12.sp,
                                    color = CafeTextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "يرجى تحديث التطبيق للحصول على أحدث الميزات وتحسينات الأداء والاستقرار.",
                            fontSize = 12.sp,
                            color = CafeTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val url = config.downloadUrl.trim()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر فتح رابط التحديث في المتصفح", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "رابط التحديث غير صالح، تواصل مع الإدارة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("app_update_download_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CafeCyanPrimary,
                            contentColor = Color(0xFF032125)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحديث التطبيق الآن",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (!config.isForceUpdate) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onDismissOptional,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("app_update_later_btn")
                        ) {
                            Text(
                                text = "لاحقاً",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CafeTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
