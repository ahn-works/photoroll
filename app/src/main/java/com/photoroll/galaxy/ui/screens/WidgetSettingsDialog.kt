package com.photoroll.galaxy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.photoroll.galaxy.data.model.AppSettings
import com.photoroll.galaxy.ui.theme.SamsungBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsDialog(
    initialSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var enableFortuneCookie by remember { mutableStateOf(initialSettings.enableFortuneCookie) }
    var enableBubblePop by remember { mutableStateOf(initialSettings.enableBubblePop) }
    var enableVintageFilter by remember { mutableStateOf(initialSettings.enableVintageFilter) }

    var enableDDayBadge by remember { mutableStateOf(initialSettings.enableDDayBadge) }
    var ddayTitle by remember { mutableStateOf(initialSettings.ddayTitle) }
    var ddayTargetDate by remember { mutableStateOf(initialSettings.ddayTargetDate) }

    var enableMemoWidget by remember { mutableStateOf(initialSettings.enableMemoWidget) }
    var memoText by remember { mutableStateOf(initialSettings.memoText) }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Widgets,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "위젯 & 미니게임 설정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "잠금화면과 홈 화면 배경 위에 특별한 인터랙션을 더해보세요.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // 1. 미니게임 섹션
                Text("🎮 잠금화면 터치 미니게임", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SamsungBlue)
                Spacer(modifier = Modifier.height(10.dp))

                SettingToggleItem(
                    title = "🥠 행운의 포춘 쿠키",
                    description = "화면 우측 하단 쿠키를 탭하면 바삭 깨지며 오늘의 행운 운세가 뜹니다.",
                    checked = enableFortuneCookie,
                    onCheckedChange = { enableFortuneCookie = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleItem(
                    title = "🫧 버블 팝 힐링 터치",
                    description = "화면 빈 공간을 톡 건드릴 때마다 예쁜 비눗방울이 퐁퐁 솟아납니다.",
                    checked = enableBubblePop,
                    onCheckedChange = { enableBubblePop = it }
                )

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // 2. 감성 위젯 섹션
                Text("📅 D-Day & 메모 감성 위젯", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SamsungBlue)
                Spacer(modifier = Modifier.height(10.dp))

                SettingToggleItem(
                    title = "📅 D-Day 카운트다운 배지",
                    description = "화면 상단에 커플 100일, 수능, 최애 생일 D-Day를 띄웁니다.",
                    checked = enableDDayBadge,
                    onCheckedChange = { enableDDayBadge = it }
                )

                if (enableDDayBadge) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ddayTitle,
                        onValueChange = { ddayTitle = it },
                        label = { Text("기념일 이름 (예: 우리 만난 지, 수능)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ddayTargetDate,
                        onValueChange = { ddayTargetDate = it },
                        label = { Text("목표 날짜 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleItem(
                    title = "📝 포스트잇 한 줄 메모",
                    description = "화면 하단에 나만의 확언 문구나 오늘의 할 일을 띄웁니다.",
                    checked = enableMemoWidget,
                    onCheckedChange = { enableMemoWidget = it }
                )

                if (enableMemoWidget) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memoText,
                        onValueChange = { memoText = it },
                        label = { Text("표시할 한 줄 문구") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleItem(
                    title = "🎞️ 레트로 필름 빈티지 필터",
                    description = "모든 배경 사진에 은은한 필름 카메라 색감과 그레인을 입힙니다.",
                    checked = enableVintageFilter,
                    onCheckedChange = { enableVintageFilter = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = initialSettings.copy(
                                enableFortuneCookie = enableFortuneCookie,
                                enableBubblePop = enableBubblePop,
                                enableDDayBadge = enableDDayBadge,
                                ddayTitle = ddayTitle,
                                ddayTargetDate = ddayTargetDate,
                                enableMemoWidget = enableMemoWidget,
                                memoText = memoText,
                                enableVintageFilter = enableVintageFilter
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("설정 완료", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) SamsungBlue.copy(alpha = 0.08f) else Color.Transparent)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SamsungBlue)
        )
    }
}
