# Handoff: 부산 로컬 탐색(/explore) — 넓은 화면 배치 + 모바일 사진 목록

## Overview
`frontend/app/explore.tsx`(305줄, `LocalBranchList` 172줄~)에 **넓은 화면 배치를 처음 넣는다.** 지금은 `useLayout`·`isAtLeast`·`Screen wide`를 한 번도 안 써서 1920 폭에서 720 기둥 하나만 서고 좌우가 빈다. 이 시안은 **폰 배치의 정보는 하나도 빼지 않고** 폭 구간마다 배치만 바꾼다. 모바일 결과 목록에는 사진 썸네일을 더한다(계약에 이미 오는 `photoUrl`).

## About the Design Files
`*.dc.html`은 **HTML 디자인 참조**다. Expo/RN 코드베이스의 기존 컴포넌트(`Screen` `Button` `Eyebrow` `Text` `BrandLogoLink`, `@/design/tokens`, `@/layout/useLayout` `breakpoints` `Split`)로 **다시 구현**한다. `support.js` `ios-frame.jsx` `browser-window.jsx` `assets/`는 프로토타입 렌더용이며 옮기지 않는다.

## Fidelity
**High-fidelity.** 색·글자·간격·반경은 전부 `src/design/tokens.ts` 값. **새 수치 없음.** 폭 경계도 `src/layout/breakpoints.ts`(599 · 1023 · 1439) 그대로.

## 🔴 구현자가 흔히 틀리는 것 — 먼저 읽기
1. **갈래(facet) 목록은 서버가 정한다.** `GET /api/v1/places/facets` 응답을 `flattenLocalFacets`로 그대로 쓴다. 하드코딩 배열·개수 고정 금지. 시안의 12개 중 뒤 4개(카페거리·해수욕장·사찰·골목 투어)는 **「늘어날 때」 견본**이지 실제 값이 아니다. 3개·8개·12개 모두 깨지지 않아야 한다.
2. **정보를 빼지 않는다.** 폰에 있는 것(뒤로가기 ‹, 안내 캡션, `검색 범위`, 반경 확대 안내, 위치 대체 안내+버튼, 주소, `distanceM`, `›`)은 모든 폭에서 그대로 있다.
3. **칩 글자를 자르지 않는다.** 어느 폭에서도 `numberOfLines={1}`·ellipsis 금지. 영어 라벨(`Traditional markets`, `Alley walking tours`)이 한국어보다 길다.
4. **새 기능 넣지 않는다.** 정렬·필터·공유·검색 없음. 지도 패널은 이미 있는 `RouteMap`(웹 전용)에 이미 오는 lat/lng를 그리는 것뿐이다.
5. **`photoUrl`을 그리면 `photoSource`도 반드시 같이 그린다**(관광공사 공공누리 이용 조건). 값이 없으면 칸 자체가 안 온다 → soft 자리표시.
6. **상단 바는 손대지 않는다.** `app/_layout.tsx`가 `TopNav`(2단, 36+60)를 한 번 붙인다. 이 화면에서 따로 그리면 두 벌이 뜬다. 시안의 상단 바는 현재 `src/nav/TopNav.tsx`를 그대로 옮겨 그린 참조다.
7. **하단 TabBar는 없다.** 이 화면은 홈에서 push 되는 상세 화면이라 현재 코드에도 없다(‹ 뒤로가기가 그 역할). 추가하려면 별도 결정.

---

## 폭 구간과 배치 (breakpoints.ts 그대로)

| 폭 | tier | 상단 | 갈래 | 범위 토글·안내 | 결과 | 지도 |
|---|---|---|---|---|---|---|
| 360~599 | sm | 뒤로가기 ‹(44 원, card) + 로고 96×28 (기존 `topBar`) | **가로 스크롤 칩**(기존 `categoryRail`) | 기존 | **목록 행 + 사진 72×72** | — |
| 600~1023 | md | `TopNav`(자동) + 본문 위 ‹ 44 원(card, 1px border) | **칩 줄바꿈**(`flexWrap:'wrap'`, gap 8) — 숨는 것 없음 | 기존 | **카드 격자**(auto-fill, 최소 280) | — |
| 1024~1439 | lg | 같음 | **왼쪽 320 세로 목록** | 목록 아래 같은 칸 | 오른쪽 카드 격자 2열 | — |
| 1440~ | xl | 같음 | 같음 | 같음 | 가운데 카드 격자 2열 | **오른쪽 320 패널** |

