package com.photoroll.galaxy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoroll.galaxy.ui.theme.SamsungBlue

@Composable
fun PinterestImportDialog(
    onDismiss: () -> Unit,
    onSaveBoardUrl: (String) -> Unit
) {
    var boardUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = SamsungBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("핀터레스트(Pinterest) 연동", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1초 다이렉트 추가 방법", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "핀터레스트 앱에서 원하는 핀을 보다가 [공유] → [PhotoRoll]을 선택하면 해당 고화질 사진이 즉시 배경화면 덱에 자동 등록됩니다.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("핀터레스트 보드 자동 동기화 (URL)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = boardUrl,
                    onValueChange = { boardUrl = it },
                    placeholder = { Text("https://pinterest.com/username/board", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("공개 보드 주소를 등록하면 새 핀이 올라올 때마다 자동 수집됩니다.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBoardUrl(boardUrl)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Text("연동 저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
