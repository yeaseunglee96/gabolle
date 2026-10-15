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
| `bigData/data/staged/place-shade.ndjson` | 363 | 그늘 (관광공사) |
| `bigData/data/staged/place-shade-sbiz.ndjson` | 1,749 | 그늘 (상가) |

```bash
for f in place-slope place-quietness place-quietness-sbiz place-locality place-locality-sbiz \
         place-shade place-shade-sbiz; do
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

# 🔴 적재기는 일을 마쳐도 앱이 계속 떠 있다. 그래서 **마침 줄이 보이면 스스로 내린다.**
#    아래 아홉 단계는 파일이 둘인 것까지 세면 열두 번 도는데, 사람이 열두 번 Ctrl+C 를
#    기억해야 하는 절차는 반드시 한 번 빠진다 (S15P21E201-1214).
run() {
  cid=$(docker run -d --network "$NET" --env-file /tmp/load.env \
    -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \
    -v /home/ubuntu/load:/load "$IMG" "$@") || return 1
  # 로그를 따라가다 마침(또는 기동 실패) 줄에서 끊는다. 10분은 넘을 일이 없다.
  timeout 600 docker logs -f "$cid" 2>&1 | sed -u '/적재를 마쳤다\|APPLICATION FAILED TO START/q'
  docker rm -f "$cid" > /dev/null
  echo "  (컨테이너 내림: ${cid:0:12})"
}
RUN=run

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

# ⑨ 그늘 (S15P21E201-1184) — 같은 방식
$RUN --gabolle.place.loader.place-shade=/load/place-shade.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-shade-r500
$RUN --gabolle.place.loader.place-shade=/load/place-shade-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-shade-r500
```

> 🔴 **⑨ 는 2026-09-17 까지 이 문서에 없었다.** 그늘은 파일 목록에도, 4절 기대값
> (`SHADE_SCORE` 2,112)에도, 되돌리는 `DELETE` 에도 있었는데 **넣는 명령만 빠져 있었다.**
> 축이 셋일 때 쓴 3절에 그늘 적재기가 나중에 들어왔기 때문이다.
> 그대로 따르면 `SHADE_SCORE` 가 **0** 으로 나오고, 아래 4절이 *「0 이면 실패다」* 라고
> 적어 둔 탓에 **없는 버그를 찾으러 가게 된다.** 축을 더할 때는 세 자리를 함께 고친다 —
> **1절 파일 목록 · 3절 명령 · 4절 기대값.**
>
> 반경 `r500` 은 지어낸 값이 아니다. 산출물 옆의 실행 기록
> (`bigData/data/staged/_place-shade-run/_run-process-place-shade.json`)에 `"radiusM": 500`
> 으로 적혀 있다. 조용함 `r200`·로컬성 `r300` 도 같은 자리에서 온 것이다.

> 🔴 **⑥⑦⑧⑨ 는 `--gabolle.place.loader.dataset-version` 을 빼면 컨테이너가 죽는다.**
> 조용히 건너뛰는 것이 아니라 `IllegalStateException` 으로 기동에서 멈춘다. 어느 산출물로
> 만든 값인지 안 적으면 그 값으로 만든 추천을 나중에 되짚을 수 없어서, **일부러** 그렇게
> 돼 있다. ⑤ 축제만 이 인자를 안 받는다.

> 🔴 **⑦⑧⑨ 는 값의 눈금을 적재기가 바꾼다.** 산출물은 사람이 읽기 좋게 0~100 인데
> (`quietnessScore: 90`) 채점기는 이 축들을 **0~1** 로 안다. 적재기가 100 으로 나눠 넣고,
> 조용함은 `1 - noiseP90` 과 같은지 **검산까지 한다.** 🔴 **그늘에는 그 짝이 없다** —
> 대조할 다른 산출값이 없어 범위 검사(0~100)만이 눈금을 지키는 유일한 장치다.
> 나중에 산출물이 0~1 로 바뀌면 그 검산이 **빨갛게 터진다** — 두 번 나눈 값(0.009)은 범위 검사를 통과해 버리기 때문이다.

> 🔴 **2026-09-18 정정 — 여기 「마침 줄이 보이면 `Ctrl+C` 로 끊는다」고 적혀 있었다.**
>
> 적재기는 일을 마쳐도 앱이 계속 떠 있는 것이 맞다. 그런데 **그 뒷정리를 사람에게
> 맡기고 있었다.** 위 `run()` 이 이제 마침 줄에서 스스로 끊고 컨테이너를 내린다 —
> **아무것도 기억하지 않아도 된다.**
>
> **왜 고쳤나.** 같은 날 새벽 적재 리허설에서 컨테이너 둘이 할 일을 끝내고도
> **36분·4분씩 떠 있었다.** 그 사이 기계의 메모리가 모자라 **뒤에서 돌던 작업 둘이
> 정리됐고**, 그중 하나가 리허설의 마지막 단계였다. 자료가 틀어지지는 않았지만
> **그 단계를 못 돌렸다.** 자바 하나가 수백 MB 다. 열두 번 돌리는 절차에서 몇 번만
> 놓쳐도 기계가 넘어간다.
>
> ⚠️ **판정은 여전히 로그 줄이다.** 적재기는 일을 마쳐도 스스로 안 끝나므로
> **종료 코드로 성공/실패를 가를 수 없다.** 위 4절의 숫자를 본다.

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

| 축 | 장소 쪽 값 | 관광공사 | 상가 | **합계 기대** |
|---|---|---|---|---|
| `SLOPE_PREFERENCE` | `SLOPE_PERCENT` | 327 | 2,355 | **2,682** |
| `QUIETNESS` | `QUIETNESS_SCORE` | 326 | 2,355 | **2,681** |
| `LOCALITY` | `LOCALITY_SCORE` | 239 | 2,230 | **2,469** |
| `SHADE_PREFERENCE` | `SHADE_SCORE` | 187 | 1,749 | **1,936** |

> 🔴 **이 숫자는 줄 수가 아니다 (2026-09-18, S15P21E201-1192).** 전에는 이 자리에 산출물
> 줄 수(3,009 · 2,728 · 2,112)가 적혀 있었다. 줄 하나가 곧 장소 하나가 아니다 — **그
> 장소가 DB 에 있어야** 값이 붙고, 관광공사 656곳 중 음식 328곳은 ①이 일부러 안 넣으므로
> **관광공사 열쇠의 상한이 328곳**이다. 줄 수로 보면 조용함이 2,681 로 나왔을 때 **328곳이
> 빈 것처럼 읽힌다.** 정상인데 없는 버그를 찾으러 가게 된다 — ⑨가 빠졌을 때와 같은 모양이다.
>
> **이 계산은 정답이 붙은 표본으로 검증했다.** 경사는 실측 2,682 가 이미 있었고, 같은
> 계산이 2,682 를 그대로 냈다. 나머지 셋은 그 검증된 계산의 결과다.
>
> **숫자를 여기 적어 두면 산출물이 바뀔 때 낡는다.** 그래서 계산을 함께 남겼다 —
> 산출물이 바뀌면 **다시 돌려서** 이 표를 갱신한다. 검증 표본이 안 맞으면 스크립트가
> 숫자를 안 내놓고 **종료 코드 1 로 멈춘다.**
>
> ```bash
> cd bigData && node process/expected-feature-rows.mjs    # bigData/dev 브랜치
> ```

### 🔴 0 이 아니어도 실패일 수 있다 — 어느 쪽이 0 인지를 본다

합계 하나만 보면 **둘 중 하나만 들어간 것을 못 잡는다.** ⑦⑧⑨ 는 파일을 둘씩 돌리므로
한쪽을 빠뜨리기 쉽고, 그때도 합계는 0 이 아니다.

| 나온 값 | 무슨 뜻인가 |
|---|---|
| **0** | (가) 속성 이름을 틀렸거나 (나) 파일을 컨테이너에 안 넣었거나 (다) 열쇠 모양이 안 맞는다 |
| **상가 몫만큼** (2,355 등) | **관광공사 파일을 안 돌렸다.** 또는 ①을 안 돌려 장소가 없다 |
| **관광공사 몫만큼** (300 안팎) | **상가 파일을 안 돌렸다.** ⑦⑧⑨ 는 두 줄이다 |
| 위 표보다 **조금 작다** | 정상 범위. 붙을 장소가 DB 에 있느냐의 문제다 |
| 위 표보다 **크다** | 산출물이 바뀌었다. 계산을 다시 돌려 표를 갱신한다 |

### 🔴 눈금은 적재 **전에** 본다 — 뒤에는 볼 방법이 없다

⑦⑧⑨ 는 적재기가 0~100 을 100 으로 나눠 넣는다. **두 번 나뉜 값(0.009)도 범위 검사를
통과하므로** 눈금이 어긋나도 오류가 안 난다. 그런데 위 갈래 목록은 **개수만** 돌려주고
값은 안 돌려줘서, **적재 뒤에는 이것을 확인할 수 없다.**

그래서 넣기 전에 산출물 쪽에서 본다. 위 스크립트가 축마다 값 범위를 함께 찍는다.

```
경사   0.0~44.8    조용함 0.0~95.0    로컬 0.0~100.0    그늘 0.0~99.9
```

🔴 **최댓값이 1.0 이하로 나오면 그 산출물은 이미 0~1 이다.** 그대로 넣으면 적재기가 한 번
더 나눠 값이 100분의 1 이 되고, **아무 오류 없이** 그 축이 순위에서 사실상 사라진다.

> 🔴 **이 확인 방법은 2026-09-17 이전에는 쓸 수 없었다.** 갈래 목록이 **점수형 축을 전부
> 0 으로 냈기 때문이다**(`S15P21E201-1149`). `feature_key` 가 없는 행을 합계에서 빼는
> 결함이었고, 그래서 **「자료가 없다」와 「세는 코드가 틀렸다」를 구분할 수 없었다.**
> 경사 2,682 는 그것을 고친 뒤 처음 보인 숫자다.

## 5. 뒷정리

```bash
rm -f /tmp/load.env
exit
```

## 6. 화면 확인

`https://j15e201.p.ssafy.io` 홈 → "로컬 8종 둘러보기" → 여덟 줄 중 **여섯이 열린다.**
야시장·기념품샵은 계속 0건이고 그것이 맞다 — 원천에 신호가 없어 일부러 비웠다
(`S15P21E201-474` 코멘트).

## 7. 가격+narrative 조사 (S15P21E201-1414, 2026-09-22 추가 — 진행 중)

agy 헤드리스로 부산 음식점을 웹에서 조사해 가격과 "왜 가는지"를 모으는 작업
(`bigData/research/price-queue.mjs`)이다. **위 절차와 다른 점 하나** — 대상 장소는
전부 SBIZ 상가정보 출처라 이미 `place` 표에 있다. ①(장소 먼저)을 다시 안 밟아도 된다.

🔴 **한 번에 다 넣는 파일이 아니다.** 조사가 계속 도는 중이라 `place-research-combined.ndjson`
이 돌 때마다 자란다. 적재기는 이미 있는 사실을 건드리지 않으므로(`ON CONFLICT DO NOTHING`),
**늘어난 뒤쪽만 새로 들어간다** — 다시 돌려도 앞서 넣은 것이 두 배가 되지 않는다.

