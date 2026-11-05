package com.photoroll.galaxy.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.photoroll.galaxy.data.model.TriggerMode
import com.photoroll.galaxy.data.repository.WallpaperRepository
import java.util.Calendar

class WallpaperScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ROTATE_WALLPAPER = "com.photoroll.galaxy.ACTION_ROTATE_WALLPAPER"
        private const val REQUEST_CODE_ALARM = 1001

        /**
         * 설정값에 맞춰 알람매니저 스케줄 등록
         */
        fun scheduleNextAlarm(context: Context) {
            val repository = WallpaperRepository.getInstance(context)
            val settings = repository.getSettings()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, WallpaperScheduleReceiver::class.java).apply {
                action = ACTION_ROTATE_WALLPAPER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 기존 등록된 알람 취소
            alarmManager.cancel(pendingIntent)

            if (!settings.isEnabled) return

            when (settings.triggerMode) {
                TriggerMode.INTERVAL_15M -> scheduleInterval(alarmManager, pendingIntent, 15 * 60 * 1000L)
                TriggerMode.INTERVAL_30M -> scheduleInterval(alarmManager, pendingIntent, 30 * 60 * 1000L)
                TriggerMode.INTERVAL_1H -> scheduleInterval(alarmManager, pendingIntent, 60 * 60 * 1000L)
                TriggerMode.INTERVAL_3H -> scheduleInterval(alarmManager, pendingIntent, 3 * 60 * 60 * 1000L)
                TriggerMode.DAILY_FIXED -> scheduleDaily(alarmManager, pendingIntent, settings.dailyHour, settings.dailyMinute)
                TriggerMode.WEEKEND_SEPARATE -> scheduleWeekend(alarmManager, pendingIntent)
                TriggerMode.SCREEN_ON, TriggerMode.DOUBLE_TAP_ONLY -> {
                    // 화면 켤 때 또는 수동 터치는 알람 불필요 (WallpaperService에서 자체 처리)
                }
            }
        }

        private fun scheduleInterval(alarmManager: AlarmManager, pi: PendingIntent, intervalMs: Long) {
            val triggerAt = System.currentTimeMillis() + intervalMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+) 정확한 알람 권한 확인 및 예외 방지 안전 처리
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        private fun scheduleDaily(alarmManager: AlarmManager, pi: PendingIntent, hour: Int, minute: Int) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
        }

        private fun scheduleWeekend(alarmManager: AlarmManager, pi: PendingIntent) {
            // 1시간 주기로 체크하여 토/일 모드 적용
            scheduleInterval(alarmManager, pi, 60 * 60 * 1000L)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_ROTATE_WALLPAPER -> {
                // 1. 배경화면 변경 브로드캐스트 발송
                val repository = WallpaperRepository.getInstance(context)
                repository.notifyWallpaperChanged()

                // 2. 다음 알람 재등록
                scheduleNextAlarm(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // 기기 재부팅 시 알람 복원
                scheduleNextAlarm(context)
            }
        }
    }
}
