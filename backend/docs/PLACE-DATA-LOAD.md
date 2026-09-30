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
