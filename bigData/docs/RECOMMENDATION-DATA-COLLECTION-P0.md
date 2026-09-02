# GABOLLE 추천·개인화 M1/P0 데이터 수집 명세

## 1. 목적과 적용 기준

이 문서는 향후 추천 분석, 규칙 기반 랭킹 개선, 학습 랭커 구축 및 MLOps 재현을 위해 **M1/P0부터 실제로 수집할 데이터**를 정의한다.

기준 문서는 다음 세 문서다.

- `GABOLLE_기능_화면_상세설계서.md`
- `GABOLLE_화면흐름_및_정보구조_명세서_v1.1.docx`
- `GABOLLE_통합_서비스_기획서_v1.1.docx`

세 문서가 확정한 DB는 **PostgreSQL**이다. 따라서 아래 타입은 PostgreSQL 기준으로 작성한다.

핵심 분석 단위는 다음과 같다.

```text
(request_id, place_id)
= 추천 요청 당시 입력 스냅샷
+ 후보 장소와 데이터 출처
+ 하드 제약 판정
+ 피처·점수·순위·버전
+ 실제 화면 노출
+ 이후 사용자 행동과 여행 결과
```

| 구분 | 의미 |
|---|---|
| **M1/P0 필수** | 확정 화면과 추천 파이프라인이 생성하며 처음부터 저장해야 하는 데이터 |
| **후속 마일스톤** | 문서에 있으나 M2 이후 구현하는 데이터 |
| **제품 결정 필요** | 아이디어는 있으나 현재 확정 화면·요구사항에 없는 데이터 |

## 2. 공통 수집 원칙

### 2.1 응답 상태를 값과 분리한다

모든 사용자 입력은 `value`와 `answer_status`를 분리한다.

| `answer_status` | 의미 |
|---|---|
| `SELECTED` | 사용자가 값을 선택함 |
| `NONE` | 해당 사항이 없다고 명시함 |
| `SKIPPED` | 질문을 봤지만 건너뜀 |
| `UNKNOWN` | 묻지 않았거나 데이터를 확인할 수 없음 |

- 취향은 `SKIPPED`를 허용한다.
- 알레르기·식단은 건너뛰기 대신 `NONE`을 명시적으로 선택한다.
- `SKIPPED`, `UNKNOWN`, `NONE`을 같은 값이나 0점으로 변환하지 않는다.

### 2.2 적용 범위를 분리한다

| `scope` | 의미 |
|---|---|
| `USER` | 계정 기본 선호·제약 |
| `TRIP` | 특정 여행에만 적용하는 선호·제약 |
| `REQUEST` | 추천 요청 시점의 맥락 |
| `EVENT` | 추천 이후 발생한 불변 행동 관측 |

여행에서 수정한 값이 계정 기본값을 덮어쓰면 안 된다.

### 2.3 원자 이벤트를 보존한다

- 수집 시 긍정·부정 학습 라벨로 즉시 변환하지 않는다.
- 노출, 조회, 좋아요, 제외, 교체, 방문을 각각 별도 이벤트로 저장한다.
- `닫힘`, `시간 부족`, `날씨`, `교통 문제`를 취향 불호로 학습하지 않는다.

## 3. 공통 식별자·버전·시간 — M1/P0 필수

| 필드 | PostgreSQL 타입 | 필수 | 설명 |
|---|---|:---:|---|
| `event_id` | UUID | 이벤트 | 이벤트 멱등·중복 제거 키 |
| `event_type` | VARCHAR(64) | 이벤트 | 이벤트 종류 |
| `event_version` | INTEGER | 이벤트 | 이벤트 스키마 버전 |
| `request_id` | UUID | Y | 추천 요청부터 후보·노출·행동까지 연결 |
| `job_id` | UUID | 조건부 | 비동기 추천 작업 식별자 |
| `user_id` | UUID | Y | 사용자 식별자 |
| `trip_id` | UUID | 조건부 | 여행 식별자 |
| `place_id` | UUID | 후보/결과 | 내부 정본 장소 식별자 |
| `itinerary_id` | UUID | 조건부 | 일정 식별자 |
| `itinerary_version` | INTEGER | 조건부 | 불변 일정 버전 |
| `occurred_at` | TIMESTAMPTZ | 이벤트 | 실제 발생 시각 |
| `received_at` | TIMESTAMPTZ | 이벤트 | 서버 수신 시각 |
| `generated_at` | TIMESTAMPTZ | 추천 | 추천 결과 생성 시각 |
| `model_version` | VARCHAR(100) | Y | 규칙 또는 모델 버전 |
| `feature_version` | VARCHAR(100) | Y | 피처 정의·계산 버전 |
| `ontology_version` | VARCHAR(100) | Y | 제약 규칙·온톨로지 버전 |
| `dataset_version` | VARCHAR(100) | Y | 장소·경로 데이터 버전 |
| `app_version` | VARCHAR(50) | 권장 | 앱·웹 빌드 버전 |

