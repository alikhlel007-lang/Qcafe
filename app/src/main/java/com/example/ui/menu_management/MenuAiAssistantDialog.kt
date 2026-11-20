package com.example.ui.menu_management

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ai.AiActionType
import com.example.data.ai.AiAssistantUiState
import com.example.data.ai.MAX_DAILY_AI_REQUESTS
import com.example.data.ai.ProposedMenuAction
import com.example.ui.MainViewModel
import com.example.ui.theme.CafeCardBorder
import com.example.ui.theme.CafeCyanPrimary
import com.example.ui.theme.CafeGold
import com.example.ui.theme.CafeInputBg
import com.example.ui.theme.CafeInputBorder
import com.example.ui.theme.CafeSurface
import com.example.ui.theme.CafeSurfaceVariant
import com.example.ui.theme.CafeTextPrimary
import com.example.ui.theme.CafeTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuAiAssistantDialog(
    viewModel: MainViewModel,
    activeCategoryId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val aiUiState by viewModel.aiUiState.collectAsState()
    val dailyRemaining by viewModel.aiDailyRemaining.collectAsState()

    var commandText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.resetAiState()
            onDismiss()
        },
        containerColor = CafeSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CafeCyanPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "مساعد الذكاء الاصطناعي",
                            tint = CafeCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "مساعد الكاشير الذكي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CafeTextPrimary
                        )
                        Text(
                            text = "متبقي اليوم: $dailyRemaining / $MAX_DAILY_AI_REQUESTS طلب",
                            fontSize = 11.sp,
                            color = CafeGold,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.resetAiState()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("ai_dialog_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = CafeTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scoped-access security notice
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF042227),
                    border = BorderStroke(1.dp, CafeCyanPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CafeCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صلاحية محدودة للمنيو فقط: يقرأ المنيو الورقي ويعدّل الأسعار والصور دون أي وصول للبيانات السرية أو المالية.",
                            fontSize = 11.sp,
                            color = CafeTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Quick task presets
                val presets = listOf(
                    "إدخال منيو ورقي" to "اقرأ جميع الأقسام والأصناف والأسعار من صورة المنيو الورقي وجهزها للإضافة",
                    "تحديث أسعار" to "عدّل أسعار الأصناف التالية: ",
                    "ربط صورة بصنف" to "اربط هذه الصورة المرفقة بصنف: ",
                    "تحسين الوصف" to "اكتب وصفاً عربياً قصيراً وجذاباً للأصناف التي بدون وصف"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (label, template) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CafeSurfaceVariant)
                                .border(1.dp, CafeCardBorder, RoundedCornerShape(10.dp))
                                .clickable { commandText = template }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CafeCyanPrimary
                            )
                        }
                    }
                }

                // Image picker row (PickVisualMedia - zero storage permission)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_pick_image_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CafeCyanPrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "إرفاق صورة",
                            tint = CafeCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedImageUri != null) "تغيير الصورة المرفقة" else "إرفاق صورة منيو أو صنف",
                            fontSize = 12.sp,
                            color = CafeCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, CafeGold, RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "معاينة الصورة المرفقة",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC000000))
                                    .clickable { selectedImageUri = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("×", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Command text input
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    label = {
                        Text(
                            "اكتب ما تريد تنفيذه (اختياري عند إرفاق صورة منيو)",
                            color = CafeTextSecondary,
                            fontSize = 11.sp
                        )
                    },
                    placeholder = {
                        Text(
                            "مثال: أضف أصناف المنيو من الصورة، أو غيّر سعر الشاي إلى 1000 د.ع",
                            color = CafeTextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_command_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CafeInputBg,
                        unfocusedContainerColor = CafeInputBg,
                        focusedBorderColor = CafeCyanPrimary,
                        unfocusedBorderColor = CafeInputBorder,
                        focusedTextColor = CafeTextPrimary,
                        unfocusedTextColor = CafeTextPrimary
                    )
                )

                // Analyze action button
                Button(
                    onClick = {
                        viewModel.analyzeMenuWithAi(commandText, selectedImageUri)
                    },
                    enabled = aiUiState !is AiAssistantUiState.Loading &&
                        (commandText.isNotBlank() || selectedImageUri != null),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_analyze_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CafeCyanPrimary,
                        contentColor = Color(0xFF032125)
                    )
                ) {
                    if (aiUiState is AiAssistantUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF032125)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري التحليل وتجهيز التعديلات...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحليل وتجهيز التعديلات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // State output rendering
                when (val state = aiUiState) {
                    is AiAssistantUiState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0D0D)),
                            border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = state.message,
                                color = Color(0xFFFFB4B4),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    is AiAssistantUiState.Success -> {
                        HorizontalDivider(color = CafeCardBorder)

                        Text(
                            text = state.replyMessage,
                            color = CafeTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (state.actions.isNotEmpty()) {
                            Text(
                                text = "راجع الإجراءات المقترحة (${state.actions.count { it.isSelected }} من ${state.actions.size} محدد):",
                                color = CafeGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.actions, key = { it.id }) { action ->
                                    ProposedActionCard(
                                        action = action,
                                        onToggle = { viewModel.toggleAiProposedAction(action.id) }
                                    )
                                }
                            }
                        }
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            val successState = aiUiState as? AiAssistantUiState.Success
            val selectedCount = successState?.actions?.count { it.isSelected } ?: 0
            if (successState != null && selectedCount > 0) {
                Button(
                    onClick = {
                        viewModel.applySelectedAiActions(
                            fallbackCategoryId = activeCategoryId,
                            onComplete = { count ->
                                Toast.makeText(
                                    context,
                                    "تم تنفيذ $count تعديل على المنيو بنجاح",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CafeGold,
                        contentColor = Color(0xFF1A1200)
                    ),
                    modifier = Modifier.testTag("ai_apply_actions_btn")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تطبيق على المنيو ($selectedCount)", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.resetAiState()
                    onDismiss()
                }
            ) {
                Text("إغلاق", color = CafeTextSecondary)
            }
        }
    )
}

@Composable
private fun ProposedActionCard(
    action: ProposedMenuAction,
    onToggle: () -> Unit
) {
    val badgeColor = when (action.actionType) {
        AiActionType.ADD_CATEGORY -> CafeGold
        AiActionType.ADD_ITEM -> Color(0xFF4CAF50)
        AiActionType.UPDATE_PRICE -> CafeCyanPrimary
        AiActionType.ATTACH_UPLOADED_IMAGE -> Color(0xFFA855F7)
        else -> Color(0xFFF59E0B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (action.isSelected) CafeSurfaceVariant else CafeInputBg.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (action.isSelected) badgeColor.copy(alpha = 0.6f) else CafeCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = action.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = badgeColor,
                    uncheckedColor = CafeTextSecondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = action.actionType.labelAr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = action.summaryAr,
                    fontSize = 12.sp,
                    color = CafeTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
