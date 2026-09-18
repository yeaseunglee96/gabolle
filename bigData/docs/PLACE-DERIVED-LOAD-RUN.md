# 유도값 운영 적재 실행표 — 조용함 · 로컬 · 그늘

**축 셋이 한 번에 산다.** 그늘만 따로 넣지 않는다.

이 문서는 **붙여 쓰는 표**다. 손으로 고쳐야 할 자리가 없게 적었다.
절차의 근거와 설계 이유는 [`backend/docs/PLACE-DATA-LOAD.md`](../../backend/docs/PLACE-DATA-LOAD.md)
에 있고(그 문서는 `back/dev` 에 있다), 여기는 **이번에 돌릴 것만** 추린 것이다.

> 🔴 **이 문서는 아직 안 돌렸다.** 사람이 실행 직전에 사용자 확인을 받고 누른다.
> 아래 「잰 것과 못 잰 것」을 먼저 읽는다 — 어느 숫자를 믿을 수 있는지가 거기 있다.

---

## 🔴 0. 순서를 거꾸로 하지 않는다 — **적재가 먼저, 배포가 나중**

그늘 취향 어휘를 읽는 서버 수정(`S15P21E201-1222` · MR `!1146`)이 **배포돼도 운영에
`SHADE_SCORE` 가 0건이면 화면은 안 바뀐다.** 그러면 경사 때와 똑같이
*「올렸는데 왜 그대로지」* 를 찾으러 가게 된다.

| | 하는 일 | 안 하는 일 |
|---|---|---|
| **배포** | 적재기를 운영에 올려 둔다 | **아무것도 안 넣는다** |
| **적재**(이 문서) | 값을 넣는다 | 코드를 안 바꾼다 |

그리고 **이미 만들어진 여행은 안 바뀐다.** 추천 후보는 만들 때 점수째로 저장되므로,
확인하려면 **새 여행을 하나 만들어야 한다.** 기존 여행을 열어 보는 것으로는 영원히
안 바뀐 것처럼 보인다.

---

## 🔴 1. 잰 것과 못 잰 것

아래 기대값이 어디서 왔는지 갈라 적는다. **섞으면 틀린 것을 믿게 된다.**

### 🟢 내가 이 저장소에서 직접 잰 것 (2026-09-18)

- **산출물 줄 수** — `origin/bigData/dev` 의 blob 을 그대로 셌다 (아래 2절 표)
- **기대값 도구의 출력** — `node process/expected-feature-rows.mjs` 를 돌렸고
  **자체 검산을 통과했다**: 도구가 계산한 경사 2,682 가 운영 실측 2,682 와 같다.
  경사가 안 맞으면 도구는 **숫자를 안 내놓고 멈춘다**
- **그늘 상가분이 붙을 자리가 있는가** — 그늘 상가 1,749개 열쇠가 조용함 상가
  2,355개 열쇠 **안에 전부 들어 있다(밖에 있는 것 0개)**. 조용함 상가는 2,355/2,355 가
  붙은 것이 확인돼 있으므로, 그늘 상가도 같은 길로 붙는다
- **산출물의 값 범위** — 그늘 0.000~99.900. 0~100 눈금이 맞다

### 🟡 다른 컴퓨터에서 잰 것 (인계 문서, 2026-09-18 새벽)

로컬 DB 리허설 결과다. **내가 본 것이 아니다** — 이 PC 에는 그 DB 가 없다.

- 조용함·로컬은 예측대로 들어갔다
- 그늘 **관광공사분 187** 도 들어갔고 눈금(0~0.998)도 맞았다
- 🔴 그늘 **상가분 1,749 는 안 돌았다.** 기계 메모리가 모자라 **시작을 못 했다** —
  실패한 것이 아니라 안 돌린 것이다

### 🔴 확인 못 함

| | 왜 |
|---|---|
| 운영에서 실제로 도는가 | 운영 DB 를 안 건드렸다 |
| 그늘 상가 1,749 가 실제로 붙는가 | 열쇠로는 붙을 자리가 있는 것까지만 확인했다. **적재기를 돌려 본 것이 아니다** |
| 경사가 이미 운영에 2,682 곳 있는가 | 인계 문서의 9/17 실측이고 내가 센 것이 아니다 |

---

## 2. 파일 꺼내기 — 🔴 `back/dev` 에는 **하나도 없다**

산출물은 **`bigData/dev` 에만** 있다. 실측으로 확인했다 —
`origin/bigData/dev` 에 16개, **`origin/back/dev` 에 0개**.
배포 이미지 안에도 없고 앞으로도 안 들어간다. 그래서 이 단계를 반드시 지난다.

