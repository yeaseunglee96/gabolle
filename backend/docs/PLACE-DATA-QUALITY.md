# 장소 데이터 품질 조회 — 채움률 기준선 · 번역 품질 · 안전정보 출처

`S15P21E201-278` · `-315` · `-328`. 셋 다 `com.gabolle.backend.dataquality.PlaceDataQualityService`
(코드는 `EventQualityGate`와 같은 자리, 같은 방식 — `JdbcTemplate` 직접 SQL) 하나에 있다.

## 🔴 티켓이 상정한 칼럼 중 실제로 없는 것

세 티켓 모두 2026-08-27에 쓰였고, 그 뒤 실제 스키마(`place`·`place_feature`,
`V20260904000000` 이후 여러 마이그레이션)가 그 가정과 어긋난 부분이 있다. 지어내지
않고 실측한 것만 적는다.

| 티켓이 원한 것 | 실제 상태 |
|---|---|
| 영문 주소(`address_en`) | **없다.** `place` 표에는 `name_en`만 있다 |
| 카카오 평점 · 야경 사진 · 체험 시간 · 기념품 항목 | **없다.** `place_feature.feature_type` 21종(`ck_place_feature_type`) 어디에도 없다 |
| 다국어 번역 표(`PlaceTranslation`) | **없다.** 이 저장소에 그런 표가 없다 |
| 휴무일 | `OPENING_HOURS`·`BREAK_TIME`·`LAST_ORDER_TIME`으로 대신 존재한다 |
| 로컬 점수 갱신 시각 | `place_feature.observed_at`이 사실마다(예: `LOCALITY_SCORE`) 이미 있다 — 별도 칼럼이 필요 없다 |

## 무엇을 재는가

### 1. `measureCompleteness()` — S15P21E201-278

`place` 총 행 수, `name_en` 채움 수, 그리고 `place_feature`의 21종(`ck_place_feature_type`,
`PlaceDataQualityService.KNOWN_FEATURE_TYPES`)마다 "그 종류의 사실을 가진 장소 수"를
`FillCount(filled, total)`로 돌려준다. `evidence_status <> 'UNKNOWN'`인 행만 "채워졌다"로
센다 — UNKNOWN은 값이 비어 있어야 하는 상태(`ck_place_feature_unknown_has_no_value`)라
"채웠다"가 아니라 "모른다고 확인했다"이기 때문이다.

### 2. `checkTranslationQuality()` — S15P21E201-315

`place.name_en`에 한글(완성형·자모)이 남아 있거나 비어 있는 행을 찾는다. `PlaceTranslation`
표는 없으므로 검사하지 않는다 — 생기면 같은 방식으로 더한다.

### 3. `checkSafetyProvenance()` — S15P21E201-328

`ALLERGEN_TAG`·`DIETARY_SUPPORT_TAG`·`ACCESSIBILITY_TAG`·`STAIRS_PRESENT`(V20260907003000이
이미 "추정값 저장 금지"로 막은 바로 그 네 종류) 중 **값은 있는데 `source_type`이 비어 있는**
행을 찾는다. `evidence_status='ESTIMATED'`를 막는 DB CHECK와 다른 구멍이다 — VERIFIED라고
적어 넣으면서 출처를 안 남기는 것은 그 CHECK를 지나간다.

**0건이 아닐 때 할 일**: 그 행의 `source_type`·`source_id`를 실제 출처로 채우거나, 출처를
확인할 수 없으면 행을 지운다. `evidence_status`를 `UNKNOWN`으로 낮추지 않는다 — UNKNOWN은
"몰라서 안 채웠다"이고 이 행은 "채웠지만 어디서 왔는지 안 남겼다"라 뜻이 다르다. 출처를
모르는 안전 정보는 신뢰할 수 없으므로 지우는 쪽이 맞다.

## 어떻게 돌리는가

`backend/src/test/java/com/gabolle/backend/dataquality/PlaceDataQualityServiceTest.java`가
실행 예시이자 재현 경로다 — 정상 fixture는 통과시키고, 오염 fixture(한글 섞인 이름, 출처
없는 안전 정보 등)를 하나씩 넣었을 때 그 행이 잡히는 것까지 확인한다. 로컬에 Docker가
없으면 `PostgresAvailableCondition`이 건너뛴다(이 저장소의 다른 Postgres 통합 테스트와
같은 동작) — CI에는 Postgres가 있어 그대로 돈다.

기준선 수치 자체를 재려면(진짜 데이터 위에서) 이 서비스 빈을 실제 DB에 붙은 컨텍스트에서
불러야 한다. `docs/PLACE-DATA-LOAD.md`에 적힌 대로 운영 DB에는 이미 장소 328곳 이상이
적재돼 있다 — 그 DB에 붙을 수 있는 사람이 이 세 메서드를 한 번 불러 결과를 아래 표에
옮겨 적으면 된다.

## 🔴 기준선 — 아직 못 잰다

이 문서를 쓴 시점(2026-09-14)에는 이 작업자가 운영 DB에 붙을 수단이 없어 **실제 수치를
아직 못 쟀다.** 여기서 숫자를 지어내면 "쟀는데 이렇다"와 "안 쟀다"를 구분할 수 없게 된다
— 그래서 빈 칸으로 남긴다. 운영 DB에 붙을 수 있는 사람이 위 세 메서드를 한 번 불러 아래
표를 채워 주면 그것이 기준선이다.

| 측정일 | place 총계 | name_en 채움 | (feature_type별 채움 — 표를 이어 붙인다) |
|---|---|---|---|
| _미측정_ | | | |
