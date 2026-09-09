# GABOLLE (가볼래)

6인 팀의 부산 초개인화 여행 추천 서비스 GABOLLE(가볼래)와 팀 공용 개발 도구를 함께 관리하는 저장소입니다.

> 서비스 구현 기준은 [`docs/gabolle/`](docs/gabolle/)의 v1.1 문서 6종입니다. 과거 `LOCAL_ROUTE_*` 문서는 형식과 검토 이력을 위한 참고 자료이며, 서비스명·DB·인증·개인정보 정책의 기준으로 사용하지 않습니다.

## 여행 서비스 작업 영역

| 폴더 | 무엇 |
|---|---|
| `frontend/` | Expo(React Native), npm — 화면 |
| `backend/` | Spring Boot + Gradle, Java 17, PostgreSQL — 서버 |
| `bigData/` | 부산 이동성 데이터 — 공개 데이터 수집과 경사·소요시간 계산 |
| `docs/` | 협업·설계·운영 문서 |
| `ci/` · `governance/` | 파이프라인이 부르는 것과 팀의 합의 데이터 (연장통이지 주인이 아니다) |

```text
main
├─ front/main   → front/dev   → feat/front/{JIRA-KEY}-{설명}
├─ back/main    → back/dev    → fix/back/{JIRA-KEY}-{설명}
├─ bigData/main → bigData/dev → feat/bigData/{JIRA-KEY}-{설명}
└─ common/main  → common/dev  → chore/common/{JIRA-KEY}-{설명}
```

> 🔴 **한 칸씩만 올라갑니다.** 건너뛰는 MR 은 CI 가 빨갛게 만듭니다
> (`verify:mr-target` 잡). 예외는 `hotfix/…` 하나뿐이고, 그것도
> **`main` 자신이 고장 났을 때**를 위한 것입니다 — 이유는
> [docs/CI-RESILIENCE.md](docs/CI-RESILIENCE.md) 원칙 3.

협업 규칙은 [`docs/git-convention.md`](docs/git-convention.md)와 [`docs/jira-convention.md`](docs/jira-convention.md)를 따릅니다.
처음이면 [`docs/ONBOARDING.md`](docs/ONBOARDING.md) 부터 읽습니다.

비밀값은 커밋하지 않고 `.env.example` 에 변수명만 기록합니다.

---

## 공용 개발 도구: axMap

**여러 AI 에이전트가 한 저장소에서 동시에 일할 때,
사람이 그 작업을 이해하고 통제할 수 있게 하는 도구.**

이름 그대로 **AI Experience(ax) 의 지도**다 — 에이전트가 코드베이스에서 길을 찾고
서로를 피해 가는 경험을 만들고, 동시에 개발 환경의 **AI Transformation**,
즉 사람 혼자 쓰던 저장소를 여러 에이전트가 함께 쓰는 곳으로 바꾸는 일을 함의한다.

의존성 0. Node 20+ 와 git 만 있으면 돈다.

> 산문에서는 `axMap`, 명령·경로·패키지 이름에서는 `axmap` 을 쓴다.

---

## 무엇을 푸는가

AI 에게 코드를 시키면 두 가지가 동시에 어려워진다.

| | 공포 | 언제 |
|---|---|---|
| **F1** | 내가 이해 못 하는 코드가 계속 쌓인다 | 작업 **전** |
| **F2** | AI 가 뭘 망가뜨렸는지 모른다 | 작업 **후** |
| **F3** | 내가 잘 하고 있는지 모른다 | 계속 |

axMap 은 셋에 각각 답한다 — 처음 보는 코드베이스를 따라갈 **순서**,
변경이 어디까지 번졌는지 보는 **PR 화면**, 그리고 수천 개 저장소에서 학습한 **기준**.

그리고 그 밑에, 여러 에이전트가 같은 코드를 동시에 고치지 않게 하는
**git 위의 선점 프로토콜**이 있다.

---

## 5분 안에 써보기

```bash
git clone <이 저장소> && cd axmap

# 아무 저장소나 열어본다 — git 주소를 그대로 줘도 된다
node app/server.mjs https://github.com/pallets/flask 7777
```

`http://127.0.0.1:7777` 을 열면 다섯 걸음이 나온다.

```
① 이 저장소는 무엇을 하는 물건인가
② 실행은 어디서 시작하나
③ 그 다음에 무엇이 불리나
④ 어디가 활발하고 위험한가
⑤ 이제 당신 차례
```

