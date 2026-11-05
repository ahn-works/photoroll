package com.photoroll.galaxy

import com.google.gson.Gson
import com.photoroll.galaxy.data.model.AppSettings
import com.photoroll.galaxy.data.model.PhotoItem
import com.photoroll.galaxy.data.model.RotationRule
import com.photoroll.galaxy.data.model.TriggerMode
import org.junit.Assert.*
import org.junit.Test

class WallpaperRotationAlgorithmTest {

    private val gson = Gson()

    @Test
    fun testAppSettingsSerialization() {
        val original = AppSettings(
            isEnabled = true,
            triggerMode = TriggerMode.INTERVAL_1H,
            rotationRule = RotationRule.FAVORITE_WEIGHTED,
            dimOpacityPercent = 25,
            dailyHour = 9,
            enableDoubleTapGesture = true
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, AppSettings::class.java)

        assertEquals(original.isEnabled, deserialized.isEnabled)
        assertEquals(original.triggerMode, deserialized.triggerMode)
        assertEquals(original.rotationRule, deserialized.rotationRule)
        assertEquals(original.dimOpacityPercent, deserialized.dimOpacityPercent)
        assertEquals(original.dailyHour, deserialized.dailyHour)
        assertEquals(original.enableDoubleTapGesture, deserialized.enableDoubleTapGesture)
    }

    @Test
    fun testSequentialRotationCalculation() {
        val photos = listOf(
            PhotoItem(uriString = "content://media/1"),
            PhotoItem(uriString = "content://media/2"),
            PhotoItem(uriString = "content://media/3")
        )

        var currentIndex = 0
        val sequenceIndices = mutableListOf<Int>()
        repeat(6) {
            sequenceIndices.add(currentIndex)
            currentIndex = (currentIndex + 1) % photos.size
        }

        assertEquals(listOf(0, 1, 2, 0, 1, 2), sequenceIndices)
    }

    @Test
    fun testFavoriteWeightedPoolGeneration() {
        val photos = listOf(
            PhotoItem(uriString = "content://media/normal1", isFavorite = false),
            PhotoItem(uriString = "content://media/fav1", isFavorite = true),
            PhotoItem(uriString = "content://media/normal2", isFavorite = false)
        )

        val weightedPool = mutableListOf<PhotoItem>()
        photos.forEach { item ->
            val weight = if (item.isFavorite) 3 else 1
            repeat(weight) { weightedPool.add(item) }
        }

        // 전체 1 + 3 + 1 = 5개 중 즐겨찾기가 3개 차지 (60% 확률)
        assertEquals(5, weightedPool.size)
        val favCount = weightedPool.count { it.isFavorite }
        assertEquals(3, favCount)
    }
}
