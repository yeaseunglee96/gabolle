# Handoff: 여행 만들기 흐름 개편 — 홈(p0) · 조건 한 페이지(p1) · TRIP PASS

## Overview
여행 조건 입력이 4단계 × 여러 카드로 흩어져 있어 누구도 끝까지 가기 어려웠다. 이 패키지는 흐름을 이렇게 바꾼다.
1. **홈(p0)** 이 시작점 — Airbnb식 「출발지 · 날짜 · 인원 → 일정 물어보기」 바. 여행지는 부산 고정이므로 묻지 않는다.
2. **개인화 특성(알레르기·식단·이동 환경)** 은 로그인 후 홈 첫 진입 시 **모달로 1회**. 「나중에」면 일정 물어볼 때마다 다시, 「다시 묻지 않기/저장」이면 끝(마이페이지에서만 수정).
3. **p1 = 기본 + 취향 한 페이지, 진행형.** 질문 카드 하나에 답하면 다음 카드가 생기고 자동 스크롤. 답한 카드는 ✓ 한 줄 요약 + 「수정」. 구 /plan/taste, /plan/conditions 화면 제거.
4. **p4 생성 중** — 데스크톱도 모바일과 같은 프린터·영수증 컴포넌트(TripPassCard). 출력 후 QR이 그려진다. 플립 카드·측면 액션 버튼 제거.
5. p5' 보드형 페이지 제거.

## About the Design Files
- `PlanFlow.dc.html` — 전체 흐름. p0 홈 → p1 조건 → p4 생성 중 → p5 추천 → p6 일정. 각 페이지 1440 데스크톱 + 390 폰 나란히. 라벨 옆 주석에 대응 소스 경로.
- `TripPassCard.dc.html` — 영수증 컴포넌트. `mode="mobile"|"desktop"`. 출력 애니메이션(gbPrint 1.6s) → QR(gbQr, 1.7s 딜레이). 폰은 아래로 끌어 찢으면 다시 출력.
- `Home v2.dc.html`, `TopNav.dc.html` — 홈·상단 바 탐색 시안(참고용).
- `support.js` `ios-frame.jsx` `browser-window.jsx` `assets/` `fonts/` — 프로토타입 렌더용. **HTML은 참조이고 Expo/RN 컴포넌트(`Screen` `Button` `Text`, `@/design/tokens`)로 다시 구현한다.**

## Fidelity
High-fidelity. 모든 값은 `src/design/tokens.ts`. 새 색·간격 없음. 「다음」 등 주 버튼은 항상 `radius.md`(14).

---

## 1. 홈 p0 — 시작 바 (`app/(tabs)/home.tsx` + 새 `src/home/PlanStartBar.tsx`)

### 데스크톱(lg↑)
- 히어로 사진·도구 카드 제거. 가운데 hero 제목 「부산의 모든 여행, 가볼래?」 + 860×72 알약 바.
- 세그먼트 3: **출발지**(어디서 출발해요?) · **날짜**(날짜 추가) · **인원**(인원 추가). 활성 세그먼트 = `surface.soft` 배경 + inset 1px border. 세그먼트 사이 1px 구분선(활성 인접 시 숨김).
- 우측 CTA 알약 56 `brand.orange` 「🔍 일정 물어보기」 — 아이콘은 **검색**(sparkle 아님).
- 팝오버(top 84, radius 24, shadow 0 16 48 rgba(11,29,58,.16), popIn .22s):
  - 출발지: 검색 입력(48, radius md) + 「추천 출발지」 목록 — 내 위치 / 부산역 / 김해공항 / 부산종합버스터미널 / 서면역 / 해운대역. 고르면 날짜 팝오버로 자동 이동. 데이터는 `src/plan/origins.ts` `MAJOR_BUSAN_ORIGINS` + `searchOrigins`.
  - 날짜: 두 달 캘리더 + 빠른 칩(당일치기·1박 2일·2박 3일·3박 4일). 출발→귀환 두 번 탭, 사이 날짜 `surface.warm`. 오늘 이전 `#c9c3ba`. 완료 시 인원으로.
  - 인원: 성인(만 13세 이상)·어린이(만 2~12세) ± 카운터 36. 기존 `Stepper` 재사용.
