# 씀 가계부 2.0 QA 게이트

기준선: `d080f484834c53d20d840ee97509b583c1264b36`

## 커밋 전 검증 — PASS
- 순수 Java 핵심 로직 QA: **PASS 15/15**
- 전체 프로덕션 Java 소스 Android 최소 스텁 컴파일: **PASS 14/14 소스**
- Manifest / resources XML 파싱: **PASS 4개 파일**
- GitHub Actions YAML 파싱: **PASS**
- 기능·보안·CI 소스 계약 검사: **PASS 21/21**
- 비밀정보 하드코딩 검사: **PASS 28개 대상 파일**
- SQLite fresh v3 스키마: **PASS**
- SQLite v1 → v3 마이그레이션 및 legacy 거래 보존: **PASS**
- SQLite v2 → v3 마이그레이션 및 기존 sync 상태 보존: **PASS**
- 외부 Calendar / Notion 삭제 큐 보존: **PASS**
- Notion 기존 데이터소스 ID / 이름 / 속성 계약: **PASS**
- Notion API version `2026-03-11`: **PASS**
- 모든 커밋 대상 핵심 파일 non-empty 검사: **PASS**

## 이번 최종 보강 범위
- 결제/입금 알림 파서 오탐·가맹점 추출 개선
- 알림 키 우선 `transaction_id` 중복 방지 강화
- Google / Samsung Calendar `transaction_id` 이벤트 중복 방지
- Samsung 전용 계정이 없을 때 Google 동기화 캘린더 공유 처리
- Notion API `2026-03-11` 반영
- 외부 연동 실패 대상별 재시도 및 다음 재시도 시각 보존
- 권한/토큰/오프라인 상태를 실패 횟수로 누적하지 않도록 보강
- 거래 수정 시 외부 레코드 재동기화
- 거래 삭제 시 Calendar / Notion 외부 삭제 큐 처리
- JobScheduler persisted 재시도 및 재부팅 복구 조건 반영
- Notion 토큰 Android Keystore AES-GCM 저장
- 알림 접근 권한 사용 목적 고지
- 상용형 대시보드 카드 UI와 씀 전용 앱 아이콘 적용
- CI zero-test / empty artifact / stale output 방지 게이트 강화

## 원격 Android QA 완료 조건
최종 PASS는 아래 GitHub Actions 단계가 **동일 commit SHA**에서 모두 실제 실행되어야 한다.
1. Unit tests
2. JUnit XML 존재 및 테스트 결과 failure/error 0건
3. Android lint
4. lint report 존재
5. debug APK build
6. release APK build
7. release AAB build
8. APK/AAB non-empty 검증
9. SHA-256 체크섬 생성
10. artifact 실제 업로드

`queued / skipped / cancelled / zero-test / empty artifact / stale SHA`는 PASS로 판정하지 않는다.

## 실기기 검증 경계
CI 통과는 코드·빌드·정적검증 완료를 의미한다. 다음 항목은 Galaxy 실기기와 실제 계정/알림이 있어야 최종 확인할 수 있다.
- 실제 카드사·은행별 알림 포맷
- Notification access 승인 후 백그라운드 자동 저장
- Google / Samsung Calendar 계정별 표시와 동기화
- 사용자의 Notion Integration 토큰/권한으로 생성·수정·삭제
- Google Play Protect / Play 내부 테스트 설치 경로
