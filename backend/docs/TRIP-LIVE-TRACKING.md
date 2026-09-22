# 여행 실시간 진행 추적 — 설계

**한 줄**: 일정 화면에서 「시작」을 누르면 GPS 로 위치를 받아 **지금 일정의 어느 단계인지·다음은 무엇인지**를 실시간으로 안내한다 (네이버 지도 대중교통 안내와 비슷).

이 문서는 **2026-09-21 운영 back/dev 실측을 반영한 확정본**이다. 🔴 **표를 새로 만들기 전에 이 문서의 「이미 있는 것」을 먼저 읽어라** — 절반은 이미 있다. 새로 만들면 「지금 몇 번째냐」의 정본이 두 벌이 된다.

---

## 1. 🟢 이미 있는 것 (재사용, 새로 안 만든다)

| 하는 일 | 기존 |
|---|---|
| 진행 중 여행 (status·현재 단계·시작 시각) | **`itinerary_run`** — status `PLANNED·RUNNING·PAUSED·DONE`, `current_stop_index`, `started_at` |
| 계획 장소별 도착/출발 | **`itinerary_item_actual`** — `arrived_at`·`departed_at` 둘 다 nullable + 「둘 다 없는 행은 못 만든다」 CHECK |
| 진행 이벤트 로그 | **`itinerary_stop_event`** — event_type CHECK 에 **`ARRIVE_AUTO` 가 이미 있다**(GPS 자동 도착 자리) |
| 시작·현재상태·일시정지·도착·건너뜀 | `POST/GET /api/v1/itineraries/{id}/progress[/start|/pause|/stops/{itemKey}/arrive|/skip]` |

## 2. 🔴 정말 없는 것 — 이것만 새로 만든다

넷이다: **① GPS 궤적 표 · ② 마지막 위치 칸 · ③ 배치 업로드 + 서버 자동 도착 판정 · ④ 완료(`DONE`) 로 보내는 코드**(상태는 CHECK 에 있는데 보내는 곳이 0건).

### DB — 칸 셋 + 표 하나 (표 셋 아님)

```sql
-- V202609211?0000__itinerary_run_location.sql  (번호는 붙이기 직전 git fetch 로 정한다)

-- ② 마지막으로 받은 위치. 재개 시 「어디쯤이었나」를 한 번에.
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_lat DOUBLE PRECISION;
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_lng DOUBLE PRECISION;
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_location_at TIMESTAMPTZ;
ALTER TABLE itinerary_run ADD CONSTRAINT ck_itinerary_run_last_location
    CHECK ((last_lat IS NULL) = (last_lng IS NULL));   -- 좌표는 둘 다 있거나 둘 다 없다

-- ① 궤적. 버리는 로그가 아니다.
CREATE TABLE itinerary_run_ping (
    itinerary_run_ping_id UUID PRIMARY KEY,
    itinerary_id UUID NOT NULL REFERENCES itinerary_run(itinerary_id) ON DELETE CASCADE,
    lat DOUBLE PRECISION NOT NULL, lng DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,                       -- 기기가 찍은 시각
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),         -- 서버가 받은 시각
    CONSTRAINT uq_itinerary_run_ping UNIQUE (itinerary_id, recorded_at)   -- 재시도 중복 방지
);
```

🔴 `recorded_at` 과 `received_at` 을 **둘 다** 두는 이유 — 배치로 모아 보내면 둘이 몇 분 벌어진다. 하나만 두면 「기기 시계 오차」와 「망 끊김」을 못 가른다.
🔴 `arrived_at` null = 「안 감」이 아니라 「모름」. 0·false 로 안 채운다.

### 엔드포인트 — 기존 progress 뿌리 아래 (새 뿌리 안 만든다)

```
POST /api/v1/itineraries/{id}/progress/location    ③ 배치 — {lat,lng,recordedAt}[] 배열
POST /api/v1/itineraries/{id}/progress/complete    ④ DONE 으로
```

`/trip-sessions/…` 새 주소는 안 만든다 — 같은 것을 두 주소로 부르게 된다.

## 3. 단계 판정 (서버 권위)

배치가 오면 점들을 `itinerary_run_ping` 에 저장 + **최신 점**으로 판정: 다음 계획 장소까지 **직선거리(길찾기 0)** 가 **도착 반경 100m 안**이면 `itinerary_stop_event` 에 `ARRIVE_AUTO` 찍고 `current_stop_index` 전진. 아니면 「이동 중, 다음: 장소N」. 좌표 없거나 권역 밖이면 **판정 보류**(안 지어냄).

## 4. 설계 결정 — 확정

| # | 결정 |
|---|---|
| A 부하 | 🔴 **순진한 폴링 아님.** 클라가 GPS 를 모아 **배치 업로드**(구글식). 요청 수가 초당 폴링의 수십분의 1 → 서버 부하 문제 사라짐. 클라는 로컬 예측을 즉시 그리고 서버가 확정 |
| B 저장 | 🔴 **다운샘플 궤적**(구글 위치기록식). **이 궤적이 나중에 「구간별 실제 이동시간·장소별 체류시간」의 원자료** — 지금 추천이 못 하는 그 값을 우리 데이터로 메운다 |
| C 판정 | **서버** 권위, 클라는 로컬 예측만 |
| D 개인정보 | 궤적은 위치기록이라 민감 → 동의 + 약관 갱신. **보존 = 여행 종료 후 1년**(계절성 분석에 한 해치 필요). 탈퇴 시 **명시적 삭제**(CASCADE 말고) |

## 5. 데모(9/28)까지의 최소

칸 셋 + `itinerary_run_ping` + 배치 업로드 + 자동 도착 판정 + `complete`. 궤적은 데모에서도 **저장은 처음부터**(나중 분석의 원자료). 궤적으로 체류시간·구간시간 **계산**하는 것과 다중기기 재개는 발표 후.

## 6. 마이그레이션·배포 주의

🔴 번호는 붙이기 직전 `git fetch`(작으면 운영이 죽는다). `back/dev` → Jenkins 자동 배포 → Flyway 적용. 되돌리기 어려우니 위 SQL 모양을 확정하고 짓는다.
