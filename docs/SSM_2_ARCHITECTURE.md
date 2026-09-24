# 씀 가계부 2.0 아키텍처

## 제품 원칙
결제하면 사용자가 직접 입력하지 않아도 로컬 DB에 먼저 기록되고, 선택한 Android Calendar Provider 캘린더와 기존 Notion 입출금 DB로 동기화한다.

## 데이터 흐름
1. NotificationListenerService는 알림 원문만 수집한다.
2. NotificationParser가 금융 소스 판별, 금액/구분/가맹점/결제수단/카테고리를 추출한다.
3. TransactionDb가 로컬 저장과 중복 후보 검사를 수행한다.
4. SyncScheduler가 Calendar와 Notion 작업을 서로 독립적으로 예약한다.
5. CalendarSyncWorker는 선택된 캘린더별 event ID를 저장한다.
6. NotionSyncWorker는 네트워크 연결 시 기존 입출금 데이터 소스에 transaction_id 기준 upsert를 수행한다.

## Notion
- 기존 data source ID: 6dbe7411-93e9-4b01-a2e9-761fe4c0e98f
- 토큰은 소스/Git에 저장하지 않는다.
- Android Keystore AES/GCM으로 기기 안에 암호화 저장한다.
- transaction_id로 원격 중복을 조회한 후 create/update 한다.
- 씀 자체 중계 서버는 사용하지 않는다.

## 중복 방지
- source_key UNIQUE
- transaction_id UNIQUE
- 금액/구분/시간 ±90초/정규화 가맹점 기반 교차 소스 중복 후보 검사
- Notion 전송 전 transaction_id 조회

## 오프라인
- 로컬 저장은 네트워크와 무관하게 완료한다.
- Notion은 WorkManager 네트워크 제약 + exponential backoff로 재시도한다.
- Calendar와 Notion 실패는 원 거래 저장 실패로 처리하지 않는다.

## 2.0 단계
- Phase A: 데이터/파서/중복/동기화 기반
- Phase B: 홈/내역/분석/설정 UI 전면 재설계
- Phase C: 수정/삭제/보정 학습 및 동기화 보강
- Phase D: 실제 알림 샘플/Calendar/Notion/오프라인/기기 QA