```bash
git fetch origin bigData/dev
git show origin/bigData/dev:bigData/data/staged/place-research-combined.ndjson \
  > /tmp/load/price-narrative.ndjson
scp -i $PEM /tmp/load/price-narrative.ndjson $HOST:/home/ubuntu/load/

ssh -i $PEM $HOST
docker run -d --network local-route-personalization_data_net --env-file /tmp/load.env \
  -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \
  -v /home/ubuntu/load:/load local-route-backend:candidate \
  --gabolle.place.loader.price-narrative=/load/price-narrative.ndjson \
  --gabolle.place.loader.dataset-version=price-narrative-<오늘날짜>
```

넣는 것 둘 — `MENU_PRICE_WON`(가격, 못 찾은 곳은 0원이 아니라 사실 자체를 안 낸다)과
`WHY_VISIT`("왜 가는지" 이유 목록 + 근거 주소). 영업시간·혼잡도·현지인 비중·메뉴
다양성은 이번엔 안 넣는다 — 아직 화면이 안 읽는 값이다(`PlaceFeatureNdjsonReader
.readPriceNarrative` 주석 참고).

> 🔴 **마이그레이션이 하나 필요하다 — 2026-09-22 (S15P21E201-1478).**
> 1465 커밋은 *"place_feature 가 feature_type 에 CHECK 를 안 걸어서 새 종류를 자유롭게
> 추가할 수 있다"* 고 적었는데 **사실이 아니었다.** `ck_place_feature_type` 이 걸려 있어
> `MENU_PRICE_WON` · `WHY_VISIT` 이 막혔고, 돌리면 **한 줄도 안 들어가고 통째로
> 되돌려졌다.** `V20260922080000__place_feature_price_narrative_types.sql` 이 그것을 푼다.
>
> **그 마이그레이션이 들어간 이미지로 돌려야 한다.** 적재 컨테이너는 앱을 통째로 띄우므로
> 기동할 때 마이그레이션도 함께 적용한다 — 낡은 이미지로 돌리면 제약이 옛것이라 또 막힌다.

### 🔴 적재 기록 — 언제 무엇을 얼마나 넣었나

**이 표는 낡지 않는다.** 「지금 몇 곳인가」가 아니라 **「그날 무엇을 넣었나」**를 적기
때문이다. 지우지 말고 **아래에 줄을 더한다.** 이게 없으면 다음 사람이 *"어디까지
넣었지"* 를 DB 를 뒤져 다시 알아내야 한다.

세는 법 — 적재기는 넣은 줄마다 `source_version` 을 찍는다.

```sql
SELECT source_version, feature_type, count(*)
FROM place_feature WHERE source_type = 'RESEARCH_PRICE_NARRATIVE'
GROUP BY 1, 2 ORDER BY 1, 2;
```

| 날짜 | `dataset-version` | 입력 줄 | `MENU_PRICE_WON` | `WHY_VISIT` | 결과 |
|---|---|---|---|---|---|
| 2026-09-22 07:21 | `price-narrative-20260922` | 522 | — | — | 🔴 **0행.** `ck_place_feature_type` 이 막았다 (S15P21E201-1478) |
| 2026-09-22 07:39 | `price-narrative-20260922` | 645 | **189** | **545** | 🟢 넣음 734 · 장소가 없어 못 넣음 **0** · 이미 있어 건너뜀 **0** · 1.7초 |

적재기가 마지막 줄에 스스로 세어 찍는다 — 그대로 옮기면 된다.

```
가격+narrative 조사 적재를 마쳤다 — 줄 645 · 값이 없어 뺌 100 · 쓸 수 있는 것 734
                                 · 넣음 734 · 장소가 없어 못 넣음 0 · 이미 있어 건너뜀 0 · 1720ms
```

> 🔴 **자료가 들어간 것과 화면에 보이는 것은 또 다른 일이다.** 2026-09-22 현재
> `MENU_PRICE_WON` 을 **읽는 코드가 없다** — 추천 응답을 만드는 자리에
> `null, // estimatedCostKrw — 비용 데이터가 없다` 라고 못 박혀 있다
> (`RecommendationResultQueryService`). 그 주석은 어제까지 맞았고 오늘부터 틀리다.
> 잇는 일은 **S15P21E201-1479** 다.

조사가 계속 도는 중이라 입력 줄은 돌릴 때마다 는다. 적재기는 `ON CONFLICT DO NOTHING`
이라 **다시 돌려도 앞의 것이 두 배가 되지 않고 늘어난 뒤쪽만** 들어간다.

## 8. 경사 다시 넣기 — 60m 안 되는 길은 「모름」인 판 (2026-09-29 추가)

60m 안 되는 길의 경사를 고도 잡음 그대로 쓰던 것을 고쳤다(bigData !1854 · !1857). 장소 경사는
**반경 200m 안 걷는 길 경사의 가운데 값(p50)**이라, 길 경사가 바뀌면 장소 값도 다시 내야 한다.
보행 그래프 파일(`geo/walk-graph.bin.gz`)은 jar 안에 들어 있어 **배포만 하면 바뀐다.** 장소
경사는 DB 에 있어 **손으로 한 번 넣어야 한다** — 이 절이 그것이다.

🔴 **순서가 있다.** !1852 → !1858 이 배포된 뒤에 한다. !1858 의 마이그레이션
(`V20260929120100__drop_p90_place_slope`)이 옛 p90 행을 지운다.

### 8-1. 운영 장소 목록 뽑기 (읽기만 한다)

```bash
ssh -i $PEM $HOST "docker exec local-route-personalization-postgres-1 \
  psql -U app_user -d app_db -At -F \$'\t' -c \"
    SELECT p.place_id, p.source_type, coalesce(p.category,''), p.curation_status,
           replace(replace(p.name_ko, E'\t', ' '), E'\n', ' '), p.lat, p.lng
      FROM gabolle.place p WHERE p.lat IS NOT NULL ORDER BY p.place_id\"" \
  > bigData/data/staged/prod-places.tsv
wc -l bigData/data/staged/prod-places.tsv   # 운영 장소 수와 같아야 한다
```

### 8-2. 장소 경사 내기 (내 PC)

`segment-slope.ndjson` · `road/walk.ndjson` 이 있는 곳(bigData !1857 을 돌린 PC)에서.

```bash
cd bigData && node process/place-slope-by-id.mjs   # 종료 코드 0 이어야 한다
```

옛 판(`git show origin/bigData/dev:bigData/data/staged/place-slope-by-id.ndjson`)과 줄 수·8.33% 이상 곳 수를
나란히 적어 둔다. 크게 다르면 넣지 말고 멈춘다.

### 8-3. 옛 값을 떠 두고 지운다

🔴 적재기는 **있는 행을 안 건드린다**(`ON CONFLICT DO NOTHING`). 2026-09-25 에 넣은 p50 행이 남아
있으면 새 값이 하나도 안 들어간다. 그래서 먼저 지운다 — 지우기 전에 뜬다.

```sql
\copy (SELECT * FROM gabolle.place_feature WHERE feature_type = 'SLOPE_PERCENT') TO '/tmp/slope-backup-20260929.csv' CSV HEADER

BEGIN;
DELETE FROM gabolle.place_feature
 WHERE feature_type = 'SLOPE_PERCENT'
   AND source_type = 'DERIVED_SLOPE'
   AND source_version = '2026-09-25-slope-p50-r200';
-- 지운 줄 수를 보고, 8-2 의 옛 판 줄 수와 비슷하면 COMMIT, 아니면 ROLLBACK
COMMIT;
```

### 8-4. 새 값 넣기

1·2 절 그대로 파일을 올리고 접속값을 빌린 뒤, 3 절의 `run` 으로:

```bash
$RUN --gabolle.place.loader.place-slope-by-id=/load/place-slope-by-id.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-29-slope-p50-r200-short60
```

### 8-5. 확인

- 4 절처럼 로그의 줄 수가 아니라 **API 로 센다** — `/api/v1/places/facets` 의 경사 축 곳 수가 8-3 에서
  지운 수와 비슷해야 한다.
- 휠체어를 「반드시」로 고른 여행을 하나 만들어 본다 — 추천이 「결과 없음」이 아니고, 걷는 구간이
  계단을 피하는지(`travelPieces` 에 `stairs: true` 가 없거나 적은지) 본다.

### 8-6. 되돌리기

```sql
DELETE FROM gabolle.place_feature
 WHERE feature_type = 'SLOPE_PERCENT' AND source_version = '2026-09-29-slope-p50-r200-short60';
\copy gabolle.place_feature FROM '/tmp/slope-backup-20260929.csv' CSV HEADER
```

## 9. 장소 상세 사실 — 공인 표식·메뉴·편의시설·입장료·가까운 곳·가기 좋은 때 (S15P21E201-1886, 2026-09-30 추가)

장소 상세 화면에 보여 줄 사실을 `place_feature` 에 넣는 적재기다 (`PlaceDetailExtrasLoader` +
`PlaceDetailExtrasLoaderRunner`). 새 갈래 여섯은 `V20260930210000__place_detail_extras.sql` 이
`ck_place_feature_type`(**`feature_type` 칸에 들어갈 수 있는 값의 허용 목록 — DB 가 목록 밖 값을 거절한다**)에
더했다. 🔴 2026-09-22 에 적재기만 머지되고 이 목록이 빠져 운영에서 행이 전부 거절된 적이 있다 — **배포(마이그레이션)가
먼저, 적재가 나중**이다.

**열쇠는 장소 번호(`place_id`, UUID) 그대로다.** 조사로 짝지은 장소에 OSM 장소가 섞여 있어, 상가·관광공사 번호로
찾는 다른 적재기로는 못 찾는다. 수집분 버전 인자(`dataset-version`)도 받지 않는다 — 줄마다 `sourceVersion` 을 든다.

🔴 **알레르기는 싣지 않는다.** `ck_place_feature_safety_never_estimated` 가 추정한 알레르기·식단·접근성 행을 일부러
막는다(틀리면 사람이 다친다). 재료는 `MENU_ITEMS` 안에 글로만 둔다.

### 9-1. 한 줄 모양 (NDJSON — 한 줄에 JSON 하나)

```json
{"placeId":"<uuid>","featureType":"MENU_ITEMS","value":{…},"evidenceStatus":"VERIFIED","sourceType":"RESEARCH_DETAIL","sourceId":null,"sourceVersion":"place-detail-202609","observedAt":"2026-09-30T12:00:00+09:00"}
```

- `evidenceStatus` 는 `VERIFIED` 또는 `ESTIMATED` 만. `sourceType`·`sourceVersion` 은 비면 안 된다. `sourceId`·`observedAt` 은 `null` 가능 (`observedAt` 은 시간대가 붙은 ISO-8601 시각)
- 🔴 **모르는 낱말이 오면 멈춘다.** 모르는 갈래·모르는 칸·모양이 틀린 값은 줄 번호와 함께 멈추고, 파일 전체를 먼저 검사하므로 **한 줄이라도 틀리면 한 행도 안 들어간다**

