# Handoff: 여행 페이지 통합 (추천 코스 + 일정)

> 두 zip으로 나뉨: `trip-handoff-1-code`(이 README + design/) · `trip-handoff-2-screenshots`(PNG 8장 → 이 폴더 옆 `screenshots/` 로 풀기). 폰트 파일은 뺐음 — 코드베이스의 Pretendard를 쓴다.

## Overview
`app/trips/[id]/recommendations.tsx`(코스 고르기)와 `app/trips/[id]/itinerary.tsx`(일정)를 **한 화면**으로 합친다. 코스 고르기는 화면 안의 3분할 알약으로 들어가고, 지도·동행 초대·날씨는 페이지 이동 없이 같은 화면에서 열린다.
- **데스크톱**: 정보 제공 중심. 진행(출발·도착 찍기) 없음. 장소를 열로 나란히 비교 + 오른쪽 지도.
- **모바일**: 여행 중에 자주 여는 화면. 지도 위에 «탭바가 확장된 창»이 올라오고, 「지금」 카드 + 실시간 위치 타임라인.

## About the Design Files
`design/` 의 `.dc.html` 은 **HTML로 만든 디자인 레퍼런스**다. 그대로 배포하지 말고 기존 코드베이스(Expo/RN + web, `src/design/tokens.ts`, `Text`, `Button`, `TabBar`, `RouteMap`, `NowCard`)로 재구현한다. 브라우저로 `design/TripPageCanvas.dc.html` 을 열면(같은 폴더 `support.js` 필요) 모든 상태를 눌러 볼 수 있다.
- `TripPageDesktop.dc.html` / `TripPageMobile.dc.html` — 실제 화면 (prop `view`: default · map · invite · weather)
- `reference-original/OriginalCourseItinerary.dc.html` — 현재 배포본을 그대로 옮긴 비교용

## Fidelity
**High-fidelity.** 색·타입·간격·모션 모두 최종값. 토큰은 `tokens.ts` 값 그대로다(아래 표).

## Screens

### 데스크톱 (1440×900, `screenshots/desktop-*.png`)
- **헤더** (padding 20 40 12): ‹ 44px 흰 원 · 제목 18/24 700 + 요약 13/18 `#6F6F6F`(「9월 23일 (수) · 4곳 · 약 22만원 · 이동 116분」) · 오른쪽 알약 `동행 초대` `날씨`(min-h 40, radius full, 흰 바탕, 열린 것은 `#191919`/흰 글자) + ⋯ 40px.
- **코스 줄** (padding 0 40 16): 「추천 코스」 13 700 muted · **3분할 알약**(트랙 `#E9E9EC` padding 4, 칸 220px min-h 40, 고른 칸 `#191919` 흰 글자 「✓ 코스 A · 22.0만원 예상」, 인디케이터 translateX 360ms cubic-bezier(.2,.8,.2,1)) · 「추정」 칩(`#FFF3DC`/`#B06A00`) · 오른쪽 끝 **보기 전환 세그먼트** 「장소 카드 | 큰 지도」(각 132px, 인디케이터 `#2B2B2E` 320ms).
- **본문** (padding 0 40 32, gap 24)
  - 장소 카드 모드: 4열 그리드 gap 12. 카드 흰색 radius 20 padding 10, 선택 시 2px `#D83A48` 테두리. 번호 24px 원 + 시각 15 700 + 갈래 13 muted / 이름 18/24 700 / 들어오는 구간 13 muted / 1:1 이미지 칸(radius 14, `#F0F0F3`, 좌상단 추정·위험 칩) / 비용 15 700 + 머무름 13 `#444` / 「메모 추가」 칸(min-h 40, `#F5F5F7`).
  - 아래 3칸 (1.2fr 1fr 1fr): 예산 대비(총액 26/34, 10px 막대 `#2B2B2E`/`#E9E9EC`, 남은 금액 `#2E9E5B`) · 이동(분) · 확인할 것(위험이면 `#B02A38`, 없으면 `#2E9E5B` 「하루 안에 여유 있게 끝나요」).
  - 지도: 440px 고정, radius 20. 좌상단 요약 칩, 우상단 ⤢ / + − 버튼(40px, radius 14, 그림자 0 2 8 rgba(25,25,25,.08)).
  - 큰 지도 모드: 왼쪽 320px 목록(행: 48px 이미지 · 번호 · 이름 · 시각·구간, 선택 시 빨간 테두리) + 지도 flex 1.