- 「바로 시작」 프리셋 칩 32(이번 주말 1박 2일 · 둘이서 2박 3일 · 아이와 당일치기 · 부산역 출발) — 누르면 바가 채워진다.
- 아래 줄: 날씨 「23° 흐림 · 부산 지금 | 실내 코스도 하나 챙겨두세요 | 처음 오셨나요? 사용법 보기 →」 — 기존 `HomeBlocks` 날씨·가이드 그대로.

### 폰
- 검색 알약 56 「🔍 여행 계획 시작해 보세요」. **누르면 새 화면이 아니라 알약이 그 자리에서 카드(radius 24)로 커진다** — max-height 58→auto .45s, 헤더에 ✕. 안에 출발지/날짜/인원 단계 카드(열린 카드 fadeUp .22s, 접힌 카드 56 한 줄), 하단 「전체 삭제 · 일정 물어보기」.
- 값이 채워지면 알약 텍스트 = 「부산역 · 9.20(토) – 9.21(일) · 1박 · 성인 2」.

### 아래 콘텐츠
- 갈래 칩(전체·축제·야시장·전통시장·액티비티·산책·자연·야경·기념품샵 — `localExplore.ts` 8갈래) → 「로컬 탐색」 정방형 사진 카드 줄(156, radius md, 좌상단 갈래 배지).
- 「지금 부산에서 남긴 기록」 줄 — **데스크톱은 이유 줄 3개**(「이번 주 가장 많이 본」 「여행 날짜에 열리는 로컬」 「취향 카페」), 첫 줄 첫 칸은 「오늘 어디 다녀왔어요? · 사진 올리기」 쓰기 카드.
  - 🔴 **배포에서는 이유 줄이 아니라 피드 전체 미리보기**로 동작한다. 서버가 `sections[{reason, items}]`를 내려주기 전까지는 최신 피드 N개를 같은 카드 문법으로 그린다. 제목은 그대로 두되 데이터 소스만 다르다.
- 「내 여행 · 예정 · 10.03 ~ 10.05 · 부산 · 3일 · 2명 · 일정 보기 →」, 「AI에게 물어보기」 마스코트 알약, 하단 TabBar — 모두 기존.

### 데이터 → p1으로
바에서 받은 `origin(+lat/lng)`, `startDate/endDate`, `adults/children`은 `PlanProvider.update()`로 draft에 넣고 `/plan`으로 push. p1은 이 셋을 **다시 묻지 않고** 상단 「홈에서 받은 정보」 칩 줄(soft 배경, 칩 32)로만 보여 준다. 「수정」→ 홈 바로 돌아감.

---

## 2. 여행 조건 모달 (새 `src/plan/ConditionsPromptModal.tsx`)

### 언제 뜨나
```
conditionsPromptState (사용자별, 서버 저장): null | 'LATER' | 'NEVER' | 'SAVED'
- 로그인 후 홈 첫 진입, state === null            → 모달
- 「나중에」                                          → LATER, 닫힘. 이후 「일정 물어보기」 누를 때마다 다시 모달(문구 바뀜)
- 「다시 묻지 않기」                                  → NEVER, 닫힘. 더 안 물음
- 「저장하고 시작」                                    → SAVED, draft·프로필에 반영. 더 안 물음
- NEVER/SAVED 이후 수정은 /me/preferences 에서만
```
LATER 상태에서 「일정 물어보기」로 열린 모달은 인트로 문구 「일정을 만들기 전에 여행 조건을 알려주실래요? … 건너뛰면 다음 「일정 물어보기」 때 다시 물어요.」, 좌측 버튼 「이번엔 건너뛰기」, 저장·건너뛰기 모두 `/plan`으로 진행.

