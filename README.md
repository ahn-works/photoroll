# PhotoRoll Galaxy (다이내믹 홈·잠금화면 배경화면 앱)

[![GitHub Pages](https://img.shields.io/badge/GitHub_Pages-Live_Website-blue?logo=github)](https://ahn-works.github.io/photoroll/)
[![Build Status](https://github.com/ahn-works/photoroll/actions/workflows/build_playstore_aab.yml/badge.svg)](https://github.com/ahn-works/photoroll/actions/workflows/build_playstore_aab.yml)
[![Privacy Policy](https://img.shields.io/badge/Privacy_Policy-Live-green)](https://ahn-works.github.io/photoroll/privacy.html)

삼성 갤럭시 전용 **홈 화면 및 잠금화면 다이내믹 사진 로테이션 안드로이드 앱**입니다.  
기존 갤럭시의 "잠금화면 멀티팩" 제한을 넘어, **홈 화면에서도 수십 장의 사진을 켤 때마다, 시간마다, 매일, 주말마다 자유롭게 자동 순환**시킵니다.

* 🌐 **공식 라이브 웹사이트 & 시뮬레이터**: [https://ahn-works.github.io/photoroll/](https://ahn-works.github.io/photoroll/)
* 🔒 **구글 플레이 필수 개인정보처리방침**: [https://ahn-works.github.io/photoroll/privacy.html](https://ahn-works.github.io/photoroll/privacy.html)
* 📖 **5분 플레이스토어 등록 가이드**: [https://ahn-works.github.io/photoroll/guide.html](https://ahn-works.github.io/photoroll/guide.html)

---

## 📱 주요 핵심 기능

1. **적용 대상 독립/동기화 선택**:
   * 홈 화면 & 잠금화면 동시 동기화 적용
   * 홈 화면 단독 적용 / 잠금화면 단독 적용
   * 홈/잠금화면 독립 분리 운영 (잠금화면은 켤 때마다, 홈화면은 1시간마다)
2. **다양한 교체 조건 (Trigger)**:
   * **화면 켤 때마다 (Screen On)**: 폰 화면이 켜지거나 언락될 때마다 즉각 다음 사진 표시
   * **특정 시간 간격**: 15분 / 30분 / 1시간 / 3시간 등 주기적 변경
   * **매일 특정 시각**: 출근길(08:00), 취침 전, 자정 등 지정 시각 변경
   * **주말/평일 분리 모드**: 평일엔 차분한 풍경, 주말엔 활기찬 앨범 자동 전환
   * **홈 화면 더블 탭 제스처**: 빈 공간을 톡톡 두 번 탭하면 즉시 다음 사진으로 스킵
3. **스마트 사진 순환 규칙**:
   * **비복원 랜덤 셔플**: 덱의 모든 사진이 1회씩 노출될 때까지 중복 없는 순환
   * **순서대로 (순차 순환)**: 유저가 지정한 순서대로 재생
   * **즐겨찾기 가중치 (⭐추천)**: 하트(❤️) 누른 최애 사진 3배 가중치 등장
   * **낮과 밤 감성 모드**: 주간(밝은 톤) / 야간(다크·감성 톤) 자동 매칭
4. **Photo Picker & 핀터레스트 연동**:
   * 저장소 권한 없이 최신 안드로이드 `Photo Picker`로 갤러리 다중 사진 임포트
   * 핀터레스트 앱에서 [공유] → [PhotoRoll] 선택 시 원터치로 배경화면 덱에 자동 추가

---

## 🛠️ 기술 스택 및 아키텍처

* **Language**: Kotlin 1.9.23
* **UI**: Jetpack Compose, Material 3 (Samsung One UI 감성)
* **Wallpaper Engine**: `android.service.wallpaper.WallpaperService` + Custom Canvas Renderer
  * 부드러운 크로스페이드 (Crossfade Transition)
  * 아이콘 가독성용 어두운 오버레이 딤(Dim) 필터
  * 화면 꺼짐 시 CPU 점유율 0% (배터리 누수 제로)
* **스케줄러**: `AlarmManager.setExactAndAllowWhileIdle()`
* **이미지 로더**: Coil Compose

---

## 🚀 안드로이드 스튜디오 실행 방법

1. **Android Studio** 실행 후 `Open`을 눌러 아래 경로를 엽니다:
   ```
   C:\Users\DT4\Downloads\photoroll_app
   ```
2. Gradle Sync가 완료되면 갤럭시 실기기(USB 디버깅 켜짐) 또는 AVD를 연결합니다.
3. `Run 'app'` (Shift + F10) 실행.
4. 앱 실행 후:
   * **[사진 덱 관리]**에서 원하는 사진들을 추가합니다.
   * 원하는 주기(화면 켤 때마다, 1시간마다 등) 및 순환 방식을 선택합니다.
   * **[갤럭시 배경화면으로 적용하기]**를 눌러 "홈 화면 및 잠금화면"에 적용합니다.

---

## ⏰ 데일리 자동화 스케줄 연동 내역

* **09:00 (task-12)**: 데일리 작업 킥오프 및 컴포넌트 개발 백로그 점검
* **13:00 (task-13)**: 중간 정적 분석, 단위 테스트 및 빌드 상태 검증
* **17:30 (task-14)**: 일일 개발 결과 종합 보고 및 회고 아티팩트 발행
