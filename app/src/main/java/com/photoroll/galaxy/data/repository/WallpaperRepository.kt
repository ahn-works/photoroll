package com.photoroll.galaxy.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.photoroll.galaxy.data.model.AppSettings
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.model.PhotoTag
import com.photoroll.galaxy.data.model.RotationRule
import com.photoroll.galaxy.data.model.TriggerMode
import java.util.Calendar
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
        val json = prefs.getString(KEY_PHOTOS, null) ?: return emptyList()
        val type = object : TypeToken<List<PhotoItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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