| `featureType` | `value` |
|---|---|
| `MENU_ITEMS` | `{"items":[{"nameKo":글,"nameEn":글\|null,"priceWon":0 이상 정수\|null,"ingredientsKo":글\|null,"ingredientsEn":글\|null,"signature":true\|false}]}` — 1~30개, `nameKo`·`signature` 필수 |
| `FOREIGN_MENU` | `{"available":true\|false}` |
| `AMENITIES` | `{"wifi":bool\|null,"parking":bool\|null,"restroom":bool\|null,"reservation":글\|null,"homepage":글\|null}` — 전부 비면 거절 (모르면 줄을 안 만든다) |
| `ADMISSION_FEE` | `{"raw":원문 글}` |
| `NEARBY_LANDMARK` | `{"name":글,"distanceM":0 이상 정수}` |
| `BEST_TIME` | `{"day":정수,"night":정수,"any":정수}` — 설문 응답 수, 셋 다 필수, 합이 0 이면 거절 |
| `RECOGNITION` | `{"badges":[{"kind":"MODEL_RESTAURANT"\|"TAXI_DRIVER_PICK","since":"YYYY-MM-DD"\|null,"menu":글\|null,"source":글}]}` — 1~5개, `kind`·`source` 필수. **공인 표식**(S15P21E201-1891, `V20260930220000__place_recognition.sql` 이 허용 목록에 더했다). `MODEL_RESTAURANT` = 구·군 지정 모범음식점(「부산광역시_구군 모범음식점 현황」), `TAXI_DRIVER_PICK` = 택시기사 추천 식당(「부산광역시 택슐랭 선정 식당(2025)」). 둘 다 공공데이터포털 자료로 이용허락범위(**어디까지 써도 되는지 정한 조건**)가 「제한 없음」이다. `source` 는 화면에 작게 그대로 적히는 출처 글이다. 모르는 `kind` 는 멈춘다. 한 장소에 한 줄, 표식은 그 안에 다 담는다 |
| `OPENING_HOURS` | `OpeningHoursReader` 가 쓰는 모양 그대로: `{"status":"PARSED"\|"ALWAYS_OPEN","byDay":{…},"seasonal":…,"closedDays":…,"notes":…,"raw":…}` — `status` 필수, 나머지는 있으면 옮긴다. `byDay` 는 객체 |
| `CHECK_IN_OUT` | `{"status":"LODGING","checkIn":글\|null,"checkOut":글\|null,"notes":…,"raw":…}` — 체크인·체크아웃 둘 다 비면 거절 |

### 9-2. 돌리기

3 절의 `run` 함수를 그대로 쓴다. 넣기만 한다 — 같은 장소에 같은 갈래가 이미 있으면(**출처가 달라도**) 건너뛴다.
그래서 두 번 돌려도 행이 늘지 않는다.

```bash
$RUN --gabolle.place.loader.place-detail-extras=/load/place-detail-extras.ndjson
```

마침 줄: `장소 상세 사실 적재를 마쳤다 — 읽은 줄 N · 넣음 A · 이미 있어 건너뜀 B · 장소가 없어 못 넣음 C`.
C 가 크면 운영이 아닌 DB 의 장소 번호로 산출물을 만든 것이다.

### 9-3. 확인

```sql
SELECT feature_type, evidence_status, count(*)
  FROM place_feature
 WHERE source_version = 'place-detail-202609'
 GROUP BY 1, 2 ORDER BY 1, 2;
```

상세 API(`GET /api/v1/places/{placeId}`)의 `features` 목록에 그 갈래가 실리는지 본다 — 새 칸은 만들지 않았다.

### 9-4. 되돌리기

줄마다 `sourceVersion` 이 찍혀 들어가므로 그 값으로 지운다. 🔴 `OPENING_HOURS`·`CHECK_IN_OUT` 도 같은 판으로 넣었다면
같이 지워진다 — 그것만 남기려면 `feature_type` 을 함께 건다.

```sql
DELETE FROM place_feature WHERE source_version = 'place-detail-202609';
```

## 10. 설문 추천 장소 — 레드테이블 식당 + 관광공사 명소 (S15P21E201-1897, 2026-09-30 추가)

부산 사람들이 설문에서 추천했는데 DB 에 없던 장소를 넣는다. 파일이 둘이고 적재기도 둘이다.

| 파일 | 줄 | 적재기 |
|---|---|---|
| `survey-redtable-places.ndjson` | 48 | **`RedtablePlaceLoader`**(새로 만듦 — 부산 레드테이블 식당을 `source_type='REDTABLE'` 로 넣는다) |
| `survey-tourapi-places.ndjson` | 25 | 이미 있는 `TourApiPlaceLoader`(3절 ①과 같은 것) |

두 파일은 git 에 없다 — 만든 사람 PC 의 `gabolle-archive/` 에 있고, 1절처럼 `/home/ubuntu/load/` 로 올린다.
같은 폴더의 `survey-places-manifest.csv` 가 한 곳씩의 설문 이름·넣을 이름·출처·번호·사진 여부를 적은 목록이다.

### 10-1. 레드테이블 한 줄 모양

```json
{"rstrId":"39929","name":"밀양가산돼지국밥","lat":35.1405587,"lng":129.0625474,"roadAddress":"부산광역시 동구 자성로133번길 45","jibunAddress":"부산광역시 동구 범일동 830-173","businessType":"한식","licenseType":"일반음식점","tel":"051-632-0409","intro":"…","imageUrl":null,"surveyRecommenders":1}
```

- `rstrId`(레드테이블 식당 번호, 숫자 글자)·`name`·`lat`·`lng`(숫자, 부산 상자 안) 필수, 주소는 도로명·지번 중 하나는 있어야 한다. 주소는 도로명이 먼저다
- 🔴 **모르는 칸·같은 번호 두 줄·틀린 모양이 하나라도 있으면 줄 번호와 함께 멈추고 한 행도 안 넣는다**
- `tel`·`intro`·`imageUrl`·`surveyRecommenders` 는 읽기만 하고 DB 에 안 넣는다
- 🔴 **사진은 넣지 않는다.** 레드테이블 사진을 다시 써도 되는지·출처를 어떻게 적는지가 확인되지 않았다. 주소는 `imageUrl` 에 남겨 두었다
- 장소 id = `UUID.nameUUIDFromBytes("gabolle:place:REDTABLE:" + rstrId)` — 두 번 돌려도 행이 안 는다. 이름이 같은 곳이 150m 안에 이미 있으면 `SamePlaceGuard` 가 막고 마침 줄 뒤에 보고한다

**갈래 옮김표** (`RedtablePlaceLoader.categoryOf`)

| 레드테이블 칸 | 갈래 |
|---|---|
| 업태가 커피숍·다방·전통찻집·떡카페·아이스크림·제과점영업 | `CAFE_HEALING` |
| 업태가 그 밖의 값(한식·일식·분식·호프/통닭·회집·경양식 …) | `FOOD` — 술집도 여기(앱에 술집 갈래가 없다) |
| 업태가 비었고 허가가 휴게음식점·제과점영업 | `CAFE_HEALING` (휴게음식점은 술을 못 파는 허가) |
| 업태가 비었고 이름에 커피·카페·다실·제과·베이커리 | `CAFE_HEALING` |
| 그 밖 | `FOOD` |

업태가 있으면 이름보다 업태를 믿는다. 같은 낱말로 `CATEGORY_TAG` 표식 하나를 함께 넣는다.

### 10-2. 관광공사 줄 — 🔴 새 분류만 오는 항목

관광공사 줄은 3절 ① 파일과 같은 모양(`stage":"list"`, `raw` 는 문자열)이다. `areaBasedList2`(`lDongRegnCd=26`) 응답에서
해당 항목만 원문 그대로 한 줄에 하나씩 옮겼다.

2026-09 에 받은 항목 대부분은 옛 분류 `cat1`·`cat3` 가 **빈 글자**이고 새 분류(`lclsSystm1`)만 있다. 그대로면 갈래가 비어
추천에 안 나온다. 그래서 `TourApiPlaceReader` 가 **`cat1` 이 빌 때만** 새 대분류를 옛 대분류로 옮긴다 — NA→A01 자연,
HS·VE·EX→A02 인문, LS→A03 레포츠(갈래 없음), SH→A04 쇼핑, FD→A05 음식(버린다), AC→B02 숙박. `cat1` 이 있는 기존 수집본은
영향이 없다.

관광공사 **음식점**(유형 39) 넷(딤타오·부우사안·해목·세븐아일랜드)은 파일에 넣지 않았다 — 이 적재기는 상가정보와 같은 가게가
두 행이 될까 봐 음식을 받지 않는다.

### 10-3. 돌리기

3 절의 `run` 함수를 그대로 쓴다.

```bash
$RUN --gabolle.place.loader.redtable-places=/load/survey-redtable-places.ndjson      --gabolle.place.loader.dataset-version=survey-redtable-20260930

$RUN --gabolle.place.loader.tourapi=/load/survey-tourapi-places.ndjson      --gabolle.place.loader.dataset-version=survey-tourapi-20260930
```

마침 줄: `레드테이블 장소 적재를 마쳤다 — 읽은 곳 N · 새로 넣은 장소 A곳 · 이미 있어 건너뛴 B곳 · 갈래별 {…}`.
두 번째 실행은 A 가 0 이어야 한다.

### 10-4. 확인

```sql
SELECT source_type, dataset_version, category, count(*), count(photo_url) AS photos
  FROM place
 WHERE dataset_version IN ('survey-redtable-20260930', 'survey-tourapi-20260930')
 GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
```

레드테이블은 `photos` 가 0 이어야 한다.

### 10-5. 되돌리기

```sql
DELETE FROM place_feature WHERE source_type = 'REDTABLE' AND source_version = 'survey-redtable-20260930';
DELETE FROM place         WHERE source_type = 'REDTABLE' AND dataset_version = 'survey-redtable-20260930';
DELETE FROM place_feature WHERE source_type = 'TOURAPI'  AND source_version = 'survey-tourapi-20260930';
DELETE FROM place         WHERE source_type = 'TOURAPI'  AND dataset_version = 'survey-tourapi-20260930';
```

🔴 그 사이 다른 적재(영업시간·사진 등)가 이 장소들에 값을 붙였으면 `fk_place_feature_place`(장소가 지워지면 안 되는 표식이
남아 있다는 외래키)에 막힌다. 그때는 `place_id IN (SELECT place_id FROM place WHERE dataset_version = …)` 로 표식을 먼저 지운다.

## 11. 공공 인증 식당 — 로컬 탐색 「맛집」 갈래 (S15P21E201-2051, 2026-10-13 추가)

앱 「로컬 탐색」에 「맛집」 갈래를 더하는 자료다. 맛집은 **공공기관이 인증한 식당만**이다 — 부산시 맛집정보 · 구군 모범음식점 ·
택슐랭(택시기사가 추천한 식당). 사용자는 구 → 동 두 단계로 고른 뒤 그 동의 맛집 목록을 본다. 적재기는
`CertifiedRestaurantLoaderRunner`(`CertifiedRestaurantLoader` + `CertifiedRestaurantReader`)이고, 이미 있는 장소에는 **표식만 달고**
없는 장소는 **새로 만든다.**

🔴 **배포(마이그레이션)가 먼저, 적재가 나중이다.** `V20261013200000__certified_restaurant_features.sql` 이
`ck_place_feature_type`(**`feature_type` 칸에 들어갈 수 있는 값의 허용 목록 — DB 가 목록 밖 값을 거절한다**)에 새 종류 둘
(`ADMIN_AREA_TAG`·`CERTIFICATION_TAG`)을 더한다. 이 마이그레이션이 운영에 안 들어간 채 돌리면 첫 장소 덩어리에서 행이 거절되어
**한 곳도 안 들어간다.** 배포가 끝난 뒤(`back/dev` 배포 잡이 초록) 돌린다.

