# 코스(Course) 기능 개발 명세

여행 일정 자동 생성 서비스의 **코스 선택 기능** 단독 명세. 이 문서만으로 구현 가능하도록 자립적으로 작성됨.

---

## 0. 한 문장 정의

사용자가 여행 조건을 입력하면 시스템이 실행 가능한 일정을 생성하는데, **코스**란 그 일정의 성격을 결정하는 선택지다. 코스는 서로 다른 두 축의 조합으로 정해진다.

---

## 1. 핵심 모델 — 두 축의 조합

```
최종 일정 = f(추천 모드, 코스 카테고리, 사용자 조건)
```

| 축 | 답하는 질문 | 개수 | 결정하는 것 |
| --- | --- | --- | --- |
| **추천 모드** | 누구의 추천을 따를 것인가 | 3 | 후보 **점수 배분 비율** |
| **코스 카테고리** | 어떤 성격의 여행인가 | 10 | **hard filter + 일정 파라미터 + 점수 가중치** |

**중요**: 두 축은 배타적이 아니라 **곱해진다**. `mode=LOCAL` + `category=OLD_TOWN` = 로컬이 인정한 노포 중심 코스.

역할 분리 규칙 — 구현 시 이 경계를 반드시 지킬 것:

| 담당 | 무엇을 |
| --- | --- |
| 카테고리 | 후보에서 **원천 배제**할 조건(hard filter), 일정 파라미터(예산·이동수단·속도·실내외 비중·시간대) |
| 모드 | 남은 후보 중 **무엇을 우선 뽑을지**(landmark/local/pet 비율) |
| 안전 조건 | 반려동물 정책·알레르기는 **카테고리·모드보다 항상 먼저** 적용 (override 불가) |

---

## 2. 추천 모드 (3종)

| code | 한글명 | landmark | local | pet |
| --- | --- | --- | --- | --- |
| `MUST_SEE` | 관광 필수 코스 | 70 | 20 | 10 |
| `LOCAL` | 현지인 코스 | 10 | 80 | 10 |
| `PET_SAFE` | 반려동물 안심 코스 | 10 | 30 | 60 |

- 사용자는 슬라이더로 비율을 직접 조정할 수도 있다(기본값 30/50/20). 위 3종은 그 프리셋이다.
- 비율 합은 항상 100이어야 한다. 검증 실패 시 `400 INVALID_RATIO`.

---

## 3. 코스 카테고리 (10종)

전체 설정은 `course_categories.json`이 **단일 진실 원천(single source of truth)**. 코드에 값을 하드코딩하지 말고 이 파일을 로드할 것.

| code | 한글명 | 영문명 | 축 | MVP |
| --- | --- | --- | --- | --- |
| `ZERO_WON` | 무일푼 코스 | Zero-Won Day | BUDGET | ✅ |
| `BEST_BANG` | 갓성비 코스 | Best Bang | BUDGET | ✖ |
| `SPLURGE` | 럭셔리 코스 | Splurge | BUDGET | ✖ |
| `OLD_TOWN` | 구수한 코스 | Old Town Flavors | MOOD | ✅ |
| `NATURE_FIX` | 자연인 코스 | Nature Fix | THEME | ✅ |
| `PICTURE_PERFECT` | 인생샷 코스 | Picture Perfect | THEME | ✖ |
| `CAR_FREE` | 뚜벅이 코스 | Car-Free | MOBILITY | ✅ |
| `EASY_PACE` | 효도 코스 | Easy Pace | COMPANION | ✖ |
| `SOLO_FRIENDLY` | 혼행 코스 | Solo Friendly | COMPANION | ✖ |
| `RAINY_DAY` | 비 오는 날 코스 | Rainy Day | SITUATION | ✅ |

**영문명은 직역이 아니라 재작명이다.** i18n 리소스에서 한글명과 영문명을 각각 독립된 문자열로 관리할 것. 기계 번역 금지.

### 3.1 비활성 카테고리 처리