- **동행 초대** — 가운데 520px 창(radius 20, padding 24), 배경 dim rgba(25,25,25,.4). 역할 라디오 카드 2개(함께 편집 / 보기만 허용 — 문구는 share.tsx 그대로), 「초대 전 확인」 안내, 버튼 `참여자·역할 관리`(tertiary) + `편집자 초대 링크 만들기`(primary `#D83A48`), 구분선 아래 읽기 전용 링크. 등장: opacity 240ms + translate(-50%,-46%→-50%) 320ms.
- **날씨** — 오른쪽 420px 서랍(`#F5F5F7`, 그림자 -8 0 24), translateX 360ms. 현재 카드(34px 기온 + 하늘·범위) → **1시간별 예보** 7열 그리드(시각·하늘·기온·강수%; 일정 있는 시간은 2px `#2B2B2E` 테두리 + 「9시·1」; 강수 ≥30% 는 `#2C64B5`) → 「들를 때 날씨」 장소별 행.

### 모바일 (390×844, `screenshots/mobile-*.png`)
- **지도가 바탕**, 좌상단 요약 칩(「장소 4곳 · 이동 116분」). 좌상단 뒤로 버튼 **없음** — 모든 이동은 하단 탭바에서.
- **탭바 = 창.** 한 요소가 크기만 바꾼다(bottom 34, radius 20, 가운데 정렬):
  - 일정(기본): 361×574, `#F5F5F7`
  - 접힘: 328×64, 흰색 — 일반 탭바. 가운데 `여행 만들기` 자리가 **「일정 펼치기」**(동백 원 + 흰 위쪽 셰브론), 마지막 `마이페이지` 자리가 **「뒤로」**(연회색 원 + ‹).
  - 동행 초대: 361×640 흰색 / 날씨: 361×600 `#F5F5F7` — 뒤에 dim.
  - 모션: width·height 420ms cubic-bezier(0.34,1.3,0.64,1), 안의 내용은 opacity 260ms 교차, 탭 줄은 200ms.
  - 창 맨 위 손잡이(36×4 `#DADCE2`)를 누르면 접힘.
- **일정 창 내용** (padding 4 16 16, gap 12): 제목 26/34 + 요약 · ⋯ / 알약 3개 `지도 보기`(=접기) `동행 초대` `날씨`(각 flex 1, min-h 44, radius 14) / **「지금」 카드**(`#191919` radius 20) / **추천 코스 카드**(3분할 알약: 칸마다 코스명 13 700 + 가격 11) + 경로 한 줄 / 위험 띠 / **카드 타임라인** / 예산 / 순서 수정.
- **카드 타임라인**: 그리드 48px | 1fr. 왼쪽 세로선 2px `#DADCE2`(left 23). 첫 칸은 요일 13 700 + 날짜 32px 원(`#D83A48`), 나머지는 10px 점(`#DADCE2`, 4px 바탕색 링). 카드 사이 32px 줄에 구간 12/16 700 `#8B8B8B`. 카드 흰색 radius 20 padding 10: 64px 썸네일(radius 14) · 이름 15/21 700 · 「09:46 · 카페 · 6,000원」 · 🔓. 누르면 펼침(예상 도착 · 도착 찍기 · 제외) + 지도가 그 장소로 이동.
- **실시간 위치 (▶ 출발 후)**:
  - 「지금」 카드 → 「지금 · 위치 추적 중」, 「늘리로 이동 중」/「늘리에 도착했어요」, 「4곳 중 1곳 다녀옴 · 다음 11:53」, 6px 진행 막대(`#F25454`), `⏸ 일정 중지` `건너뛰기`(rgba(255,255,255,.16)), 「도착은 GPS로 자동 기록돼요」.
  - 타임라인에 **내 위치 점**(16px `#F25454`, 흰 3px 테두리, 1.6s 맥동 링)이 이동 중에는 구간 줄 안에서 위→아래로, 도착하면 그 장소 점 자리에.
  - 이동 중인 구간 글자 `#B02A38` 「가는 중 · 이동 21분」. 지난 장소: 점이 18px `#2E9E5B` ✓ + 카드에 「✓ 다녀옴」(`#E7F5EC`). 현재 장소 카드에 「● 지금 여기」(`#FEF0F1`).
  - 실제 구현: 위치 = 현재 구간 진행률(GPS 또는 시각 기준). `src/plan/tripProgress.ts` 의 stepStates·drift 를 그대로 쓴다. 조사(로/으로)는 받침 판정.
- **접힘(지도)**: 지도가 커지고 탭바 위 106px 에 정차지 카드 줄(150px, 그림자 0 4 12 rgba(25,25,25,.12)).
- **동행 초대 / 날씨**: 데스크톱과 같은 내용, 세로 배치. 날씨의 시간별 예보는 가로 스크롤 60px 칸.

