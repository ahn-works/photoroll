package com.photoroll.galaxy.ui.screens

import android.content.Intent
import android.net.Uri
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
import coil.compose.AsyncImage
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.repository.WallpaperRepository
import com.photoroll.galaxy.ui.theme.AccentPink
import com.photoroll.galaxy.ui.theme.SamsungBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckManagerScreen(
    repository: WallpaperRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(repository.getPhotos()) }

    // 안드로이드 최신 Photo Picker 런처 (권한 요청 팝업 없이 갤러리 다중 선택)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val count = repository.importAndSavePhotosFromUris(uris)
            photos = repository.getPhotos()
            android.widget.Toast.makeText(context, "${count}장의 사진이 안전하게 등록되었습니다!", android.widget.Toast.LENGTH_SHORT).show()
        }
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
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp
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
        AsyncImage(
            model = photo.uriString,
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
                .background(Color.Black.copy(alpha = 0.6f))
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
                .background(Color.Black.copy(alpha = 0.6f))
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