`enabled: false`인 카테고리는 **데이터가 없어서** 지금 만들 수 없는 것이다(코드 문제가 아님).

| code | disabledReason | 필요한 것 |
| --- | --- | --- |
| `BEST_BANG` | `DATA_NOT_READY_SATISFACTION` | 사용자 만족도·재방문 데이터 축적 |
| `SPLURGE` | `INVENTORY_UNVERIFIED_BUSAN` | 부산 고가 숙소·식당 재고량 실측 |
| `PICTURE_PERFECT` | `DATA_NOT_READY_PHOTO_TIME_WINDOW` | 장소별 `photoTimeWindow` 태깅 |
| `EASY_PACE` | `DATA_NOT_READY_STEP_FREE` | 계단·경사·좌석 정보 수집 |
| `SOLO_FRIENDLY` | `DATA_NOT_READY_SOLO_FLAG` | 1인 이용 가능 여부 태깅 |

**요구사항**: 비활성 카테고리는 목록 API에서 `enabled: false`로 내려보내고, **클라이언트는 목록에서 숨긴다.** 선택은 되는데 결과가 부실한 상태를 만들지 말 것. 강제로 요청이 들어오면 `422 CATEGORY_NOT_AVAILABLE` + `disabledReason` 반환.

---

## 4. 데이터 모델

### 4.1 Place — 카테고리 필터에 필요한 필드

기존 Place 스키마에 아래를 추가한다. **3-state를 지킬 것**: `true` / `false` / `null`(정보 없음). `null`을 `false`로 뭉개면 안 된다.

| 필드 | 타입 | 용도 | 데이터 출처 |
| --- | --- | --- | --- |
| `isFree` | `boolean?` | ZERO_WON | 이용요금 필드 파싱 + 자체 확인 |
| `isIndoor` | `boolean?` | RAINY_DAY, NATURE_FIX | 카테고리 코드로 유도 |
| `priceLevel` | `int?` (0~4) | BEST_BANG, SPLURGE | 가격대 정규화 |
| `touristDensity` | `float?` (0~1) | OLD_TOWN | 관광객 방문 비율 집계 |
| `stepFree` | `boolean?` | EASY_PACE | **자체 조사 필요** |
| `hasSeating` | `boolean?` | EASY_PACE | 자체 조사 |
| `soloFriendly` | `boolean?` | SOLO_FRIENDLY | **자체 태깅 필요** |
| `requiresGroupBooking` | `boolean?` | SOLO_FRIENDLY | 자체 태깅 |
| `hasPhotoTag` | `boolean` | PICTURE_PERFECT | 태그 존재 여부 |
| `photoTimeWindow` | `enum?` | PICTURE_PERFECT | `SUNSET`/`GOLDEN_HOUR`/`NIGHT`/`ANY` |
| `localScore` | `float` (0~1) | OLD_TOWN 등 | 자체 산출 |

인덱스: `isFree`, `isIndoor`, `stepFree`, `soloFriendly`에 **부분 인덱스(partial index)** 를 건다. 이 필드들은 필터링에만 쓰이고 카디널리티가 낮다.

### 4.2 TripPreference

```
courseCategory: string?   // 카테고리 code. null이면 카테고리 미적용(일반 일정)
mode: string              // MUST_SEE | LOCAL | PET_SAFE
landmarkRatio: int
localRatio: int
petFriendlyRatio: int
```

---

## 5. 점수 계산

### 5.1 기본 가중치

```
tasteMatch        25
localScore        20
travelEfficiency  15
petFit            15
budgetFit         10
foreignerEase     10
freshness          5
                 ---
합계             100
```

### 5.2 카테고리 적용 절차 (순서 고정)

```
1. 반려동물 미동반이면 petFit 제거 후 나머지를 합 100으로 재정규화
2. 카테고리의 weightMultipliers를 각 항목에 곱함
3. 다시 합 100으로 재정규화          ← 반드시 수행. 누락 시 카테고리마다 점수 스케일이 달라짐
4. boostTasteTags가 있으면 해당 태그 매칭 시 tasteMatch 항목에 가산
5. landmarkPenalty가 있으면 대표 관광지 플래그 장소의 최종 점수에 곱함
6. budgetFitMode == "INVERSE"이면 budgetFit 계산을 뒤집음(고가일수록 높은 점수)
```

