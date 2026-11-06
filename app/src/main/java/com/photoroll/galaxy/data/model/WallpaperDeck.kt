package com.photoroll.galaxy.data.model

import java.util.UUID

/**
 * 덱(Deck)에 등록된 개별 사진 아이템
 */
data class PhotoItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val tag: PhotoTag = PhotoTag.GENERAL,
    val source: PhotoSource = PhotoSource.GALLERY
)

enum class PhotoTag {
    GENERAL,
    DAY_BRIGHT,   // 낮 시간대용 밝은 사진
    NIGHT_DARK,   // 야간/눈부심 방지용 다크 사진
    WEEKEND       // 주말 전용 사진
}

enum class PhotoSource {
    GALLERY,
    PINTEREST
}

/**
 * 사진 교체 주기 트리거
 */
enum class TriggerMode(val title: String, val description: String) {
    SCREEN_ON("화면 켤 때마다", "폰 화면이 켜지거나 잠금 해제될 때마다 다음 사진 표시"),
    INTERVAL_15M("15분마다", "15분 주기로 배경화면 자동 변경"),
    INTERVAL_30M("30분마다", "30분 주기로 배경화면 자동 변경"),
    INTERVAL_1H("1시간마다", "1시간 주기로 배경화면 자동 변경"),
    INTERVAL_3H("3시간마다", "3시간 주기로 배경화면 자동 변경"),
    DAILY_FIXED("매일 특정 시각", "매일 설정한 시간(예: 08:00, 00:00)에 변경"),
    WEEKEND_SEPARATE("주말/평일 분리 모드", "평일엔 차분한 앨범, 주말엔 활기찬 앨범으로 전환"),
    DOUBLE_TAP_ONLY("더블 탭 수동 변경", "홈 화면 빈 공간을 두 번 탭할 때만 변경")
}

/**
 * 사진 순환 및 노출 알고리즘
 */
enum class RotationRule(val title: String, val description: String) {
    RANDOM_SHUFFLE("랜덤 셔플", "모든 사진이 한 번씩 나올 때까지 중복 없는 비복원 셔플"),
    SEQUENTIAL("순서대로", "덱 목록에 정렬된 순서대로 1번부터 순차 재생"),
    FAVORITE_WEIGHTED("즐겨찾기 가중치 (⭐추천)", "하트(❤️) 누른 사진이 일반 사진보다 3배 자주 등장"),
    DAY_NIGHT_CONTEXT("낮과 밤 감성 모드 (⭐추천)", "주간엔 화사한 톤, 야간엔 다크/감성 톤 사진 자동 배정")
}

/**
 * 적용 대상 스코프
 */
enum class TargetScope(val title: String) {
    HOME_AND_LOCK_SYNC("홈 화면 & 잠금화면 동기화"),
    HOME_ONLY("홈 화면만 적용"),
    LOCK_ONLY("잠금화면만 적용"),
    INDEPENDENT("홈/잠금화면 독립 분리 운영")
}

/**
 * 화면 비율 맞춤 방식
 */
enum class DisplayFit(val title: String) {
    CENTER_CROP("화면 꽉 채우기 (Center Crop)"),
    FIT_WITH_BLUR("원본 비율 + 배경 가우시안 블러")
}

/**
 * 앱 전역 설정 모델
 */
data class AppSettings(
    val isEnabled: Boolean = true,
    val targetScope: TargetScope = TargetScope.HOME_AND_LOCK_SYNC,
    val triggerMode: TriggerMode = TriggerMode.SCREEN_ON,
    val rotationRule: RotationRule = RotationRule.FAVORITE_WEIGHTED,
    val displayFit: DisplayFit = DisplayFit.CENTER_CROP,
    val dimOpacityPercent: Int = 10, // 0~50% 딤 오버레이
    val dailyHour: Int = 8,
    val dailyMinute: Int = 0,
    val enableDoubleTapGesture: Boolean = true,
    val transitionDurationMs: Long = 400L, // 크로스페이드 시간

    // v2.0 감성 위젯 옵션
    val enableDDayBadge: Boolean = false,
    val ddayTitle: String = "우리의 기념일",
    val ddayTargetDate: String = "2024-11-06",
    val enableMemoWidget: Boolean = false,
    val memoText: String = "오늘도 행복한 하루 보내기 ✨",
    val enableVintageFilter: Boolean = false,

    // v2.0 인터랙티브 미니게임 옵션
    val enableFortuneCookie: Boolean = true,
    val enableBubblePop: Boolean = true
)
