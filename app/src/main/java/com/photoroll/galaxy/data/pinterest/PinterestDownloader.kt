package com.photoroll.galaxy.data.pinterest

import android.content.Context
import android.net.Uri
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.model.PhotoSource
import com.photoroll.galaxy.data.repository.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object PinterestDownloader {

    /**
     * 핀터레스트 링크(pin.it 또는 pinterest.com)에서 고화질 원본 이미지 URL 추출 및 로컬 다운로드
     */
    suspend fun downloadAndAddToDeck(
        context: Context,
        rawTextOrUrl: String,
        repository: WallpaperRepository,
        onSuccess: (PhotoItem) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val extractedUrl = extractUrl(rawTextOrUrl)
                ?: return@withContext onError("유효한 핀터레스트 URL을 찾을 수 없습니다.")

            // 1. HTTP 리다이렉션 추적 및 HTML 파싱하여 og:image(고화질 핀 이미지) 추출
            val directImageUrl = resolveDirectImageUrl(extractedUrl)
                ?: return@withContext onError("핀 이미지 원본 링크를 추출할 수 없습니다.")

            // 2. 내부 저장소에 캐시 파일로 영구 저장 (URI 권한 만료 방지)
            val cacheDir = File(context.filesDir, "pinterest_photos").apply { mkdirs() }
            val outputFile = File(cacheDir, "pin_${UUID.randomUUID()}.jpg")

            val connection = URL(directImageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. 덱에 사진 추가
            val savedUri = Uri.fromFile(outputFile)
            val newPhoto = PhotoItem(
                uriString = savedUri.toString(),
                source = PhotoSource.PINTEREST
            )

            val currentPhotos = repository.getPhotos().toMutableList()
            currentPhotos.add(0, newPhoto) // 최신 추가 사진을 맨 앞에 배치
            repository.savePhotos(currentPhotos)

            withContext(Dispatchers.Main) {
                onSuccess(newPhoto)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onError("다운로드 실패: ${e.localizedMessage}")
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val regex = "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)".toRegex()
        return regex.find(text)?.value
    }

    private fun resolveDirectImageUrl(pinUrl: String): String? {
        var connection = URL(pinUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode in 300..399) {
            val redirectedUrl = connection.getHeaderField("Location")
            connection = URL(redirectedUrl).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
            connection.connect()
        }

        val html = connection.inputStream.bufferedReader().use { it.readText() }

        // og:image 태그 추출 (<meta property="og:image" content="...">)
        val ogImageRegex = "<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"".toRegex()
        val match = ogImageRegex.find(html)
        var imageUrl = match?.groups?.get(1)?.value

        // 핀터레스트 원본 고화질 교체 (236x -> originals 또는 736x)
        if (imageUrl != null) {
            imageUrl = imageUrl.replace("/236x/", "/originals/").replace("/474x/", "/originals/")
        }
        return imageUrl
    }
}
