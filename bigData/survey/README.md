# 현장 설문 — 완료 코드로 왕복하기

해운대·광안리 현장에서 짝 비교 응답을 받는 방법(S15P21E201-190)의 실무 절차다.
참가자는 자기 폰으로 답하고 우리는 그 폰을 다시 못 본다 — 완료 화면에 뜨는
**완료 코드**(문자열 하나)만 진행요원이 종이에 옮겨 적어 가져온다.

관련 스토리: S15P21E201-190 · S15P21E201-404(현장 수집) · S15P21E201-409(해독) ·
S15P21E201-499(적재) · S15P21E201-414(계수 추정)

---

## 0. 왕복 경로

```
[참가자 화면]                [진행요원]           [이 폴더]
문항 답변 → 완료 코드 표시  →  종이에 옮겨 적음  →  decode.mjs   → 세션 ndjson
                                                  →  load.mjs     → responses.ndjson
                                                                  → (process/choice-fit.mjs 가 읽는다)
```

이 폴더는 **가운데부터 오른쪽**만 다룬다. 완료 화면(참가자 쪽 웹 화면) 자체는
별도 티켓의 몫이다 — 이 문서는 그 화면이 만들어야 할 **완료 코드의 정확한 모양**을
정해 둔다.

---

## 1. 완료 코드에 담는 것 / 안 담는 것

| 담는다 | 안 담는다 |
|---|---|
| 무작위 세션 ID (신원과 무관) | 이름 · 연락처 |
| 문항별 응답(A/B) | 위치 궤적 · GPS |
| — | 기기 식별자 |

세션 ID 는 참가자를 구별하기 위한 것이지 **참가자가 누구인지 알기 위한 것이
아니다.** `codec.mjs`의 `randomSessionId()`가 매 세션 새로 뽑는다.

---

## 2. 문항 설계와의 관계

`process/choice-design.mjs`가 만드는 `data/staged/choice-design.json`의
`sets` 배열 순서가 곧 완료 코드 응답열의 자리다. **회차마다 시드를 바꾸지
않는 이유**(`choice-design.mjs` 주석 참고)가 여기서도 그대로 적용된다 — 시드가
바뀌면 자리 배정도 바뀌어 옛 코드를 더 이상 해독할 수 없다.

함정 문항(의도적으로 답이 뻔한 문항 — 집중해서 답하고 있는지 확인용)은 지금
`choice-design.mjs`에 없다. 생기면 `design.traps` 배열로 추가하고, 완료 코드의
응답열 뒤에 그 순서대로 이어 붙인다 — **코드 형식 자체는 바꾸지 않는다.**

---

## 🔴 3. 완료 코드 사양 — S15P21E201-409·499 가 여기를 본다

```
<세션ID:4글자>-<응답열:N글자, A 또는 B>-<검사합:2글자>

예:  7K3M-ABAABBAABBAB-Q2
```

### 3.1. 알파벳

**Crockford Base32** — `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (32글자).
`I`·`L`·`O`·`U` 가 없다 — 손으로 옮겨 적을 때 `I`/`1`, `O`/`0`, `U`/`V` 가
헷갈리는 것을 막으려는 표준 알파벳이라 그대로 가져다 쓴다.

### 3.2. 세션 ID

무작위 4글자. 위 알파벳 안에서만 뽑는다. `codec.randomSessionId()`.

### 3.3. 응답열

문항 순서대로 `A` 또는 `B`. 참가자가 도중에 그만두면(3~5분 초과) **뒤쪽 문항이
그냥 없다** — 짧은 응답열도 유효하다. 순서를 건너뛰거나 비우고 이어가지 않는다.

### 3.4. 검사합

세션 ID + 응답열을 이어붙인 문자열에 자리별 가중치를 곱해 더한 값을
1024 로 나눈 나머지, 그것을 2글자로 인코딩한다(`codec.checksumOf`, 비공개).
**자리마다 가중치가 달라서 두 글자를 맞바꿔 적는 실수도 잡는다.**

검사합이 안 맞으면 `decodeCode()`는 던지지 않고 `{ok:false, reason}`을
돌려준다 — 호출부(`decode.mjs`)가 그 코드를 **버리지 않고 목록으로 남기게**
하기 위해서다.

### 3.5. 대소문자

디코더가 소문자를 대문자로 자동 변환한다(`decodeCode`). 옮겨 적을 때 대소문자를
가리지 않아도 된다.

---

## 4. 파일 스키마

| 파일 | 만드는 것 | 모양 |
|---|---|---|
| `<코드파일>` (진행요원이 만듦) | 손 | 한 줄에 완료 코드 하나 |
| `data/raw/survey/decoded/<타임스탬프>.ndjson` | `decode.mjs` | 한 줄 = 세션 하나. `{"sessionId","code","answers":[{"setId","chosen"}]}` |
| `data/raw/survey/decoded/<타임스탬프>.rejects.json` | `decode.mjs` | 검사합 실패 목록. `{"line","code","reason"}[]` |
| `data/raw/survey/responses.ndjson` | `load.mjs` | 한 줄 = 응답 하나. `{"sessionId","setId","chosen"}` — `process/choice-fit.mjs`가 그대로 읽는다 |
| `data/raw/survey/decoded/<타임스탬프>.load-rejects.json` | `load.mjs` | 적재 시 재검사 실패 목록 |

`data/`는 저장소에 커밋하지 않는다(`bigData/CLAUDE.md` 4절). 개인정보가 없는
응답 데이터라도 예외를 두지 않는다 — 규칙은 하나로 유지한다.

---

## 5. 쓰는 법

```bash
# 1) 기록지의 코드를 파일 하나로 옮긴다 (한 줄에 하나) — 예: codes-round1.txt

# 2) 해독 — 검사합 확인, 세션 ndjson + rejects 생성
node bigData/survey/decode.mjs --file codes-round1.txt

# 3) 적재 — responses.ndjson 에 이어 쓴다. 같은 파일을 두 번 넣어도 줄이 안 늘어난다
node bigData/survey/load.mjs --file bigData/data/raw/survey/decoded/<위 2단계가 출력한 파일>.ndjson

# 4) 계수 추정 (응답이 쌓인 뒤)
npm --prefix bigData run choice:fit
```

`decode.mjs`의 rejects 는 **버리기 전에 기록지와 대조한다** — 옮겨 적기
실수인지, 정말 손상된 응답인지는 사람이 판단한다.

---

## 6. 안전장치 — 무엇을 막고 있는가

* **조용히 틀린 응답이 들어가는 것** — 검사합이 두 단계(`decode.mjs`,
  `load.mjs`)에서 각각 확인된다. 한쪽만 믿지 않는다.
* **같은 응답이 두 번 들어가는 것** — `sessionId::setId` 중복 판정. 파일을
  실수로 두 번 돌려도 표가 늘지 않는다.
* **응답이 없는데 계수가 나오는 것** — `process/choice-fit.mjs`가
  `data/raw/survey/responses.ndjson`이 없으면 종료 코드 2 로 멈춘다(지어낸
  값을 안 만든다).