### 11-1. 입력 파일과 한 줄 모양

파일은 git 에 없다 — 만든 사람 PC 의 `gabolle-archive/certified-restaurants-20261013.ndjson`(**한 줄에 JSON 하나인 글 파일**, 666줄)이다.

```json
{"sourceId":"BF70","name":"만드리곤드레밥","lat":35.177387,"lng":128.95245,"address":"강서구 공항앞길 85번길 13","tel":"051-941-3669","gu":"강서구","dong":"대저2동","sources":["부산시 맛집정보"],"menu":"돌솥곤드레정식, 단호박오리훈제","existingPlaceId":null,"photoUrl":"https://www.visitbusan.net/uploadImgs/files/cntnts/20191209162810545_ttiel","photoSource":"출처 : 부산광역시 부산맛집정보"}
```

- `gu`·`dong` 은 좌표를 VWorld(국토교통부 지도 서비스)로 **역지오코딩**(좌표로 행정 주소를 알아내는 것)해서 얻은 법정동이다. 주소 글의 동이 아니다
- `sources` 는 `부산시 맛집정보` · `모범음식점` · `택슐랭` 중 하나 이상. 모르는 이름이 오면 멈춘다
- `existingPlaceId` 가 있으면 우리 DB 에 이미 있는 장소다 — **장소는 새로 안 만들고 표식만 단다.** 없으면 새로 만든다
- `photoUrl`·`photoSource` 는 짝이다. 출처 없는 사진은 거절하고, 출처 문구는 그대로 `place.photo_source` 에 간다
- `tel`·`menu` 는 읽기만 하고 DB 에 안 넣는다(`place` 에 전화번호 칸이 없고, 메뉴는 가격이 글에 섞여 있다)
- 🔴 **모르는 칸·틀린 모양이 하나라도 있으면 줄 번호와 함께 멈추고 한 행도 안 넣는다.** 단 아래 셋은 멈추지 않고 로그에 한 줄씩 남기고 건너뛴다

| 건너뛰는 줄 | 이유 | 파일에서 |
|---|---|---|
| `gu` 가 `?` | 좌표를 역지오코딩하지 못한 줄이라 구를 모른다(「보느파티쓰 리」 — 경도 칸에 위도가 한 번 더 적혀 있다). 좌표 검사보다 **먼저** 건너뛴다 | 1줄 |
| 같은 `sourceId` 가 여러 줄 | 번호 하나에 장소 하나다. 그런데 이 파일은 같은 번호가 **서로 다른 좌표·구·동**으로 겹쳤다(같은 이름의 다른 지점으로 짝지어진 것). 그리고 **먼저 나온 줄이 일곱 번호 모두 틀렸다.** 그래서 첫 줄이 아니라 **주소 글과 가장 잘 맞는 줄**을 남긴다(주소의 구와 같음 +4 · 주소에 동 이름이 있음 +1 · 번호 끝 `@위도` 와 같음 +1, 같으면 먼저 나온 줄) | 7개 번호, 8줄이 남는 줄 |
| 주소의 구와 좌표로 얻은 구가 다름 | 주소가 「연제구 …」인데 좌표의 구가 금정구다(큐제) · 주소가 「남구 …」인데 해운대구다(초원복국). 같은 이름의 **다른 지점**에 좌표가 붙은 것이라, 그대로 넣으면 핀이 엉뚱한 곳에 찍히고 `existingPlaceId` 는 **다른 지점의 장소**를 가리킨다. 주소 **첫머리**에 구가 적혀 있을 때만 판단한다 | 2줄 |

주소에 구가 없는 줄(「기장읍 …」「조방로 16번길 …」 같은 것, 이 파일에서 40줄)은 **대조할 수 없어서 그대로 믿는다.** 수집 쪽에 확인을 부탁할 수 있다.

### 11-2. 무엇을 넣나

| 대상 | 내용 |
|---|---|
| 새 `place` | `category='FOOD'`, `source_type='BUSAN_CERTIFIED_FOOD'`, `source_id=sourceId`, `dataset_version=<수집분>`, 공공 사진이 있으면 `photo_url`·`photo_source`(`http://tong.visitkorea.or.kr` 은 `https` 로 바꾼다). 장소 id 는 `UUID.nameUUIDFromBytes("gabolle:place:BUSAN_CERTIFIED_FOOD:" + sourceId)` 라 두 번 돌려도 같다 |
| `CATEGORY_TAG` = `FOOD` | **새 장소에만.** 추정(`ESTIMATED`) |
| `INTEREST_TAG` = `LOCAL_RESTAURANT` | 맛집 갈래 표식. 확인됨(`VERIFIED`). 탐색 갈래 서랍이라 `/api/v1/places/facets` 가 건수를 센다 |
| `ADMIN_AREA_TAG` = `구/동` | 어느 구·동인가. 열쇠가 `해운대구/우동` 꼴이고 값은 `{"gu":…,"dong":…}`. 추정. **한 장소에 하나** — 이미 있으면 다른 줄이 다른 곳을 말해도 먼저 것을 두고 로그에 센다 |
| `CERTIFICATION_TAG` = `BUSAN_FOOD_INFO` · `MODEL_RESTAURANT` · `TAXI_DRIVER_PICK` | 인증한 목록마다 한 줄. 값은 `{"source":"모범음식점"}`. 확인됨 |

구·동을 값(JSON) 안이 아니라 **열쇠**에 넣은 것은, 목록을 구·동으로 거르려면 DB 가 비교해야 하는데 이 저장소의 조회는 JPQL(JPA 의 객체 질의어)이고
JPQL 은 JSONB 안을 못 보기 때문이다.

> **2026-10-15 (S15P21E201-2059).** 「맛집」 칩에는 이제 공공 인증 말고 **현지인이 추천한 식당**(`CERTIFICATION_TAG/LOCAL_SURVEY`, 13절)도 든다. 이 적재기가 다는 것은 위 표 그대로이고, 현지인 추천은 13절 SQL 이 단다.
> 같은 엔진으로 **카페**를 넣는 적재기는 14절이다.

새로 만들기 전에 `SamePlaceGuard`(3절 ①과 같은 **같은 곳 검사** — 이름이 같고 150m 안이면 같은 곳)를 거친다.
- **같은 곳이 이미 있고 추천에 쓰는 장소면** 새로 만들지 않고 그 장소에 표식을 단다(수집 쪽 짝짓기가 놓친 것을 주워 담는다)
- **체인 가게 30~150m · 해변·산책로·공원 150m~1km** 같은 이름은 넣지도 달지도 않고 사람 확인 목록으로 남긴다(`same-place-review-BUSAN_CERTIFIED_FOOD-<수집분>.csv`, 입력 파일 옆)
- `existingPlaceId` 의 장소가 **합쳐진 줄**이면 남은 줄에 단다. 이 DB 에 없거나 추천에서 빠진 곳(숨김·사용자가 고른 곳)이면 안 달고 로그에 센다

### 11-3. 돌리기

서버는 `ubuntu@3.34.84.167`(`api.gabolle.kr`)이다 — 1절의 옛 주소(`j15e201.p.ssafy.io`)가 아니다. 도커 컨테이너 이름(`backend`)과 망(`local-route-personalization_data_net`)은 그대로다.

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@3.34.84.167

# 1) 입력 파일을 올린다 (내 PC → 서버)
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load'
scp -i $PEM /c/Users/SSAFY/Desktop/gabolle-archive/certified-restaurants-20261013.ndjson $HOST:/home/ubuntu/load/

# 2) 서버에 들어가 2절대로 DB 접속값을 빌린다 (끝나면 /tmp/load.env 를 지운다 — 5절)
ssh -i $PEM $HOST
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}'   | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
wc -l /tmp/load.env    # 네 줄이면 정상

# 3) 3절의 run 함수를 정의한 뒤 (IMG 는 지금 도는 백엔드와 같은 이미지로 잡는다), 돌린다
IMG=$(docker inspect backend --format '{{.Config.Image}}')
NET=local-route-personalization_data_net
#   run() { … }   ← 3절 그대로
RUN=run
$RUN --gabolle.place.loader.certified-restaurants=/load/certified-restaurants-20261013.ndjson      --gabolle.place.loader.dataset-version=certified-restaurants-20261013
```

마침 줄: `인증 식당 적재를 마쳤다 — 읽은 줄 N · 넣을 곳 M · 안 넣고 건너뜀 K(구·동 모름 a · 주소의 구와 안 맞음 b) · 같은 번호의 남는 줄 D · 새 장소 …곳(사진 …) · 있던 장소에 표식 …줄 · … · 구·동별 {…}`.
앞에 안 넣은 줄이 **한 줄씩** 찍힌다(`안 넣고 건너뜀(UNKNOWN_AREA) — …`, `같은 번호의 남는 줄이라 안 넣음 — …`).

**2026-10-13 파일**(이 날짜의 파일 한 개 기준이다 — 파일이 바뀌면 숫자도 바뀐다)을 처음 돌리면 읽은 줄 666 · 넣을 곳 655 · 건너뜀 3(구·동 모름 1 · 주소의 구와 안 맞음 2) ·
남는 줄 8 이어야 하고, 그중 `existingPlaceId` 가 있는 213곳은 표식만, 나머지 442곳이 새 장소 후보(공공 사진 235곳)다. 같은 곳 검사가 걸러 낸 만큼
새 장소는 442곳보다 줄고 「같은 곳이라 표식만」 줄이 그만큼 는다.
**두 번째 실행은 새 장소 0곳 · 새 표식 0** 이어야 한다 — 아니면 멈추고 본다.

> `existingPlaceId 의 장소가 이 DB 에 없어` 경고가 크면, 운영이 아닌 DB 의 장소 번호로 만든 파일이다. 멈추고 수집 쪽에 알린다.

### 11-4. 확인

```sql
-- 표식 종류별 개수 (수집분 하나)
SELECT feature_type, evidence_status, count(*)
  FROM place_feature
 WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_version = 'certified-restaurants-20261013'
 GROUP BY 1, 2 ORDER BY 1, 2;

-- 맛집 갈래 장소 수 = 구별 합 (장소 하나에 구·동은 하나)
SELECT split_part(feature_key, '/', 1) AS gu, count(DISTINCT place_id)
  FROM place_feature WHERE feature_type = 'ADMIN_AREA_TAG' GROUP BY 1 ORDER BY 2 DESC;

-- 새로 만든 장소
SELECT category, count(*) AS places, count(photo_url) AS photos
  FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = 'certified-restaurants-20261013' GROUP BY 1;