**구현 주의**: 3번 재정규화를 빠뜨리는 것이 가장 흔한 버그다. `ZERO_WON`은 multiplier 합이 커서 재정규화 없이는 다른 카테고리보다 총점이 부풀려진다.

### 5.3 커스텀 점수 (BEST_BANG 전용)

`customScore`가 정의된 카테고리는 별도 항목을 하나 추가한다.

```
valueScore = localScore / normalize(priceLevel)
```

만족도 데이터가 없는 동안에는 `localScore` 단독으로 근사하고, `customScore.fallback` 사용 중임을 응답 메타에 표시한다.

---

## 6. 일정 파라미터

카테고리는 최적화 엔진에 아래 파라미터를 주입한다.

| 파라미터 | 의미 | 예 |
| --- | --- | --- |
| `pace` | 여행 속도 | `RELAXED` / `NORMAL` / `PACKED` |
| `placesPerDay` | 하루 방문 장소 수 `[min, max]` | `[3, 4]` |
| `transport` | 이동수단 | `WALK_ONLY` / `WALK_FIRST` / `CAR` / `CAR_OR_TAXI` / `WALK_OR_TRANSIT` |
| `stayMinutesScale` | 기본 체류시간 배율 | `1.4` |
| `maxWalkDistanceScale` | 하루 도보 상한 배율 | `1.5` |
| `maxTravelMinutesBetween` | 장소 간 이동 상한(분) | `20` |
| `dayEndTimeCap` | 하루 종료 시각 상한 | `"21:00"` |
| `forbidTimeRange` | 배치 금지 시간대 | `{ after: "20:00" }` |
| `regularMealTimes` | 식사 시간 규칙적 배치 강제 | `true` |

### 6.1 DAY 스코프 필터 — 특별 취급

`scope: "DAY"`인 hard filter는 **개별 장소가 아니라 하루 일정 전체**에 대한 제약이다. 최적화 모델의 제약조건으로 넣어야 하며, 후보 필터링 단계에서 처리할 수 없다.

| 필터 | 의미 |
| --- | --- |
| `outdoorRatioPerDay >= 0.7` | 하루 방문 장소 중 실외가 70% 이상 |
| `indoorRatioPerDay >= 0.9` | 하루 방문 장소 중 실내가 90% 이상 |
| `walkClusterRadiusM <= 1200` | 하루 모든 장소가 반경 1.2km 클러스터 내 |

### 6.2 시간대 고정 배치 (PICTURE_PERFECT)

`timeWindowRule.hard == true`이면 해당 장소는 **일반 운영시간이 아니라 계산된 좁은 시간 창**으로 최적화 모델에 투입한다.

```
sunsetTime = calcSunset(lat, lng, date)      // 천문 계산 라이브러리 사용
SUNSET      → [sunsetTime - 30m, sunsetTime + 30m]
GOLDEN_HOUR → [sunsetTime - 90m, sunsetTime - 30m]
NIGHT       → [sunsetTime + 30m, sunsetTime + 180m]
```

이 창은 장소의 실제 운영시간과 **교집합**을 취한다. 교집합이 공집합이면 그 장소는 그 날짜에 후보에서 제외.

---

## 7. 일정 생성 파이프라인에서의 위치

```
1. 사용자 조건 파싱
2. 예산-카테고리 충돌 검사              ← 카테고리 (§8)
3. 장소 후보 수집
4. 안전 필터 (반려동물 정책, 알레르기)   ← 항상 최우선, override 불가
5. 카테고리 hard filter (장소 단위)      ← 카테고리
6. 점수 계산 (기본 가중치 × 카테고리 multiplier → 재정규화)  ← 카테고리 + 모드
7. 모드 비율에 따른 후보 배분            ← 모드
8. 지역 클러스터링
9. 날짜별 배정
10. 최적화 (OR-Tools) — DAY 스코프 제약 + 일정 파라미터 + 시간창 투입  ← 카테고리
11. 예산 검증
12. 실패 시 완화 사다리 실행             ← 카테고리 (§9)
13. 결과 반환 (적용된 완화 내역 포함)
```