| 파일 | 줄 | 쓰는 적재기 |
|---|---|---|
| `place-slope.ndjson` | 2,213 | 경사 (관광공사) |
| `place-quietness.ndjson` | 654 | 조용함 (관광공사) |
| `place-quietness-sbiz.ndjson` | 2,355 | 조용함 (상가) |
| `place-locality.ndjson` | 498 | 로컬 (관광공사) |
| `place-locality-sbiz.ndjson` | 2,230 | 로컬 (상가) |
| `place-shade.ndjson` | 363 | 그늘 (관광공사) |
| `place-shade-sbiz.ndjson` | 1,749 | 그늘 (상가) |

```bash
git fetch origin bigData/dev
mkdir -p /tmp/load
for f in place-slope place-quietness place-quietness-sbiz place-locality place-locality-sbiz \
         place-shade place-shade-sbiz; do
  git show origin/bigData/dev:bigData/data/staged/$f.ndjson > /tmp/load/$f.ndjson
done
wc -l /tmp/load/*.ndjson    # 위 표와 같아야 한다
```

> 🔴 **상가 경사 파일은 안 꺼낸다.** 상가 2,355곳의 경사는 마이그레이션
> (`V20260916230000__place_slope_sbiz.sql`)이 이미 넣었다. 경사 적재기는 관광공사
> 열쇠만 읽는다.

---

## 3. 서버에 올리고 DB 접속값을 빌린다

```bash
PEM=<각자 받은 .pem>
HOST=ubuntu@j15e201.p.ssafy.io
ssh -i $PEM $HOST 'mkdir -p /home/ubuntu/load'
scp -i $PEM /tmp/load/*.ndjson $HOST:/home/ubuntu/load/
```

접속 정보가 Jenkins 자격증명이라 파일에 없다. **도는 `backend` 컨테이너에서 빌린다.**

```bash
ssh -i $PEM $HOST
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
wc -l /tmp/load.env    # 네 줄이면 정상
```

🔴 `/tmp/load.env` 에 **DB 비밀번호가 들어 있다.** 6절에서 지운다.

---

## 4. 적재 — 줄 그대로 붙여 쓴다

```bash
VER=tourapi-busan-20260911
IMG=local-route-backend:candidate
NET=local-route-personalization_data_net
RUN="docker run --rm --network $NET --env-file /tmp/load.env \
  -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \
  -v /home/ubuntu/load:/load $IMG"
```

`GABOLLE_JWT_SECRET` 은 임시값이다. 빌린 네 줄에 이 값이 없는데 기동 검사가 32자 이상을
요구해서, 없으면 앱이 뜨지도 못하고 죽는다. **적재기는 토큰을 안 만들므로 운영의 진짜
값을 가져올 필요가 없다.**

> 🔴 **`--gabolle.place.loader.dataset-version` 을 빼면 컨테이너가 죽는다.**
> 조용히 건너뛰는 것이 아니라 `IllegalStateException` 으로 기동에서 멈춘다.
> 어느 산출물로 만든 값인지 안 적으면 그 값으로 만든 추천을 나중에 못 되짚기 때문이고,
> **일부러** 그렇게 돼 있다.

### 🔴 단계마다 컨테이너를 내린다

적재기는 **일을 마쳐도 안 꺼진다.** 절차서가 「마침 줄 보이면 `Ctrl+C`」라고만 적어 둬서,
리허설에서 컨테이너 둘이 끝내고도 **36분·4분씩 떠 있었다.** 자바 하나가 수백 MB 고 이
기계를 여럿이 쓴다 — 그래서 **뒤에서 돌던 작업 둘이 메모리 부족으로 정리됐고 그중 하나가
리허설의 마지막 단계였다.**

이것을 고치는 MR `!1133` 은 **아직 안 머지됐다.** 그러니 **한 명령이 끝날 때마다**
마침 줄을 보고 `Ctrl+C` 로 내린 뒤 다음으로 간다. 두 개를 동시에 띄우지 않는다.

```bash
# ⑥ 경사 (관광공사만 — 상가는 마이그레이션이 이미 넣었다)
$RUN --gabolle.place.loader.place-slope=/load/place-slope.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-16-slope-r200

# ⑦ 조용함 — 파일 둘을 같은 속성으로 한 번씩
$RUN --gabolle.place.loader.place-quietness=/load/place-quietness.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-quietness-r200
$RUN --gabolle.place.loader.place-quietness=/load/place-quietness-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-quietness-r200

# ⑧ 로컬
$RUN --gabolle.place.loader.place-locality=/load/place-locality.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-locality-r300
$RUN --gabolle.place.loader.place-locality=/load/place-locality-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-locality-r300

# ⑨ 그늘
$RUN --gabolle.place.loader.place-shade=/load/place-shade.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-shade-r500
$RUN --gabolle.place.loader.place-shade=/load/place-shade-sbiz.ndjson \
     --gabolle.place.loader.dataset-version=2026-09-17-shade-r500
```

