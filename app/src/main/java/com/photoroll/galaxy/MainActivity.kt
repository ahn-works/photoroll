package com.photoroll.galaxy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.photoroll.galaxy.data.pinterest.PinterestDownloader
import com.photoroll.galaxy.data.repository.WallpaperRepository
import com.photoroll.galaxy.scheduler.WallpaperScheduleReceiver
import com.photoroll.galaxy.ui.screens.DeckManagerScreen
import com.photoroll.galaxy.ui.screens.MainDashboardScreen
import com.photoroll.galaxy.ui.screens.PinterestImportDialog
import com.photoroll.galaxy.ui.screens.ScheduleSettingDialog
import com.photoroll.galaxy.ui.screens.StepWizardScreen
import com.photoroll.galaxy.ui.theme.PhotoRollTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy { WallpaperRepository.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 외부(핀터레스트/갤러리 공유)로부터 넘어온 이미지/URL 처리
        handleIncomingSharedIntent(intent)

        setContent {
            PhotoRollTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Wizard) }
                    var showScheduleDialog by remember { mutableStateOf(false) }
                    var showPinterestDialog by remember { mutableStateOf(false) }

                    when (currentScreen) {
                        is Screen.Wizard -> {
                            StepWizardScreen(
                                repository = repository,
                                onOpenClassicDashboard = { currentScreen = Screen.Dashboard }
                            )
                        }
                        is Screen.Dashboard -> {
                            MainDashboardScreen(
                                repository = repository,
                                onNavigateToDeck = { currentScreen = Screen.DeckManager },
                                onOpenScheduleDialog = { showScheduleDialog = true },
                                onOpenPinterestDialog = { showPinterestDialog = true },
                                onOpenWizard = { currentScreen = Screen.Wizard }
                            )
                        }
                        is Screen.DeckManager -> {
                            DeckManagerScreen(
                                repository = repository,
                                onBack = { currentScreen = Screen.Dashboard }
                            )
                        }
                    }

                    if (showScheduleDialog) {
                        ScheduleSettingDialog(
                            initialSettings = repository.getSettings(),
                            onDismiss = { showScheduleDialog = false },
                            onSave = { updatedSettings ->
                                repository.saveSettings(updatedSettings)
                                WallpaperScheduleReceiver.scheduleNextAlarm(this@MainActivity)
                                showScheduleDialog = false
                                Toast.makeText(this@MainActivity, "스케줄 설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (showPinterestDialog) {
                        PinterestImportDialog(
                            onDismiss = { showPinterestDialog = false },
                            onSaveBoardUrl = { url ->
                                if (url.isNotBlank()) {
                                    Toast.makeText(this@MainActivity, "핀터레스트 사진을 다운로드하는 중...", Toast.LENGTH_SHORT).show()
                                    lifecycleScope.launch {
                                        PinterestDownloader.downloadAndAddToDeck(
                                            context = this@MainActivity,
                                            rawTextOrUrl = url,
                                            repository = repository,
                                            onSuccess = {
                                                Toast.makeText(this@MainActivity, "핀터레스트 사진이 배경화면 덱에 추가되었습니다!", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { errMsg ->
                                                Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSharedIntent(intent)
    }

    private fun handleIncomingSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            // 1. 이미지 스트림 수신 (갤러리나 다른 앱에서 이미지 공유)
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        imageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {}

                repository.addPhotos(listOf(imageUri.toString()))
                Toast.makeText(this, "새 사진이 배경화면 덱에 추가되었습니다!", Toast.LENGTH_LONG).show()
                return
            }

            // 2. 텍스트 / 링크 수신 (핀터레스트 앱에서 [공유] 클릭 시)
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                Toast.makeText(this, "핀터레스트 사진을 가져오는 중입니다...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    PinterestDownloader.downloadAndAddToDeck(
                        context = this@MainActivity,
                        rawTextOrUrl = sharedText,
                        repository = repository,
                        onSuccess = {
                            Toast.makeText(this@MainActivity, "핀터레스트 사진이 성공적으로 등록되었습니다!", Toast.LENGTH_LONG).show()
                        },
                        onError = { errMsg ->
                            Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    sealed class Screen {
        object Wizard : Screen()
        object Dashboard : Screen()
        object DeckManager : Screen()
    }
}
