package com.photoroll.galaxy.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.photoroll.galaxy.data.model.*
import com.photoroll.galaxy.data.repository.WallpaperRepository
import com.photoroll.galaxy.scheduler.WallpaperScheduleReceiver
import com.photoroll.galaxy.service.PhotoRollWallpaperService
import com.photoroll.galaxy.ui.theme.SamsungBlue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainDashboardScreen(
    repository: WallpaperRepository,
    onNavigateToDeck: () -> Unit,
    onOpenScheduleDialog: () -> Unit,
    onOpenPinterestDialog: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(repository.getSettings()) }
    val photos = remember { mutableStateOf(repository.getPhotos()) }
    val currentPhoto = photos.value.firstOrNull()

    val scrollState = rememberScrollState()

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        repository.saveSettings(newSettings)
        WallpaperScheduleReceiver.scheduleNextAlarm(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = SamsungBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "PhotoRoll Galaxy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPinterestDialog) {
                        Icon(Icons.Default.Share, contentDescription = "핀터레스트 연동", tint = SamsungBlue)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // 1. 서비스 활성 토글 카드
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isEnabled) "다이내믹 배경화면 켜짐" else "배경화면 일시 정지",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (settings.isEnabled) "설정된 규칙에 맞춰 배경이 자동 교체됩니다" else "사진 로테이션이 멈춰 있습니다",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { isChecked ->
                            updateSettings(settings.copy(isEnabled = isChecked))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 실시간 폰 목업 프리뷰 (Galaxy Screen Mockup)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .border(3.dp, Color.DarkGray, RoundedCornerShape(28.dp))
                        .background(Color.Black)
                ) {
                    if (currentPhoto != null) {
                        AsyncImage(
                            model = currentPhoto.uriString,
                            contentDescription = "프리뷰",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "사진을 추가해 주세요",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // 가상 홈 화면 아이콘 오버레이
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 상단 시계 & 날짜
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("13:45", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("11월 5일 목요일", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }

                        // 하단 닥(Dock) 아이콘 시뮬레이션
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 적용 대상 선택 (Target Scope)
            Text("적용 대상", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TargetScope.values().forEach { scope ->
                    FilterChip(
                        selected = settings.targetScope == scope,
                        onClick = { updateSettings(settings.copy(targetScope = scope)) },
                        label = { Text(scope.title) },
                        leadingIcon = if (settings.targetScope == scope) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 교체 주기 트리거 선택 (Trigger Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("교체 조건 (트리거)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                TextButton(onClick = onOpenScheduleDialog) {
                    Text("세부 시간 설정 ⚙️", fontSize = 13.sp, color = SamsungBlue)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TriggerMode.values().forEach { mode ->
                    FilterChip(
                        selected = settings.triggerMode == mode,
                        onClick = { updateSettings(settings.copy(triggerMode = mode)) },
                        label = { Text(mode.title) },
                        leadingIcon = if (settings.triggerMode == mode) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 사진 순환 규칙 선택 (Rotation Rule)
            Text("사진 순환 방식", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RotationRule.values().forEach { rule ->
                    FilterChip(
                        selected = settings.rotationRule == rule,
                        onClick = { updateSettings(settings.copy(rotationRule = rule)) },
                        label = { Text(rule.title) },
                        leadingIcon = if (settings.rotationRule == rule) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. 하단 메뉴 버튼들
            Button(
                onClick = onNavigateToDeck,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Collections, contentDescription = null, tint = SamsungBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "사진 덱 관리 (${photos.value.size}장 등록됨)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 시스템 배경화면 적용 CTA 버튼
            Button(
                onClick = {
                    openSystemWallpaperChooser(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "갤럭시 배경화면으로 적용하기",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun openSystemWallpaperChooser(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, PhotoRollWallpaperService::class.java)
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // 일부 갤럭시 기종에서 기본 배경화면 피커로 대체
        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        context.startActivity(fallbackIntent)
    }
}
