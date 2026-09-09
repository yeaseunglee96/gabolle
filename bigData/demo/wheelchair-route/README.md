# 휠체어 경로 판정 데모 — 좌수영교 → 신세계 센텀시티

같은 출발지·도착지로 경로 **두 개**를 그린다.

| 경로 | 무엇인가 |
|---|---|
| **최단거리** | 판정을 무시하고 거리만 본다. 사람이 보통 받는 경로다 |
| **휠체어 최선** | 온톨로지가 **불가**로 판정한 구간을 아예 지나지 않는 가장 짧은 길 |

선 하나를 클릭하면 **왜 그 색인지**(판정 이유·경사·길이·way id)가 옆 패널에 나온다.
이 데모의 핵심은 색이 아니라 **이유를 말할 수 있다는 것**이다.

---

## 🔴 띄우는 법 — 포트는 5173 하나로 고정이다

```bash
# 1. 경로를 만든다 (route.geojson 이 생긴다)
node bigData/process/wheelchair-route.mjs

# 2. 정적 서버를 띄운다 — 포트는 반드시 5173
npx serve -l 5173 bigData/demo/wheelchair-route
```

그리고 브라우저에서 **`http://localhost:5173`** 을 연다.

> 🔴 **`file://` 로 열면 지도가 뜨지 않는다.**
> 카카오 지도는 앱키에 등록된 **도메인**에서만 뜨는데, `file://` 에는 등록할 도메인이
> 없다. `fetch` 로 `route.geojson` 을 읽는 것도 막힌다. 반드시 위 서버로 연다.

> 🔴 **포트를 바꾸지 않는다.** 카카오 개발자 콘솔에 등록하는 주소와 실제로 여는 주소가
> **글자 하나까지 같아야** 한다. 다르면 지도가 조용히 안 뜨고, 그때 원인을 찾기 어렵다.
> 그래서 이 팀은 `http://localhost:5173` 하나로 정했다.

---

## 앱키 — 커밋하지 않는다

1. [카카오 개발자 콘솔](https://developers.kakao.com/console/app) 에서 앱을 만들고
   **JavaScript 키**를 복사한다
2. 같은 앱의 **플랫폼 → Web → 사이트 도메인**에 `http://localhost:5173` 을 **그대로** 등록한다
3. 키를 파일에 넣는다

```bash
cp bigData/demo/wheelchair-route/config.example.js \
   bigData/demo/wheelchair-route/config.local.js
# 그리고 config.local.js 를 열어 kakaoAppKey 를 채운다
```

`config.local.js` 는 이 폴더의 `.gitignore` 에 들어 있어 커밋되지 않는다.
파일이 없으면 화면이 **만드는 법을 그대로 띄운다** — 빈 화면이 나오지 않는다.

---

## 🔴 이 데모의 한계 — 숨기지 않는다

**지금 "가능" 으로 나오는 구간은 하나도 없다.** 버그가 아니라 온톨로지의 현재 상태다.

온톨로지(`bigData/config/ontology.jsonld`)에 선언된 가능판정 규칙은 **전부
`verdict: infeasible`**, 즉 *"이러면 못 간다"* 뿐이다. *"이러면 갈 수 있다"* 를
선언하는 규칙이 하나도 없다. 통과 가능을 확인해 줄 수 있는 유일한 규칙인
**유효폭 미달**(`bm:RuleWheelchairWidth`)은 입력인 `bm:widthM` 이 아직
수집되지 않아 `evidenceStatus: 입력미수집` 으로 멈춰 있다.

그래서 판정은 실질적으로 **불가 아니면 미상** 둘뿐이다.

**미상을 가능으로 기본값 주지 않는다.** 보도가 있다고 말했는데 없으면
사용자가 차도를 걷게 된다. 모르면 회색 점선으로 "모른다" 고 말한다.
유효폭이 수집되면 `--route-ok` 토큰이 그때 쓰이기 시작한다.

---

## 🔴 디자인 시스템 — `tokens.css` 하나만 갈아끼우면 된다

색·굵기·간격·글꼴이 **전부 `tokens.css` 의 `:root` 한 곳**에 있다.
`index.html` 에는 색 리터럴(`#ff0000` 같은 것)이 **한 개도 없다.**
자바스크립트가 지도에 선을 그릴 때도 `getComputedStyle` 로 토큰을 읽어 쓴다.

토큰 이름은 **의미로** 지었다 — `--red` 가 아니라 `--route-blocked` 다.
디자인 시스템이 *"불가는 주황"* 이라고 정하면 **이름은 그대로 두고 값만 바꾼다.**

| 판정 | 색 토큰 | 형태 토큰 | 기본값 |
|---|---|---|---|
| 가능 | `--route-ok` | `--route-style-ok` | 초록 · 실선 |
| 불가 | `--route-blocked` | `--route-style-blocked` | 붉은색 · 실선 |
| 미상 | `--route-unknown` | `--route-style-unknown` | 회색 · **점선** |

미상은 색만으로 말하지 않고 **점선이라는 형태로도** 말한다 — 색각 이상이 있는
사람에게 회색과 초록의 구분은 어렵다.

`tokens.css` 에는 이 밖에도 굵기(`--route-width…`)·끝점(`--endpoint-…`)·
바탕과 글자(`--surface…` `--text-…`)·간격(`--space-1…6`)·모서리(`--radius-…`)·
글꼴(`--font-…`) 토큰이 있고, 어두운 화면용 값도 같은 파일에서 토큰만 다시 정의한다.

---

## 이 폴더의 파일

| 파일 | 무엇인가 |
|---|---|
| `index.html` | 화면 전부. 색 리터럴 없음 |
| `tokens.css` | 🔴 디자인 토큰. 갈아끼우는 대상은 이 파일이다 |
| `config.example.js` | 앱키 파일의 본. 복사해서 쓴다 |
| `config.local.js` | 내 앱키. **커밋되지 않는다** (`.gitignore`) |
| `route.geojson` | `process/wheelchair-route.mjs` 가 만든다 |

## 관련 코드

| 파일 | 무엇인가 |
|---|---|
| `bigData/process/wheelchair-verdict.mjs` | 구간 판정기. 규칙과 임계값을 **온톨로지에서 읽는다** |
| `bigData/process/wheelchair-route.mjs` | 그래프를 만들고 경로 둘을 찾는다 |
| `bigData/test/verify-wheelchair.mjs` | 검사. 종료 코드 0 이면 통과 |
