package com.photoroll.galaxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import com.photoroll.galaxy.data.model.DisplayFit
import com.photoroll.galaxy.data.model.TriggerMode
import com.photoroll.galaxy.data.repository.WallpaperRepository
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

// 🫧 버블 팝 인터랙션 파티클 모델 (Top-level)
internal data class WallpaperBubble(
    val x: Float,
    val y: Float,
    var radius: Float = 15f,
    var alpha: Int = 230,
    val color: Int = Color.CYAN
)

class PhotoRollWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return PhotoRollEngine()
    }

    inner class PhotoRollEngine : Engine() {

        private val repository by lazy { WallpaperRepository.getInstance(applicationContext) }
        private val handler = Handler(Looper.getMainLooper())

        private var currentBitmap: Bitmap? = null
        private var nextBitmap: Bitmap? = null
        private var transitionAlpha = 1f
        private var isTransitioning = false

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dimPaint = Paint().apply { color = Color.BLACK }

        // 감성 위젯 & 미니게임 전용 페인트
        private val widgetBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val widgetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private var surfaceWidth = 1080
        private var surfaceHeight = 2400

        // 🫧 버블 팝 인터랙션 파티클
        private val activeBubbles = mutableListOf<WallpaperBubble>()
        private var isBubbleLoopRunning = false

        // 🥠 포춘 쿠키 미니게임 상태
        private var showFortune: Boolean = false
        private var currentFortuneText: String = ""
        private var fortuneExpireTime: Long = 0L
        private val fortunes = listOf(
            "오늘 당신에게 뜻밖의 기분 좋은 행운이 찾아옵니다 ✨",
            "당신의 미소가 오늘 누군가에게 큰 힘이 됩니다 💖",
            "잠시 쉬어가도 괜찮아요. 이미 충분히 잘하고 있어요 🍀",
            "원하는 일들이 하나씩 술술 풀리는 최고의 하루가 될 거예요 🌟",
            "오늘 마주치는 모든 순간들이 특별한 기적으로 변합니다 🎈",
            "자신을 믿으세요. 당신은 생각보다 훨씬 더 멋진 사람입니다 ☀️",
            "기적 같은 일은 늘 가장 평범한 순간에 찾아옵니다 🎁",
            "마음먹은 대로 모든 것이 이루어지는 마법 같은 날 🌱",
            "오늘 하루 당신이 걸어가는 모든 길에 행운이 함께합니다 🌈",
            "소중한 사람과의 따뜻한 대화가 기다리고 있어요 ☕"
        )

        // 더블 탭 제스처 감지기
        private val gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val settings = repository.getSettings()
                if (settings.enableDoubleTapGesture) {
                    rotateToNextPhoto()
                    return true
                }
                return super.onDoubleTap(e)
            }
        })

        // 외부(스케줄러 또는 UI)로부터 사진 변경 명령 수신
        private val changeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WallpaperRepository.ACTION_WALLPAPER_CHANGE) {
                    rotateToNextPhoto()
                }
            }
        }

        override fun onCreate(surfaceHolder: android.view.SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter(WallpaperRepository.ACTION_WALLPAPER_CHANGE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(changeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(changeReceiver, filter)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(changeReceiver)
            } catch (ignored: Exception) {}
            recycleBitmaps()
        }

        override fun onSurfaceChanged(
            holder: android.view.SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            if (currentBitmap == null) {
                rotateToNextPhoto()
            } else {
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: android.view.SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            handler.removeCallbacksAndMessages(null)
            isTransitioning = false
            isBubbleLoopRunning = false
            activeBubbles.clear()
            recycleBitmaps()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val settings = repository.getSettings()
                if (settings.triggerMode == TriggerMode.SCREEN_ON) {
                    rotateToNextPhoto()
                } else {
                    drawFrame()
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null) return
            gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN) {
                val settings = repository.getSettings()
                val tx = event.x
                val ty = event.y

                // 1. 포춘 쿠키 버튼 터치 영역 판별 (화면 우측 하단 80dp 반경)
                val cookieCenterX = surfaceWidth - 110f
                val cookieCenterY = surfaceHeight - 240f
                val dist = Math.hypot((tx - cookieCenterX).toDouble(), (ty - cookieCenterY).toDouble())

                if (settings.enableFortuneCookie && dist <= 70f) {
                    // 포춘 쿠키 탭 -> 바삭 깨지며 운세 카드 노출 (4.5초간)
                    currentFortuneText = fortunes.random()
                    showFortune = true
                    fortuneExpireTime = System.currentTimeMillis() + 4500L
                    drawFrame()
                    return
                }

                // 2. 버블 팝 힐링 인터랙션 (화면 빈 공간 터치 시 방울 생성)
                if (settings.enableBubblePop) {
                    val colors = listOf(Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.WHITE)
                    activeBubbles.add(WallpaperBubble(tx, ty, radius = 15f, alpha = 230, color = colors.random()))
                    if (!isBubbleLoopRunning) {
                        startBubbleAnimationLoop()
                    }
                }
            }
        }

        private fun startBubbleAnimationLoop() {
            isBubbleLoopRunning = true
            val bubbleRunnable = object : Runnable {
                override fun run() {
                    if (activeBubbles.isEmpty()) {
                        isBubbleLoopRunning = false
                        drawFrame()
                        return
                    }

                    val iterator = activeBubbles.iterator()
                    while (iterator.hasNext()) {
                        val b = iterator.next()
                        b.radius += 8f
                        b.alpha = (b.alpha - 30).coerceAtLeast(0)
                        if (b.alpha <= 0) {
                            iterator.remove()
                        }
                    }

                    drawFrame()
                    if (activeBubbles.isNotEmpty()) {
                        handler.postDelayed(this, 30L)
                    } else {
                        isBubbleLoopRunning = false
                    }
                }
            }
            handler.post(bubbleRunnable)
        }

        fun rotateToNextPhoto() {
            val photoItem = repository.getNextPhoto() ?: return
            loadBitmapFromUri(Uri.parse(photoItem.uriString)) { loadedBitmap ->
                if (loadedBitmap != null) {
                    startCrossfade(loadedBitmap)
                }
            }
        }

        private fun startCrossfade(newBitmap: Bitmap) {
            val settings = repository.getSettings()
            if (currentBitmap == null || settings.transitionDurationMs <= 0) {
                currentBitmap?.recycle()
                currentBitmap = newBitmap
                drawFrame()
                return
            }

            nextBitmap = newBitmap
            isTransitioning = true
            transitionAlpha = 0f

            val stepDuration = 20L
            val totalSteps = (settings.transitionDurationMs / stepDuration).coerceAtLeast(1)
            val alphaIncrement = 1f / totalSteps

            val animationRunnable = object : Runnable {
                override fun run() {
                    transitionAlpha += alphaIncrement
                    if (transitionAlpha >= 1f) {
                        transitionAlpha = 1f
                        isTransitioning = false
                        currentBitmap?.recycle()
                        currentBitmap = nextBitmap
                        nextBitmap = null
                        drawFrame()
                    } else {
                        drawFrame()
                        handler.postDelayed(this, stepDuration)
                    }
                }
            }
            handler.post(animationRunnable)
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderToCanvas(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun renderToCanvas(canvas: Canvas) {
            val settings = repository.getSettings()

            // 빈티지 필터 ColorFilter 설정
            if (settings.enableVintageFilter) {
                val vintageMatrix = ColorMatrix().apply {
                    set(floatArrayOf(
                        1.05f, 0.05f, 0.0f, 0.0f, 15f,
                        0.05f, 0.95f, 0.0f, 0.0f, 10f,
                        0.0f, 0.05f, 0.85f, 0.0f, 5f,
                        0.0f, 0.0f, 0.0f, 1.0f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(vintageMatrix)
            } else {
                paint.colorFilter = null
            }

            // 1. 현재 비트맵 렌더링
            currentBitmap?.let { bmp ->
                paint.alpha = 255
                drawScaledBitmap(canvas, bmp, settings.displayFit)
            }

            // 2. 크로스페이드 중인 다음 비트맵 렌더링
            if (isTransitioning && nextBitmap != null) {
                paint.alpha = (transitionAlpha * 255).toInt().coerceIn(0, 255)
                drawScaledBitmap(canvas, nextBitmap!!, settings.displayFit)
            }

            // 3. 아이콘 가독성용 어두운 오버레이 레이어 (0~50%)
            if (settings.dimOpacityPercent > 0) {
                val alpha = ((settings.dimOpacityPercent / 100f) * 255).toInt().coerceIn(0, 255)
                dimPaint.alpha = alpha
                canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), dimPaint)
            }

            // ==========================================
            // 🌟 4. v2.0 감성 오버레이 위젯 렌더링 레이어
            // ==========================================

            // 4-1. D-Day 카운트다운 배지 (화면 상단 중앙)
            if (settings.enableDDayBadge) {
                drawDDayBadge(canvas, settings.ddayTitle, settings.ddayTargetDate)
            }

            // 4-2. 포스트잇 한 줄 메모 / 다짐 (화면 하단 중앙)
            if (settings.enableMemoWidget && settings.memoText.isNotBlank()) {
                drawMemoBanner(canvas, settings.memoText)
            }

            // 4-3. 🥠 행운의 포춘 쿠키 미니게임 버튼 & 팝업
            if (settings.enableFortuneCookie) {
                drawFortuneCookie(canvas)
            }

            // 4-4. 🫧 버블 팝 인터랙션 파티클
            if (activeBubbles.isNotEmpty()) {
                for (b in activeBubbles) {
                    bubblePaint.color = b.color
                    bubblePaint.alpha = b.alpha
                    canvas.drawCircle(b.x, b.y, b.radius, bubblePaint)
                }
            }
        }

        private fun drawDDayBadge(canvas: Canvas, title: String, targetDateStr: String) {
            val ddayText = calculateDDay(targetDateStr)
            val fullText = "$title • $ddayText"

            textPaint.textSize = 34f
            textPaint.color = Color.WHITE
            val textWidth = textPaint.measureText(fullText)
            val boxW = textWidth + 70f
            val boxH = 68f
            val left = (surfaceWidth - boxW) / 2f
            val top = surfaceHeight * 0.08f

            // 반투명 글래스모피즘 박스
            widgetBgPaint.color = Color.argb(150, 20, 25, 35)
            val rect = RectF(left, top, left + boxW, top + boxH)
            canvas.drawRoundRect(rect, 34f, 34f, widgetBgPaint)

            widgetBorderPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawRoundRect(rect, 34f, 34f, widgetBorderPaint)

            // 텍스트 출력
            canvas.drawText(fullText, surfaceWidth / 2f, top + 46f, textPaint)
        }

        private fun calculateDDay(dateStr: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val targetDate = sdf.parse(dateStr) ?: return "D-Day"
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val targetCal = Calendar.getInstance().apply {
                    time = targetDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffDays = ((targetCal.timeInMillis - todayCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                when {
                    diffDays == 0 -> "D-Day"
                    diffDays > 0 -> "D-$diffDays"
                    else -> "D+${-diffDays}"
                }
            } catch (e: Exception) {
                "D-Day"
            }
        }

        private fun drawMemoBanner(canvas: Canvas, memo: String) {
            textPaint.textSize = 32f
            textPaint.color = Color.argb(240, 255, 255, 255)
            val textW = textPaint.measureText(memo)
            val boxW = (textW + 60f).coerceAtMost(surfaceWidth * 0.9f)
            val boxH = 62f
            val left = (surfaceWidth - boxW) / 2f
            val top = surfaceHeight * 0.82f

            widgetBgPaint.color = Color.argb(140, 15, 20, 30)
            val rect = RectF(left, top, left + boxW, top + boxH)
            canvas.drawRoundRect(rect, 24f, 24f, widgetBgPaint)

            canvas.drawText(memo, surfaceWidth / 2f, top + 42f, textPaint)
        }

        private fun drawFortuneCookie(canvas: Canvas) {
            val cx = surfaceWidth - 110f
            val cy = surfaceHeight - 240f

            // 포춘 쿠키 버튼
            widgetBgPaint.color = Color.argb(200, 245, 166, 35) // 골든 오렌지
            canvas.drawCircle(cx, cy, 42f, widgetBgPaint)

            textPaint.textSize = 32f
            textPaint.color = Color.BLACK
            canvas.drawText("🥠", cx, cy + 12f, textPaint)

            // 운세가 열렸을 때 (화면 중앙에 행운 카드 표시)
            if (showFortune && System.currentTimeMillis() < fortuneExpireTime) {
                val cardW = surfaceWidth * 0.85f
                val cardH = 170f
                val cardLeft = (surfaceWidth - cardW) / 2f
                val cardTop = (surfaceHeight - cardH) / 2f

                widgetBgPaint.color = Color.argb(235, 18, 22, 32)
                val cardRect = RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)
                canvas.drawRoundRect(cardRect, 32f, 32f, widgetBgPaint)

                widgetBorderPaint.color = Color.argb(200, 245, 166, 35)
                canvas.drawRoundRect(cardRect, 32f, 32f, widgetBorderPaint)

                textPaint.textSize = 28f
                textPaint.color = Color.argb(220, 245, 166, 35)
                canvas.drawText("✨ 오늘의 포춘 메시지 ✨", surfaceWidth / 2f, cardTop + 55f, textPaint)

                textPaint.textSize = 34f
                textPaint.color = Color.WHITE
                canvas.drawText(currentFortuneText, surfaceWidth / 2f, cardTop + 115f, textPaint)
            } else if (showFortune && System.currentTimeMillis() >= fortuneExpireTime) {
                showFortune = false
            }
        }

        private fun drawScaledBitmap(canvas: Canvas, bitmap: Bitmap, fit: DisplayFit) {
            if (bitmap.isRecycled) return

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val sw = surfaceWidth.toFloat()
            val sh = surfaceHeight.toFloat()

            when (fit) {
                DisplayFit.CENTER_CROP -> {
                    val scale = maxOf(sw / bw, sh / bh)
                    val dx = (sw - bw * scale) * 0.5f
                    val dy = (sh - bh * scale) * 0.5f

                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(dx, dy)
                    }
                    canvas.drawBitmap(bitmap, matrix, paint)
                }

                DisplayFit.FIT_WITH_BLUR -> {
                    val bgScale = maxOf(sw / bw, sh / bh)
                    val bgMatrix = Matrix().apply {
                        setScale(bgScale, bgScale)
                        postTranslate((sw - bw * bgScale) * 0.5f, (sh - bh * bgScale) * 0.5f)
                    }
                    val bgPaint = Paint(paint).apply { alpha = 120 }
                    canvas.drawBitmap(bitmap, bgMatrix, bgPaint)

                    val fgScale = minOf(sw / bw, sh / bh)
                    val fgMatrix = Matrix().apply {
                        setScale(fgScale, fgScale)
                        postTranslate((sw - bw * fgScale) * 0.5f, (sh - bh * fgScale) * 0.5f)
                    }
                    canvas.drawBitmap(bitmap, fgMatrix, paint)
                }
            }
        }

        private fun loadBitmapFromUri(uri: Uri, callback: (Bitmap?) -> Unit) {
            Thread {
                var bitmap: Bitmap? = null
                var stream: InputStream? = null
                try {
                    stream = openStreamSafely(uri)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    stream?.close()

                    val targetW = surfaceWidth.coerceAtLeast(1080)
                    val targetH = surfaceHeight.coerceAtLeast(1920)
                    var inSampleSize = 1
                    while (options.outWidth / inSampleSize > targetW || options.outHeight / inSampleSize > targetH) {
                        inSampleSize *= 2
                    }

                    stream = openStreamSafely(uri)
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val rawBitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)

                    bitmap = if (rawBitmap != null) {
                        applyExifOrientation(uri, rawBitmap)
                    } else null
                } catch (t: Throwable) {
                    t.printStackTrace()
                    System.gc()
                } finally {
                    try { stream?.close() } catch (ignored: Exception) {}
                }

                handler.post { callback(bitmap) }
            }.start()
        }

        private fun openStreamSafely(uri: Uri): InputStream? {
            return try {
                if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    applicationContext.contentResolver.openInputStream(uri)
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun applyExifOrientation(uri: Uri, source: Bitmap): Bitmap {
            return try {
                var degrees = 0f
                openStreamSafely(uri)?.use { stream ->
                    val exif = androidx.exifinterface.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    degrees = when (orientation) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    source.recycle()
                    rotated
                } else {
                    source
                }
            } catch (e: Exception) {
                source
            }
        }

        private fun recycleBitmaps() {
            currentBitmap?.recycle()
            currentBitmap = null
            nextBitmap?.recycle()
            nextBitmap = null
        }
    }
}
