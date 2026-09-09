# 인기 신호 — 부산에서 무엇이 맛집을 만드는가

**S15P21E201-731**

이 폴더는 데이터를 모으는 곳이 아니라 **우리만의 선정 기준을 찾는 곳**이다.
남기는 것은 **발견**이지 원본이 아니다.

| 남긴다 (`findings/`) | 안 남긴다 |
|---|---|
| 신호별 판별력(AUC)·상관·계수 | 🔴 수집 원본 |
| "무엇이 맛집을 만드는가" 서술 | 🔴 가게별 평점·리뷰수 표 |
| 커버리지·표본 통계 | 🔴 리뷰 본문·작성자·사진 |

결론은 [`bigData/docs/POPULARITY-SIGNAL.md`](../../docs/POPULARITY-SIGNAL.md) 에 있다.

---

## 🔴 파기 — 원본을 지우는 명령

```bash
node bigData/eval/popularity/destroy-raw.mjs
```

`~/Desktop/raw-popularity-2026-09-07/` 을 통째로 지운다. 가게별 목록이 거기 말고
다른 곳에 없어서, 이 한 줄이 파기의 전부다.

**이 스크립트는 수집 코드보다 먼저 만들었다.** 파기를 나중에 붙이면 그건 약속이지
구조가 아니다 — 약속은 잊히고, 구조는 잊혀도 남는다.

폴더를 옮기려면 `POPULARITY_RAW_DIR` 환경변수를 준다. 파기도 같은 곳을 본다.

---

## 순서

```bash
R=/c/Users/rlead/Desktop/git/S15P21E201                 # 상가정보·지하철 원본이 있는 곳
T=/c/Users/rlead/Desktop/git/S15P21E201-wt-data          # 정답지 붙임표가 있는 곳 (S15P21E201-712)

node bigData/eval/popularity/01-probe.mjs
node bigData/eval/popularity/02-sample.mjs   --repo "$R" --truth-repo "$T"
node bigData/eval/popularity/03-signals.mjs  --repo "$R" --truth-repo "$T"
node bigData/eval/popularity/destroy-raw.mjs
```

| 파일 | 하는 일 | 내놓는 것 |
|---|---|---|
| `01-probe.mjs` | **가져와도 되는가**를 먼저 묻는다 — `robots.txt` 원문과 공식 API 실측 | `findings/01-source-verdict.json` |
| `02-sample.mjs` | 표본 508곳을 뽑는다 (정답 158 + 업종 맞춘 무작위 350) | 가게별 목록은 **저장소 밖**, 분포만 `findings/02-sample-stats.json` |
| `03-signals.mjs` | 판별력·겹침·쏠림을 잰다 | `findings/03-signals.json` |
| `destroy-raw.mjs` | 원본을 지운다 | — |

`--repo` 와 `--truth-repo` 가 갈라져 있는 것은 **임시**다. 정답지 붙임표
(S15P21E201-712, 브랜치 `feat/bigData/S15P21E201-712-candidate-pool`)가
`bigData/dev` 로 머지되면 한 경로로 합쳐진다.

---

## 지킨 규칙

1. **표본 500곳부터.** 전체를 긁지 않는다
2. **요청 사이 3초, 동시 요청 1개.** 429(**너무 자주 왔다**)를 받으면 20초 쉬고 **한 번만** 더 묻는다
3. **`robots.txt` 를 먼저 읽는다.** 원문을 그대로 `findings/01-source-verdict.json` 에 적어 둔다
4. **숫자만 가져온다.** 리뷰 본문·작성자·프로필·사진은 대상이 아니다
5. **취득 기록을 남긴다** — 언제·어디서·몇 건·어떤 칸 (`findings/01-source-verdict.json` 의 `취득기록`)
6. **막히면 뚫지 않는다.** 이 폴더에는 사람인 척하는 코드도, 프록시도, 우회 재시도도 없다 —
   **없는 것이 결과다**

### 🔴 판정용 시험지는 열지 않았다

맛 정답지 158곳은 **조정용 111곳 / 판정용 47곳**으로 이미 갈라져 있다
(`r4-split-tune.json` · `r4-split-holdout.json`).

이 폴더의 계산은 **전부 조정용 111곳으로만** 했다. `paths.mjs` 는 판정용 파일의
경로를 **아예 적지 않는다** — 실수로도 못 열게 하려고 그렇게 뒀다.
