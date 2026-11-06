package com.photoroll.galaxy.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.repository.WallpaperRepository
import com.photoroll.galaxy.ui.theme.AccentPink
import com.photoroll.galaxy.ui.theme.SamsungBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckManagerScreen(
    repository: WallpaperRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(repository.getPhotos()) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }

    fun processUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isImporting = true
        importProgress = "1 / ${uris.size}"
        coroutineScope.launch(Dispatchers.IO) {
            val count = repository.importAndSavePhotosFromUris(uris) { curr, tot ->
                importProgress = "$curr / $tot"
            }
            withContext(Dispatchers.Main) {
                photos = repository.getPhotos()
                isImporting = false
                if (count > 0) {
                    Toast.makeText(context, "${count}장의 사진이 안전하게 등록되었습니다!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "사진 등록에 실패했습니다. 파일 탐색기에서 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사진 덱 관리 (${photos.size}장)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isImporting = true
                        importProgress = "샘플 생성 중..."
                        coroutineScope.launch(Dispatchers.IO) {
                            val count = repository.loadCuratedSamplePhotos()
                            withContext(Dispatchers.Main) {
                                photos = repository.getPhotos()
                                isImporting = false
                                Toast.makeText(context, "${count}장의 감성 샘플이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "샘플 사진 채우기", tint = SamsungBlue)
                    }
                    IconButton(onClick = { filePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "파일에서 추가", tint = SamsungBlue)
                    }
                    IconButton(onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "사진 추가", tint = SamsungBlue)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = SamsungBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("갤러리에서 사진 추가")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("등록된 사진이 없습니다", color = Color.Gray, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("우측 하단 버튼을 눌러 갤러리에서 사진을 추가하세요", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoGridCard(
                            photo = photo,
                            onToggleFavorite = {
                                repository.toggleFavorite(photo.id)
                                photos = repository.getPhotos()
                            },
                            onDelete = {
                                repository.removePhoto(photo.id)
                                photos = repository.getPhotos()
                            }
                        )
                    }
                }
            }

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
                            Text("사진 최적화 등록 중...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(if (importProgress.isNotBlank()) importProgress else "잠시만 기다려 주세요", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridCard(
    photo: PhotoItem,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {
        val imageModel = remember(photo.uriString) {
            if (photo.uriString.startsWith("file://")) {
                java.io.File(android.net.Uri.parse(photo.uriString).path ?: "")
            } else {
                android.net.Uri.parse(photo.uriString)
            }
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageModel)
                .size(240, 320)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 상단 오버레이 (삭제 버튼)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "삭제", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // 하단 오버레이 (즐겨찾기 하트 버튼)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "즐겨찾기",
                tint = if (photo.isFavorite) AccentPink else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
