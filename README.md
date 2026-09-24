# 씀 가계부 2.0

로컬 저장을 우선하는 Android 자동 가계부입니다.

## 핵심 기능
- 결제/입금 알림 자동 감지 및 거래 추출
- SHA-256 transaction_id 중복 방지
- SQLite 로컬 저장
- 자동 카테고리 분류와 사용자 보정 규칙
- Google Calendar / 삼성 캘린더 동시 기록
- 기존 Notion 입출금 캘린더 데이터소스 동기화
- 외부 연동별 상태 저장과 지수 백오프 재시도
- 수동 거래 추가·수정·삭제
- Notion 토큰 Android Keystore 암호화 저장

## 검증
GitHub Actions에서 unit test, Android lint, debug/release APK build를 실행하고 APK artifact를 업로드합니다.
실제 기기의 알림 접근, Google/삼성 캘린더 계정, Notion Integration 권한은 별도 실기 QA가 필요합니다.
