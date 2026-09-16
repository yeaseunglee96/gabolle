# Handoff: TopNav (넓은 화면 상단 바) — 안 1b′ · 2단 + 캡슐 내비

## Overview
`src/nav/TopNav.tsx`(≥600px에서만 그려지는 단 하나의 상단 바, `app/_layout.tsx`가 한 번 붙임)를 2단 구조로 바꾼다. 지금은 56px 한 줄에 링크·CTA·언어·계정이 같은 간격으로 붙어 이동과 계정이 구분되지 않고 높이도 들쭉날쭉하다.

## About the Design Files
`TopNav-1b.dc.html`은 HTML 디자인 참조(1280px 바 하나). RN 코드베이스의 `Text` `BrandLogoLink` `@/design/tokens`로 다시 구현한다. `support.js`·`assets/logo.png`는 프로토타입 렌더용.

## Fidelity
High-fidelity. 값은 전부 `tokens.ts`. 링크 구성·라우팅·활성 판정(`LINKS`, `isPlanRoute`, `isChromeless`)·`ready`/`signedOut` 로직은 기존 그대로 유지한다.

## 구조 (총 96px)
### 1층 · 유틸 바 — 높이 36, 배경 `surface.soft`(#f8f3eb), 좌우 padding 40, 우측 정렬, gap 20
- 언어: 「KO」 `caption`급 13px bold `text.muted` / 구분자 「|」 `surface.field` / 「EN」 13px medium muted. 현재 언어가 bold. 누르면 `setPreferences(nextLanguage, mobility)` (기존 동작).
- 세로선 1×14 `surface.field`.
- `signedOut`: 「로그인」 13px bold muted → `/sign-in` / 「회원가입」 13px bold `brand.navy` → `/sign-up`.
- 로그인함: 「회원가입」 자리에 `user.displayName`(13px bold navy, maxWidth 160, numberOfLines 1) → `/me`. 「로그인」은 숨김.
- 각 항목 누를 영역 최소 36 높이(바 전체), 좌우 padding 4로 hit 확보.

### 2층 · 주 내비 — 높이 60, 배경 `brand.ivory`, 하단 1px `surface.border`, 좌우 padding 40, `justify-content: space-between`
- **좌**: `BrandLogoLink` 120×28.
- **가운데 캡슐**: 컨테이너 `surface.soft`, radius full, padding 4, gap 4. 항목 = 높이 40, padding 0 20, radius full, `body`(15/22).
  - 비활성: 배경 투명, medium, `text.body`.
  - 활성: 배경 `surface.card`(#fff), bold, `brand.navy`, shadow `0 1 2 rgba(11,29,58,.08)`(elevation 1). 기존 18×3 orange 마커는 **캡슐 안에서는 안 쓴다**(흰 카드가 활성 표식).
  - 항목: 홈(`/` + `/home`) · 피드 · 내 여행. 로그인 시 「마이페이지」는 캡슐에 넣지 않는다 — 1층의 이름 버튼이 `/me`로 간다(기존 주석과 같은 판단).
- **우**: CTA 「여행 만들기」 — 높이 40, padding 0 16, radius `md`(14), `brand.navy`, `body bold onAction`. `planActive`일 때 기존처럼 CTA 바닥에 18×3 orange 마커(bottom −10 → 이 구조에선 CTA 아래 여백이 10이라 그대로 맞음).

## 반응형
- `kind !== 'tablet'`이면 기존처럼 null(폰은 TabBar).
- 600~1023: 캡슐이 가운데 오도록 `space-between` 유지. 캡슐 폭 ≈ 260, 로고 120, CTA ≈ 120 → 600에서도 여백 ≥ 40. 가로 스크롤 없음(폭 768·1024·1440·1920 확인).
- 1층·2층 모두 SafeArea top은 기존 `SafeAreaView edges=['top']`이 1층 위에 두른다(배경 soft까지 칠함).

## Design Tokens
navy #0b1d3a · ivory #fffdf8 · card #fff · soft #f8f3eb · field #e4ddd3 · border #e8e4dd · heading #152238 · body #667882 · muted #64748b · orange #f26532 · onAction #fff
13px 유틸 글자는 caption(11)과 body(15) 사이 — `type`에 없으므로 **caption을 쓰고 line-height 18로만 조정**하거나, 팀 결정으로 13을 추가. 시안은 13.

## Files
- `TopNav-1b.dc.html` — 선택된 안(1b′) 바 하나, 1280 폭
- `support.js`, `assets/logo.png` — 렌더용
