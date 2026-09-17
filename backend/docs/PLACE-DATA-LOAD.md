# 장소 자료 운영 적재 절차

관광공사·무장애 자료와 **유도값**(경사·조용함·로컬성)을 운영 DB 에 넣는 순서다. 적재기는
전부 DB 에 붙어 도는 러너라 **배포 호스트에서** 돌려야 하고, 서로 순서가 있다.

`S15P21E201-854`·`-474`·`-852`·`-862`·`-1047`·`-1167` 의 마지막 단계다. 코드는 전부
`back/dev` 에 머지돼 있고 이 문서가 남은 절차다.

> 🔴 **적재기를 머지하는 것과 자료를 넣는 것은 다른 일이다.** 배포는 적재기를 운영에
> 올려 놓을 뿐 **아무것도 넣지 않는다.** 여기 적힌 것을 누군가 손으로 한 번 돌려야 한다.
> 개수를 이 문단에 적어 두지 않는 이유는, 적재기가 늘 때마다 그 줄이 낡기 때문이다 —
> 목록은 3절에 있다.

## 0. 입력 파일 셋 — 전부 git 에 있다

**`bigData/dev` 브랜치에 있다.** `back/dev` 에는 없으므로 그쪽만 보면 안 보인다.

| 파일 | 크기 | 쓰는 적재기 |
|---|---|---|
| `bigData/data/raw/tourapi/tourapi-busan.ndjson` | 1.0MB | 장소 · 탐색 표식 |
| `bigData/data/staged/opening-hours.ndjson` | 0.26MB | 영업시간 |
| `bigData/data/raw/tourapi/tourapi-barrier-free-busan.ndjson` | 0.33MB | 접근성 |

장소와 탐색 표식은 **같은 파일**을 읽는다. 접근성은 **다른 파일**이다 — 무장애 여행
정보는 별도 수집본이고 접근성 문장이 그 상세에만 있다.

꺼내는 방법:

```bash
git fetch origin bigData/dev
mkdir -p /tmp/load
for f in bigData/data/raw/tourapi/tourapi-busan.ndjson \
         bigData/data/staged/opening-hours.ndjson \
         bigData/data/raw/tourapi/tourapi-barrier-free-busan.ndjson; do
  git show origin/bigData/dev:$f > /tmp/load/$(basename $f)
done
```

### 유도값 파일 — 경사·조용함·로컬성 (2026-09-17 추가)

| 파일 | 줄 | 쓰는 적재기 |
|---|---|---|
| `bigData/data/staged/place-slope.ndjson` | 2,213 | 경사 (관광공사) |
| `bigData/data/staged/place-quietness.ndjson` | 654 | 조용함 (관광공사) |
| `bigData/data/staged/place-quietness-sbiz.ndjson` | 2,355 | 조용함 (상가) |
| `bigData/data/staged/place-locality.ndjson` | 498 | 로컬성 (관광공사) |
| `bigData/data/staged/place-locality-sbiz.ndjson` | 2,230 | 로컬성 (상가) |

```bash
for f in place-slope place-quietness place-quietness-sbiz place-locality place-locality-sbiz; do
  git show origin/bigData/dev:bigData/data/staged/$f.ndjson > /tmp/load/$f.ndjson
done
```

> 🔴 **이 파일들은 `backend/` 빌드 산출물이 아니다.** 배포 이미지 안에 없고 앞으로도 안
> 들어간다 — `bigData/` 에 있고 `back/dev` 에서는 **안 보인다.** 그래서 위 0절과 1절(서버에
> 올리기)을 반드시 지나야 한다.
>
> 2026-09-17 에 이 자리에서 실제로 걸렸다. 적재기는 `back/dev` 에 머지돼 있는데
> *"배포하면 들어가겠지"* 로 읽어서, 배포를 한 번 돌리고 나서야 **아무것도 안 들어간 것**을
> 알았다. **적재기를 머지하는 것과 자료를 넣는 것은 다른 일이다.**