---

## 8. 예산 충돌 처리

사용자는 전체 예산을 별도로 입력한다. 예산 축 카테고리(`ZERO_WON`/`BEST_BANG`/`SPLURGE`)를 고르면 충돌할 수 있다.

**규칙: 덮어쓰지 않고 사용자에게 되묻는다.**

```
requiredBudget = category.budgetPerPersonPerDay.min × partySize × days

if (userBudget < requiredBudget) {
    return 409 BUDGET_CATEGORY_CONFLICT {
        requiredBudget,
        userBudget,
        suggestedCategories: [ /* 예산에 맞는 다른 예산축 카테고리 */ ]
    }
}
```

클라이언트는 두 가지 선택지를 제시한다: ① 예산 올리기 ② 제안된 카테고리로 변경. 사용자가 둘 다 거부하면 `courseCategory = null`로 일반 일정을 생성한다.

**금지**: 카테고리가 사용자 입력 예산을 조용히 변경하는 것. 사용자 제약이 무시된 것처럼 보인다.

---

## 9. 완화 사다리 (Relaxation Ladder)

후보 부족으로 일정을 채우지 못할 때, **조건을 어긴 장소를 몰래 끼워 넣지 않는다.** 카테고리별로 정의된 순서대로 완화하고, **무엇을 완화했는지 반드시 응답에 포함**한다.

```
for step in category.relaxationLadder:
    apply(step)
    result = tryGenerate()
    if result.isComplete: 
        return result.withRelaxations(appliedSteps)

return partialResult.withRelaxations(appliedSteps)   // 부분 충족도 반환. 침묵 실패 금지
```

액션 타입:

| action | 의미 |
| --- | --- |
| `EXPAND_RADIUS` | 후보 탐색 반경 배율 확대 |
| `ALLOW_PRICE_LEVEL` | 허용 가격대 완화 (`maxPlacesPerDay`로 개수 제한 가능) |
| `LOWER_OUTDOOR_RATIO` / `LOWER_INDOOR_RATIO` | DAY 스코프 비율 완화 |
| `RAISE_TOURIST_DENSITY` | 관광객 밀집도 상한 완화 |
| `EXPAND_CLUSTER_RADIUS` | 도보 클러스터 반경 확대 |
| `ALLOW_TRANSIT_DEEPLINK` | 대중교통 구간 허용 |
| `WIDEN_TIME_WINDOW` / `DROP_TIME_WINDOW` | 시간창 확대 / 포기 |
| `DROP_FIELD` | 특정 hard filter 필드 제거 |
| `SUGGEST_ALTERNATIVE_CATEGORY` | 다른 카테고리 제안하고 중단 |

응답 예시:

```json
{ "itinerary": { ... },
  "relaxations": [
    { "rule": "FREE_ONLY", "action": "ALLOW_PRICE_LEVEL",
      "noticeKo": "무료 장소가 부족해 저가 장소 1곳을 포함했습니다" }
  ] }
```

---

## 10. API 계약

### 10.1 카테고리 목록 조회

```
GET /v1/course-categories?lang=ko
200 →
{ "categories": [
    { "code": "ZERO_WON", "nameKo": "무일푼 코스", "nameEn": "Zero-Won Day",
      "axis": "BUDGET", "summary": "입장료 없는 곳만으로 채우는 하루",
      "budgetPerPersonPerDay": { "min": 0, "max": 20000 }, "available": true },
    { "code": "EASY_PACE", "nameKo": "효도 코스", "nameEn": "Easy Pace",
      "axis": "COMPANION", "available": false, "reason": "DATA_NOT_READY_STEP_FREE" }
] }
```