```

API 로도 본다 — 로그인한 토큰(손님 토큰도 된다)으로 `GET /api/v1/places/facets` 의 `INTEREST_TAG` 항목 `keys` 끝에 `LOCAL_RESTAURANT`(`맛집`)이 건수와 함께 나오는지,
`GET /api/v1/places/restaurants/areas` 가 구 → 동 목록을, `GET /api/v1/places/restaurants?gu=해운대구&dong=우동` 이 그 동의 맛집을 주는지 본다.

### 11-5. 되돌리기

표식과 장소 모두 `source_type` 과 수집분이 찍혀 있다. **표식을 먼저** 지운다(`fk_place_feature_place`).

```sql
DELETE FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_version = 'certified-restaurants-20261013';
DELETE FROM place         WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = 'certified-restaurants-20261013';
```

첫 줄은 **이미 있던 장소에 단 표식도 같이** 지운다(그 표식도 같은 `source_type` 이다) — 이미 있던 장소 자체는 안 건드린다. 새 장소에 그 뒤 다른 적재(영업시간·사진 등)가 표식을 붙였으면
둘째 줄이 외래키에 막힌다. 그때는 `place_id IN (SELECT place_id FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = …)` 로 그 표식을 먼저 지운다.

### 11-6. 다시 돌려도 다른 출처가 단 표식은 지워지지 않는다 (S15P21E201-2059 에서 코드로 확인)

이 적재기는 **더하기만 한다.** 엔진(`LocalPickLoader.saveChunk` — 맛집과 카페 적재기가 같이 쓴다)이 DB 에 쓰는 길은 `saveAll`(없는 장소·표식을 새로 넣는 것) 하나뿐이고 `delete`·`update` 호출이 없다.
표식은 (장소, 종류, 열쇠)가 이미 있으면 건너뛴다(`addIfAbsent`). 그래서 같은 파일을 다시 돌려도:

- 현지인 추천(`CERTIFICATION_TAG/LOCAL_SURVEY`, 13절)과 카페 표식(`INTEREST_TAG/LOCAL_CAFE`)은 **지워지지 않는다** — 이 적재기에 그것을 지우는 코드가 없다
- 🔴 **반대 방향의 함정을 하나 막았다.** 13-3 이 카페로 옮긴 장소에 이 적재기가 맛집 표식을 **되살리면** 한 장소가 맛집과 카페 양쪽에 선다. 그래서 `CertifiedRestaurantLoader.keepsOutOfExplore` 가
  「장소 갈래가 `CAFE_HEALING` 이고 카페 표식이 이미 있는 곳」에는 맛집 표식을 달지 않는다(구·동과 인증 목록은 그대로 단다). **카페 표식이 아직 없는 카페 갈래 장소**에는 예전처럼 맛집 표식이 붙는다 —
  그곳을 옮기는 일은 13절 SQL 의 몫이므로, 새 맛집 파일을 넣은 뒤에는 13-4 의 SQL 을 한 번 더 돌린다(멱등이라 안전하다)

## 12. 근처 편의시설 — 주차장 · 약국 · 공중화장실 · 지하철 물품보관함 (S15P21E201-2052, 2026-10-14 추가)

앱 「AI 챗봇 → 근처 편의시설」(`app/field/nearby.tsx`)이 보여 주는 자료다. 여행자가 내 위치(또는 고른 목적지) 근처의 ① 주차장(싼 순) ② 약국(지금 연 곳 ·
일요일/공휴일 · 심야) ③ 공중화장실(장애인용 · 기저귀 교환대) ④ 지하철 물품보관함을 찾는다. 자료는 **장소(`place`) 표에 섞지 않고 새 표 `facility`** 에
넣는다 — 장소 표는 추천·탐색·일정이 읽는 후보 목록이라 화장실이 거기 들어가면 「오늘의 추천 코스」에 끼어든다. 적재기는
`FacilityLoaderRunner`(`FacilityLoader` + `FacilityReader`)이고, 조회 끝점은 `GET /api/v1/facilities/nearby` 다.

🔴 **배포(마이그레이션)가 먼저, 적재가 나중이다.** `V20261014120000__facility.sql` 이 `facility` 표를 만든다. 이 마이그레이션이 운영에 안 들어간 채 돌리면
`relation "facility" does not exist` 로 첫 덩어리에서 멈춘다(그래도 한 행도 안 들어간다). 배포가 끝난 뒤(`back/dev` 배포 잡이 초록) 돌린다.

### 12-1. 입력 파일과 한 줄 모양

파일은 git 에 없다 — 수집한 사람 PC 의 `gabolle-archive/facilities-20261014/` 에 있는 NDJSON(**한 줄에 JSON 하나인 글 파일**) 넷이다. **이 폴더의 다른 파일은 쓰지 않는다**
(`parking_fee_busan.ndjson` · `metro_facility.json` 같은 것은 수집 쪽 참고 원자료다).

| 파일 | 줄 | 한 줄이 말하는 것 |
|---|---|---|
| `parking.ndjson` | 667 | 주차장 — 공영/민영, 구획 수, 요금표, 요일별 시간. `feeKnown`(요금을 아는가)과 지하철 환승주차장(`METRO-…`) 메모가 있다 |
| `pharmacy.ndjson` | 1,714 | 약국 — 전화, 찾아가는 길, 요일별 시간(공휴일 칸 포함) |
| `toilet.ndjson` | 2,507 | 공중화장실 — 종류, 개방시간 글, 장애인용, 기저귀 교환대 |
| `locker.ndjson` | 71 | 지하철 물품보관함 — 역, 호선, 크기별 칸 수, 요금 글 |

```json
{"sourceId":"10000-11191-00003-00-1","name":"신평패션칼라 공영주차장","address":"부산광역시 사하구 신평동  664","lat":35.0925135,"lng":128.9568185,"operator":"PUBLIC","free":false,"feeKnown":true,"spaces":94,"fee":{"baseMinutes":30,"baseWon":300,"addMinutes":10,"addWon":0,"dayMaxWon":2400,"freeMinutes":0},"hours":{"Monday":{"open":"08:00","close":"20:00"},"Sunday":{"open":"00:00","close":"00:00"},"Holiday":{"open":"00:00","close":"00:00"}}}
```

- 🔴 **모르는 칸·틀린 모양이 하나라도 있으면 줄 번호와 함께 멈추고 한 행도 안 넣는다**(파일 넷을 **전부 먼저 검사**한 뒤에야 넣기 시작한다). 같은 `sourceId` 가 종류 안에서 두 번 나와도, 좌표가 부산 밖이어도 멈춘다
- 🔴 **이상한 값은 멈추지 않고 비운다 — 모른다는 것을 0 이나 빈 글자로 지어내지 않는다.** 아래가 실제 파일에서 본 것이다. 앱은 비운 칸(`null`)을 「요금 정보 없음」 「운영시간 모름」으로 그린다

| 이상한 값 | 처리 | 이 파일에서 |
|---|---|---|
| 주차장 기본요금이 정수 최댓값(2147483647)이거나 4만 원(월정기권이 섞인 값) — `feeKnown=true` 인데도 | 요금표 **통째로 null**. 입력의 `feeKnown` 표시를 믿지 않고 값도 본다 | 70곳(60 + 10) |
| `feeKnown=false` | 요금표 null (유료 → 예상요금 「요금 정보 없음」, 순서는 요금 아는 곳 뒤) | 220곳 |
| 추가요금이 0원인데 기본요금은 있음(같은 요금표를 쓰는 다른 주차장은 추가요금이 있고, 「하루 최대 2,400원」이 있다는 것 자체가 추가요금이 0 이 아니라는 뜻이다) | **추가요금 칸만 null** — 기본 시간(30분) 안의 요금은 알고, 넘는 체류의 예상요금은 모른다 | 109곳 |
| 요일 시간이 `00:00~00:00` (하루 종일인지 운영 안 하는지 모른다) · 시작·끝이 null · 약국의 끝이 시작보다 빠름(`19:00~14:30`) · 1분짜리(`00:00~00:01`) | 그날만 **「모르는 날」(null)**. 하루도 못 읽으면 `hours` 통째로 null — 「닫았다」라고 하지 않는다 | 요일 칸 1,077 + 192 + 2 |
| 화장실 주소가 「부산광역시 연제구」뿐 — 좌표가 구 중심점 한 점이다(학교 25곳이 한 점을 나눠 가졌다) | **넣지 않고** 로그에 한 줄씩 남긴다 | 25곳 |
| 물품보관함 좌표 — 「역 좌표」라고 했지만 역 자료와 대 보니 **64곳 가운데 46곳이 300m 넘게 어긋났다**(다대포해수욕장 6.0km · 장산 5.5km). 서로 다른 역이 같은 좌표를 나눠 쓰기도 한다 | **저장소의 부산교통공사 역 자료**(`transit/busan-subway-network.json`)의 좌표로 바꾼다. 역을 못 찾으면 넣지 않는다 | 71곳 모두 바꿈(300m 넘게 52곳, 가장 멀리 6,038m) |
| 지하철 환승주차장(`METRO-<역 번호>-<n>`)의 좌표 — 일부가 같은 문제다(모라역 3.7km · 남천역 2.1km 어긋남) | 번호의 가운데 숫자가 가리키는 역의 좌표로 바꾼다(이름의 역이 같을 때만) | 14곳 모두(300m 넘게 6곳) |
| 주소 자리에 「2번 출구」 같은 **위치 설명**이 온 환승주차장(`note` 가 같은 말로 시작) | `address` 를 null 로 두고 설명은 `note` 에 남긴다 | 14곳 |
| 약국 메모의 `&#xD;` · `&lt;` 같은 글자 코드와 줄바꿈 · 「.」뿐인 메모 | 풀어서 한 줄로 · 메모 null | — |
| 화장실 개방시간 앞의 분류말 「정시」(「정시 09:00~18:00」) · 「정시 정시」 | 분류말은 뗀다 · 분류말뿐이면 null. 「상시」「24시간」「00:00~24:00」은 `alwaysOpen=true` | 「정시 정시」 24곳 |
| 기저귀 교환대 없음인데 위치가 적힌 화장실 | 위치 null | 1곳 |

### 12-2. 무엇을 넣나

표 `facility` 한 줄 = 시설 한 곳이다. 공통 칸(`kind` · `source_id` · `name` · `address` · `lat` · `lng` · `dataset_version` · `collected_at`)과 종류별 칸 `detail`(JSONB —
안쪽 값으로 검색할 수 있는 JSON 타입)이다. `(kind, source_id)` 가 유일하고, 행 번호는 그 쌍에서 언제나 같게 만든다.

| `kind` | `detail` 의 칸 |
|---|---|
| `PARKING` | `operator`(PUBLIC/PRIVATE) · `free` · `spaces` · `fee`{baseMinutes, baseWon, addMinutes, addWon, dayMaxWon, freeMinutes} 또는 null · `hours`{Monday…Sunday, Holiday} 또는 null · `note` |
| `PHARMACY` | `tel` · `directions` · `note` · `hours`(약국은 쉬는 날의 칸을 안 적는다 — **칸이 없으면 쉰다**, 값이 null 이면 모른다) |
| `TOILET` | `type`(OPEN/PUBLIC/SIMPLE/MOBILE) · `openHours` · `alwaysOpen` · `accessible` · `diaper` · `diaperPlace`(WOMEN/MEN/BOTH) · `emergencyBell` · `cctv` · `tel` |
| `LOCKER` | `station` · `stationEn` · `line` · `location` · `small`/`medium`/`large`/`xlarge`(크기별 칸 수) · `feeText` · `operator` |

시각의 끝이 `24:00` 이상이면 다음 날이다(`22:00~25:00` = 밤 10시부터 다음 날 새벽 1시) — 약국 자료의 방식 그대로이고, 주차장의 `10:00~02:00` 도 같은 모양(`26:00`)으로 바꿔 넣는다.