버전 값을 얻지 못하면 임의의 기본값으로 처리하지 않고 요청 실패 또는 명시적 오류로 처리한다.

## 4. 추천 입력 스냅샷 — M1/P0 필수

프로필 전체의 `profile_version`을 먼저 도입하기보다 **추천 요청에서 실제 사용한 입력**을 불변 스냅샷으로 고정한다.

### 4.1 스냅샷 메타데이터

```text
preference_snapshot_id UUID
constraint_snapshot_id UUID
trip_version INTEGER
created_at TIMESTAMPTZ
```

`RecommendationJob`은 다음을 참조한다.

```text
request_id
job_id
trip_id
trip_version
preference_snapshot_id
constraint_snapshot_id
model_version
feature_version
ontology_version
dataset_version
generated_at
```

### 4.2 여행 기본 조건

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `start_date` | DATE | 여행 시작일 |
| `end_date` | DATE | 여행 종료일 |
| `origin_lat` | DOUBLE PRECISION | 추천 계산용 출발 좌표; PostGIS 사용 시 `origin_point`로 대체 |
| `origin_lng` | DOUBLE PRECISION | 추천 계산용 출발 좌표; PostGIS 사용 시 `origin_point`로 대체 |
| `origin_source` | VARCHAR(20) | `ADDRESS`, `MAP`, `CURRENT_LOCATION` |
| `origin_area_code` | VARCHAR(30) | 장기 분석용 지역 코드 |
| `budget_krw` | BIGINT | 여행 전체 예산 |
| `party_size` | INTEGER | 총 여행 인원 |
| `time_window_start` | TIME | 여행 가능 시작 시각 |
| `time_window_end` | TIME | 여행 가능 종료 시각 |
| `travel_modes` | VARCHAR(30)[] | 허용 이동 수단 |

```text
WALK
BUS
SUBWAY
TAXI
PRIVATE_CAR
RENTAL_CAR
BICYCLE
FERRY
OTHER
```

### 4.3 소프트 취향

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `dimension` | VARCHAR(50) | 취향 차원 |
| `value` | JSONB | 단일값·다중값·척도값 |
| `scope` | VARCHAR(10) | `USER` 또는 `TRIP` |
| `answer_status` | VARCHAR(10) | `SELECTED`, `SKIPPED`, `UNKNOWN` |

M1 확정 취향 차원:

```text
CATEGORY
ATMOSPHERE
LOCALITY
QUIETNESS
TOURIST_PREFERENCE
FOOD_PREFERENCE
SLOPE_PREFERENCE
SHADE_PREFERENCE
```

정확한 카테고리·분위기·음식 enum은 화면 옵션과 장소 태그 온톨로지가 확정된 뒤 동일 코드로 고정한다. 사용자 입력 코드와 장소 피처 코드가 달라지면 안 된다.

## 5. 안전·식단·이동 제약 — M1/P0 필수

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `constraint_type` | VARCHAR(30) | `ALLERGY`, `DIET`, `MOBILITY` |
| `value` | JSONB | 제약 상세값 |
| `hard` | BOOLEAN | 절대 제외 조건 여부 |
| `scope` | VARCHAR(10) | `USER` 또는 `TRIP` |
| `answer_status` | VARCHAR(10) | `SELECTED`, `NONE`, `UNKNOWN` |

### 5.1 알레르기

알레르기는 소프트 취향이 아니라 하드 제약으로 저장한다.

```text
PEANUT
TREE_NUT
WALNUT
ALMOND
CASHEW
PISTACHIO
HAZELNUT
MILK_DAIRY
EGG
WHEAT
GLUTEN
SOY
FISH
SHELLFISH_CRUSTACEAN
SHRIMP
CRAB
LOBSTER
MOLLUSK
OYSTER
MUSSEL
ABALONE
SQUID
SESAME
BUCKWHEAT
PEACH
TOMATO
OTHER
```

```text
other_allergy_ciphertext BYTEA
encryption_key_version VARCHAR(30)
encryption_nonce BYTEA
cross_contact_policy VARCHAR(40)
```

