package com.photoroll.galaxy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoroll.galaxy.data.model.AppSettings
import com.photoroll.galaxy.ui.theme.SamsungBlue

@Composable
fun ScheduleSettingDialog(
    initialSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var settings by remember { mutableStateOf(initialSettings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = SamsungBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("세부 시간 & 인터랙션 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // 1. 매일 변경 시각 설정
                Text("매일 변경 시각 (Daily Fixed)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("시각: ", fontSize = 14.sp, color = Color.Gray)
                    listOf(7, 8, 9, 12, 18, 0).forEach { hour ->
                        FilterChip(
                            selected = settings.dailyHour == hour,
                            onClick = { settings = settings.copy(dailyHour = hour) },
                            label = { Text(if (hour == 0) "자정" else "${hour}시") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 2. 홈화면 더블 탭 제스처
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("홈 화면 빈 곳 더블 탭", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Text("두 번 연속 터치 시 다음 사진으로 스킵", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = settings.enableDoubleTapGesture,
                        onCheckedChange = { settings = settings.copy(enableDoubleTapGesture = it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 3. 아이콘 가독성용 어두운 오버레이 딤(Dim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("홈 아이콘 가독성 딤 오버레이: ${settings.dimOpacityPercent}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Slider(
                    value = settings.dimOpacityPercent.toFloat(),
                    onValueChange = { settings = settings.copy(dimOpacityPercent = it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(settings) },
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Text("저장하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
