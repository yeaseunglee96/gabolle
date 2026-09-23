# Handoff: 마이페이지 (/me)

## Overview
가볼래 앱의 마이페이지. 폰(390)은 프로필 카드 + 「기록 | 설정」 탭, 넓은 화면(1440)은 전폭 커버 + 기록 격자 + 2열 설정. 대상 파일: `app/(tabs)/me.tsx` (가볼래-프론트-배포본).

## About the Design Files
`mypage.dc.html`은 **HTML로 만든 디자인 레퍼런스**입니다. 그대로 배포하지 말고, 기존 코드베이스(Expo / React Native + web)의 컴포넌트·토큰·패턴으로 재구현하세요. 브라우저에서 파일을 열면(같은 폴더의 `support.js` 필요) 탭·언어·토글을 직접 눌러볼 수 있습니다.

## Fidelity
**High-fidelity.** 색·타이포·간격·인터랙션 모두 최종값. 픽셀 단위로 맞춰 주세요.

## Screenshots
- `screenshots/01-mobile-records.png` — 모바일, 기록 탭
- `screenshots/02-mobile-settings.png` — 모바일, 설정 탭
- `screenshots/03-desktop.png` — 데스크톱 1440

## 이번 변경점 (기존 me.tsx 대비)
1. **모바일 프로필 편집 버튼**: 커버 이미지(120px)와 흰 카드 경계선에 세로 중앙 정렬. 아바타(80px)와 버튼이 같은 행에서 `align-items: center`, 행은 `margin-top: -40px` → 둘 다 경계선 중심.
2. **기록 | 설정 탭 애니메이션**
   - 빨간 인디케이터가 슬라이드: `position:absolute; top/bottom/left:4px; width:calc(50% - 4px)`, 설정 선택 시 `translateX(100%)`. `transition: transform 320ms cubic-bezier(0.2,0.8,0.2,1)`, 그림자 `0 2px 8px rgba(216,58,72,0.28)`.
   - 탭 글자색 `#FFFFFF`(선택) / `#444444`, `transition: color 240ms ease`.
   - 콘텐츠 진입: 기록은 왼쪽에서(`translateX(-24px)→0`), 설정은 오른쪽에서(`24px→0`), opacity 0→1, 320ms 같은 easing. 첫 로드 시 기록 탭은 애니메이션 없음.
   - RN에서는 Reanimated `withTiming` + `FadeIn/SlideIn` 계열로 구현 권장.
3. **데스크톱 하단 레이아웃**: 3열 → 2열 `grid-template-columns: 1fr 1fr; gap: 24px; padding: 40px 40px 64px; align-items: start`.
   - 왼쪽: 내 여행(가로형 카드) → 내 계정 리스트 → 로그아웃
   - 오른쪽: 앱(언어, 알림·차단·도움말·약관, 맞춤 추천 토글)

## Screens

### 모바일 (390×844)
- 스크롤 영역 padding `24px 24px 112px`, 배경 `#F5F5F7`, 스크롤바 숨김.
- 헤더: eyebrow "내 계정" 13/18 700 `#6F6F6F`, 타이틀 "마이페이지" 26/34 700 ls -0.2.
- 프로필 카드: radius 20, border 1px `#EBEBEF`, 흰 배경. 커버 120px. 아바타 80px 원, 4px 흰 테두리, `#2B2B2E`. 프로필 편집 버튼 min-h 44, padding 0 16, radius 999, `#D83A48` 흰 글자 15 700. 이름 26/34 700, 이메일 13/18 `#6F6F6F`. 카운트 칩(기록/팔로워/팔로잉) min-h 32, `#F0F0F3`, 숫자 18/24 700 + 라벨 13.
- 세그먼트 탭: margin-top 24, padding 4, radius 999, `#E9E9EC`, 각 탭 min-h 44, 15 700.
- 기록 탭: 격자/달력 토글(선택 `#191919` 흰 글자), 필터 칩 한 줄 가로 스크롤(nowrap, 1px `#DADCE2`), 2열 카드 gap 12 (정사각 이미지 또는 텍스트 카드, radius 20), "새 기록 남기기" 점선 카드.
- 설정 탭: 섹션 라벨 13/18 700 `#6F6F6F` (margin 24 0 8). 리스트 카드 radius 20 흰색, 행 min-h 62, padding 12 16, 구분선 1px `#EBEBEF`, 라벨 15/23 700, 설명 13/18 `#444`, 값 13 `#444` + "›". 언어 칩 min-h 48, radius 14, 선택 `#191919`/흰 + "✓ ", 비선택 `#E9E9EC`. 토글 44×26, on `#D83A48`, off `#DADCE2`, 노브 20 `#F5F5F7`. 로그아웃 min-h 52, radius 14, `#E9E9EC`.
- 하단 탭바: 328×64 radius 20, 그림자 `0 -2px 14px rgba(25,25,25,0.10)`, bottom 34. 여행 만들기 아이콘 배경 `#D83A48`.

### 데스크톱 (1440)
- 상단 유틸 바 36px `#E9E9EC`(국기, 알림, 이름) / 네비 60px 흰색(로고 154×28, 홈·피드·내 여행, "여행 만들기" 아웃라인 버튼 `#D83A48`).
- 커버 420px, 그라디언트 `rgba(25,25,25,.10) 0% → .55 60% → .80 100%`. 아바타 152px, 이름 34/40 700 흰색, 서브 15/23 `#DADADF`, 반투명 카운트 칩. 프로필 편집 흰 버튼 min-h 48, 그림자 `0 5px 12px rgba(25,25,25,0.16)`.
- 기록: "김민지의 기록 6개" 18/24 700, "기록 관리 ›", 4열 격자 gap 12.
- 하단 2열 (위 변경점 3 참고). 내 여행 카드: 좌측 텍스트(다가오는 여행 13 700 `#2E9E5B`, 제목 18/24 700, 날짜 15/23 `#444`), 우측 "일정 보기 →" 15 700.

## State
- `tab: 'records' | 'settings'` (모바일)
- `lang: 'ko'|'en'|'ja'|'zh-Hans'|'zh-Hant'`
- `personalized: boolean` (기기 로컬 저장)
- 달력 보기 전환은 아직 미구현(버튼만 존재).

## Design Tokens
- 색: primary `#D83A48`, ink `#191919`, text-2 `#444444`, text-3 `#6F6F6F`, bg `#F5F5F7`, surface `#FFFFFF`, fill `#E9E9EC`, chip `#F0F0F3`, line `#EBEBEF`, border `#DADCE2`, dark `#2B2B2E`, success `#2E9E5B`
- 간격: 4 / 8 / 12 / 16 / 24 / 32 / 40 / 64
- Radius: 14 (버튼·언어칩), 20 (카드), 999 (pill)
- 타입(Pretendard): 12/16, 13/18, 15/23, 18/24, 26/34, 34/40 · 400/500/700

## Assets
`assets/` (배포본에서 가져옴): 로고, 탭 아이콘, 국기, 알림 아이콘, 부산 사진(`web-hero.png`, `busan-night.jpg` — 기록 사진은 임시). `fonts/` Pretendard. 이름·숫자·여행 데이터는 더미.

## Files
- `mypage.dc.html` — 디자인 레퍼런스 (모바일 #1a, 데스크톱 #1b)
- `support.js` — HTML 레퍼런스 실행용 런타임 (구현에는 불필요)
- `screenshots/`, `assets/`, `fonts/`
