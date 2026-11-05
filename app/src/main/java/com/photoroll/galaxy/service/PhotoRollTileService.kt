package com.photoroll.galaxy.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.photoroll.galaxy.R
import com.photoroll.galaxy.data.repository.WallpaperRepository

/**
 * 삼성 갤럭시 상단바 빠른 설정(Quick Settings) 타일 서비스
 * 상단바를 내려서 'PhotoRoll' 타일을 탭하면 앱을 열지 않고도 즉시 다음 배경화면으로 변경!
 */
class PhotoRollTileService : TileService() {

    private val repository by lazy { WallpaperRepository.getInstance(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val settings = repository.getSettings()
        if (settings.isEnabled) {
            // 즉시 다음 사진으로 교체 브로드캐스트 발송
            repository.notifyWallpaperChanged()
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val settings = repository.getSettings()

        if (settings.isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "PhotoRoll (다음 사진)"
            tile.contentDescription = "탭하여 다음 배경화면으로 변경"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "PhotoRoll (꺼짐)"
            tile.contentDescription = "PhotoRoll 비활성화됨"
        }
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_gallery)
        tile.updateTile()
    }
}
