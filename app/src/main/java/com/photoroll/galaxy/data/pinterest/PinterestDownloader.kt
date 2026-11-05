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

            // 3. 파일 유효성 검증 (정상 이미지 디코딩 가능한지 확인)
            val decodeOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(outputFile.absolutePath, decodeOptions)
            if (decodeOptions.outWidth <= 0 || decodeOptions.outHeight <= 0) {
                outputFile.delete()
                return@withContext onError("다운로드된 파일이 유효한 이미지가 아닙니다.")
            }

            // 4. 덱에 사진 추가
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
        var currentUrl = pinUrl
        var hops = 0
        var htmlContent = ""

        // 최대 5단계 리다이렉트 추적 (pin.it 단축 URL 대응)
        while (hops < 5) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Galaxy S24) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            connection.connect()

            val status = connection.responseCode
            if (status in 300..399) {
                val newUrl = connection.getHeaderField("Location") ?: break
                currentUrl = if (newUrl.startsWith("http")) newUrl else URL(URL(currentUrl), newUrl).toString()
                hops++
            } else if (status in 200..299) {
                htmlContent = connection.inputStream.bufferedReader().use { it.readText() }
                break
            } else {
                break
            }
        }

        if (htmlContent.isEmpty()) return null

        // og:image 태그 추출 (<meta property="og:image" content="...">)
        val ogImageRegex = "<meta\\s+property=[\"']og:image[\"']\\s+content=[\"']([^\"']+)[\"']".toRegex()
        val match = ogImageRegex.find(htmlContent)
        var imageUrl = match?.groups?.get(1)?.value

        // 핀터레스트 원본 고화질 교체 (236x -> originals 또는 736x)
        if (imageUrl != null) {
            imageUrl = imageUrl.replace("/236x/", "/originals/").replace("/474x/", "/originals/")
        }
        return imageUrl
    }
}
