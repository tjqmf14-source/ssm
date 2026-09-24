# 씀 가계부 2.0 QA 보고서

## 검증 기준
- 제품 기준 main: `71e75a1277c28dbf62eb387744e2420ea6733f55`
- QA 코드 HEAD: `9062d3cdd5b37b5e9b41bc1939c0f8911c703c7a`
- GitHub Actions: Android CI Run #42 / `36004652803`
- 결과: **PASS**

## 실제 실행 결과
- JDK 17 / Android SDK 36 설치: PASS
- Clean: PASS
- Unit tests: **12/12 PASS, skipped 0, failures 0, errors 0**
- Android lint: **0 warnings / 0 errors**
- debug APK: PASS
- release APK: PASS
- release AAB: PASS
- non-empty 산출물 검증: PASS
- SHA-256 생성/대조: PASS
- GitHub Actions artifact 업로드: PASS

## 산출물
- Artifact: `ssm-2.0-final`
- Artifact ID: `10809687847`
- Artifact archive digest: `sha256:87ea0f4d8dd161363be16a4ec4fc42310449c04a3b3495add6624964d9bd4060`
- debug APK: 2,610,282 bytes  
  `ad68a0f3a9e0e9fc679652848a561735c40d5af3a56c5370c8859f6936d9a077`
- release APK (unsigned): 67,958 bytes  
  `3b30790f034f84c4c39d8738efd41769917fc0ad3057470cafb277369caa9e54`
- release AAB: 57,219 bytes  
  `93fbfbea9aa96e09343dff79a9258ecca263e7cc8930ab416af8611860863d9f`

## 이번 QA에서 추가 보강한 항목
- release resource shrinking 적용
- Android 12+ data extraction rules와 Android 11 이하 backup rules 추가
- 가계부 DB·환경설정 등 앱 데이터의 백업/기기전송 제외 정책 명시
- minSdk 28 기준 불필요한 SDK_INT 분기 제거
- lint 게이트를 0 warning / 0 error로 강화
- API 37은 현재 CI sdkmanager 기본 채널에서 설치되지 않아 제품 타깃으로 채택하지 않고 API 36 안정 채널 유지

## 제품 기능 범위
- 결제/입금 알림 자동 감지 및 파싱
- 금액·가맹점·결제수단·지출/입금·시간 추출
- SQLite 로컬 저장
- notification key 기반 SHA-256 `transaction_id` 중복 방지
- 자동 카테고리 분류 및 사용자 보정 규칙
- 수동 거래 추가/수정/삭제
- Google Calendar / Samsung Calendar 동기화
- 기존 Notion 입출금 데이터소스 동기화
- 오프라인 우선 저장 및 외부 연동 재시도
- 거래 수정 재동기화 및 삭제 큐 처리
- Notion 토큰 Android Keystore 암호화 저장

## PASS로 처리하지 않은 실기기 항목
다음은 Galaxy 실기기와 실제 계정/알림이 필요하므로 **NOT RUN** 상태다.
- 카드사·은행별 실제 알림 포맷 전수 검증
- 알림 접근 승인 후 장시간 백그라운드 자동 저장
- 실제 Google/Samsung Calendar 계정별 생성·수정·삭제
- 실제 Notion Integration 계정 생성·수정·삭제
- Android 17 실기기 동작 검증
- Google Play 내부 테스트/Play Protect 배포 검증

queued / skipped / cancelled / zero-test / empty artifact / stale SHA는 PASS로 판정하지 않는다.