> **반경 숫자(`r200`·`r300`·`r500`)는 지어낸 것이 아니다.** 산출물 옆의 실행 기록
> (`bigData/data/staged/_place-shade-run/_run-process-place-shade.json` 등)에
> `"radiusM"` 으로 적혀 있다.
>
> **전부 두 번 돌려도 행이 안 는다.** 다시 돌리는 것은 안전하다.

---

## 5. 로그에서 볼 숫자

적재기가 명령마다 이런 줄을 찍는다.

```
장소 조용함 적재를 마쳤다 — 줄 654 · 넣음 326 · 장소가 없어 못 넣음 328 · 이미 있어 건너뜀 0
```

### 기대값

| 명령 | 줄 | 넣음 | 장소가 없어 못 넣음 | 근거 |
|---|---|---|---|---|
| ⑦ 조용함 관광공사 | 654 | **326** | **328** | 🟡 리허설 로그 실물 |
| ⑦ 조용함 상가 | 2,355 | **2,355** | **0** | 🟢 도구(검산 통과) |
| ⑧ 로컬 관광공사 | 498 | **239** | **259** | 🟢 도구 |
| ⑧ 로컬 상가 | 2,230 | **2,230** | **0** | 🟢 도구 |
| ⑨ 그늘 관광공사 | 363 | **187** | **176** | 🟡 리허설에서 실제로 들어갔다 |
| ⑨ 그늘 상가 | 1,749 | **1,749** | **0** | 🔴 **확인 못 함** — 아래 |
| ⑥ 경사 관광공사 | 2,213 | 🔴 **확인 못 함** | — | 이미 들어가 있으면 「이미 있어 건너뜀」으로 샌다 |

**축별 합계 기대** — 조용함 **2,681** · 로컬 **2,469** · 그늘 **1,936**.

> 🔴 **그늘 상가 1,749 만 성격이 다르다.** 리허설에서 이 한 칸이 **안 돌았다**(메모리
> 부족으로 시작을 못 했다). 열쇠로는 붙을 자리가 있는 것까지 확인했지만
> **적재기를 돌려 본 것이 아니다.** 여기서 처음 도는 것이라고 보고 로그를 본다.

> 🔴 **경사는 이미 운영에 2,682 곳 있다고 인계 문서가 말한다(9/17 실측, 내가 센 것이
> 아니다).** 그것이 사실이면 ⑥ 은 새로 넣을 것이 없다 — 기대값 도구가 관광공사 경사
> 2,213줄 중 **327줄이 붙는다**고 계산했으므로, 「넣음 0 · 이미 있어 건너뜀 327 ·
> 못 넣음 1,886」 언저리가 된다. **이건 계산이지 실측이 아니다.**
>
> **이 줄을 예외로 외울 필요는 없다.** 아래 「실패 판정」의 네 줄이 그대로 답한다 —
> `넣음 0 · 건너뜀 큼` 은 **이미 들어가 있다**는 뜻이고 정상이다.
> 반대로 ⑥ 에서 「넣음」이 크게 나오면 **운영에 경사가 없었다는 뜻**이고, 그때는
> 인계 문서의 2,682 쪽을 의심한다.

### 🔴 실패 판정 — 네 줄이면 전부 가려진다

로그 줄의 네 숫자는 **서로 더해진다.**

```
줄 = 넣음 + 장소가 없어 못 넣음 + 이미 있어 건너뜀
```

리허설 로그 실물로 검산된다 — `654 = 326 + 328 + 0`.
그러니 **「넣음」 하나만 보면 안 된다.** 아래 네 줄로 가린다.

```
🔴 실패 :  넣음 + 이미 있어 건너뜀 == 0    → 아무것도 안 붙었다
🟢 정상 :  못 넣음이 크다                  → 그 장소가 운영 DB 에 없다
🟢 정상 :  못 넣음이 0                     → 전부 붙었다 (상가 파일이 이 모양)
🟢 정상 :  넣음 0 인데 건너뜀이 크다       → 이미 들어가 있다 (다시 돌린 것)
```

🔴 **「넣음 0」이 곧 실패가 아니다.** 전부 두 번 돌려도 행이 안 늘고, 그때는
「넣음 0 · 건너뜀 큼」이 나온다 — **다시 돌리는 것은 안전하다.** 실패는 **붙은 것이
하나도 없을 때**이고, 그것은 `넣음 + 건너뜀 == 0` 일 때뿐이다.

🔴 **「못 넣음 0」을 실패로 읽지 않는다.** 그건 **제일 좋은 결과**다 — 그 파일의 장소가
운영 DB 에 전부 있었다는 뜻이고, 상가 파일 셋이 바로 이 모양이어야 한다(2,355 / 2,230 / 1,749).
거꾸로 읽으면 정확히 반대가 된다.

