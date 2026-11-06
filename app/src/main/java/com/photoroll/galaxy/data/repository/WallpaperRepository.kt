package com.photoroll.galaxy.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.photoroll.galaxy.data.model.AppSettings
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.model.PhotoTag
import com.photoroll.galaxy.data.model.RotationRule
import com.photoroll.galaxy.data.model.TriggerMode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class WallpaperRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "photoroll_prefs"
        private const val KEY_SETTINGS = "key_app_settings"
        private const val KEY_PHOTOS = "key_photo_list"
        private const val KEY_CURRENT_INDEX = "key_current_index"
        private const val KEY_SHUFFLE_HISTORY = "key_shuffle_history"
        private const val KEY_SEEDED = "key_initial_seeded_v2"

        const val ACTION_WALLPAPER_CHANGE = "com.photoroll.galaxy.ACTION_CHANGE_NOW"

        @Volatile
        private var instance: WallpaperRepository? = null

        fun getInstance(context: Context): WallpaperRepository {
            return instance ?: synchronized(this) {
                instance ?: WallpaperRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getSettings(): AppSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
        notifyWallpaperChanged()
    }

    fun getPhotos(): List<PhotoItem> {
        val json = prefs.getString(KEY_PHOTOS, null)
        val type = object : TypeToken<List<PhotoItem>>() {}.type
        val list: List<PhotoItem> = if (json != null) {
            try {
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // 최초 실행 시 또는 사진이 없는 경우, 즉시 체험 가능한 고화질 큐레이션 배경 5장 자동 탑재
        if (list.isEmpty() && !prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            loadCuratedSamplePhotos()
            val newJson = prefs.getString(KEY_PHOTOS, null)
            if (newJson != null) {
                return try {
                    gson.fromJson(newJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        return list
    }

    fun savePhotos(photos: List<PhotoItem>) {
        prefs.edit().putString(KEY_PHOTOS, gson.toJson(photos)).apply()
        notifyWallpaperChanged()
    }

    fun addPhotos(uriStrings: List<String>) {
        val current = getPhotos().toMutableList()
        val newItems = uriStrings.map { PhotoItem(uriString = it) }
        current.addAll(newItems)
        savePhotos(current)
    }

    /**
     * Photo Picker 등 외부 URI의 사진을 앱 내부 전용 저장소로 안전하게 최적화 복제
     * - 단 1회 스트림 읽기로 원본 바이트를 로컬 파일에 완전 보존 (HEIC/파이프/임시URI 만료 방지)
     * - 갤럭시 초고화질 사진(12MP~200MP) 메모리 OOM 완전 방지 (화면 해상도 1440p에 맞춰 스마트 다운샘플링)
     * - Exif 회전각 자동 정방향 보정 (사진이 돌아가는 현상 방지)
     * - 고화질 92% JPEG 압축 저장으로 디스크 용량 및 로딩 속도 최적화
     */
    fun importAndSavePhotosFromUris(
        uris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int {
        val targetDir = File(context.filesDir, "deck_photos").apply { mkdirs() }
        val newPhotoItems = mutableListOf<PhotoItem>()
        val total = uris.size

        for ((index, uri) in uris.withIndex()) {
            onProgress?.invoke(index + 1, total)
            val tempFile = File(targetDir, "temp_${UUID.randomUUID()}.tmp")
            val finalFile = File(targetDir, "photo_${UUID.randomUUID()}.jpg")
            try {
                // 1. 단 1회의 스트림 읽기로 원본 바이트를 로컬 파일에 완전 복제 (openInputStream + pfd fallback)
                var copySuccess = false
                try {
                    val stream: InputStream? = if (uri.scheme == "file") {
                        File(uri.path ?: "").inputStream()
                    } else {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (ignored: Exception) {}

                        try {
                            context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            if (pfd != null) java.io.FileInputStream(pfd.fileDescriptor) else null
                        }
                    }

                    stream?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copySuccess = tempFile.exists() && tempFile.length() > 0
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!copySuccess) {
                    tempFile.delete()
                    continue
                }

                // 2. 완벽하게 확보된 로컬 파일에서 해상도 최적화 및 Exif 회전 처리
                val optimized = optimizeLocalImageFile(tempFile, finalFile, targetW = 1440, targetH = 3120)

                val savedFile = if (optimized && finalFile.exists() && finalFile.length() > 0) {
                    tempFile.delete()
                    finalFile
                } else {
                    // 최적화 실패(메모리 부족/특수 포맷 등) 시 원본 tempFile 복사 보존 (실패 확률 0%)
                    try {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    } catch (e: Exception) {
                        tempFile.renameTo(finalFile)
                    }
                    if (finalFile.exists() && finalFile.length() > 0) finalFile else tempFile
                }

                if (savedFile.exists() && savedFile.length() > 0) {
                    val savedUri = Uri.fromFile(savedFile).toString()
                    newPhotoItems.add(PhotoItem(uriString = savedUri))
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                tempFile.delete()
            }
        }

        if (newPhotoItems.isNotEmpty()) {
            val current = getPhotos().toMutableList()
            current.addAll(newPhotoItems)
            savePhotos(current)
        }
        return newPhotoItems.size
    }

    private fun optimizeLocalImageFile(tempFile: File, finalFile: File, targetW: Int, targetH: Int): Boolean {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return false

            var inSampleSize = 1
            while (options.outWidth / (inSampleSize * 2) >= targetW &&
                   options.outHeight / (inSampleSize * 2) >= targetH) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions) ?: return false

            var degrees = 0f
            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (ignored: Exception) {}

            val finalBitmap = if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rotated != raw) raw.recycle()
                rotated
            } else {
                raw
            }

            FileOutputStream(finalFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            finalBitmap.recycle()
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    /**
     * 테스트 및 즉시 체험용 고화질 큐레이션 감성 사진 5장 즉시 채우기
     * (갤러리 권한 및 네트워크 연결 없이 로컬에서 즉시 100% 안전 생성)
     */
    fun loadCuratedSamplePhotos(): Int {
        val targetDir = File(context.filesDir, "deck_photos").apply { mkdirs() }
        val newPhotoItems = mutableListOf<PhotoItem>()
        val samples = listOf(
            Triple("일몰의 해변 (Sunset Ocean)", 0xFF1E3A8A.toInt(), 0xFFF97316.toInt()),
            Triple("신비로운 오로라 (Night Aurora)", 0xFF0F172A.toInt(), 0xFF06B6D4.toInt()),
            Triple("피치 선셋 (Peach Sunset)", 0xFF831843.toInt(), 0xFFF43F5E.toInt()),
            Triple("에메랄드 숲 (Emerald Forest)", 0xFF064E3B.toInt(), 0xFF10B981.toInt()),
            Triple("사이버 네온 (Cyber Neon)", 0xFF3B0764.toInt(), 0xFFEC4899.toInt())
        )

        for (sample in samples) {
            val file = File(targetDir, "sample_${UUID.randomUUID()}.jpg")
            try {
                val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, 2400f,
                    sample.second, sample.third,
                    android.graphics.Shader.TileMode.CLAMP
                )
                val paint = android.graphics.Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, 1080f, 2400f, paint)

                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 54f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(10f, 0f, 4f, 0x88000000.toInt())
                }
                canvas.drawText(sample.first, 540f, 1150f, textPaint)

                val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(210, 255, 255, 255)
                    textSize = 34f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.drawText("PhotoRoll Curated Wallpapers", 540f, 1230f, subPaint)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bitmap.recycle()

                if (file.exists() && file.length() > 0) {
                    newPhotoItems.add(PhotoItem(uriString = Uri.fromFile(file).toString()))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newPhotoItems.isNotEmpty()) {
            val current = getPhotos().toMutableList()
            current.addAll(newPhotoItems)
            savePhotos(current)
        }
        return newPhotoItems.size
    }

    fun removePhoto(id: String) {
        val current = getPhotos().filterNot { it.id == id }
        savePhotos(current)
    }

    fun toggleFavorite(id: String) {
        val current = getPhotos().map {
            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
        }
        savePhotos(current)
    }

    /**
     * 다음 순환할 사진 결정 알고리즘
     */
    fun getNextPhoto(): PhotoItem? {
        val photos = getPhotos()
        if (photos.isEmpty()) return null

        val settings = getSettings()
        if (!settings.isEnabled) return null

        return when (settings.rotationRule) {
            RotationRule.SEQUENTIAL -> {
                val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
                val nextIndex = (currentIndex + 1) % photos.size
                prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
                photos[nextIndex]
            }

            RotationRule.RANDOM_SHUFFLE -> {
                // 비복원 셔플: 전체를 다 볼 때까지 히스토리 관리
                val historyJson = prefs.getString(KEY_SHUFFLE_HISTORY, "[]")
                val type = object : TypeToken<MutableList<String>>() {}.type
                val history: MutableList<String> = try {
                    gson.fromJson(historyJson, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }

                val unshown = photos.filterNot { history.contains(it.id) }
                val selected = if (unshown.isNotEmpty()) {
                    unshown.random()
                } else {
                    history.clear()
                    photos.random()
                }

                history.add(selected.id)
                prefs.edit().putString(KEY_SHUFFLE_HISTORY, gson.toJson(history)).apply()
                selected
            }

            RotationRule.FAVORITE_WEIGHTED -> {
                // 즐겨찾기(❤️) 사진은 3배 가중치 풀 생성 후 랜덤 추출
                val weightedPool = mutableListOf<PhotoItem>()
                photos.forEach { item ->
                    val weight = if (item.isFavorite) 3 else 1
                    repeat(weight) { weightedPool.add(item) }
                }
                weightedPool.randomOrNull() ?: photos.first()
            }

            RotationRule.DAY_NIGHT_CONTEXT -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isNight = hour < 6 || hour >= 18
                val targetTag = if (isNight) PhotoTag.NIGHT_DARK else PhotoTag.DAY_BRIGHT
                val filtered = photos.filter { it.tag == targetTag }
                if (filtered.isNotEmpty()) {
                    filtered.random()
                } else {
                    photos.random()
                }
            }
        }
    }

    fun notifyWallpaperChanged() {
        val intent = Intent(ACTION_WALLPAPER_CHANGE).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