🔴 **공휴일 달력이 이 프로젝트에 없다.** 「오늘이 공휴일인가」를 모르므로 지금은 **요일 칸으로만** 「지금 여는가」를 판정한다(`FacilityNearbyService.isPublicHoliday` 한 곳). 평일에 낀 공휴일에는
평일 시간으로 「영업 중」이 나올 수 있다. 약국 화면이 「운영시간은 약국이 신고한 정보예요. 방문 전 전화로 확인해 주세요」를 늘 함께 적는 이유다. `Holiday` 칸은 이미 저장돼 있어 달력이 생기면 그 한 곳만 고친다.

### 12-3. 돌리기

서버는 `ubuntu@3.34.84.167`(`api.gabolle.kr`), 이미지는 `local-route-backend:candidate`, 망은 `local-route-personalization_data_net` 이다(11절과 같다).

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@3.34.84.167

# 1) 입력 파일 넷을 올린다 (내 PC → 서버). 다른 적재 파일과 이름이 겹치지 않게 폴더를 따로 쓴다
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load/facilities-20261014'
scp -i $PEM /c/Users/SSAFY/Desktop/gabolle-archive/facilities-20261014/{parking,pharmacy,toilet,locker}.ndjson $HOST:/home/ubuntu/load/facilities-20261014/

# 2) 서버에 들어가 2절대로 DB 접속값을 빌린다 (끝나면 /tmp/load.env 를 지운다 — 5절)
ssh -i $PEM $HOST
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}'   | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
wc -l /tmp/load.env    # 네 줄이면 정상

# 3) 3절의 run 함수를 정의한 뒤 돌린다. 이 이미지가 12절의 마이그레이션·적재기를 담은 배포본이어야 한다
IMG=local-route-backend:candidate
NET=local-route-personalization_data_net
#   run() { … }   ← 3절 그대로 (/home/ubuntu/load 를 /load 로 마운트한다)
RUN=run
$RUN --gabolle.facility.loader.parking=/load/facilities-20261014/parking.ndjson \
     --gabolle.facility.loader.pharmacy=/load/facilities-20261014/pharmacy.ndjson \
     --gabolle.facility.loader.toilet=/load/facilities-20261014/toilet.ndjson \
     --gabolle.facility.loader.locker=/load/facilities-20261014/locker.ndjson \
     --gabolle.facility.loader.dataset-version=facilities-20261014
```

파일 넷을 다 주는 것이 표준이다. **하나만 줘도 돈다** — 약국처럼 자주 바뀌는 자료만 새 수집분으로 다시 넣을 때 쓴다(주지 않은 종류는 손대지 않는다). `dataset-version` 은 늘 필수다 — 빼면 컨테이너가 죽는다.

마침 줄: `근처 편의시설 적재를 마쳤다 — 수집분 facilities-20261014 · 주차장 667곳(새 행 … · 바뀌어 고친 행 … · 그대로 …) · 약국 1714곳(…) · 화장실 2482곳(…) · 물품보관함 71곳(…) · …ms`.
종류마다 앞에 `… 읽음`(읽은 줄 · 넣을 곳 · 건너뜀 · 비운 값 개수)과 `… 넣음`(새 행 · 파일에 없는 기존 행) 줄이 찍히고, 안 넣은 줄은 **한 줄씩** 찍힌다(`안 넣고 건너뜀(화장실·COARSE_ADDRESS) — …`).

**2026-10-14 파일**(이 날짜의 파일 기준이다 — 파일이 바뀌면 숫자도 바뀐다)을 처음 돌리면 **새 행 667 + 1,714 + 2,482 + 71 = 4,934** 이고, 화장실에서 25줄이 건너뛰어져야 한다.
**두 번째 실행은 새 행 0 · 바뀌어 고친 행 0 · 그대로 4,934, 「파일에 없는 기존 행 0개」** 여야 한다 — 값이 하나도 안 바뀌면 DB 에 갱신이 한 줄도 안 나간다(수집 시각도 그대로). 아니면 멈추고 본다.

> 새 수집분에서 **사라진 행은 지우지 않고** 그대로 둔다 — 원천이 잠깐 빠뜨린 것을 지우면 앱의 「근처 약국」이 줄어든다. 얼마나 남는지는 `파일에 없는 기존 행 N개(지우지 않고 그대로 둠)` 로 로그에 센다.
> 정말 폐업한 곳을 치우려면 이 개수를 보고 사람이 정한다(12-5).

### 12-4. 확인

```sql
-- 종류별 행 수. 기대: LOCKER 71 · PARKING 667 · PHARMACY 1714 · TOILET 2482
SELECT kind, count(*) AS rows, min(dataset_version) AS dataset FROM facility GROUP BY kind ORDER BY kind;

-- 주차장 요금 상태. 기대: 무료 42 · 요금표 있음 335 · 요금 모름 290 (모르는 곳이 0원이 되어 있으면 틀린 것이다)
SELECT count(*) FILTER (WHERE detail ->> 'free' = 'true')                                                 AS free,
       count(*) FILTER (WHERE detail ->> 'free' = 'false' AND jsonb_typeof(detail -> 'fee') = 'object')   AS known,
       count(*) FILTER (WHERE detail ->> 'free' = 'false' AND jsonb_typeof(detail -> 'fee') = 'null')     AS unknown
  FROM facility WHERE kind = 'PARKING';

-- 물품보관함이 역 좌표인가 — 다대포해수욕장역은 위도 35.04 대여야 한다 (파일 값 35.10 이면 바로잡기가 안 돈 것)
SELECT name, lat, lng FROM facility WHERE kind = 'LOCKER' AND source_id = 'BTC-1-다대포해수욕장-1';
```

API 로도 본다 — 로그인한 토큰(손님 토큰도 된다)으로:

- `GET /api/v1/facilities/nearby?kind=PARKING&lat=35.1580&lng=129.0600&radiusM=1000&stayMinutes=120` — 서면 근처 주차장이 **예상요금이 싼 순**으로 나오고, 요금을 모르는 곳(`estimatedWon: null`)이 맨 뒤여야 한다
- `kind=PHARMACY` — 지금 연 곳(`openNow: true`)이 먼저. `hoursAreReported` 는 늘 `true`
- `kind=TOILET&accessible=true&diaper=true` — 장애인용이면서 기저귀 교환대가 있는 곳만
- `kind=LOCKER&lat=35.0980&lng=129.0349` — 남포·자갈치역 보관함이 가까운 순으로

### 12-5. 되돌리기

모든 행에 수집분이 찍혀 있다.

```sql
DELETE FROM facility WHERE dataset_version = 'facilities-20261014';
```

다른 표를 가리키는 곳이 없어 외래키에 막히지 않는다. 같은 파일을 다시 돌려 행이 새 수집분 이름으로 갱신됐다면 그 이름으로 지운다. 한 종류만 지우려면 `AND kind = 'PHARMACY'` 를 붙인다.
폐업한 곳만 치우고 싶으면 **새 수집분을 돌린 뒤** `DELETE FROM facility WHERE kind = 'PHARMACY' AND dataset_version <> '<새 수집분>'` (새 파일에 없어서 옛 이름으로 남은 행) — 먼저 `SELECT` 로 개수와 이름을 눈으로 본다.

## 13. 현지인 설문 추천 47곳 — 「현지인 추천」 배지 + 맛집에 든 카페 옮기기 (S15P21E201-2059, 2026-10-15 추가)

앱 「로컬 탐색」 카드에 **「현지인 추천」 배지**를 다는 자료다. 부산 현지인 설문에서 추천받은 곳 가운데 47곳(맛집 35 · 카페 12)이고, **전부 우리 DB 에 이미 있는 장소**라 장소를 새로
만들지 않고 표식(`place_feature` — 장소에 붙는 사실 한 줄)만 단다. 적재기(자바 러너)가 아니라 **SQL 한 파일**이다 — 이미 있는 47곳에 표식 몇 줄을 다는 일이라 러너를 만들 이유가 없고,
파일 하나가 한눈에 읽힌다. 같은 파일이 **맛집(`LOCAL_RESTAURANT`)에 든 카페를 카페(`LOCAL_CAFE`)로 옮기는 일**도 한다(13-3).

🔴 **배포(마이그레이션)가 먼저다.** `V20261015120000__local_survey_and_cafe.sql` 이 사전(`place_feature_code` — 표식에 올 수 있는 낱말의 목록)에 새 낱말 `LOCAL_CAFE` ·
`LOCAL_SURVEY` · `KTO_CAFE` · `REDTABLE_FEATURED` 를 넣는다. 사전에 낱말이 없으면 SQL 의 첫 확인이 **아무것도 바꾸지 않고 멈춘다**(「배포가 먼저 나가야 한다」).
앱의 카페 칩은 서버가 `/facets` 에 카페를 건수와 함께 줘야 나타난다.

### 13-1. 파일

`backend/scripts/local-picks-20261015/` (이 저장소 안에 있다 — 입력 파일은 만든 사람 PC 에만 있던 것을 같이 커밋했다).

| 파일 | 무엇 |
|---|---|
| `survey-local-picks-20261015.ndjson` | 입력 47줄(**한 줄에 JSON 하나인 글 파일**). `placeId` · `name` · `category`(`FOOD` 35 · `CAFE_HEALING` 12) · `gu` · `dong` · `recommenders`(추천한 사람 수 — 한 곳은 `null`) |
| `make_sql.mjs` | 입력 → SQL. `node make_sql.mjs` 로 다시 만든다. **SQL 은 손으로 고치지 않고 이 스크립트를 고친다** |
| `local_picks_20261015.sql` | 운영에서 돌릴 파일. 한 트랜잭션(**전부 되거나 전부 안 되는 한 덩어리**)이고 **멱등**(**두 번 돌려도 결과가 같다**)이다 |

### 13-2. 무엇을 다나

| 대상 | 내용 |
|---|---|
| `INTEREST_TAG` = `LOCAL_RESTAURANT` / `LOCAL_CAFE` | 입력의 `category` 가 `FOOD` 면 맛집, `CAFE_HEALING` 이면 카페. 추정(`ESTIMATED`) — 설문 추천과 우리 장소 갈래에서 옮긴 것이지 가게에 직접 확인한 것이 아니다 |
| `ADMIN_AREA_TAG` = `구/동` | **한 장소에 하나** — 이미 있으면 파일의 구·동이 달라도 먼저 것을 둔다. 추정 |
| `CERTIFICATION_TAG` = `LOCAL_SURVEY` | 값 `{"source":"현지인 추천","recommenders":n}`. 설문에서 실제로 추천받은 기록이라 확인됨(`VERIFIED`). **추천한 사람 수를 모르는 한 곳은 `recommenders` 칸을 뺀다**(0 으로 채우지 않는다) |

- 표식의 `source_type` 은 `LOCAL_SURVEY`, `source_version` 은 `survey-local-picks-20261015` 다 — 되돌릴 때 이 둘로 걸린다
- 표식 id 는 **이름 기반 UUID**(**같은 입력 글자에서 언제나 같은 값이 나오는 번호**)다. 맛집 적재기와 같은 식(`gabolle:place_feature:<출처>:<장소>:<종류>:<열쇠>` 를 MD5 로 해시)이라 자바의
  `UUID.nameUUIDFromBytes` 와 같은 값이 나온다 — `LocalPicksSqlIntegrationTest` 가 두 값을 맞춰 본다
- **달 수 없는 곳은 건너뛴다**(에러로 멈추지 않는다): DB 에 없는 장소 · 추천에서 빠진 장소(숨김·합쳐짐 등 `curation_status` 가 `CURATED` 가 아님) · 폐업일자가 있는 장소.
  이런 곳은 SQL 출력의 **「확인 1」** 표에 이름과 이유가 나온다 — 비어 있어야 47곳이 다 달린다

### 13-3. 맛집에 든 카페를 카페로 옮긴다

공공 인증 맛집 중에는 **장소 갈래(`place.category`)가 `CAFE_HEALING`(카페·힐링) 인 곳**이 있어, 맛집 칩에 카페가 섞여 보인다. 그 곳들의 `LOCAL_RESTAURANT` 를 떼고 `LOCAL_CAFE` 로 옮긴다.

- **몇 곳을 옮기는지 먼저 센다** — 출력의 **「확인 3」**(옮길 곳 수, 그중 이미 카페 표식도 있는 곳 수)
- 카페 표식이 **이미 있는 곳**은 맛집 표식만 지운다. 같은 장소에 같은 표식이 둘이면 `uq_place_feature_keyed`(**장소·종류·열쇠가 같은 표식을 둘 못 두게 하는 DB 규칙**)에 걸리기 때문이다
- 나머지는 **같은 행의 열쇠만 바꾼다**(`UPDATE`) — 표식 id · 출처(`BUSAN_CERTIFIED_FOOD`) · 수집분 · 증거 등급이 그대로라 어느 적재가 단 표식인지 되짚을 수 있다
- **장소 갈래가 음식(`FOOD`)인 곳은 안 건드린다.** 음식점을 겸하는 카페는 맛집 표식과 카페 표식이 둘 다 설 수 있다(「확인 6」이 둘 다 선 곳을 보여 준다)

### 13-4. 돌리기

서버는 `ubuntu@3.34.84.167`(`api.gabolle.kr`), DB 는 8-1 의 컨테이너(`local-route-personalization-postgres-1`)다. `psql` 은 PostgreSQL 의 명령줄 도구, `ON_ERROR_STOP` 은
**문장 하나가 틀리면 거기서 멈추게 하는 옵션**이다. 파일이 `BEGIN` 으로 시작하므로 멈추면 아무것도 바뀌지 않는다.

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@3.34.84.167

# 1) SQL 을 올린다 (내 PC → 서버)
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load'
scp -i $PEM backend/scripts/local-picks-20261015/local_picks_20261015.sql $HOST:/home/ubuntu/load/

# 2) 돌린다. 출력의 「확인」 표들을 읽는다
ssh -i $PEM $HOST "docker exec -i local-route-personalization-postgres-1 psql -U app_user -d app_db -v ON_ERROR_STOP=1 < /home/ubuntu/load/local_picks_20261015.sql"
```