그 밖에 눈여겨볼 것 둘.

| 보이는 것 | 뜻 |
|---|---|
| 「못 넣음」이 기대보다 **훨씬** 크다 | 앞 단계(① 장소 적재)를 안 돌린 것이다. 관광공사 656곳 중 음식 328곳은 장소 적재기가 **일부러 안 넣으므로** 기대만큼 큰 것은 정상이다 |
| 관광공사분만 들어가고 상가분이 0 | **두 줄 중 하나를 안 돌렸다.** ⑦⑧⑨ 는 파일을 **둘씩** 돌린다 |

---

## 6. 눈금 확인 — 🔴 넣은 **뒤에도** DB 로는 볼 수 있다

```sql
select feature_type,
       min((value->>'score')::numeric) as 최소,
       max((value->>'score')::numeric) as 최대,
       count(*) as 행
from place_feature
where feature_type in ('SLOPE_PERCENT','QUIETNESS_SCORE','LOCALITY_SCORE','SHADE_SCORE')
group by feature_type order by feature_type;
```

**🟡 리허설에서 실제로 나온 값** (다른 컴퓨터, 로컬 DB). **행 수를 섞어 읽지 않는다** —
그늘은 상가분을 안 돌려서 187 이다.

```
LOCALITY_SCORE    0.0010 ~ 1.0000     2469
QUIETNESS_SCORE   0.0000 ~ 0.9500     2681
SHADE_SCORE       0.0000 ~ 0.9980      187   ← 🔴 상가분을 안 돌린 수다. 눈금만 맞다
SLOPE_PERCENT     0.0000 ~ 44.8000    2682
```

운영에서는 상가분까지 넣으므로 **그늘 행이 1,936 이어야 한다.** 그 숫자는 리허설에서
나온 것이 아니라 **기대값**이다 — 위 187 과 같은 줄에 놓고 읽지 않는다.

> 🔴 **경사만 0~100 이고 나머지 셋은 0~1 인데, DB 에서는 넷 다 같은 `score` 칸이다.**
> 채점기가 경사만 100 으로 나눠 맞춘다. **한 줄로 전부 세면 눈금이 섞인 표가 나오므로**
> 경사는 따로 읽는다.
>
> 🔴 **최댓값이 0.01 언저리로 나오면 두 번 나뉜 것이다.** 산출물이 이미 0~1 인데
> 적재기가 또 100 으로 나눈 경우인데, **범위 검사를 통과해 버려서 아무 오류도 안 난다.**
> 그 축은 순위에서 사실상 사라진다. 그늘 산출물의 실측 범위는 **0.000~99.900** 이므로
> (내가 쟀다) 적재 뒤 `SHADE_SCORE` 의 최댓값은 **0.999 언저리**여야 한다.

---

## 7. 되돌리기 — 넣은 것만 지운다

조용함·로컬·그늘은 **자기 이름의 출처**로 들어가서 한 줄씩 지워진다.

```sql
DELETE FROM place_feature WHERE source_type = 'DERIVED_QUIETNESS';
DELETE FROM place_feature WHERE source_type = 'DERIVED_LOCALITY';
DELETE FROM place_feature WHERE source_type = 'DERIVED_SHADE';
```

> 🔴 **경사는 이렇게 못 지운다.** 경사 적재기는 `source_type` 을 `TOURAPI` 로 넣어서
> 관광공사 수집본에서 실제로 온 표식들과 **한 덩어리**가 된다. 종류까지 걸어야 한다.
>
> ```sql
> DELETE FROM place_feature WHERE feature_type = 'SLOPE_PERCENT' AND source_type = 'TOURAPI';
> -- 상가 경사는 마이그레이션이 넣었다 — source_version 으로 가른다
> DELETE FROM place_feature WHERE source_version = '2026-09-16-slope-r200-sbiz';
> ```

---

## 8. 뒷정리

```bash
rm -f /tmp/load.env     # 🔴 DB 비밀번호가 들어 있다
exit
```

---

## 9. 끝났는지 어떻게 아나

1. 6절 SQL 의 행 수가 **조용함 2,681 · 로컬 2,469 · 그늘 1,936** 근처인가
2. 그늘 최댓값이 **0.999 언저리**인가 (0.01 언저리면 두 번 나뉜 것)
3. 그늘 취향이 실제로 쓰이는가 — **새 여행을 하나 만들고**
   `recommendation_candidate.score_components` 의
   `preferenceAlignment.dimensions` 안에 `SHADE_PREFERENCE` 가 있는지 본다
   (🔴 서버 수정 `!1146` 이 배포된 뒤라야 한다. 그 전에는 없는 것이 정상이다)

**1·2 는 적재만으로 확인된다. 3 은 배포까지 끝나야 한다.**