### 내용 (개인화 특성만)
- **알레르기**(필수 dot) — 해당 없음 / 조건 선택 → 8칩(땅콩·견과류·갑각류·생선·달걀·우유·유제품·밀·대두). `constraints.tsx` ALLERGIES 그대로.
- **식단** — 해당 없음 / 조건 선택 → 5칩(채식·비건·할랄·글루텐 프리·페스코).
- **이동 환경** — 한 번에 걷는 최대 거리(500m·1km·2km·제한 없음), 가파른 경사 피하기 / 계단 피하기 / 그늘길 우선 (예·아니요).
- 🔴 **휠체어·유아차·큰 짐은 여기 없다.** 여행마다 달라서 p1 질문으로 옮겼다.
- 안내 「이 정보는 여행을 준비하는 동안에만 쓰고 기기에 저장하지 않아요. 「다시 묻지 않기」를 누르면 마이페이지 › 여행 조건에서만 바꿀 수 있어요.」
- 칩은 navy 선택(`brand.navy` bg, 흰 글자), 미선택 `brand.ivory` bg + `surface.border`.

### 레이아웃
- 데스크톱: 640 센터 모달, radius 24, 헤더 64(제목 가운데 + ✕), 본문 스크롤, 푸터(좌 「나중에」 밑줄 + 「다시 묻지 않기」 caption, 우 navy 48 「저장하고 시작」). 배경 rgba(11,29,58,.45). popIn .28s.
- 폰: 바텀시트(radius 24 상단, max-height 90%), 같은 내용, 푸터 하단 여백 36.

---

## 3. p1 — 조건 한 페이지 (`app/(plan)/index.tsx`로 통합, taste·constraints 제거)

### 구조
```
[제목] 여행 조건 알려주기 / 하나씩만 답해 주세요. 답한 만큼 다음 질문이 열려요.
[칩 줄] 홈에서 받은 정보: 부산역 출발 · 9.20(토) – 9.21(일) · 1박 · 성인 2 · [수정]
[진행] 질문 n / 10 ─────────── 남은 질문 k개  (4px 바, width 전환 .4s)
[카드 n]  ← 열린 카드 (fadeUp .3s)
```
질문 순서와 answered 조건:
| n | key | 제목 | 답한 것으로 보는 조건 | 건너뛰기 |
|---|---|---|---|---|
| 1 | areas | 여행 범위 | 칩 1개↑ | ✗ |
| 2 | budget | 총예산 | budgetKrw ≠ null | ✗ |
| 3 | move | 하루 여행 시간 · 이동수단 | transport 선택 | ✗ |
| 4 | cats | 여행 카테고리 | 1~3개 | ✓ |
| 5 | pace | 여행 기분 | 선택 | ✓ |
| 6 | moods | 좋아하는 분위기 | 1개↑ | ✓ |
| 7 | scales | 로컬성 · 조용함 · 관광지 | 하나라도 | ✓ |
| 8 | foods | 음식 취향 | 1개↑ | ✓ |
| 9 | aids | 이번 여행 이동 보조 · 짐 | 하나라도 답 | ✓ |
| 10 | must | 꼭 가고 싶은 장소 | 항상 | ✓ |

- 각 카드: `radius.lg` 20, padding 24(폰 16), 헤더 = eyebrow 「n / 10」 + title + hint(caption muted) + 우측 「건너뛰기」(orange caption). 바닥에 전폭 48 「다음」(마지막은 「입력 완료」) — 미답이면 `#c9c3ba` 비활성.
- 「다음」/「건너뛰기」 → 카드가 **접힌 줄**(64, ✓ 초록 원 28 + 제목 caption + 요약 body bold + 「수정」 orange)로 바뀌고 다음 카드가 생성. 「수정」→ 그 카드만 다시 펼침(다른 카드 상태 유지).
- 자동 스크롤: 새 카드 offsetTop − 96(폰은 스크롤 컨테이너, 데스크톱은 window −140) smooth. React Native는 `ScrollView.scrollTo` + `onLayout`으로 y 측정.
- 카드 내부 위젯은 기존 코드 그대로: 예산 키패드(+1만~+10만, 되돌리기/전체 지우기, 스택 undo), 카테고리 이미지 그리드(3열, 폰 2열, 최대 3, 선택 = 2px orange + warm bg + ✓), 척도 5점, 음식 충돌 비활성.
- 모두 끝나면 orange-warm 카드 「다 됐어요. 이 조건으로 일정을 만들까요?」 + navy 54 「이 조건으로 일정 만들기」 → 기존 `/plan/confirm` 로직(consent·submit) 그대로 실행 후 `/plan/generating`.

