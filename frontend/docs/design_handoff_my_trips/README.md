# Handoff: 내 여행 탭 — 데스크톱 격자 레이아웃 + 모바일 정리

## Overview
`app/(tabs)/trips.tsx`(내 여행 탭) 수정안. 배포본을 그대로 재현한 뒤 세 가지를 바꿨다.
1. **데스크톱**: 카드가 1440 폭 전체로 늘어지던 한 줄 목록 → **최대 1200, 3열 카드 격자**
2. **부슐랭 버튼 제거**(데스크톱·모바일 모두). 헤더 행동은 「새 여행」 하나만 남는다
3. **모바일 상단 여백 24 축소** — 헤더의 `marginTop: spacing[6]` 제거 (Screen 의 `paddingTop: 24` 와 겹쳐 48 이던 것)

## About the Design Files
`design/내 여행.dc.html` 은 **HTML 로 만든 디자인 참고본**이다. 그대로 옮기지 말고 기존 RN/Expo 코드(`Screen`, `Text`, `Button`, `tokens.ts`)로 재구현한다. 브라우저에서 열면 데스크톱(1440)·모바일(390×844) 두 판이 나란히 보이고, Tweaks 의 `state`(여행 있음/빈 목록/비회원)로 상태를 바꿀 수 있다.

## Fidelity
**High-fidelity.** 색·타이포·간격은 모두 `src/design/tokens.ts` 값 그대로다. 새 토큰은 없다.

## 변경 1 — 데스크톱 레이아웃 (`kind === 'tablet'` / `desktop`)
- **콘텐츠 폭**: `maxWidth: 1200`, 가운데 정렬, 좌우 패딩 24 (Screen gutter). 현재는 `Screen wide` → `MAX_SPLIT_WIDTH 1440`. 이 화면은 `wide` 대신 1200 짜리 스타일을 주거나 `styles.canvas` 에 `maxWidth: 1200, alignSelf: 'center'` 추가.
- **헤더**: `flexDirection: 'row'`, `alignItems: 'flex-end'`, `justifyContent: 'space-between'`, gap 12, marginTop 24 (데스크톱은 유지). 오른쪽 `headerActions` 는 「새 여행」(outline, width 96, minHeight 48) 하나.
- **목록**: `marginTop: 24`, **3열 격자**, gap 16.
  - RN: `flexDirection: 'row', flexWrap: 'wrap', gap: 16` + 카드 `width: (contentWidth - 16*2) / 3` (또는 `flexBasis: '31%'`/`useLayout().width` 로 계산). 웹 전용이면 `display: grid; grid-template-columns: repeat(3, minmax(0,1fr))`.
  - 한 줄의 카드 높이는 같게(stretch). 카드 내부는 `justifyContent: 'space-between'` — 행동 줄(이름 바꾸기 / ⋯)이 늘 바닥에 붙는다.
- **카드 커버**: 높이 **160** (기존 132), radius 14, `object-fit: cover`.
  - ⚠️ 커버 없는 카드: 시안은 격자 줄맞춤을 위해 `surface.tint #F0F0F3` 빈 판(높이 160, radius 14)을 넣었다. 현재 코드(`TripCover`)는 「빈 회색 판은 못 불러왔다로 읽힌다」며 자리를 안 만든다 — **데스크톱 격자에서만 빈 판을 둘지 결정 필요.** 두지 않으면 같은 줄 카드끼리 제목 위치가 어긋난다.
- **배지 줄바꿈 방지**: 좁아진 카드에서 「일정 준비 중」이 두 줄로 깨졌다 → `metaPill`/`statusPillPending` 텍스트에 `numberOfLines={1}` (웹: `white-space: nowrap`).
- 카드 스타일은 기존 그대로: padding 16, radius 20, border 1 `surface.border #EBEBEF`, bg `#FFFFFF`, shadow navy 0.06 / radius 10 / offset y4, 내부 gap 12.

## 변경 2 — 부슐랭 버튼 제거
`trips.tsx` 헤더의 `<Button label={tx('부슐랭', 'My places')} variant="tertiary" … />` 삭제. `/collection` 으로 가는 다른 진입점이 있는지 확인할 것(없어지면 부슐랭 목록 진입 경로가 사라짐).

## 변경 3 — 모바일 상단 여백
- 현재: 안전 영역(기기) + `Screen.paddingTop 24` + `styles.header.marginTop 24` = 안전 영역 + 48
- 변경: 안전 영역 + 24 → **모바일(`kind === 'phone'`)에서만** `header.marginTop: 0`. (데스크톱은 TopNav 아래 24+24 유지)
- 안전 영역 47 은 시안의 아이폰 가정 값일 뿐, 실제는 `useSafeAreaInsets` 가 준다.

## 그대로인 것 (참고)
- TopNav(데스크톱): 유틸 바 36 (`surface.soft`), 주 내비 60 (흰 바탕, 아래선 `surface.border`), 활성 탭 굵게 + 5×5 `state.dot` 점, 우측 「여행 만들기」 outline CTA.
- TabBar(모바일): 떠 있는 알약 328×64, radius 20, shadow y-2 / 14 / 0.10, 내 여행(`map`) 활성.
- 상태별 화면(비회원·빈 목록·로딩·오류), ⋯ 메뉴, 이름 시트, 삭제 확인 모달 — 변경 없음.

## Design Tokens (사용된 값)
- 색: canvas `#F5F5F7` · card `#FFFFFF` · soft `#E9E9EC` · tint `#F0F0F3` · border `#EBEBEF` · field `#DADCE2` · primary/outline `#D83A48` · heading `#191919` · body `#444444` · muted `#6F6F6F` · inactiveTab `#8B8B8B` · danger `#B02A38` · dangerBg `#FEF0F1` · success `#2E9E5B` · dot `#F25454`
- 타이포(Pretendard): display 26/34 −0.2 · title 18/24 · body 15/23 · caption 13/18 · util 14/20 · micro 12/16 +0.2
- 간격: 4 · 8 · 12 · 16 · 24 · 32 / radius: 8 · 14 · 20 · 999

## Assets
`design/assets/` — 모두 배포본 저장소에서 복사. `icons/*-muted.png`, `map-active.png` 는 시안용으로 틴트를 구운 사본(앱은 `tintColor` 사용). `covers/*` 는 예시 커버(`assets/home/`에서 복사), 실제는 `trip.coverImageUrl`.

## Files
- `design/내 여행.dc.html` — 시안 (데스크톱 `#desktop`, 모바일 `#mobile`)
- `screenshots/my-trips-desktop-1440.png`, `screenshots/my-trips-mobile-390.png`
- 수정 대상: `app/(tabs)/trips.tsx` (주), 필요 시 `src/components/Screen.tsx`
- 예시 데이터(카드 4장, 사용자명 「김부산」)는 시안용이다.