`cross_contact_policy`:

```text
AVOID_IF_CROSS_CONTACT_POSSIBLE
ALLOW_WITH_WARNING
NOT_SPECIFIED
```

세부 알레르기 enum은 제품·온톨로지 팀 검토 후 확정한다. 자유 입력 원문은 일반 행동 로그에 복제하지 않는다.

### 5.2 식단

```text
HALAL
KOSHER
VEGETARIAN
VEGAN
PESCATARIAN
NO_PORK
NO_BEEF
NO_SEAFOOD
GLUTEN_FREE
LACTOSE_FREE
DAIRY_FREE
EGG_FREE
NUT_FREE
NO_ALCOHOL
OTHER
```

```text
diet_requirement VARCHAR(10)       -- REQUIRED | PREFERRED
verification_policy VARCHAR(30)    -- VERIFIED_ONLY | ALLOW_UNKNOWN_WITH_WARNING
```

### 5.3 이동·접근성

```text
maximum_walking_meters INTEGER
wheelchair BOOLEAN
stroller BOOLEAN
heavy_luggage BOOLEAN
stairs_avoidance BOOLEAN
slope_preference VARCHAR(20)
shade_preference VARCHAR(20)
```

- 장애 진단명이 아니라 추천에 필요한 기능적 요구를 저장한다.
- 이동약자 하드 제약과 경사·그늘 소프트 선호를 구분한다.
- 장소 정보가 없으면 임의로 `PASS` 처리하지 않고 `UNKNOWN`으로 남긴다.

## 6. 장소 마스터와 데이터 출처 — M1/P0 필수

### 6.1 장소 기본 정보

```text
place_id UUID
name_ko VARCHAR
name_en VARCHAR
category_code VARCHAR
subcategory_code VARCHAR
address TEXT
district_code VARCHAR
latitude NUMERIC(9,6)
longitude NUMERIC(9,6)
open_hours JSONB
recommended_stay_minutes INTEGER
expected_cost_krw INTEGER
source_type VARCHAR
source_id VARCHAR
collected_at TIMESTAMPTZ
observed_at TIMESTAMPTZ
dataset_version VARCHAR
```

### 6.2 추천·제약 피처

```text
interest_tags VARCHAR[]
atmosphere_tags VARCHAR[]
cuisine_tags VARCHAR[]
locality_score DOUBLE PRECISION
quietness_score DOUBLE PRECISION
tourist_ratio DOUBLE PRECISION
popularity_score DOUBLE PRECISION
crowding_score DOUBLE PRECISION
allergen_tags VARCHAR[]
dietary_support_tags VARCHAR[]
accessibility_tags VARCHAR[]
stairs_present BOOLEAN
slope_percent NUMERIC
shade_score DOUBLE PRECISION
```

각 주요 피처에 다음 메타데이터를 붙인다.

```text
is_missing BOOLEAN
evidence_status VARCHAR(10) -- VERIFIED | ESTIMATED | UNKNOWN
observed_at TIMESTAMPTZ
source_version VARCHAR
```

알레르기·식단·접근성 정보가 없거나 검증되지 않은 장소를 안전하다고 표시하지 않는다.

## 7. 후보·제약 판정·랭킹 로그 — M1/P0 필수

추천 요청별 모든 후보를 `(request_id, place_id)` 단위로 저장한다.

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `candidate_source` | VARCHAR(50) | 후보 생성 출처 |
| `candidate_stage` | VARCHAR(30) | 후보 처리 단계 |
| `eligible` | BOOLEAN | 랭킹 가능 여부 |
| `constraint_verdict` | VARCHAR(10) | `PASS`, `FAIL`, `UNKNOWN` |
| `violations` | JSONB | 위반 코드와 근거 |
| `unknown_facts` | JSONB | 확인 불가능한 사실 |
| `constraint_confidence` | DOUBLE PRECISION | 판정 신뢰도 |
| `feature_values` | JSONB | 랭킹 시점 피처 스냅샷 |
| `score_components` | JSONB | 규칙 점수 구성 |
| `pre_rank_score` | DOUBLE PRECISION | 재정렬 전 점수 |
| `final_score` | DOUBLE PRECISION | 최종 점수 |
| `original_rank` | INTEGER | 초기 순위 |
| `final_rank` | INTEGER | 최종 순위 |
| `returned` | BOOLEAN | Top-K 반환 여부 |
| `reason_codes` | VARCHAR[] | 추천 이유 코드 |
| `warning_codes` | VARCHAR[] | 경고 코드 |
| `fallback_mode` | VARCHAR(20) | `MODEL`, `RULE`, `BASELINE` |

