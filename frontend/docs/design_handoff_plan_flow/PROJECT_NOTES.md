# 가볼래 (GABOLLE) 디자인 프로젝트

원본: 첨부 폴더 `gabolle-screens` (Expo/React Native). 화면별 소스+스크린샷은 `gabolle-screens/screens/*.html|png`, 토큰은 `source/src/design/tokens.ts`.

## 토큰 (tokens.ts)
- 색: navy #0b1d3a · orange #f26532 · ivory/canvas #fffdf8 · card #fff · tint/warm #fff1e8 · soft #f8f3eb · field #e4ddd3 · subtle #f6f5f2 · border #e8e4dd
- 글자: heading #152238 · body #667882 · muted #64748b · eyebrow/accent #f26532 · onAction #fff
- 상태: danger #e85d5b / dangerBg #fff0ee · success #30a687 / successBg #e7f8ef · warning #a46700 / warningBg #fff3d7 · rating #ffa11f
- 타입(Pretendard): caption 11/14 +.1 · body 15/22 · title 18/24 · display 22/28 -.15 · hero 34/40 -.25 · 홈 히어로 36/42 -.3
- radius sm 8 · md 14 · lg 20 · full 999 / spacing 4·8·12·16·24·32 / gutter 24
- Button: 전폭, minHeight 48, radius 14, padding 12 — primary navy / ghost 흰 배경+field 테두리+navy 글자
- TabBar: 카드 위 플로팅, maxWidth 328, height 64, radius 20, 활성 마커 18×3 orange

## 파일
- `Home.dc.html`, `Trips.dc.html` — iOS 프레임(390×844) 안 재현. `signedIn` 트윅으로 로그인/비회원 상태 전환.
- `Feed.dc.html` — (구) 피드 탭, 옛 56 단일 바. **`Feed v2.dc.html`가 확정안**: 모바일 = 사진 카드형(정방형 커버, 제목=장소명 없으면 「OO의 기록」, 사진 없는 글은 tint 카드에 본문 크게, 「지도 표시하기」 플로팅) · 데스크톱 = 2열 카드 + 우측 520 지도 고정(핀 라벨=장소명). `signedIn` 트윅. 탐색 시안은 `FeedList.dc.html`(1a–1f).
- `FeedDetail.dc.html` — 기록 상세 /feed/[id] 시안. **2a 확정 방향**: 사진 그리드 → 장소 제목(hasPlace) → 좋아요·인용(링크 복사 수)·조회(글 클릭 수) 행 → 원글+댓글을 같은 글 컴포넌트로 구분선만 두고 이어 붙임 → 알약 입력창(아바타+입력+↑). 트윅 `mine`(수정 버튼) · `hasPlace`.
- 폰트: `fonts/Pretendard-*.woff2` 로컬 @font-face(Regular·Medium·SemiBold·Bold). 새 파일은 CDN 대신 이걸 쓴다.
- `TripName.dc.html` — 새 화면 「이 여행에 이름 붙이기」: 6개 상태(받는 중·후보·빈 배열·직접 쓰기(라이브 입력)·저장 중·저장됨) + 이어지는 카드(이름/날짜). 트윅 `source`(MODEL/TEMPLATE) · `discardedCount`.
- `ExploreScreen.dc.html` — /explore 화면 본체(props: width·state·scope·facetCount·lang, width로 tier 판단). `Explore.dc.html` 7폭 캔버스, `ExploreStates.dc.html` 상태 10장 시트. 핸드오프 `design_handoff_explore/`(스크린샷 포함).
- TopNav(src/nav/TopNav.tsx): 2026-09-16부터 **2단** — 유틸 36(soft, KO|EN · 로그인 · 회원가입, util 13/18) + 주 내비 60(ivory, 로고 120×28 · 캡슐 홈/피드/내 여행 · navy CTA 「여행 만들기」 40/14). 좌우 40. Feed.dc.html은 아직 옛 56 단일 바.
- `assets/` — 스크린샷에서 잘라낸 로고·마스코트·탭 아이콘(원본 폴더에 에셋 파일 없음, 1× 저해상도).
- 새 화면 추가 시 위 두 파일 복제 후 수정.

## Claude Code 인계 메모 (PlanFlow.dc.html)
- p0 홈 「광안리 · 이번 주 가장 많이 본 기록」 등 이유 줄은 **표현만 그렇고 배포에서는 피드 전체 미리보기**로 동작(서버 sections[{reason, items}] 준비 전까지는 최신 피드 N개).
- 여행 조건 모달(알레르기·식단·이동 환경 = 개인화 특성): 로그인 후 홈 첫 진입 시 1회. 「나중에」→ 이후 「일정 물어보기」마다 모달로 다시 묻기. 「다시 묻지 않기」/「저장」→ 더 묻지 않음, 마이페이지에서만 수정. 사용자별 상태 `conditionsPromptState: LATER | NEVER | SAVED`.
- 휠체어·유아차·큰 짐은 여행마다 달라 모달이 아니라 p1 질문(「이번 여행 이동 보조 · 짐」)에서 매번 받음.
- p1 = 기본+취향 한 페이지(진행형, 답하면 다음 카드 생성 + 자동 스크롤). 날짜·인원·출발지는 p0 바에서 받아 넘김. 구 p2·p3 화면 제거.
- 「다음」 버튼은 항상 radius 14.
