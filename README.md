# 씀 가계부

무료·로컬 중심 Android 가계부입니다.

## 핵심 기능
- 결제/입금 알림 감지
- 카드사·은행 알림에서 금액/구분 자동 추출
- 로컬 SQLite 저장
- 이번 달 총지출, 오늘 지출, 입금, 거래 건수 표시
- 최근 거래 내역 표시
- Android Calendar Provider를 통한 캘린더 기록
- 알림 접근/캘린더 권한을 앱에서 바로 설정
- 수동 거래 추가
- 중복 알림 방지

## 데이터 흐름
결제 알림 → 씀 알림 리스너 → 거래 파싱 → 로컬 DB → 선택한 캘린더 → 대시보드 갱신

## 빌드 환경
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk / targetSdk 36
- minSdk 28

## 로컬 실행
1. Android Studio Quail 4 이상에서 저장소를 엽니다.
2. Android SDK 36을 설치합니다.
3. Gradle Sync 후 app을 실행합니다.
4. 앱의 '알림 접근 설정'에서 씀을 허용합니다.
5. '캘린더 선택'에서 사용할 Google 캘린더를 고릅니다.

## 자동 QA
GitHub Actions에서 다음을 수행합니다.
- testDebugUnitTest
- lintDebug
- assembleDebug
- APK artifact 업로드

## 개인정보
거래 데이터는 앱의 로컬 SQLite에 저장합니다. 외부 서버나 유료 API로 전송하지 않습니다.
