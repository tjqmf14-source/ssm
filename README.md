# 씀 가계부 2.0

로컬 저장을 우선하는 Android 자동 가계부입니다.

## 2.0 핵심
- Android 결제/입금 알림 자동 감지
- `₩`, `원`, 카드/은행 알림에서 금액·가맹점·지출/입금 유형 추출
- 알림 키 + 거래 정보 기반 SHA-256 `transaction_id` 중복 방지
- SQLite 로컬 원본 저장 및 v1 데이터 마이그레이션
- 가맹점 기반 자동 카테고리 분류 + 사용자 보정 학습
- Google Calendar / Samsung Calendar 동기화 및 이벤트 중복 방지
- 기존 Notion `입출금 캘린더` 데이터소스 동기화
- 거래 수정 시 외부 항목 재동기화
- 거래 삭제 시 외부 Calendar / Notion 삭제 큐 처리
- 오프라인 저장 + 외부 연동 지수 백오프 재시도 + 재부팅 후 JobScheduler 복구
- 수동 거래 추가·수정·삭제
- Notion 토큰 Android Keystore AES-GCM 암호화 저장
- 앱 내 알림 접근 권한 사용 목적 고지

## Notion 연동
기존 데이터소스 ID `6dbe7411-93e9-4b01-a2e9-761fe4c0e98f`를 그대로 사용합니다.
앱에서 사용하는 기존 속성은 `내역`, `금액`, `결제일`, `구분`, `가맹점`, `카테고리`, `결제수단`, `등록경로`, `transaction_id`, `원본 이벤트 ID`, `메모`입니다.
토큰은 소스나 GitHub에 저장하지 않고 기기 Android Keystore로 암호화합니다.

## 자동 QA
GitHub Actions에서 아래 항목을 실제 실행합니다.
- `testDebugUnitTest`
- JUnit XML 존재 및 failure/error 0건 확인
- `lintDebug`
- lint HTML 리포트 존재 확인
- `assembleDebug`
- `assembleRelease`
- `bundleRelease`
- debug APK / release APK / AAB 파일 크기 확인
- SHA-256 체크섬 생성
- QA 결과와 설치/배포 산출물을 artifact로 업로드

## 설치 및 배포 주의
알림 접근 권한은 Android/Google Play Protect에서 민감 권한으로 취급될 수 있습니다. 파일 관리자에서 직접 설치한 APK는 기기 정책에 따라 차단될 수 있으므로, 일반 배포는 Google Play 내부 테스트/비공개 테스트 등 검증된 배포 경로를 권장합니다. 이 시스템 정책을 우회하기 위해 핵심 알림 감지 권한을 제거하지 않습니다.

## 실기기 QA
자동 CI가 통과하더라도 아래 항목은 Galaxy 실기기에서 최종 확인해야 합니다.
- 실제 카드/은행별 알림 포맷
- 알림 접근 권한 승인 후 자동 저장
- Google/Samsung Calendar 계정 제공자별 동기화
- Notion Integration 권한 및 실데이터 기록/수정/삭제
- Play Protect 및 실제 설치 경로