- 컨테이너: sm 전폭(gutter 24) · md `Screen` maxWidth **720** · lg/xl `Screen wide` maxWidth **1440** 가운데. 1920에서는 1440 밖 좌우가 canvas 색으로 비는 것이 정상(Feed·일정 화면과 같음).
- 좌우 분할 = CSS grid `320px | minmax(0,1fr) | 320px`(xl) / `320px | minmax(0,1fr)`(lg), gap 24. RN에서는 `Split.tsx` 패턴(master 320 고정 + `paddingRight 16` + `borderRight 1 field`, detail `flex:1 paddingLeft 24`)을 쓰되 **xl에서 셋째 칸(지도 320)이 하나 더 붙는다** — `Split`은 두 칸이라 이 화면은 직접 row로 짜거나 `Split`에 선택적 `aside`를 더한다(결정 필요).
- 왼쪽 칸·지도 패널은 sticky(상단 바 96 + 24 = top 120).
- 확인 폭: 360 · 375 · 414 · 768 · 1024 · 1440 · 1920 (스크린샷 참고).

### 헤딩 (모든 폭 공통)
`Eyebrow`「로컬 탐색」(caption bold orange) → `display bold`「부산 로컬 탐색」 → `body` 설명. md 이상은 왼쪽에 ‹ 44 원(card, border 1 `surface.border`)이 row로 붙고 헤딩 블록은 `flex:1`, maxWidth 720, marginBottom 24.

---