> 🔴 **상가 파일을 따로 셀 필요가 없다 (2026-09-17, S15P21E201-1167).** 조용함·로컬성
> 적재기는 **한 줄씩 보고 열쇠 모양을 스스로 가른다** — `contentid` 가 있으면 관광공사,
> `sourceType`+`sourceId` 가 있으면 그 출처다. 그래서 두 파일을 **같은 속성으로 한 번씩**
> 돌리면 된다.
>
> **경사는 아니다.** 경사 적재기(`readPlaceSlopes`)는 관광공사 열쇠만 읽어서, 상가
> 2,355곳을 마이그레이션(`V20260916230000__place_slope_sbiz.sql`)으로 따로 넣었다. 그래서
> 아래 ⑥에 상가 경사 파일이 없다 — **이미 들어가 있다.**

## 1. 서버에 올린다

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@j15e201.p.ssafy.io
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load'
scp -i $PEM /tmp/load/*.ndjson $HOST:/home/ubuntu/load/
```

## 2. 도는 백엔드의 DB 접속값을 빌린다

적재기는 DB 에 붙어야 하는데 접속 정보가 Jenkins 자격증명이라 파일에 없다. 이미 도는
`backend` 컨테이너가 그 값을 들고 있으므로 그것을 빌린다.

```bash
ssh -i $PEM $HOST
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
wc -l /tmp/load.env    # 네 줄이면 정상
```

🔴 `/tmp/load.env` 에 DB 비밀번호가 들어 있다. **5 단계에서 지운다.**

## 3. 적재 — 순서를 지킨다

**①이 언제나 먼저다.** 뒤의 것들은 전부 장소에 붙는 값이라, 붙을 장소가 없으면 「장소가
없어 못 넣음」으로만 세어진다. 🔴 **전부 두 번 돌려도 행이 안 는다** — 다시 돌리는 것은
안전하다.

`GABOLLE_JWT_SECRET` 을 임시값으로 준다. 2 단계에서 빌린 네 줄에는 이 값이 없는데, 기동할 때 32자 이상을 요구하는 검사가 있어 없으면 앱이 뜨지도 못하고 죽는다
(`authStartupValidator`). 적재기는 토큰을 만들지 않으므로 **운영의 진짜 값을 가져올 필요가 없다** — 아무 32자 이상이면 된다. 2026-09-13 실측.

```bash
VER=tourapi-busan-20260911
IMG=local-route-backend:candidate
NET=local-route-personalization_data_net
RUN="docker run --rm --network $NET --env-file /tmp/load.env \n  -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \n  -v /home/ubuntu/load:/load $IMG"

# ① 장소 328곳
$RUN --gabolle.place.loader.tourapi=/load/tourapi-busan.ndjson \
     --gabolle.place.loader.dataset-version=$VER

# ② 탐색 갈래 표식 106곳
$RUN --gabolle.place.loader.explore-facets=/load/tourapi-busan.ndjson \
     --gabolle.place.loader.dataset-version=$VER

# ③ 영업시간 268곳
$RUN --gabolle.place.loader.opening-hours=/load/opening-hours.ndjson \
     --gabolle.place.loader.dataset-version=$VER

# ④ 접근성 130곳
$RUN --gabolle.place.loader.barrier-free=/load/tourapi-barrier-free-busan.ndjson \
     --gabolle.place.loader.dataset-version=$VER

# ⑤ 축제 회차 (S15P21E201-863)
# 수집분 버전을 안 준다 — 이 적재기만 그 인자를 요구하지 않는다. 만드는 것이 장소에 붙는
# 표식이 아니라 회차 그 자체이고, 그 표에는 어느 수집분에서 왔는지 적는 칸이 없다.
$RUN --gabolle.place.loader.festival=/load/tourapi-festival-busan.ndjson

# ⑥ 경사 (S15P21E201-1047) — 관광공사만. 상가는 마이그레이션으로 이미 들어가 있다
$RUN --gabolle.place.loader.place-slope=/load/place-slope.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-16-slope-r200

# ⑦ 조용함 (S15P21E201-1167) — 파일 둘을 같은 속성으로 한 번씩
$RUN --gabolle.place.loader.place-quietness=/load/place-quietness.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-quietness-r200
$RUN --gabolle.place.loader.place-quietness=/load/place-quietness-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-quietness-r200

# ⑧ 로컬성 (S15P21E201-1167) — 같은 방식
$RUN --gabolle.place.loader.place-locality=/load/place-locality.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-locality-r300
$RUN --gabolle.place.loader.place-locality=/load/place-locality-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-locality-r300
```

> 🔴 **⑥⑦⑧ 은 `--gabolle.place.loader.dataset-version` 을 빼면 컨테이너가 죽는다.**
> 조용히 건너뛰는 것이 아니라 `IllegalStateException` 으로 기동에서 멈춘다. 어느 산출물로
> 만든 값인지 안 적으면 그 값으로 만든 추천을 나중에 되짚을 수 없어서, **일부러** 그렇게
> 돼 있다. ⑤ 축제만 이 인자를 안 받는다.

> 🔴 **⑦⑧ 은 값의 눈금을 적재기가 바꾼다.** 산출물은 사람이 읽기 좋게 0~100 인데
> (`quietnessScore: 90`) 채점기는 이 축들을 **0~1** 로 안다. 적재기가 100 으로 나눠 넣고,
> 조용함은 `1 - noiseP90` 과 같은지 **검산까지 한다.** 나중에 산출물이 0~1 로 바뀌면 그
> 검산이 **빨갛게 터진다** — 두 번 나눈 값(0.009)은 범위 검사를 통과해 버리기 때문이다.

적재를 마치면 애플리케이션이 계속 떠 있으므로 로그에 마침 줄이 보이면 `Ctrl+C` 로
끊는다. `--rm` 이라 컨테이너는 남지 않는다.

## 4. 로그에서 확인할 숫자

| 적재 | 기대하는 줄 |
|---|---|
| ① | `새로 넣은 장소 328곳` · `갈래별 — {CITY=47, CULTURE_TEMPLE=162, NATURE_WALK=23, SEA_BEACH=2}` |
| ② | `새로 붙인 106` · `갈래별 — {ACTIVITY=22, FESTIVAL=14, NATURE=22, NIGHT_VIEW=5, TRADITIONAL_MARKET=33, WALK=10}` |
| ③ | `새로 넣은 268` · `붙일 장소가 없어 넘긴 328` |
| ④ | `새로 붙인 111` · `코드별 {STROLLER=13, WHEELCHAIR=117}` · `붙일 장소가 없어 넘긴 18` |

③ 의 328 은 정상이다 — 음식점을 일부러 안 넣었고 그 줄들이 붙을 자리가 없다. 그보다
크면 ①을 안 돌린 것이다.

④ 의 18 이 정상이다. 무장애 179곳은 전부 관광공사 656곳 안에 있지만, 656 중 음식
328곳은 ①이 일부러 안 넣는다. 무장애 179곳 가운데 32곳이 그 음식점이고, 그중
접근성을 실제로 말한 18곳이 붙을 자리를 못 찾는다 — 빠진 것이 아니라 대상이 아닌
것이다. 이 값이 크게 늘면 그때는 ①을 안 돌린 것이다.

이 자리는 2026-09-13 까지 `넘긴 0` 으로 적혀 있었고 "0 이 아니면 잘못된 것" 이라고
까지 못 박혀 있었다. 실제로 돌려 보니 18 이었다 — 문서가 음식 제외를 안 셈에 넣었다.

### 🔴 ⑥⑦⑧ 은 로그 대신 **API 로 센다** (2026-09-17)

유도값 적재는 **줄 수와 들어간 수가 다르다.** 줄이 있다고 그 장소가 운영 DB 에 있는 것이
아니기 때문이다. 경사가 그것을 크게 겪었다 — **2,213줄 중 327곳만** 붙었다. 나머지
1,886줄은 그 장소 자체가 DB 에 없어서 못 붙었다(`V20260916230000` 주석). **열쇠가 틀린
것이 아니라 자물쇠가 없는 것이다.**

그래서 로그의 「새로 붙인 N」만 보지 말고 **실제로 몇 곳이 값을 갖게 됐는지**를 센다.

```bash
curl -s https://j15e201.p.ssafy.io/api/v1/places/facets \
  -H "X-Session-Token: <익명 세션 토큰>"
```

| 축 | 장소 쪽 값 | 적재 뒤 기대 |
|---|---|---|
| `SLOPE_PREFERENCE` | `SLOPE_PERCENT` | **2,682** (2026-09-17 실측) |
| `QUIETNESS` | `QUIETNESS_SCORE` | 확인 못 함 — 아직 안 돌렸다 |
| `LOCALITY` | `LOCALITY_SCORE` | 확인 못 함 — 아직 안 돌렸다 |

> 🔴 **이 확인 방법은 2026-09-17 이전에는 쓸 수 없었다.** 갈래 목록이 **점수형 축을 전부
> 0 으로 냈기 때문이다**(`S15P21E201-1149`). `feature_key` 가 없는 행을 합계에서 빼는
> 결함이었고, 그래서 **「자료가 없다」와 「세는 코드가 틀렸다」를 구분할 수 없었다.**
> 경사 2,682 는 그것을 고친 뒤 처음 보인 숫자다.

> ⚠️ **숫자가 줄 수보다 작다고 실패가 아니다.** 붙을 장소가 DB 에 있느냐의 문제다.
> 다만 **0 이면 실패다** — 그때는 (가) 속성 이름을 틀렸거나 (나) 파일을 컨테이너에 안
> 넣었거나 (다) 열쇠 모양이 안 맞는 것이다.

## 5. 뒷정리

```bash
rm -f /tmp/load.env
exit
```

## 6. 화면 확인

`https://j15e201.p.ssafy.io` 홈 → "로컬 8종 둘러보기" → 여덟 줄 중 **여섯이 열린다.**
야시장·기념품샵은 계속 0건이고 그것이 맞다 — 원천에 신호가 없어 일부러 비웠다
(`S15P21E201-474` 코멘트).

## 되돌리기

전부 출처와 수집분이 찍힌다.

```sql
DELETE FROM place_feature WHERE source_type = 'TOURAPI' AND source_version = 'tourapi-busan-20260911';
DELETE FROM place         WHERE source_type = 'TOURAPI' AND dataset_version = 'tourapi-busan-20260911';
```

상가정보 2,355곳은 `source_type` 이 달라 안 걸린다.

### 유도값 되돌리기 (2026-09-17)

조용함·로컬성은 **자기 이름의 출처**로 들어가서 한 줄로 지워진다.

```sql
DELETE FROM place_feature WHERE source_type = 'DERIVED_QUIETNESS';
DELETE FROM place_feature WHERE source_type = 'DERIVED_LOCALITY';
```

🔴 **경사는 이렇게 못 지운다.** 경사 적재기는 `source_type` 을 `TOURAPI` 로 넣어서, 관광공사
수집본에서 실제로 온 표식들과 **한 덩어리**가 된다. 경사만 지우려면 종류까지 함께 걸어야
한다.

```sql
DELETE FROM place_feature WHERE feature_type = 'SLOPE_PERCENT' AND source_type = 'TOURAPI';
-- 상가 경사는 마이그레이션이 넣었다 — source_version 으로 가른다
DELETE FROM place_feature WHERE source_version = '2026-09-16-slope-r200-sbiz';
```

> 조용함·로컬성이 `TOURAPI` 를 안 쓰는 이유가 이것이다. `source_type` 은 **「값이 어디서
> 왔나」**를 적는 칸인데 이 값들은 관광공사가 아니라 **도로 등급에서 유도한 우리 계산**이고,
> 원천 이름을 빌려 쓰면 되돌릴 때 남의 자료까지 걸린다.

## 적재 전에 정하면 싼 것

접근성 판정(`S15P21E201-862`)에 열린 질문이 둘 있다 — 휠체어를 문구 하나로 좁힌 것과
유모차 칸의 뜻. 바꾸려면 적재 전이 가장 싸다. 적재 뒤에 바꾸면 위 `DELETE` 로 지우고
다시 돌려야 한다.
