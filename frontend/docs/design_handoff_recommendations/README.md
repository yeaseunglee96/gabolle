# Handoff: 추천 코스 고르기 (`/trips/[id]/recommendations`)

## Overview
배포본 `app/trips/[id]/recommendations.tsx` + `src/plan/CourseCard.tsx` 의 **디테일 수정** 인계. TopNav·데이터 흐름·`build()`·로딩/에러 상태는 그대로 두고 아래만 바꾼다.

1. **CourseCard** — 옆 표지(96/200px) → **위 콜라주 표지**(200px, 장소 사진 1~3장). 「코스 A/B/C」 배지 삭제, 선택 카드에만 「✓ 내 일정으로」. 선택 테두리 **빨강**.
2. **모바일** — 상단 코스 칩 줄 삭제. 코스 하나를 고르면 **나머지 카드가 한 줄로 접힘**(.42s). TabBar 는 `hidden`, 그 자리에 **코스 바**(비용·요약 + 「해당 코스 일정 보기」 빨강) → 누르면 **시트로 자람**(지도 모달 대체).
3. **데스크톱** — 지도 범례(날짜 알약) → **일차 칩**. 「코스 내용」 칸 삭제, 지도가 남은 높이 전부. 고른 날 정차지는 지도 **하단 가로 스트립** + 카드↔마커 연동 + 넘침 처리.

## About the Design Files
`배포본 추천 코스 고르기.dc.html` 은 **HTML 로 만든 디자인 참고본**이다. 그대로 쓰는 코드가 아니라 Expo/React Native(react-native-web) 코드베이스의 기존 부품(`Screen`, `TabBar`, `CourseCard`, `RouteMap`, `TopNav`, `tokens.ts`)으로 **재구현**한다. 색은 `tokens.ts` 이름으로만 쓴다(하드코딩 금지 규칙).

## Fidelity
**High-fidelity.** 색·크기·간격·타이포는 `tokens.ts` 값과 1:1. 지도 배경만 자리표시(실제는 Kakao `RouteMap`).

## 변경 없음
TopNav(유틸 36 + 주 내비 60), 좌측 목록 폭 600, 헤더(눈썹 「추천 코스 고르기」 · hero `N가지 코스`/`추천 코스` · full=false 안내문), 로딩 스켈레톤·에러 카드, `build()`·이름 묻기, ☆ 저장 토글, 「처음엔 아무 안도 미리 고르지 않는다(1안이면 자동 선택)」 규칙.

---

## 1. CourseCard (모바일·데스크톱 공통) — `screenshots/04`, `05`

세로 스택: **표지(있을 때만)** → 본문(padding 16, gap 8).

**표지 (콜라주, 높이 200)** — `course.days[*].stops` 중 `photoUrl` 있는 것 앞에서 최대 3장.
- 3장: grid `2fr 1fr` × `1fr 1fr`, gap 3, 바탕 흰. 큰 사진이 왼쪽 두 줄, 작은 두 장이 오른쫙 위/아래.
- 2장: 오른쪽 사진이 두 줄을 차지(`grid-row: 1 / span 2`).
- 1장: 한 칸 전체(`grid-template-columns: 1fr`).
- 0장: **표지 칸 없음**(회색 띠 남기지 않음).
- 사진마다 좌하단 이름 꼬리표: padding 2 8, radius full, bg rgba(25,25,25,.72), 흰 micro 12/16 bold, 1줄 말줄임(작은 칸은 `max-width: calc(100% - 16px)`, +N 이 있는 칸은 `right 44`).
- **+N** 배지(N = `summary.places - 사진 수`, 0 이면 숨김): 마지막 작은 칸 **우하단**(2장일 때도 오른쪽 칸 우하단 — 우상단은 ☆ 자리라 겹친다) — bg rgba(255,255,255,.92), 12 bold. 같은 칸의 이름 꼬리표는 `max-width: calc(100% - 52px)`.
- 선택 카드만 좌상단 「✓ 내 일정으로」: padding 4 12, radius full, bg `brand.navy`, 흰 caption 13 bold. **미선택 카드에는 배지 없음(「코스 A」 삭제).**
- ☆ 저장: 우상단 32px 흰 원(top 8 right 8). 표지가 없으면 본문 첫 줄 오른쪽 끝에.