**팀으로 쓰려면** → [docs/ONBOARD-TEAM.md](docs/ONBOARD-TEAM.md) (3줄이면 붙는다)

---

## 두 개의 층

### 1. 지도 — 코드가 실제로 어떻게 엮여 있나

정적 `import` 그래프와 **git 공변경**(함께 바뀐 이력)을 겹쳐 본다.
겹치지 않는 부분이 사고가 나는 자리다.

```
둘 다          import 있고 함께 바뀜        예상대로
숨은 결합      import 없는데 함께 바뀜      🔴 코드를 읽어서는 못 찾는다
안정된 경계    import 하지만 따로 바뀜
판정 보류      히스토리가 부족하다
```

**엣지마다 출처가 붙는다.** 어떻게 알아냈는지 모르는 선은 긋지 않는다.

### 2. 장부 — 지금 누가 어디를 잡고 있나

```bash
export AXMAP_AGENT=<자기이름>
node bin/axmap.mjs hook install

node bin/axmap.mjs claim src/auth --task T-12 --intent "토큰 만료 처리"
node bin/axmap.mjs status      # 누가 무엇을
node bin/axmap.mjs release     # 끝나면 즉시
```

git 은 **같은 줄**을 고쳐야 충돌을 안다. 그런데 더 위험한 것은
같은 기능을 앞에서부터·뒤에서부터 만드는 두 사람이다 — 경로가 안 겹쳐도 어긋난다.
장부는 그것을 코드를 쓰기 **전에** 터뜨린다.

두 개의 관문으로 지킨다 — git ref 의 원자적 갱신(CAS)과 순수 함수 판정.
불변식 I1~I7 은 [docs/INVARIANTS.md](docs/INVARIANTS.md) 에 있고,
모델 검증기·카오스 테스트·`axmap audit` 이 **같은 검사기**를 공유한다.

---

## 🔴 이 저장소가 지키는 규칙

이게 이 프로젝트의 성격을 가장 잘 말한다.

### 없는 것과 못 읽은 것을 같은 값으로 말하지 않는다

```
claims: []      ← "아무도 안 잡고 있다" 로 읽힌다
claims: null    ← "모른다" 다
```

맥락 없는 신입 에이전트에게 도구만 주고 저장소를 이해시키는 실험을 반복하는데,
**발견된 버그가 거의 전부 이 한 가지 모양이었다.** 코드는 대개 맞았고
*말하지 않은 것*이 문제였다. 한 신입은 `[]` 를 읽고 "부딪힐 사람 없음" 이라고
확신 있게 틀렸다.

### 애매하면 거부한다 (fail-closed)

같은 꼬리를 가진 파일이 둘이면 잇지 않는다. 표본이 모자라면 기준을 내지 않는다.
**틀리게 잇는 것보다 안 잇는 것이 낫다.**

### 지침에는 적용 조건이 붙는다

```
❌  함수는 20줄을 넘기지 마세요
✅  커밋 1,500~6,000 · Python 저장소 34개에서 관찰 (스냅샷 v418)
```

적용 조건 없는 지침은 점성술이다.

### 주석은 "왜" 를 쓴다

무엇은 코드가 이미 말한다. 이 저장소의 주석 대부분은 **그렇게 하지 않았을 때
실제로 무엇이 깨졌는지**를 적고 있다.

---

## 기준 (SSOT) — 우리 숫자에 근거를 붙인다

도구의 임계값이 전부 사람이 눈으로 고른 상수였다. 그래서 공개 저장소를
돌면서 그 상수를 **분포**로 바꾼다.

🔴 **"인기 있는 걸 따라해라" 가 아니다.** 별 개수는 품질의 증거가 아니고,
저장소 7,107개로 실제로 확인했다 —

| 별 | 재수정률 |
|---|---|
| 0~2,000 | 0.081 |
| 2,000~6,000 | 0.150 |
| 6,000~20,000 | 0.103 |
| 20,000~ | 0.149 |

단조 관계가 없다. 그래서 인기 대신 **결과**를 센다 — *그 파일이 나중에
얼마나 고쳐졌나*. 그리고 관계가 없으면 **지침을 내지 않는다.**

자세히 → [docs/WHY-CORPUS.md](docs/WHY-CORPUS.md)

---

## 검증

```bash
npm test              # 순수 로직 + 모델 검증기
npm run demo          # 에이전트 9명 동시 작업
npm run demo:compare  # git 텍스트 충돌 vs 파서 판정
npm run demo:chaos    # 진짜 경합 (무작위 순서)
npm run smoke         # 🔴 화면이 실제로 뜨는지 헤드리스 크롬으로
```

