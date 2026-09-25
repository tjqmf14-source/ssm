# Galaxy 실기 QA

이 실기 QA는 실제 Samsung Galaxy, Windows self-hosted GitHub Actions runner, USB 디버깅을 요구한다.

## PASS 조건

- 연결된 authorized Android 장치가 정확히 1대이며 제조사가 Samsung일 것
- 앱/계측 테스트 APK 설치 성공
- 기기에서 instrumentation regression tests가 실제 실행되고 zero-test 또는 실패가 아닐 것
- NotificationListenerService를 통해 Android 시스템 알림이 거래로 저장될 것
- 동일 notification key 재전송 시 transaction_id 중복 방지가 동작할 것
- Google Calendar, Samsung Calendar, Notion sync target이 모두 synced이고 remote_id가 존재할 것
- 증거 파일이 app/build/galaxy-real-device/ 에 생성될 것

Notion 토큰 또는 캘린더 계정/권한이 없으면 PASS가 아니라 BLOCKED/FAIL이다.

주의: 자동 알림 probe는 실제 Android NotificationListener 경로를 검증하지만 실제 카드 승인/은행 입금 자체를 발생시키지는 않는다. 실제 금융사 앱 고유 알림 포맷은 별도의 실거래 1건으로 최종 확인해야 한다.