**본문**
- (표지 없을 때) 첫 줄: [선택이면 「✓ 내 일정으로」] … ☆ (space-between).
- 제목 줄: title 18/24 bold 1줄 말줄임 + 상태 배지(확인됨 `state.successBg` / 추정 `state.warningBg`, caption 13 bold, `flexShrink 0`).
- tagline caption 13/18 muted(있을 때만).
- 일차 줄: 알약 `N일차`(bg `surface.soft`, 13 bold) + `A → B → C` 13/18 `text.body` 1줄 말줄임.
- 하단: 비용(title 18 bold + 「예상」 caption muted / 없으면 「비용 미정」 caption muted) … 버튼. 선택 = 「이 코스로 일정 만들기 →」 bg `brand.navy` 흰 글자 44px pill; 미선택 = 「이 코스 선택」 bg `surface.tint` 글자 `action.secondary`.
- **카드 테두리**: 2px. 미선택 `surface.border`; **선택 `action.outline` #D83A48** + shadow (color `action.outline`, opacity .16, radius 16, offset 0 6). 전환 300ms.

## 2. 모바일 — `screenshots/01~03`

- `Screen scroll withTabBar` 그대로(padding 24). **phoneChips(상단 코스 칩 줄) 삭제.**
- **접힘/펼침** (`courses.length > 1 && picked` 일 때 미선택 카드):
  - 접힌 줄: minHeight 64, padding `12 12 12 16`. 제목 body 15 bold 1줄 + 아래 caption 13 muted `「{비용} 예상 · 장소 N곳 · 이동 M분 · Nkm · 확인됨/추정」` 1줄 말줄임. 오른쪽 ☆(32) + ⌄(32, muted).
  - 애니메이션: 접힌 블록/펼친 블록을 각각 `grid-template-rows 0fr↔1fr` + opacity 로 **420ms cubic-bezier(.34,1.3,.64,1)** (RN 에선 `Animated` height 측정 후 보간 또는 `LayoutAnimation`). 카드 탭 = `setPicked` → 이전 카드 접히고 새 카드 펼쳐짐.
  - 아무 것도 안 고른 처음엔 셋 다 펼침(비교용).
- **TabBar**: 이 페이지에서는 `<TabBar active="map" hidden />` — 항상 hidden(translateY 110, opacity 0, pointerEvents none). **코스 바가 TabBar 를 대신한다.**
- **코스 바(기존 bottomBar 대체)** — **항상 표시**. TabBar 와 같은 dock: fixed bottom, 가운데, paddingBottom `max(8, insets.bottom)`, zIndex 30.
  - 접힘: **328×64**, radius 20, bg `surface.card`, shadow `0 -2 14 rgba(25,25,25,.10)`(TabBar 와 동일), padding `0 8 0 16`.
    - **선택 전**(`picked == null`): 왼쪽 「코스를 골라 주세요」 body 15 bold + `N가지 중 하나를 고르면 일정이 열려요` caption muted. 오른쪽 「일정 보기」 **비활성**(bg `surface.tint`, 글자 `text.disabled`, disabled). 문구는 328 폭에서 말줄임 없이 들어가야 한다.
    - **선택 후**: 왼쪽 1줄 `{courseCost} 예상` / `비용 미정` body 15 bold; 2줄 `courseFacts` caption muted. 각 1줄 말줄임. 오른쪽 「**해당 코스 일정 보기**」: minHeight 44, paddingH 16, pill, **bg `action.primary`**, 흰 15 bold. (카드 안의 「이 코스로 일정 만들기」는 navy 유지 — 화면당 빨강 채움 하나.) 두 상태 전환은 내용 crossfade 200ms.
  - 열림(시트): **361×560**, 420ms cubic-bezier(.34,1.3,.64,1)(TabBar `expanded` 와 동일). 접힌 줄 opacity 0(200ms)·터치 차단, 시트 내용 opacity 0→1(420ms).
    - padding `12 12 16`, gap 12. 손잡이 36×4 `surface.field`(터치 44×20) → 닫기.
    - 제목 줄: `course.title` 18 bold 1줄 + `courseFacts` caption muted.
    - **일차 칩**(가로 스크롤): `N일차`, minHeight 36, paddingH 16, pill; 선택 bg/border `brand.navy` + 흰 글자, 미선택 흰 bg + `surface.border`. 300ms.
    - 지도: flex 1, radius 14. `RouteMap` 에 **고른 일차의 stops/route 만**. 마커 28px 흰 원·검은 2px·번호. 좌하단 「점선은 장소를 곧게 이은 선이에요」 배지.
    - 정차지 목록: 행 28 — 시각(44 고정, caption bold muted) · 이름(body bold 1줄).

## 3. 데스크톱 — `screenshots/04~06`