```text
GENERATED
QUALITY_FILTERED
HARD_FILTERED
RANKED
RERANKED
RETURNED
```

전체 `feature_values`를 장기 보관하기 어렵다면 최소한 피처 버전과 입력 스냅샷으로 동일 값을 재구성할 수 있어야 한다.

후보 수는 후보 행마다 반복하지 않고 `RecommendationJob`에 단계별로 한 번 저장한다.

```text
generated_candidate_count INTEGER
eligible_candidate_count INTEGER
returned_candidate_count INTEGER
```

## 8. 실제 노출과 사용자 행동

### 8.1 M1/P0 필수 이벤트

| 이벤트 | 필수 데이터 |
|---|---|
| `recommendation_requested` | `request_id`, `job_id`, `trip_id`, 입력 스냅샷 ID, 전체 버전 |
| `recommendation_impression` | `request_id`, `place_id`, `final_rank`, `reason_codes`, `source_screen`, 전체 버전 |
| `preference_set` | `trip_id`, `dimension`, `value`, `scope`, `answer_status` |
| `constraint_set` | `trip_id`, `constraint_type`, `value`, `scope`, `answer_status`, `hard` |
| `trip_created` | 여행 기본 조건과 `trip_version` |

`recommendation_impression`은 목록에 포함됐다는 이유로 만들지 않는다. IntersectionObserver 등으로 카드가 **실제 화면에 노출된 경우에만** 클라이언트가 전송한다.

### 8.2 후속 마일스톤 이벤트

```text
place_view
place_like
place_dislike
itinerary_item_locked
itinerary_item_removed
itinerary_item_replaced
itinerary_reordered
itinerary_recalculated
editorial_pick_like
editorial_pick_dislike
editorial_pick_hide
copy_to_trip
visit_inferred
trip_skipped
route_deviation
```

모든 행동 이벤트는 가능한 경우 `request_id`와 원래 노출 `place_id`에 연결한다. `request_id`가 없는 반응은 추천 성과 분석용 데이터에서 제외한다.

운영 사유 예시:

```text
NOT_INTERESTED
ALREADY_VISITED
WRONG_CATEGORY
TOO_CROWDED
TOO_TOURISTY
TOO_EXPENSIVE
TOO_FAR
CLOSED
BAD_WEATHER
NO_TIME
TRANSPORT_PROBLEM
ACCESSIBILITY_PROBLEM
DATA_INCORRECT
OTHER
```

## 9. 운영·품질 로그 — M1/P0 필수

```text
job_status
job_stage
progress_percent
total_latency_ms
candidate_generation_latency_ms
ontology_latency_ms
feature_lookup_latency_ms
ranking_latency_ms
optimization_latency_ms
error_code
failure_stage
retry_count
timeout_occurred
fallback_reason
fallback_mode
service_version
deployment_environment
```

데이터 품질 지표:

```text
event_schema_valid_rate
duplicate_event_rate
missing_request_id_rate
exposure_action_join_rate
candidate_feature_missing_rate
place_field_fill_rate
stale_data_rate
```

## 10. 개인정보·동의·보존

- 알레르기·건강성 식단·정밀 위치는 일반 행동 로그와 분리하고 접근을 통제한다.
- 행동 개인화는 `EXPLICIT_ONLY`와 `BEHAVIOR_ENABLED`를 구분한다.
- 행동 개인화 OFF 시 행동 피처 서빙을 즉시 중단한다.
- 정밀 위치는 여행 실행 중 전경에서만 사용한다.
- 원본 GPS는 기본 7일 이내 삭제하고 거리 구간·체류시간 등 파생값만 장기 사용한다.
- 개인화 초기화·계정 삭제 시 파생 피처와 식별 가능한 이벤트까지 추적해 처리할 수 있어야 한다.

동의 데이터는 M2 범위지만 스키마는 미리 충돌 없이 설계한다.

```text
personalization_mode VARCHAR(20) -- EXPLICIT_ONLY | BEHAVIOR_ENABLED
behavior_consent_status VARCHAR(10)
sensitive_consent_status VARCHAR(10)
precise_location_consent_status VARCHAR(10)
consent_policy_version VARCHAR(50)
consent_decided_at TIMESTAMPTZ
```