`smoke` 가 따로 있는 이유 — 문법이 멀쩡하고 테스트가 전부 초록인데
브라우저에서는 아무것도 안 뜬 적이 두 번 있었다.
**되는 길만 보면 안 되는 길은 영원히 안 보인다.**

---

## 문서

### 이 저장소

| | |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | 작업 규칙 (사람에게도 AI 에게도 같다) |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | clone 부터 첫 작업까지 |
| [docs/HANDOVER.md](docs/HANDOVER.md) | 다른 PC·다른 사람이 이어받을 때 |
| [docs/CI.md](docs/CI.md) | 파이프라인·러너·봇 토큰 |
| [docs/CI-RESILIENCE.md](docs/CI-RESILIENCE.md) | **MR 이 한 달 내내 도는 조건** — 2026-08-26 사고에서 나온 원칙 |
| [docs/git-convention.md](docs/git-convention.md) · [docs/jira-convention.md](docs/jira-convention.md) | 브랜치·커밋·이슈 규약 |
| [bigData/CLAUDE.md](bigData/CLAUDE.md) | 부산 이동성 데이터 — 하지 않는 것부터 |
| [bigData/docs/FIELD-STUDY.md](bigData/docs/FIELD-STUDY.md) | 현장 실험 설계 — 투표인가 텔레메트리인가, 모델은 언제 |

### axMap 저장소로 나간 문서

`SPEC` · `DECISIONS` · `INVARIANTS` · `WHY-CORPUS` · `PERSONA-LOOP` 는
2026-08-26 에 axMap 이 분리되면서 함께 나갔다 —
[`rleaderjoon/axmap`](https://lab.ssafy.com/rleaderjoon/axmap) 의 `docs/` 에 있다.
**이 저장소에는 axMap 이 아예 없다** — npm 꾸러미 `axmap-cli` 를 각자 깔아서 쓴다
([CONTRIBUTING.md](CONTRIBUTING.md) 0.3 · [docs/AXMAP-NPM-MIGRATION.md](docs/AXMAP-NPM-MIGRATION.md)).

---

## 아직 안 된 것 (숨기지 않는다)

**2026-08-27 기준.** axMap 쪽 미해결은 [그 저장소](https://lab.ssafy.com/rleaderjoon/axmap)에 있다.

- 🔴 **`Pipelines must succeed` 가 꺼져 있다.** 그래서 CI 검사 넷은 지금
  **아무것도 막지 않는다.** 켜는 순서와 조건은
  [docs/CI-RESILIENCE.md](docs/CI-RESILIENCE.md) 원칙 6.
- 🔴 **러너가 한 대뿐이고 개인 PC 에 있다.** 그 PC 가 꺼지면 CI 가 멈춘다.
  각자 `bash ci/runner-up.sh` 로 하나씩 띄우는 것이 목표다.
- **`bigData` 파트가 아직 `main` 에 없다.** `feat/S15P21E201-9-bigdata-bootstrap`
  이 오래된 지점에서 갈라져 있다. 올릴 때 `main` 을 먼저 머지해야 한다.
- **현장 실험 응답이 아직 0건이다.** 문항과 추정기는 있고 검사도 통과하지만,
  계수는 사람에게 물어야 나온다 — [bigData/docs/FIELD-STUDY.md](bigData/docs/FIELD-STUDY.md).
- **BIMS 폴링이 노선 5개뿐이다.** 일일 트래픽 한도에서 역산한 수이고, 고른 기준은
  배차간격이다. focus(중구·동구)를 실제로 지나는 노선으로 좁히는 것은 아직 안 했다.
- **고도 타일 738개를 못 읽었다** (`tilesMissing`). 받기는 3,127개 다 받았는데
  경사 계산에서 1,842개만 로드됐다. 왜 벌어진 차이인지 아직 안 봤다.
- **팀원 셋이 아직 `governance/policy.json` 명단에 없다.** 이 저장소에 커밋이
  없어 이메일을 모른다 — 투표는 커밋 저자 이메일로 대조하므로 **지어내면 그 사람이
  영영 투표를 못 한다.** 첫 커밋 뒤에 추가한다.

---

## 라이선스

MIT. 마음대로 쓰고 고치고 팔아도 된다 — 저작권 표시만 남기면 된다.
[LICENSE](LICENSE)

---

<sub>SSAFY 프로젝트로 시작했다. 한국어로 쓰고 한국어로 생각한다.</sub>