**mapPane**(flex 1, bg `surface.soft`) 세로:
1. **일차 칩 줄**(mapLegend 대체): padding 16, gap 8, 칩 규격 위와 동일. **`flexShrink 0` + nowrap** 필수.
2. **RouteMap `flex: 1`** (고정 640 → 남은 높이 전부). 고른 일차만: 선 `dayColor(dayIdx)`(courseMap.ts DAY_PALETTE), shortdash, weight 5, opacity .75.
   - 선택 전: 지도 가운데 「코스를 고르면 내용이 여기에 보여요.」 배지(13 muted, 흰 .94 pill). 배지·스트립 모두 숨김.
   - **우상단 요약 배지**: `장소 N곳 · 이동 M분` caption bold, padding 8 12, radius 14, 흰 .94, shadow 0 2 8 .08. (M 은 구간별 `moveMin` 합 — 시안은 시각 차 추정치)
   - **마커** 32px 흰 원·검은 2px·번호 13 bold. **선택 마커**: bg/테두리 `dayColor`, 흰 번호, scale 1.25(300ms bounce), zIndex 위. 탭 → `selStop` + 스트립 해당 카드 가운데로 스크롤.
   - **하단 스트립**(absolute bottom, padding `24 0 36`, bg gradient rgba(244,241,234,0→.9 at 40%)):
     - 가로 스크롤 컨테이너 padding `6 16`, 스크롤바 숨김, `scroll-behavior: smooth`.
     - 카드 **150** 폭, padding 12, radius 14, 흰, 테두리 2px(선택 = `dayColor`, 아니면 흰), shadow 0 4 16 .10(선택 0 8 20 .16 + translateY -4). 1줄: 22px 원(bg `dayColor`, 흰 번호 12 bold) + 시각 caption bold muted; 2줄 이름 body bold 1줄. 탭 → `selStop`, 카드 가운데로.
     - 연결부(마지막 제외) 폭 44: 위 이동 시간 11/14 bold muted, 아래 `2px dashed #B9BBC2`.
     - **넘칠 때만**(scrollWidth > clientWidth): 양끝 56px 페이드(`mask-image`, 끝에 닿은 쪽은 해제), 좌/우 **‹ ›** 36px 검은 원 버튼(bg `brand.navy`, 한 번에 카드 2장 = 388px 이동, 끝에서 숨김), 아래 페이지 점(6px, 현재 18px `brand.navy`, 나머지 `#B9BBC2`).
     - 세로 휠 → 가로 스크롤. 일차·코스 바뀌면 스크롤 0(즉시), 선택 해제.
   - 우하단 「점선은 실제 길이 아니라 장소를 곧게 이은 선이에요.」 12 muted 배지(기존 RouteMap 안내문을 지도 위로). 좌하단 Kakao 축척·로고 그대로.
3. **mapBody(코스 내용 · 이렇게 골랐어요) 삭제.**

## State
```
picked: string | null           // 기존
saved: Record<string, boolean>  // 기존
dayIdx: number                  // 신규, 0. picked 바뀌면 min(dayIdx, days.length-1)
selStop: number                 // 신규, -1. 일차/코스 바뀌면 -1
sheetOpen: boolean              // 신규(phone). picked 없으면 열리지 않음
strip: {left, max, w}           // 데스크톱 스트립 스크롤 측정(‹ › · 페이드 · 점)
```

## Design Tokens (tokens.ts 이름)
- 선택 테두리/그림자 `color.action.outline` · 주 CTA 「해당 코스 일정 보기」 `color.action.primary` · 칩 선택/「일정 만들기」/‹ › `color.brand.navy`
- 일차 색 `dayColor(i)` — 1 `action.primary` · 2 `state.success` · 3 `brand.navy` · 4 `text.eyebrow`
- 상태 배지 `state.successBg` / `state.warningBg` · 알약 `surface.soft` · 미선택 버튼 `surface.tint`
- 라디우스 14 `md` · 20 `lg` · full · 간격 4/8/12/16/24 · 타입 micro 12/16, caption 13/18, body 15/23, title 18/24, hero 34/40
- 애니메이션: 시트·접힘 420ms cubic-bezier(.34,1.3,.64,1) · TabBar hidden 500ms cubic-bezier(.22,1,.36,1) · 색 전환 300ms

## Assets
코드베이스 기존: `assets/icons/home/*.png`, `assets/flags/*.png`, `assets/brand/gabolle-logo-hd.png`. 시안의 장소 사진은 자리표시(실제는 `stop.photoUrl`).

## Files
- `recommendations.dc.html` + `support.js` + `assets/` — 참고본(원래 이름: 배포본 추천 코스 고르기.dc.html). 웹폰트는 코드베이스 `assets/fonts` 그대로 사용. Tweaks `threeCourses` 끄면 배포본 현재(일정 하나) 상태.
- `screenshots/` — 01 모바일 3안 미선택(코스 바 안내 상태) · 02 모바일 선택(나머지 접힘·코스 바 활성) · 03 모바일 시트 열림(2일차) · 04 데스크톱 미선택 · 05 데스크톱 1일차 · 06 데스크톱 2일차.
- 배포본 원본: `app/trips/[id]/recommendations.tsx`, `src/plan/CourseCard.tsx`, `src/components/TabBar.tsx`, `src/map/RouteMap.tsx`, `src/plan/courseMap.ts`.