`user_ref_map`을 삭제하는 것만으로 계정 삭제가 충족된다고 가정하지 않는다. 식별 가능한 원본·파생 데이터의 삭제 또는 정책에 따른 익명화가 별도로 검증돼야 한다.

## 11. 위치·GPS 데이터

GPS는 하나의 영구 행동 로그로 저장하지 않는다. 사용 목적과 보존 기간에 따라 세 종류로 분리한다.

### 11.1 계획 여행 출발지 — M1/P0 필수

사용자가 주소, 지도 또는 현재 위치로 지정한 여행 출발지는 `Trip` 입력으로 저장한다.

PostGIS 미사용 시:

```text
origin_lat DOUBLE PRECISION
origin_lng DOUBLE PRECISION
origin_source VARCHAR(20) -- ADDRESS | MAP | CURRENT_LOCATION
```

PostGIS 사용 시 권장 타입:

```sql
origin_point GEOGRAPHY(POINT, 4326)
```

이 좌표는 연속 GPS 궤적이 아니라 사용자가 선택한 여행 입력이다. 여행 또는 계정 삭제 정책에 맞춰 함께 삭제한다.

### 11.2 현재 위치 기반 추천 — M2

`지금 갈 곳` 추천은 정확한 현재 좌표를 계산 중에 사용할 수 있다. 위치 권한을 거부하면 수동 위치 입력으로 동일 기능을 제공한다.

단기 운영 데이터:

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `request_id` | UUID | 추천 요청 연결 키 |
| `location_point` | `GEOGRAPHY(POINT, 4326)` | PostGIS 사용 시 현재 위치 |
| `location_lat` | DOUBLE PRECISION | PostGIS 미사용 시 현재 위도 |
| `location_lng` | DOUBLE PRECISION | PostGIS 미사용 시 현재 경도 |
| `location_accuracy_m` | REAL | 단말이 제공한 정확도 반경 |
| `location_source` | VARCHAR(20) | `GPS` 또는 `MANUAL` |
| `captured_at` | TIMESTAMPTZ | 위치 측정 시각 |

PostGIS를 사용하면 `location_point`만 저장하고 `location_lat`, `location_lng`는 중복 저장하지 않는다.

장기 추천 분석에는 정확한 현재 좌표 대신 다음 파생값을 남긴다.

```text
origin_area_code VARCHAR(30)
location_available BOOLEAN
location_source VARCHAR(20)
distance_meters INTEGER
travel_time_seconds INTEGER
distance_bucket VARCHAR(20)
```

### 11.3 여행 실행 중 GPS 표본 — M3

사용자가 여행 실행을 명시적으로 시작했고 앱이 foreground인 동안에만 위치를 batch로 수집한다.

단기 원본 표본:

| 필드 | PostgreSQL 타입 | 설명 |
|---|---|---|
| `location_sample_id` | UUID | 표본 식별자 |
| `execution_id` | UUID | 활성 여행 실행 식별자 |
| `captured_at` | TIMESTAMPTZ | 단말 측정 시각 |
| `received_at` | TIMESTAMPTZ | 서버 수신 시각 |
| `point` | `GEOGRAPHY(POINT, 4326)` | PostGIS 사용 시 위치 |
| `latitude` | DOUBLE PRECISION | PostGIS 미사용 시 위도 |
| `longitude` | DOUBLE PRECISION | PostGIS 미사용 시 경도 |
| `accuracy_m` | REAL | 정확도 반경 |
| `speed_mps` | REAL | 단말 제공 시 선택 수집 |

PostGIS를 사용하면 `point`만 저장하고 `latitude`, `longitude`는 중복 저장하지 않는다.

원본 GPS 좌표는 기본 **7일 이내 삭제**한다. 장기 보관 데이터는 다음 파생 결과로 제한한다.

```text
execution_id UUID
place_id UUID
distance_bucket VARCHAR(20)
minimum_distance_meters INTEGER
dwell_seconds INTEGER
visit_status VARCHAR(30) -- INFERRED | VERIFIED_BY_REVIEW | UNKNOWN
deviation_distance_meters INTEGER
derived_at TIMESTAMPTZ
derivation_version VARCHAR(50)
```

### 11.4 GPS 수집 금지 조건

- 정밀 위치 별도 동의가 없으면 수집하지 않는다.
- 앱이 background로 전환되면 즉시 중단한다.
- 여행 실행이 `PAUSED` 또는 `ENDED`이면 즉시 중단한다.
- 위치 미동의 사용자의 방문 여부를 추측해서 채우지 않는다.
- 원본 좌표를 일반 행동 이벤트 `JSONB`에 복제하지 않는다.
- 연속 좌표, 고도, 단말 광고 ID 등 추천·방문 판정에 필요 없는 값은 수집하지 않는다.

