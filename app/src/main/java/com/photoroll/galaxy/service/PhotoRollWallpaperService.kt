package com.photoroll.galaxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
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
        private val dimPaint = Paint().apply {
            color = Color.BLACK
        }

        private var surfaceWidth = 1080
        private var surfaceHeight = 2400

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

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val settings = repository.getSettings()
                // '화면 켤 때마다' 옵션이 켜져 있으면 화면이 보일 때마다 즉각 다음 사진 렌더링
                if (settings.triggerMode == TriggerMode.SCREEN_ON) {
                    rotateToNextPhoto()
                } else {
                    drawFrame()
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            event?.let { gestureDetector.onTouchEvent(it) }
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

            // 1. 현재 비트맵 그리기
            currentBitmap?.let { bmp ->
                paint.alpha = 255
                drawScaledBitmap(canvas, bmp, settings.displayFit)
            }

            // 2. 크로스페이드 중인 다음 비트맵 오버레이 그리기
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
                    // 배경 여백을 채우기 위해 가우시안 블러 효과 시뮬레이션 (확대 후 반투명 오버레이)
                    val bgScale = maxOf(sw / bw, sh / bh)
                    val bgMatrix = Matrix().apply {
                        setScale(bgScale, bgScale)
                        postTranslate((sw - bw * bgScale) * 0.5f, (sh - bh * bgScale) * 0.5f)
                    }
                    val bgPaint = Paint(paint).apply { alpha = 120 }
                    canvas.drawBitmap(bitmap, bgMatrix, bgPaint)

                    // 중앙 원본 비율 비트맵 렌더링
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
                    stream = applicationContext.contentResolver.openInputStream(uri)
                    // 해상도에 맞춘 샘플링 디코딩으로 메모리 보호
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                    stream?.close()

                    val targetW = surfaceWidth.coerceAtLeast(1080)
                    val targetH = surfaceHeight.coerceAtLeast(1920)
                    var inSampleSize = 1
                    while (options.outWidth / inSampleSize > targetW || options.outHeight / inSampleSize > targetH) {
                        inSampleSize *= 2
                    }

                    stream = applicationContext.contentResolver.openInputStream(uri)
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { stream?.close() } catch (ignored: Exception) {}
                }

                handler.post { callback(bitmap) }
            }.start()
        }

        private fun recycleBitmaps() {
            currentBitmap?.recycle()
            currentBitmap = null
            nextBitmap?.recycle()
            nextBitmap = null
        }
    }
}