## 갈래 선택 — 세 가지 모양, 한 데이터
공통: minHeight 44, paddingHorizontal 16, 선택 = bg **navy** + 글자 **onAction(#fff)** + 「✓ 」접두, 미선택 = bg card + 1px `surface.border` + 글자 heading. 글자 body 15/22 bold.
- **칩(sm·md)**: radius full, `✓ 축제 · 14` 한 줄(nowrap). sm은 가로 ScrollView(indicator 없음, paddingRight 16), md는 `flexWrap` 줄바꿈.
- **세로 목록(lg·xl)**: 폭 100%, radius **md(14)**, `justifyContent:'space-between'` — 왼쪽 `✓ 라벨`(줄바꿈 허용, paddingVertical 10), 오른쪽 개수(선택 시 흰 bold, 미선택 muted medium). 항목 gap 8. 12개 → 12×(44+8)=624, 사이드바 안에 그대로 쌓인다(스크롤 없음).
- 시안은 첫 갈래 선택 상태. 홈에서 `?facet=`으로 들어오면 그 갈래가 맨 위·선택(기존 `visibleFacets` 정렬 유지).

### 범위 토글 (모든 폭 동일)
`surface.soft` 트랙 padding 4 radius full → 두 옵션 flex 1, minHeight 44, 선택 = bg **orange** + 흰 bold + 「✓ 」. 아래 안내 `caption muted`(기존 문구 둘 그대로). lg 이상은 사이드바 안, 갈래 목록 아래(gap 16).

---

## 결과 — 두 가지 모양
헤더 행(모든 폭): 왼쪽 `title bold`「(선택 갈래 이름)」, 오른쪽 `caption muted`「8곳 / 8 places」(nowrap). 내 근처면 그 위에 `caption`「검색 범위: 3km 이내」(기존).

### A. 모바일 목록 행 (sm) — 기존 `placeRow` + 사진
row, gap **12**, paddingVertical 8, `borderTop hairline surface.border`.
- **썸네일 72×72**, radius **md(14)**, overflow hidden, bg `surface.soft`. `photoUrl` 있으면 `Image cover`; 없으면 soft 칸에 핀 아이콘 20(muted).
- 가운데 `flex:1`: `body bold` 이름(`localPlaceName`) / `caption muted` 주소(한 줄, 넘치면 ellipsis — 주소만 예외) / 사진 있으면 `caption muted` **photoSource**(예 「사진: 한국관광공사」).
- 오른쪽: 내 근처면 `caption bold accent` `3,240m`(nowrap) → `title orange` `›`.

### B. 카드 격자 (md 이상)
`repeat(auto-fill, minmax(280px, 1fr))`, gap 16 → 720에서 2열, lg detail(≈630) 2열, xl detail(≈700) 2열. RN은 `flexBasis` 계산으로 2열(폭 < 600이면 1열로 두지 않는다 — sm은 A 목록).
카드: bg card, 1px `surface.border`, radius **lg(20)**, overflow hidden.
- 상단 사진 **16:9**, bg soft. 사진 있으면 cover + 왼쪽 아래 **출처 필**(`caption muted`, bg `rgba(255,255,255,.85)`, padding 2 8, radius full, maxWidth 칸-24, 넘치면 ellipsis). 없으면 가운데 핀 24.
- 본문 padding 12 16 16, row gap 8: `flex:1`에 `body bold` 이름(줄바꿈 허용) / `caption muted` 주소(marginTop 2) / 내 근처면 `caption bold accent` 거리(marginTop 8). 오른쪽 `title orange` `›`.
- 카드에 더한 정보는 **계약에 이미 오는 것만**: `photoUrl`+`photoSource`, `distanceM`. `category`는 코드 값 목록이 미확정(`places.ts` 주석)이라 넣지 않았다 — 라벨표가 생기면 주소 위 `caption` 후보.

---

## 지도 패널 (xl, 1440~ 전용)
`Eyebrow`「결과 위치」 → 카드(bg card, 1px border, radius lg, overflow hidden): **`RouteMap`(웹 전용) 360 높이**에 결과 핀(순번 22 원, 첫 항목 orange, 나머지 navy, 흰 2px 테두리) → 아래 padding 12 16: `body`「지도에 표시 8곳」, 내 근처면 `caption muted`「기준점: 부산시청 · 반경 3km」(위치 대체 시). 시안의 회색 지도는 자리표시 — 실제 `RouteMap`을 넣는다. 정렬·필터 등 조작 없음.

---

## 상태 (전부 그렸다 — screenshots/ 참고)
갈래 불러오기(화면 전체) 상태 3종은 **헤딩 아래 폭 480 카드 가운데**(bg card, 1px border, radius lg, padding 24, 가운데 정렬, gap 12). 1920에서도 좌우가 텅 비어 보이지 않게 카드 폭을 480으로 묶는다.
1. **불러오는 중**: 주황 스피너 24(`ActivityIndicator color orange`) + `body`「갈래를 불러오고 있어요」. `accessibilityLiveRegion="polite"`.
2. **못 불러옴**(`role=alert`): 마스코트 `mascot-thinking` 72 + `title bold` 제목 + `body` `result.message` + **ghost Button**「다시 시도」(전폭). 제목은 상태별 기존 문구: offline「인터넷 연결을 확인해 주세요」/ unavailable(404·501)「로컬 탐색 API를 기다리고 있어요」/ 그 밖「갈래를 불러오지 못했어요」.
3. **갈래 없음**: 마스코트 `mascot-idle` 72 + `body` 기존 문구.
4. **정상**: 위 배치.
5. **내 근처인데 위치 권한 없음**(부산 중심 대체, S15P21E201-982): 결과 칸 맨 위 `surface.tint` 박스(padding 8, radius md, gap 8) — `caption bold orange` 기존 문구 + **ghost Button**(alignSelf flex-start, nowrap)「내 위치로 다시 찾기」(`canAskAgain` 거짓이면 「설정에서 위치 허용하기」). 거리는 부산시청 기준으로 그대로 표시.
- 갈래 하나의 결과 로딩·실패·0건(`LocalBranchList` 안 상태)은 기존 `branchBody` 그대로 결과 칸 안에서만 바뀐다 — 사이드바·지도 패널은 유지.

---

## 코드 변경 요약
- `explore.tsx`: `useLayout()`·`isAtLeast(width,'lg'|'xl')` 도입. `Screen scroll wide={isAtLeast(width,'lg')}`.
- 갈래 렌더를 `FacetPicker` 같은 내부 컴포넌트로 빼고 `mode: 'rail' | 'wrap' | 'list'` 하나로 세 모양.
- `PlaceRows`에 `photoUrl/photoSource` 표시 추가(sm 72 썸네일), md 이상은 `PlaceCards`(격자).
- xl에서만 `RouteMap` 패널 마운트(`Platform.OS === 'web'`).
- 폭 검사: 360·375·414·768·1024·1440·1920 — 가로 스크롤(칩 rail 제외) 0, 잘린 글자 0.

## Design Tokens
navy #0b1d3a · orange #f26532 · ivory/canvas #fffdf8 · card #fff · tint #fff1e8 · soft #f8f3eb · field #e4ddd3 · border #e8e4dd · heading #152238 · body #667882 · muted #64748b · accent #f26532 · onAction #fff
caption 11/14 +.1 · util 13/18 · body 15/22 · title 18/24 · display 22/28 -.15 · radius sm 8 / md 14 / lg 20 / full 999 · spacing 4·8·12·16·24·32 · gutter 24 · Button minHeight 48 radius 14 padding 12 (primary navy / ghost card+field 테두리+navy 글자) · hit 44

## Copy (ko / en)
「로컬 탐색 / Local explore」「부산 로컬 탐색 / Explore Busan like a local」「관심 갈래를 고르고 내 근처 또는 부산 전체에서 찾아보세요. / Choose a category, then search nearby or across Busan.」「내 근처 / Nearby」「부산 전체 / All Busan」「부산 전체는 거리 제한 없이 찾아요. / All Busan searches without a distance limit.」「내 근처는 반경 안에서 찾아요. 결과의 검색 범위를 확인하거나 부산 전체로 바꿔 보세요. / Nearby searches within a radius. …」「검색 범위: 3km 이내 / Search range: within 3 km」「내 위치를 몰라 부산 중심에서 찾았어요. 거리도 그 기준이에요. / We searched from the center of Busan because your location is unavailable. Distances use that point.」「내 위치로 다시 찾기 / Search from my location」「설정에서 위치 허용하기 / Allow location in Settings」「다시 시도 / Try again」「갈래를 불러오고 있어요 / Loading categories」「지금은 둘러볼 수 있는 갈래가 없어요. 자료가 들어오면 다시 열어 드릴게요. / No categories to explore right now — check back once new places are added.」「결과 위치 / Result locations」「지도에 표시 N곳 / N places on the map」「기준점: 부산시청 · 반경 3km / Center: Busan City Hall · 3 km radius」「N곳 / N places」「사진: 한국관광공사 / Photo: Korea Tourism Organization」

## Files
- `ExploreScreen.dc.html` — 화면 본체. props: `width`(폭으로 tier 판단) · `state`(ok / denied / loading / error-offline / error-unavailable / error-other / empty) · `scope`(all / nearby) · `facetCount`(3 / 8 / 12) · `lang`(ko / en).
- `Explore.dc.html` — 7폭(1920·1440·1024·768·414·375·360) 캔버스, 트윅으로 상태·언어·갈래 수 전환.
- `ExploreStates.dc.html` — 상태·언어·갈래 수 조합 10장(S1~S10) 고정 시트. `screenshots/`가 이 파일을 찍은 것.
- `screenshots/S1~S10*.png` — 구현 후 **폭·상태별로 나란히 비교**할 기준 이미지.
- `ref-current-explore-mobile.png` — 현재 앱의 모바일 화면(변경 전 기준).
- `support.js` `ios-frame.jsx` `browser-window.jsx` `assets/` — 프로토타입 런타임·프레임·로고/마스코트(1× 저해상도, 참고용).
