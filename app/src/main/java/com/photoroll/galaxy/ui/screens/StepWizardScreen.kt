package com.photoroll.galaxy.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photoroll.galaxy.data.model.*
import com.photoroll.galaxy.data.repository.WallpaperRepository
import com.photoroll.galaxy.scheduler.WallpaperScheduleReceiver
import com.photoroll.galaxy.service.PhotoRollWallpaperService
import com.photoroll.galaxy.ui.theme.AccentPink
import com.photoroll.galaxy.ui.theme.SamsungBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WizardStep {
    STEP_PHOTOS,    // 1단계: 사진 등록
    STEP_TARGET,    // 2단계: 적용 대상 (홈 / 잠금 / 둘 다)
    STEP_TRIGGER,   // 3단계: 언제 바뀔지 (켤 때마다 / 1시간 / 매일 등)
    STEP_ROTATION,  // 4단계: 순환 규칙 (랜덤 / 즐겨찾기 / 순차 등)
    STEP_COMPLETE   // 5단계: 완성 & 원터치 시스템 적용
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StepWizardScreen(
    repository: WallpaperRepository,
    onOpenClassicDashboard: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(WizardStep.STEP_PHOTOS) }
    var settings by remember { mutableStateOf(repository.getSettings()) }
    var photos by remember { mutableStateOf(repository.getPhotos()) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }

    fun processUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isImporting = true
        coroutineScope.launch(Dispatchers.IO) {
            val count = repository.importAndSavePhotosFromUris(uris) { curr, tot ->
                importProgress = "$curr / $tot"
            }
            withContext(Dispatchers.Main) {
                photos = repository.getPhotos()
                isImporting = false
                Toast.makeText(context, "${count}장의 사진이 안전하게 등록되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 포토 피커 런처 (Google Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    // 파일/갤러리 앨범 탐색기 런처 (GetMultipleContents 폴백)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        repository.saveSettings(newSettings)
        WallpaperScheduleReceiver.scheduleNextAlarm(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentStep) {
                            WizardStep.STEP_PHOTOS -> "1/4 사진 선택"
                            WizardStep.STEP_TARGET -> "2/4 적용 대상"
                            WizardStep.STEP_TRIGGER -> "3/4 교체 타이밍"
                            WizardStep.STEP_ROTATION -> "4/4 순환 방식"
                            WizardStep.STEP_COMPLETE -> "설정 완료"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (currentStep != WizardStep.STEP_PHOTOS) {
                        IconButton(onClick = {
                            currentStep = when (currentStep) {
                                WizardStep.STEP_TARGET -> WizardStep.STEP_PHOTOS
                                WizardStep.STEP_TRIGGER -> WizardStep.STEP_TARGET
                                WizardStep.STEP_ROTATION -> WizardStep.STEP_TRIGGER
                                WizardStep.STEP_COMPLETE -> WizardStep.STEP_ROTATION
                                else -> WizardStep.STEP_PHOTOS
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "이전 단계")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onOpenClassicDashboard) {
                        Text("전체 메뉴", color = SamsungBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut()
                        )
                    }
                },
                label = "WizardFlowAnimation"
            ) { step ->
                when (step) {
                    WizardStep.STEP_PHOTOS -> {
                        StepPhotosView(
                            photos = photos,
                            onPickPhotos = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = {
                                filePickerLauncher.launch("image/*")
                            },
                            onDeletePhoto = { id ->
                                repository.removePhoto(id)
                                photos = repository.getPhotos()
                            },
                            onToggleFav = { id ->
                                repository.toggleFavorite(id)
                                photos = repository.getPhotos()
                            },
                            onNext = { currentStep = WizardStep.STEP_TARGET }
                        )
                    }

                    WizardStep.STEP_TARGET -> {
                        StepTargetView(
                            selectedScope = settings.targetScope,
                            onSelect = { scope ->
                                updateSettings(settings.copy(targetScope = scope))
                                currentStep = WizardStep.STEP_TRIGGER
                            }
                        )
                    }

                    WizardStep.STEP_TRIGGER -> {
                        StepTriggerView(
                            selectedTrigger = settings.triggerMode,
                            onSelect = { trigger ->
                                updateSettings(settings.copy(triggerMode = trigger))
                                currentStep = WizardStep.STEP_ROTATION
                            }
                        )
                    }

                    WizardStep.STEP_ROTATION -> {
                        StepRotationView(
                            selectedRule = settings.rotationRule,
                            onSelect = { rule ->
                                updateSettings(settings.copy(rotationRule = rule))
                                currentStep = WizardStep.STEP_COMPLETE
                            }
                        )
                    }

                    WizardStep.STEP_COMPLETE -> {
                        StepCompleteView(
                            photos = photos,
                            settings = settings,
                            onApplyWallpaper = {
                                openSystemWallpaperChooser(context)
                            },
                            onRestart = { currentStep = WizardStep.STEP_PHOTOS }
                        )
                    }
                }
            }

            // 사진 임포트 진행 중 다이얼로그
            if (isImporting) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = SamsungBlue)
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                "사진 최적화 등록 중...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (importProgress.isNotBlank()) importProgress else "잠시만 기다려 주세요",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1단계: 사진 등록
 */
@Composable
private fun StepPhotosView(
    photos: List<PhotoItem>,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "배경화면으로 쓸 사진들을\n골라주세요",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "반려동물, 여행, 가족, 최애 사진 등 마음에 드는 사진을 한 번에 여러 장 골라보세요.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. 큰 사진 추가 카드 버튼 (포토 피커)
            Card(
                onClick = onPickPhotos,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SamsungBlue.copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SamsungBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = SamsungBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "갤러리에서 사진 추가하기 (추천)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SamsungBlue
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "터치하여 원하는 사진들을 선택하세요",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 다른 앨범 또는 파일 탐색기 선택 버튼
            OutlinedButton(
                onClick = onPickFiles,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("다른 앨범 또는 파일에서 선택", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 💡 여러 장 선택 꿀팁 안내 카드
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "여러 장을 고를 땐 첫 번째 사진을 1초간 꾹 누르면 다중 선택 체크박스가 뜹니다!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 등록된 사진 썸네일 캐러셀
            if (photos.isNotEmpty()) {
                Text(
                    "현재 담긴 사진 (${photos.size}장)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(photos, key = { it.id }) { photo ->
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.uriString)
                                    .size(240, 320)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // 삭제 버튼
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { onDeletePhoto(photo.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                            // 하트 버튼
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { onToggleFav(photo.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (photo.isFavorite) AccentPink else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 하단 다음 버튼 (사진이 1장 이상 등록되면 활성화)
        Button(
            onClick = onNext,
            enabled = photos.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SamsungBlue,
                disabledContainerColor = Color.DarkGray
            )
        ) {
            Text(
                if (photos.isEmpty()) "사진을 1장 이상 추가해 주세요" else "다음 단계로 가기 (어디에 적용할까요?) ➔",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 2단계: 적용 대상 선택
 */
@Composable
private fun StepTargetView(
    selectedScope: TargetScope,
    onSelect: (TargetScope) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "어느 화면에\n적용할까요?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "잠금화면과 홈 화면 모두에 적용할 수도 있고, 홈 화면만 바꿀 수도 있어요.",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        OptionCard(
            title = "홈 화면 & 잠금화면 둘 다 (⭐ 추천)",
            description = "폰을 켤 때도, 잠금을 풀어 홈으로 들어갈 때도 사진이 함께 순환합니다.",
            icon = Icons.Default.Smartphone,
            isSelected = selectedScope == TargetScope.HOME_AND_LOCK_SYNC,
            onClick = { onSelect(TargetScope.HOME_AND_LOCK_SYNC) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        OptionCard(
            title = "홈 화면만 적용",
            description = "잠금화면은 삼성 기본을 유지하고, 앱 아이콘이 있는 홈 화면만 바꿉니다.",
            icon = Icons.Default.Home,
            isSelected = selectedScope == TargetScope.HOME_ONLY,
            onClick = { onSelect(TargetScope.HOME_ONLY) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        OptionCard(
            title = "잠금화면만 적용",
            description = "잠금화면 배경화면만 등록된 사진들로 자동 교체합니다.",
            icon = Icons.Default.Lock,
            isSelected = selectedScope == TargetScope.LOCK_ONLY,
            onClick = { onSelect(TargetScope.LOCK_ONLY) }
        )
    }
}

/**
 * 3단계: 교체 조건 (언제 바뀔지)
 */
@Composable
private fun StepTriggerView(
    selectedTrigger: TriggerMode,
    onSelect: (TriggerMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "사진은 언제\n바뀔까요?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "가장 편한 교체 타이밍을 하나 선택해 주세요.",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(28.dp))

        OptionCard(
            title = "화면 켤 때마다 (⚡ 가장 인기)",
            description = "전원 버튼을 눌러 화면을 켤 때마다 새로운 사진으로 즉시 바뀝니다.",
            icon = Icons.Default.FlashOn,
            isSelected = selectedTrigger == TriggerMode.SCREEN_ON,
            onClick = { onSelect(TriggerMode.SCREEN_ON) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "1시간마다",
            description = "너무 자주 바뀌지 않고 1시간 간격으로 차분하게 변경됩니다.",
            icon = Icons.Default.AccessTime,
            isSelected = selectedTrigger == TriggerMode.INTERVAL_1H,
            onClick = { onSelect(TriggerMode.INTERVAL_1H) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "매일 아침 출근길 (08:00)",
            description = "하루에 한 번, 매일 아침 새로운 기분으로 배경을 맞이합니다.",
            icon = Icons.Default.WbSunny,
            isSelected = selectedTrigger == TriggerMode.DAILY_FIXED,
            onClick = { onSelect(TriggerMode.DAILY_FIXED) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "주말 / 평일 분리 모드 (🌴 주말 특화)",
            description = "평일엔 차분하게, 토/일 주말엔 활기찬 앨범으로 자동 스위칭됩니다.",
            icon = Icons.Default.Weekend,
            isSelected = selectedTrigger == TriggerMode.WEEKEND_SEPARATE,
            onClick = { onSelect(TriggerMode.WEEKEND_SEPARATE) }
        )
    }
}

/**
 * 4단계: 순환 규칙 (어떤 순서로)
 */
@Composable
private fun StepRotationView(
    selectedRule: RotationRule,
    onSelect: (RotationRule) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "어떤 순서로\n보여드릴까요?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "사진이 노출되는 알고리즘을 선택해 주세요.",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(28.dp))

        OptionCard(
            title = "랜덤 셔플 (🔀 골고루)",
            description = "모든 사진이 한 번씩 다 나올 때까지 중복 없이 고르게 순환합니다.",
            icon = Icons.Default.Shuffle,
            isSelected = selectedRule == RotationRule.RANDOM_SHUFFLE,
            onClick = { onSelect(RotationRule.RANDOM_SHUFFLE) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "하트(❤️) 누른 최애 사진 3배 더 자주 (⭐ 추천)",
            description = "즐겨찾기 하트를 누른 최애 사진이 일반 사진보다 3배 더 자주 등장합니다.",
            icon = Icons.Default.Favorite,
            isSelected = selectedRule == RotationRule.FAVORITE_WEIGHTED,
            onClick = { onSelect(RotationRule.FAVORITE_WEIGHTED) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "등록한 순서대로 (1번부터 순차 재생)",
            description = "내가 앨범에 담아둔 순서대로 차례차례 재생됩니다.",
            icon = Icons.Default.FormatListNumbered,
            isSelected = selectedRule == RotationRule.SEQUENTIAL,
            onClick = { onSelect(RotationRule.SEQUENTIAL) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionCard(
            title = "낮과 밤 감성 테마 (주간/야간 자동 매칭)",
            description = "낮엔 밝고 화사하게, 밤엔 눈부심 없는 감성 다크 톤으로 자동 선별합니다.",
            icon = Icons.Default.NightsStay,
            isSelected = selectedRule == RotationRule.DAY_NIGHT_CONTEXT,
            onClick = { onSelect(RotationRule.DAY_NIGHT_CONTEXT) }
        )
    }
}

/**
 * 5단계: 완성 & 시스템 적용
 */
@Composable
private fun StepCompleteView(
    photos: List<PhotoItem>,
    settings: AppSettings,
    onApplyWallpaper: () -> Unit,
    onRestart: () -> Unit
) {
    val currentPhoto = photos.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "모든 준비가 끝났습니다! 🎉",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${photos.size}장의 사진이 '${settings.triggerMode.title}'로 작동합니다.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 실시간 폰 목업
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(310.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .border(3.dp, SamsungBlue, RoundedCornerShape(30.dp))
                    .background(Color.Black)
            ) {
                if (currentPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentPhoto.uriString)
                            .size(360, 640)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 스마트폰 오버레이 시계 UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "14:45",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            "11월 6일 금요일",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }

                    // 하단 독 아이콘 시뮬레이션
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

        Column(modifier = Modifier.fillMaxWidth()) {
            // 적용 안내 카드
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "📱 배경화면 적용 안내",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = SamsungBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "아래 버튼을 누르면 갤럭시 시스템 설정창이 열립니다. [홈 화면 및 잠금화면 설정]을 누르시면 화면을 켤 때마다 사진들이 자동으로 교체됩니다!",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = onApplyWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "갤럭시 배경화면으로 바로 적용하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("처음부터 다시 설정하기", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

/**
 * 큼직하고 미려한 선택형 카드 컴포넌트
 */
@Composable
private fun OptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SamsungBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, SamsungBlue) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) SamsungBlue else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else SamsungBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isSelected) SamsungBlue else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 17.sp
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SamsungBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
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
        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        context.startActivity(fallbackIntent)
    }
}