## Interactions
- **코스 확정**
  - 모바일: 추천 코스 카드의 알약을 누르면 카드가 살짝 커지며(max-height 0→64px, 380ms) 안내 문구 + `코스 A로 확정`(primary, min-h 44)이 나온다. 확정하면 카드 전체가 접힌다(max-height→0, opacity→0, margin-top -12px 로 gap 상쇄, 420ms). 제목 아래 「✓ 코스 A 확정 · 바꾸기」 칩을 누르면 다시 열린다. (접히는 래퍼는 스크롤 열 안의 flex 자식이라 `flex:none` 필수 — 없으면 0 으로 눌린다.)
  - 데스크톱: 코스 줄 3분할 알약 오른쪽에 `코스 A로 확정`(primary 알약, min-h 40). 확정하면 알약이 「✓ 코스 A 확정 · 22.0만원 예상」(흰 알약, 초록 글자) + `코스 바꾸기` 텍스트 버튼으로 바뀐다.
  - 서버: 확정 = recommendations.tsx 의 `build(course)` 흐름(이름 묻기 포함). 확정 전에는 어느 코스도 내 일정이 아니다.
- **장소를 누르면 지도가 그 위치를 가운데로** (모바일·데스크톱 공통). 520ms cubic-bezier(.2,.8,.2,1). 선택 표시: 빨간 링 44–52px(맥동 1.8s) + 아래 검은 이름표. 지도 끝을 넘지 않게 가둔다(가장자리 장소는 가운데에서 조금 비켜남). 실제로는 `RouteMap` 에 `panTo(latlng)` + 선택 마커 scale 1.25(이미 있음).
- 코스 알약을 바꾸면 요약·카드·타임라인·예산·위험·날씨가 그 코스로 바뀐다. 모바일은 진행 상태를 초기화한다.
- 데스크톱 「장소 카드 | 큰 지도」 전환은 초대/날씨 열기와 독립(열어도 보기 모드 유지).

## State
- `course: 0|1|2`, `confirmed: boolean`, `touched`(모바일: 알약을 눌렀나 — 확정 줄을 펼칠지), `selectedStopId`, `layout: 'cards'|'map'`(데스크톱), `panel: 'trip'|'collapsed'|'invite'|'weather'`(모바일), `overlay: null|'invite'|'weather'`(데스크톱), `role: 'EDITOR'|'VIEWER'`, `progress`(기존 tripProgress), `expanded`, `locked`.
- 데이터: 코스 3안(`loadTripCourses` — 지금은 1안만 옴, `full=false`), 일정, 예산(`loadTripBudget`), 시간별 예보(**새 API 필요** — 지금은 출발일 하루치만 있음), 초대(share.tsx 흐름 그대로).

## Design Tokens (tokens.ts)
- canvas `#F5F5F7` · card `#FFFFFF` · tint `#F0F0F3` · soft `#E9E9EC` · field `#DADCE2` · border `#EBEBEF`
- action.primary `#D83A48` · secondary `#2B2B2E` · navy `#191919`
- text body `#444444` · muted `#6F6F6F` · inactive `#8B8B8B` · onDarkMuted `#DADADF`
- danger `#B02A38` / dangerBg `#FEF0F1` · dot `#F25454` · success `#2E9E5B` / `#E7F5EC` · warning `#B06A00` / `#FFF3DC` · info `#2C64B5`
- radius 8 / 14 / 20 / 999 · spacing 4 8 12 16 24 32 (+ 데스크톱 gutter 40)
- type (Pretendard): 12/16(0.2) · 13/18 · 15/23 · 18/24 · 26/34(-0.2) · 34/40(-0.25)
- 그림자: 탭바/창 `0 -2px 14px rgba(25,25,25,.10)` · 지도 위 칩 `0 2px 8px rgba(25,25,25,.08~.12)`

## ⚠️ 레퍼런스에서 가짜인 것
- 지도는 **카카오맵 캡처 이미지**(`assets/kakao-course-map.jpg`)와 손으로 잡은 마커 좌표다 → 실제 `RouteMap` 으로.
- 코스 B·C 의 장소·시각·비용, 날씨·시간별 예보 수치는 **예시**.
- 머무름 시간 = 다음 시각 − 현재 시각 − 다음 구간 이동.
- 실시간 위치는 타이머로 흉내낸 것이다.

## Files
- `design/TripPageCanvas.dc.html` — 모든 상태 캔버스
- `design/TripPageDesktop.dc.html`, `design/TripPageMobile.dc.html` — 화면 본체 (로직·수치는 각 파일 아래 `class Component`)
- `design/reference-original/` — 현재 배포본 재현
- `screenshots/` — 데스크톱 4장(기본·큰 지도·동행 초대·날씨), 모바일 4장(일정·접힘·동행 초대·날씨)
- 대상 코드: `app/trips/[id]/recommendations.tsx`, `app/trips/[id]/itinerary.tsx`, `app/(trip)/[id]/share.tsx`, `app/(trip)/[id]/prepare.tsx`, `src/components/TabBar.tsx`, `src/map/RouteMap.tsx`, `src/plan/NowCard.tsx`, `src/plan/CourseCard.tsx`