### 상태
`draft`(PlanProvider) 그대로 쓰되 화면 상태 `{ q: number, editing: number|null, skipped: Record<key,boolean> }`만 추가. 뒤로 가기는 홈.

---

## 4. TRIP PASS (`TripPassCard.dc.html` → `src/plan/TripPass.tsx`)
- 데스크톱/폰 **동일 컴포넌트**: 프린터(318×52 다크 그라데이션 + 슬롯) → 영수증 286 폭이 위에서 내려옴(gbPrint 1.6s cubic-bezier(.25,.7,.25,1)). 영수증: 헤더(로고 · 코드) / 출발→도착 / 날짜·모드 / 여행자 / 6칸 그리드 / 지그재그 절취 / 바코드 / **QR 120(데스크톱)** — 1.7s 뒤 blur 6→0, scale .6→1 (.7s).
- 폰은 영수증을 아래로 110px 이상 끌면 찢어지며(rotate 14deg, .7s) 다시 출력. 데스크톱은 「다시 출력」 텍스트 버튼.
- 우측(데스크톱): 출발지→부산 제목, 날짜, 상세 5행(출발지·첫 일정·마지막 일정·이동 합계·예산) + 일정 상태 「● 생성 완료」, 아래 「일정 보기」(navy) / 「지도에서 보기」(ghost). **공유·저장·수정·아바타 원형 버튼 열은 제거.**
- 실데이터: `generating.tsx`의 `itinerary`(days, totalEstimatedCostKrw, totalWalkingMeters) + `draft.origin`.

---

## 5. 지운 것 / 남긴 것
- 지움: `/plan/taste`, `/plan/conditions` 화면, PlanStepHeader 4단계, p5' 보드형, TripPass 플립 카드·측면 액션, 홈 히어로 사진·통역/현장 도구 카드.
- 남김: `/plan/confirm`은 **화면은 없어도 로직(HEALTH_CONSTRAINTS 동의·submit)은 p1 마지막 CTA에서 그대로 호출**. `/plan/generating` → recommendations → itinerary 흐름 유지.

## 6. 애니메이션 토큰
- popIn: opacity 0→1, translateY(−8)→0, scale .98→1, .22s cubic-bezier(.2,.8,.2,1) — 팝오버·모달
- fadeUp: opacity 0→1, translateY(8~10)→0, .22~.3s ease-out — 새 카드
- 칩/버튼 상태 전환: background·color·border .18s ease
- 폰 알약 확장: max-height .45s cubic-bezier(.2,.8,.2,1), radius .3s
- RN: `react-native-reanimated` `FadeInDown/FadeInUp`(200~300ms), `LayoutAnimation` 또는 `Animated.timing`으로 알약 max-height.

## Common Mistakes
- 「다음」 버튼 radius 0으로 만들지 않기 — 항상 14.
- p1에서 날짜·인원·출발지를 다시 묻지 않기(칩 줄로만 보여 준다).
- 모달에 휠체어·유아차·짐 넣지 않기 — p1 질문 9.
- 홈 이유 줄 제목을 보고 서버에 없는 sections API를 만들지 않기 — 피드 전체 미리보기로 시작.
- 폰 p0는 라우트 이동 없이 알약 자리에서 확장.
- 예산 키패드는 **누적**(5 두 번 = 10만).