**두 번째 실행은 출력의 「확인 3」 이 0 곳이고 새로 단 표식이 한 줄도 없어야 한다.** 아니면 멈추고 본다.

### 13-5. 확인

파일 기준 기대값이다(입력의 `existingTags` 에 이미 맛집·구동·인증이 달려 있다고 적힌 4곳이 있다 — DB 가 파일을 받은 뒤로 바뀌었으면 숫자가 달라진다).

| SQL 출력 | 기대 |
|---|---|
| 확인 1 (달 수 없는 곳) | 비어 있음 |
| 확인 4 (이 수집분이 단 표식) | `CERTIFICATION_TAG/LOCAL_SURVEY` 47 · `INTEREST_TAG/LOCAL_RESTAURANT` 31 · `INTEREST_TAG/LOCAL_CAFE` 12 · `ADMIN_AREA_TAG` 43 |
| 확인 5 (지금 갈래별 곳 수) | 카페가 옮긴 곳 + 12 이상, 맛집은 옮긴 곳만큼 줄어듦 |
| 확인 6 (맛집·카페 둘 다 선 곳) | 음식점을 겸하는 카페뿐 |

API 로도 본다 — 로그인한 토큰(손님 토큰도 된다)으로 `GET /api/v1/places/facets` 의 `INTEREST_TAG` 항목 `keys` 에 `LOCAL_CAFE`(`카페`)가 **`LOCAL_RESTAURANT` 바로 뒤에** 건수와
함께 나오는지, `GET /api/v1/places/restaurants?kind=CAFE&gu=부산진구` 의 카드에 `certifiedSources: ["LOCAL_SURVEY"]` 가 들어 있는지 본다.

### 13-6. 되돌리기

```sql
-- 설문 표식을 지운다 (이 수집분이 단 것만 — 갈래·구동·현지인 추천)
DELETE FROM gabolle.place_feature WHERE source_type = 'LOCAL_SURVEY' AND source_version = 'survey-local-picks-20261015';

-- 카페로 옮긴 곳을 맛집으로 되돌리려면 (옮긴 표식은 출처가 BUSAN_CERTIFIED_FOOD 로 남아 있다 — 카페 적재기(14절)가 단 표식은 출처가 달라 안 걸린다)
UPDATE gabolle.place_feature f SET feature_key = 'LOCAL_RESTAURANT' FROM gabolle.place p
 WHERE p.place_id = f.place_id AND p.category = 'CAFE_HEALING' AND f.feature_type = 'INTEREST_TAG'
   AND f.feature_key = 'LOCAL_CAFE' AND f.source_type = 'BUSAN_CERTIFIED_FOOD';
```

## 14. 카페 250곳 — 로컬 탐색 「카페」 갈래 (S15P21E201-2059, 2026-10-15 추가)

앱 「로컬 탐색」에 「카페」 칩(맛집 바로 다음)을 채우는 자료다. 구 → 동으로 골라 그 동의 카페를 본다(맛집과 같은 틀). 적재기는 `LocalCafeLoaderRunner`(`LocalCafeReader` + `LocalCafeLoader`)이고,
**맛집 적재기(11절)와 같은 엔진**(`LocalPickLoader`)을 쓴다 — 이미 있는 장소에는 **표식만 달고**, 없는 장소는 **새로 만들고**, 새로 만들기 전에 같은 곳 검사(`SamePlaceGuard`)를 거친다.

🔴 **배포(마이그레이션)가 먼저, 적재가 나중이다.** 13절과 같은 마이그레이션이 사전에 `KTO_CAFE` · `REDTABLE_FEATURED` 를 넣는다.
🔴 **이 적재기를 머지하는 것과 자료를 넣는 것은 다르다** — 배포는 적재기를 올려 놓을 뿐 아무것도 넣지 않는다. 아래를 누군가 한 번 돌려야 한다.

### 14-1. 입력 파일과 한 줄 모양

파일은 git 에 없다 — 만든 사람 PC 의 `gabolle-archive/cafes-20261015.ndjson`(**한 줄에 JSON 하나인 글 파일**, 250줄)이다.

```json
{"sourceId":"KTO2841481","name":"공극샌드커피","lat":35.1845252618,"lng":129.2131276086,"address":"부산광역시 기장군 기장읍 공수해안길 41","tel":null,"photoUrl":"http://tong.visitkorea.or.kr/cms/resource/76/2841476_image2_1.jpg","photoSource":"한국관광공사 공공누리 제3유형","sources":["한국관광공사"],"existingPlaceId":null,"existingCategory":null,"gu":"기장군","dong":"기장읍"}
{"sourceId":"RT1192","name":"백구당","lat":35.1048197,"lng":129.035743,"address":"부산광역시 중구 중앙대로81번길 3","tel":"051-465-0109","photoUrl":null,"photoSource":null,"sources":["레드테이블(방송·수상)"],"intro":"…","existingPlaceId":"fe8f096a-ca9b-3a0f-ae00-6d7f456831f8","existingCategory":"CAFE_HEALING","gu":"중구","dong":"중앙동4가"}
```

- `sourceId` 의 앞머리가 **출처**다 — `KTO` + 한국관광공사 콘텐츠 번호(contentid), `RT` + 레드테이블 식당 번호(RSTR_ID). 레드테이블은 부산광역시가 공공데이터포털에 연 식당 목록이다
- `sources` 는 `한국관광공사` · `레드테이블` · `레드테이블(방송·수상)` 중 하나 이상(「방송·수상」은 레드테이블이 TV 맛집 프로그램에 나왔거나 블루리본 서베이 같은 평가를 받았다고 적어 둔 곳). **번호 앞머리와 맞아야 한다**(`KTO…` 는 `한국관광공사` 를, `RT…` 는 레드테이블 쪽을 포함)
- `gu`·`dong` 은 좌표를 VWorld(국토교통부 지도 서비스)로 **역지오코딩**(좌표로 행정 주소를 알아내는 것)해서 얻은 법정동이다
- `existingPlaceId` 가 있으면 우리 DB 에 이미 있는 장소다 — **장소는 새로 안 만들고 표식만 단다. 장소의 갈래(`category`)도 안 바꾼다.** `existingCategory` 는 그 장소가 가진 갈래(`FOOD` 10곳 · `CAFE_HEALING` 21곳)로, 읽어 두기만 한다 — **음식 갈래인 곳도 카페로 옮기지 않고 그대로 두되 카페 표식만 더한다**
- `tel` · `intro` 는 읽기만 하고 DB 에 넣지 않는다(`place` 에 전화번호 칸이 없고, `intro` 는 레드테이블이 쓴 홍보 문구라 쓸 조건을 확인한 적이 없다)
- 🔴 **모르는 칸·틀린 모양이 하나라도 있으면 줄 번호와 함께 멈추고 한 행도 안 넣는다.** 같은 `sourceId` 가 두 번 나와도 멈춘다. 구·동을 모르는 줄(`?`)과 주소의 구가 좌표의 구와 다른 줄만 11-1 과 같이 건너뛰고 로그에 남긴다
- 🔴 **레드테이블 사진은 읽는 순간 멈춘다.** `photoUrl` 은 관광공사 줄(`KTO…`)에 `한국관광공사…` 출처 문구와 짝으로만 올 수 있다. 레드테이블 사진은 다시 써도 되는지 이용 조건을 확인하지 못했다.
  수집 파일은 이미 비워 두었지만(레드테이블 175줄 모두 사진 없음), 사람이 파일을 다시 만들다 섞어도 여기서 막힌다

### 14-2. 무엇을 넣나

| 대상 | 내용 |
|---|---|
| 새 `place` | `category='CAFE_HEALING'`, `dataset_version=<수집분>`. **`source_type` 은 원천의 것을 그대로 쓴다** — 관광공사 줄은 `TOURAPI`(`source_id`=콘텐츠 번호), 레드테이블 줄은 `REDTABLE`(`source_id`=식당 번호). 장소 번호도 그 적재기들이 쓰는 식(`gabolle:place:TOURAPI:<번호>` · `gabolle:place:REDTABLE:<번호>`)이라, **같은 가게를 그 적재기가 먼저(또는 나중에) 넣어도 같은 장소 번호**라 두 번 만들어지지 않는다. 관광공사 사진은 `https` 로 바꿔(`tong.visitkorea.or.kr`) 출처 문구와 함께 `photo_url`·`photo_source` 에 넣는다 |
| `CATEGORY_TAG` = `CAFE_HEALING` | **새 장소에만.** 추정 |
| `INTEREST_TAG` = `LOCAL_CAFE` | 카페 갈래 표식. 확인됨(`VERIFIED`) — 목록이 카페로 소개한 것. `/facets` 가 건수를 센다 |
| `ADMIN_AREA_TAG` = `구/동` | 한 장소에 하나 — 이미 있으면 먼저 것을 두고 로그에 센다. 추정 |
| `CERTIFICATION_TAG` = `KTO_CAFE` · `REDTABLE_FEATURED` | 소개한 목록마다 한 줄. 값 `{"source":"한국관광공사"}` · `{"source":"레드테이블"}`. 레드테이블이 방송·수상을 적어 둔 곳은 **`"broadcastOrAward":true` 가 더 붙는다.** 확인됨 |