- 인증 불필요. 캐시 가능(TTL 1시간).
- `lang`에 따라 `summary`를 해당 언어로 반환.

### 10.2 일정 생성 요청

```
POST /v1/trips/{tripId}/itineraries:generate
{ "mode": "LOCAL",
  "courseCategory": "OLD_TOWN",
  "preference": { "tasteTags": ["local_food","cafe"],
                  "landmarkRatio": 10, "localRatio": 80, "petRatio": 10 } }

202 → { "jobId": "job_...", "statusUrl": "...", "streamUrl": "..." }
409 → BUDGET_CATEGORY_CONFLICT  (§8)
422 → CATEGORY_NOT_AVAILABLE { "reason": "DATA_NOT_READY_STEP_FREE" }
400 → INVALID_RATIO           (비율 합 ≠ 100)
```

- `courseCategory`는 선택 필드. 생략하면 카테고리 미적용.
- `Idempotency-Key` 헤더 필수.

### 10.3 일정 결과 조회

```
GET /v1/trips/{tripId}/itineraries?mode=LOCAL
200 → { "mode": "LOCAL", "courseCategory": "OLD_TOWN",
        "days": [...], "relaxations": [...] }
```

---

## 11. 테스트 케이스

구현 완료 판정 기준. 각 항목은 자동 테스트로 검증 가능해야 한다.

| # | 케이스 | 기대 결과 |
| --- | --- | --- |
| T1 | `ZERO_WON` 선택 | 결과의 모든 장소가 `isFree == true` (완화 적용 전) |
| T2 | `RAINY_DAY` 선택 | 하루 방문 장소 중 실내 비율 ≥ 0.9 |
| T3 | `NATURE_FIX` 선택 | 하루 방문 장소 중 실외 비율 ≥ 0.7 |
| T4 | `CAR_FREE` 선택 | 하루 모든 장소가 반경 1200m 클러스터 내 |
| T5 | `OLD_TOWN` 선택 | 20:00 이후 배치된 장소 0건 |
| T6 | 가중치 재정규화 | 모든 카테고리에서 적용 후 가중치 합 == 100 (±0.01) |
| T7 | 반려동물 우선순위 | 반려견 조건 + 임의 카테고리 → 정책 미충족 장소 0건 |
| T8 | 예산 충돌 | 저예산 + `SPLURGE` → `409` + `suggestedCategories` non-empty |
| T9 | 비활성 카테고리 | `EASY_PACE` 요청 → `422` + `disabledReason` |
| T10 | 완화 내역 표시 | 무료 장소 부족 상황 → `relaxations` 배열 non-empty |
| T11 | 3-state 보존 | `isFree == null`인 장소가 `ZERO_WON`에서 통과되지 않음 |
| T12 | 모드 × 카테고리 | `LOCAL` + `OLD_TOWN` → 두 조건 모두 만족 |
| T13 | 카테고리 미지정 | `courseCategory: null` → hard filter 없이 정상 생성 |
| T14 | 목록 필터링 | 목록 API 응답에서 `available: false` 항목이 UI에 렌더링되지 않음 |

---

## 12. 구현 시 반드시 지킬 것

1. **설정은 JSON에서 읽는다.** 카테고리 값을 코드에 하드코딩하지 말 것. 튜닝이 잦다.
2. **가중치 재정규화를 빠뜨리지 말 것.** (§5.2 3번)
3. **`null`은 `false`가 아니다.** 정보 없음과 불가를 구분한다.
4. **안전 조건이 항상 우선.** 반려동물·알레르기는 카테고리가 덮어쓸 수 없다.
5. **침묵 실패 금지.** 조건을 완화했으면 반드시 응답에 남긴다.
6. **비활성 카테고리는 숨긴다.** 선택 가능한데 결과가 부실한 상태가 최악이다.
7. **DAY 스코프 필터는 최적화 제약**이지 후보 필터가 아니다.
8. **영문명은 번역이 아니라 별도 리소스.** 기계 번역하지 말 것.
