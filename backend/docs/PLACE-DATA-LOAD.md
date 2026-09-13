# 장소 자료 운영 적재 절차

관광공사·무장애 자료를 운영 DB 에 넣는 순서다. 적재기 넷은 전부 DB 에 붙어 도는
러너라 배포 호스트에서 돌려야 하고, 서로 순서가 있다.

`S15P21E201-854`·`-474`·`-852`·`-862` 의 마지막 단계다. 코드는 전부 `back/dev` 에
머지돼 있고 이 문서가 남은 절차다.

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

## 3. 적재 넷 — 순서를 지킨다

뒤 셋은 장소에 붙는 값이라 ①이 먼저다. 넷 다 두 번 돌려도 행이 안 는다.

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
```

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

## 적재 전에 정하면 싼 것

접근성 판정(`S15P21E201-862`)에 열린 질문이 둘 있다 — 휠체어를 문구 하나로 좁힌 것과
유모차 칸의 뜻. 바꾸려면 적재 전이 가장 싸다. 적재 뒤에 바꾸면 위 `DELETE` 로 지우고
다시 돌려야 한다.