- 표식의 `source_type` 은 `BUSAN_LOCAL_CAFE`, `source_version` 은 수집분이다 — 새 장소의 `CATEGORY_TAG` 와 이미 있던 장소에 단 표식까지 모두 같다. 되돌릴 때 이 둘로 한 번에 걸린다
- 새로 만들기 전 같은 곳 검사: **이름이 같고 150m 안이면** 새로 안 만들고 그 장소에 표식을 단다(체인은 30m). 체인 가게 30~150m 같은 이름은 넣지도 달지도 않고 사람 확인 목록 CSV 로 남긴다
  (`same-place-review-BUSAN_LOCAL_CAFE-<수집분>.csv`, 입력 파일 옆). 이 파일에는 같은 이름이 두 번 나오는 쌍이 하나 있다(`제일다방` RT11463 · RT18013) — 가까우면 한 장소로 합쳐진다

### 14-3. 돌리기

서버는 `ubuntu@3.34.84.167`(`api.gabolle.kr`), 이미지는 `local-route-backend:candidate`, 망은 `local-route-personalization_data_net` 이다(11-3 과 같다).

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@3.34.84.167

# 1) 입력 파일을 올린다 (내 PC → 서버)
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load'
scp -i $PEM /c/Users/SSAFY/Desktop/gabolle-archive/cafes-20261015.ndjson $HOST:/home/ubuntu/load/

# 2) 서버에 들어가 2절대로 DB 접속값을 빌린다 (끝나면 /tmp/load.env 를 지운다 — 5절)
ssh -i $PEM $HOST
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}'   | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
wc -l /tmp/load.env    # 네 줄이면 정상

# 3) 3절의 run 함수를 정의한 뒤 (IMG 는 지금 도는 백엔드와 같은 이미지로 잡는다), 돌린다
IMG=$(docker inspect backend --format '{{.Config.Image}}')
NET=local-route-personalization_data_net
#   run() { … }   ← 3절 그대로
RUN=run
$RUN --gabolle.place.loader.local-cafes=/load/cafes-20261015.ndjson \
     --gabolle.place.loader.dataset-version=cafes-20261015
```

`--gabolle.place.loader.dataset-version` 을 빼면 컨테이너가 죽는다(어느 수집분인지 안 적으면 나중에 되돌릴 수 없어서 일부러 그렇다). 맛집 적재(11절)의 `certified-restaurants` 프로퍼티와 **이름이 다르다** — `local-cafes` 다.

마침 줄: `카페 적재를 마쳤다 — 읽은 줄 N · 넣을 곳 M · 안 넣고 건너뜀 K(구·동 모름 a · 주소의 구와 안 맞음 b) · 새 장소 …곳(사진 …) · 있던 장소에 표식 …줄 · … · 출처별 {KTO=…, RT=…} · 구별 {…}`.
안 넣은 줄이 있으면 앞에 **한 줄씩** 찍힌다(`안 넣고 건너뜀(ADDRESS_GU_MISMATCH) — …`).

**2026-10-15 파일**(이 날짜의 파일 한 개 기준이다 — 파일이 바뀌면 숫자도 바뀐다)을 처음 돌리면 **읽은 줄 250 · 넣을 곳 250 · 건너뜀 0** 이어야 한다. 그중 `existingPlaceId` 가 있는 **31줄(음식 10 · 카페 21)은 표식만** 달고,
나머지 **219줄**이 새 장소 후보다(관광공사 57 · 레드테이블 162, 사진이 있는 줄은 55). 같은 곳 검사가 걸러 낸 만큼 새 장소는 219곳보다 줄고 「같은 곳이라 표식만」 줄이 그만큼 는다.
`existingPlaceId 의 장소가 이 DB 에 없어` 경고가 나오면 운영이 아닌 DB 의 번호로 만든 파일이다 — 멈추고 수집 쪽에 알린다.
**두 번째 실행은 새 장소 0곳 · 새 표식 0** 이어야 한다 — 아니면 멈추고 본다.

### 14-4. 확인

```sql
-- 이 수집분이 단 표식 종류별 개수
SELECT feature_type, feature_key, evidence_status, count(*)
  FROM place_feature
 WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = 'cafes-20261015'
 GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

-- 새로 만든 카페 (출처별 · 사진 수)
SELECT source_type, count(*) AS places, count(photo_url) AS photos
  FROM place WHERE dataset_version = 'cafes-20261015' AND source_type IN ('TOURAPI', 'REDTABLE') GROUP BY 1;

-- 🔴 레드테이블 카페에는 사진이 하나도 없어야 한다 — 0 이어야 한다
SELECT count(*) FROM place
 WHERE dataset_version = 'cafes-20261015' AND source_type = 'REDTABLE' AND (photo_url IS NOT NULL OR photo_source IS NOT NULL);

-- 지금 카페 갈래 곳 수와 구별 분포
SELECT split_part(a.feature_key, '/', 1) AS gu, count(DISTINCT a.place_id) AS cafes
  FROM place_feature a JOIN place_feature c ON c.place_id = a.place_id
 WHERE a.feature_type = 'ADMIN_AREA_TAG' AND c.feature_type = 'INTEREST_TAG' AND c.feature_key = 'LOCAL_CAFE'
 GROUP BY 1 ORDER BY 2 DESC;
```

API 로도 본다 — 로그인한 토큰으로 `GET /api/v1/places/facets` 의 `INTEREST_TAG` `keys` 에 `LOCAL_CAFE` 가 **맛집 바로 뒤에** 나오는지, `GET /api/v1/places/restaurants/areas?kind=CAFE` 가 구 → 동 목록을,
`GET /api/v1/places/restaurants/areas?kind=CAFE&lat=35.1631&lng=129.1636` 가 `nearestGu` 에 `해운대구` 를, `GET /api/v1/places/restaurants?kind=CAFE&gu=해운대구` 가 그 구의 카페를 주는지 본다.

### 14-5. 되돌리기

표식과 새 장소 모두 출처와 수집분이 찍혀 있다. **표식을 먼저** 지운다(`fk_place_feature_place`).

```sql
DELETE FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = 'cafes-20261015';
DELETE FROM place         WHERE dataset_version = 'cafes-20261015' AND source_type IN ('TOURAPI', 'REDTABLE');
```

첫 줄은 **이미 있던 장소에 단 표식도 같이** 지운다(그 표식도 같은 출처다) — 이미 있던 장소 자체는 안 건드린다. 새 장소에 그 뒤 다른 적재(영업시간·사진 등)가 표식을 붙였으면 둘째 줄이 외래키에 막힌다.
그때는 `place_id IN (SELECT place_id FROM place WHERE dataset_version = 'cafes-20261015' AND source_type IN ('TOURAPI', 'REDTABLE'))` 로 그 표식을 먼저 지운다.

## 15. 로컬 탐색 끝점이 바뀐 것 — 종류(kind) · 내 위치의 구 (S15P21E201-2059)

맛집과 카페는 **같은 끝점 둘**을 쓴다. 옛 앱은 아무것도 안 바꿔도 된다(새 칸은 전부 선택이고 기본은 예전 그대로다).

| 끝점 | 새 칸 | 뜻 |
|---|---|---|
| `GET /api/v1/places/restaurants/areas` | `kind` = `RESTAURANT`(기본) \| `CAFE` | 어느 갈래의 구 → 동 목록인가. 모르는 값은 400 이다 |
| | `lat` · `lng` | 내 위치. **둘이 함께** 와야 하고(하나만 오거나 범위 밖이면 400) 오면 응답에 `nearestGu` 를 더한다 |
| `GET /api/v1/places/restaurants` | `kind` | 어느 갈래의 목록인가. 각 줄에 `broadcastOrAward`(레드테이블이 방송·수상을 적어 둔 곳이면 `true`, 아니면 칸째 없음)가 더해질 수 있다 |
| `GET /api/v1/places/facets` | — | `INTEREST_TAG` 의 `keys` 끝에 `LOCAL_RESTAURANT`(맛집) 다음 `LOCAL_CAFE`(카페)가 건수와 함께 붙는다(자료가 없는 동안은 아예 없다) |

- **`nearestGu`** = 그 갈래의 장소 가운데 **내 위치에서 가장 가까운 곳의 구**. 가장 가까운 곳이 **5km 를 넘으면 `null`**(부산 밖에서 엉뚱한 구를 짐작하지 않는다). 외부 지도 서비스를 부르지 않는다 — 이미 DB 에 있는 구·동 표식과 좌표만 쓴다.
  동점이면 장소 번호 순이라 늘 같은 답이 나온다. **구 경계 가까이에서는 옆 구의 가게가 더 가까울 수 있다**(행정 구역이 아니라 가게까지의 거리로 고르므로) — 사용자가 다른 구 칩을 누르면 그대로 따른다
- **좌표는 이 계산에만 쓰고 저장도 기록도 하지 않는다.** 앱은 약 100m 단위(소수 셋째 자리)로 줄여 보낸다(`coarseCoordinate`)
- `certifiedSources` 의 낱말: 공공기관 `BUSAN_FOOD_INFO` · `MODEL_RESTAURANT` · `TAXI_DRIVER_PICK`, 카페 `KTO_CAFE` · `REDTABLE_FEATURED`, 현지인 설문 `LOCAL_SURVEY`. 소개·인증한 목록이 많은 곳이 먼저, 같으면 이름 순이다

## 되돌리기

전부 출처와 수집분이 찍힌다.

```sql
DELETE FROM place_feature WHERE source_type = 'TOURAPI' AND source_version = 'tourapi-busan-20260911';
DELETE FROM place         WHERE source_type = 'TOURAPI' AND dataset_version = 'tourapi-busan-20260911';
```

상가정보 2,355곳은 `source_type` 이 달라 안 걸린다.

### 유도값 되돌리기 (2026-09-17)

조용함·로컬성은 **자기 이름의 출처**로 들어가서 한 줄로 지워진다.

### 가격+narrative 되돌리기 (2026-09-22)

```sql
DELETE FROM place_feature WHERE source_type = 'RESEARCH_PRICE_NARRATIVE';
```

수집분(`dataset_version`)이 날마다 달라도 `source_type` 하나로 전부 걸린다 — 조사가
계속 자라는 산출물이라, 날짜별로 나눠 지우면 그날 이후 걸 놓친다.

```sql
DELETE FROM place_feature WHERE source_type = 'DERIVED_QUIETNESS';
DELETE FROM place_feature WHERE source_type = 'DERIVED_LOCALITY';
DELETE FROM place_feature WHERE source_type = 'DERIVED_SHADE';
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