## 12. 현재 P0에서 제외하는 입력

다음은 유용할 수 있지만 현재 확정 요구사항에 없으므로 P0 필수 입력으로 구현하지 않는다.

- 동행자 세부 유형과 연령별 인원: 현재 M1 확정값은 `party_size`
- 반려동물 종류와 수
- 숙소 유형과 숙박 성향
- 일일·숙소·식비·교통비별 예산: 현재 M1 확정값은 여행 전체 `budget_krw`
- 하루 최대 방문 장소 수와 세부 여행 페이스
- 매우 세분화된 여행 스타일·관심사·음식 enum
- 연속적인 국가·기기·화면 크기 수집

제품에서 화면과 추천 사용처를 확정하면 별도 버전으로 추가한다. 수집 목적이나 추천 피처 매핑이 없는 질문은 먼저 받지 않는다.

## 13. 조건부: 장면 선택·짝 비교 온보딩

현재 확정 문서는 3페이지 장면 선택·짝 비교 온보딩을 요구하지 않는다. 따라서 다음 항목은 P0 핵심 추천 로그가 아니다.

```text
onboarding_session_id
page
item_id
shown
visible
chosen
shown_order
grid_position
design_seed
pair_position
device_width
viewport_height
shown_at
answered_at
skipped
```

추후 해당 UX를 실제 채택하면 다음 원칙을 적용한다.

- 보여준 전체 후보를 남긴다.
- 실제 화면에 들어온 항목과 단순히 데이터로 받은 항목을 구분한다.
- `chosen=false`를 보지 않은 항목과 혼동하지 않는다.
- 표시 순서·위치를 남겨 위치 편향을 분석할 수 있게 한다.
- 온보딩 분석 이벤트는 추천 결과의 `recommendation_impression`과 분리한다.

## 14. 9월 4일 전 완료 체크리스트

- [ ] PostgreSQL 이벤트·후보·입력 스냅샷 테이블 DDL 확정
- [ ] `request_id` 생성 주체와 전 서비스 전파 규칙 확정
- [ ] `preference_snapshot_id`, `constraint_snapshot_id`, `trip_version` 연결
- [ ] `SELECTED`, `NONE`, `SKIPPED`, `UNKNOWN` 저장 규칙 적용
- [ ] `recommendation_requested` 서버 이벤트 저장
- [ ] 전체 후보 수·출처와 `(request_id, place_id)` 후보 로그 저장
- [ ] `PASS`, `FAIL`, `UNKNOWN`, 위반·미확인 근거 저장
- [ ] 최종 점수·순위·이유·fallback·전체 버전 저장
- [ ] 실제 화면 노출만 `recommendation_impression`으로 전송
- [ ] 계획 여행 출발 좌표와 `origin_source` 저장
- [ ] 현재 위치 원본과 장기 분석용 거리·지역 파생값 분리
- [ ] GPS foreground·동의·PAUSED/ENDED 중단 조건 정의
- [ ] 원본 GPS 7일 이내 삭제 작업과 검증 테스트 설계
- [ ] Outbox 또는 동등한 방식으로 업무 저장과 이벤트 저장의 유실 방지
- [ ] 중복 이벤트 제거와 스키마 검증 테스트
- [ ] 후보 → 노출을 `request_id + place_id`로 조인하는 검증 쿼리 작성

Kafka, Spark, UserBias 자동 계산, MLflow 모델 레지스트리, 실험 자동 배정 및 실시간 재학습은 이 체크리스트의 선행 조건이 아니다.

## 15. 최종 분석 레코드

향후 분석·학습 데이터는 다음 구조로 생성할 수 있어야 한다.

```text
request_id
user_id
trip_id
trip_version
preference_snapshot_id
constraint_snapshot_id
place_id
사용자·여행 선호와 응답 상태
요청 시점 여행 맥락
장소 피처와 데이터 상태
제약 판정과 근거
규칙 점수 구성
최종 점수·순위
실제 노출 여부
상세 조회·좋아요·싫어요
일정 고정·제거·교체
방문·건너뛰기 결과
운영 사유
model/feature/ontology/dataset version
행동 개인화 적격 여부
```

학습 데이터셋은 원자 이벤트에서 파생하고, 라벨 정의·관측 기간·가중치·제외 규칙을 데이터셋 버전과 함께 기록한다.
