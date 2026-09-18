# 가볼래 피드 개편 — 디자인 핸드오프 (2026-09-17)

대상: `app/(tabs)/feed.tsx`(목록) · `app/feed/[id].tsx`(상세). 시안 파일을 브라우저로 열어 보며 구현한다 (`Feed v2.dc.html` 더블클릭 → 로컬 파일로 열림. 우측 Tweaks로 상태 전환).

## 확정안
- **목록 `Feed v2.dc.html`** — 모바일 = FeedList 1b(사진 카드형), 데스크톱(1024+) = FeedList 1e(2열 카드 + 우측 520 지도 고정). 트윅 `signedIn`.
- **상세 `FeedDetail.dc.html` → 섹션 `2a`** (맨 위). 나머지(1a–1f)는 탐색 시안, 참고만. 트윅 `mine` · `hasPlace`.
- `FeedList.dc.html` — 목록 탐색 시안 6종. 참고용.

## 목록 카드 (모바일·데스크톱 동일 규칙)
1. 커버: 모바일 높이 300 / 데스크톱 `aspect-ratio:1`, radius 20, 첫 사진 `cover`. 사진 여러 장 → 하단 중앙 점 인디케이터(6px, 첫 점 opacity 1, 나머지 .5, 최대 5개). 좌상단 작성자 알약(아바타 24 + 이름, 배경 `rgba(255,253,248,.92)`), 우상단 하트 원 36.
2. **사진 없는 글**: 커버 자리에 tint(#fff1e8) 카드, 본문을 중앙 정렬로 크게(모바일 22/30 bold, 데스크톱 18/26). 아래 본문 미리보기는 생략.
3. 제목(15/22 bold, 한 줄 ellipsis) = `story.place.name`. 장소 없으면 `${author.displayName}의 기록`. 우측에 하트(#f26532 채움)+좋아요 수.
4. 본문 미리보기 2줄 clamp(15/22 #667882). 메타 한 줄(11/14 #64748b): `{상대시간} · 답글 N · 조회 N`.
5. 모바일 하단 플로팅: 「지도 표시하기」 navy 알약 48(중앙, 탭바 위 96) + 로그인 시 orange 기록 FAB 48. 데스크톱은 우측 지도 패널(width 520, 상하 24 패딩, radius 20) 안에 장소명 라벨 핀(선택 = navy 배경/흰 글자, 나머지 흰 배경).
6. 헤더: 제목 「피드」(데스크톱은 「기록 N개」) + 전체/팔로잉 세그먼트(soft 배경 알약). 비회원은 `팔로잉` opacity .5 + 로그인 안내 배너(soft, 11px).

## 상세 (2a)
순서 그대로: 뒤로(44 원형) … 우상단 **`mine`이면 연필 44 원형, 아니면 ⋯** → 사진 그리드(3열 2행, 각 행 140, gap 6, radius 14; 마지막 셀에 `08+` 오버레이 `rgba(11,29,58,.45)`) → **장소 제목**(22/28 bold + 인증 배지, 아래 핀 아이콘 + 주소 15/22 #667882; `place` 없으면 이 블록 통째로 생략) → **지표 행**(위·아래 1px #e8e4dd, 패딩 12 0): 좌 좋아요(하트 orange stroke, 15 bold) · 인용(링크 아이콘, 15 bold) … 우 눈 아이콘 + 「조회 N」(11px muted) → 글 목록 → 하단 고정 알약 입력창.

- **원글과 댓글은 같은 컴포넌트**(`StoryPost`): 아바타 36 · 이름 15 bold · 시각 11 muted · 본문 · 지표 행(좋아요·인용·조회 11px). 카드 배경 **없음** — 아이보리 위에 `border-bottom:1px solid #e8e4dd`, 패딩 16 0. 원글만 `원글` 칩(tint 배경, orange 11 bold)과 본문 18/26 #152238; 댓글은 15/22 #667882.
- **입력창**: 알약 하나(높이 56, radius 999, 흰 배경, 1px #e4ddd3, 패딩 0 6) 안에 아바타 44 + placeholder 「이 기록에 답글 남기기」 + 전송 원 44(navy, ↑ 아이콘, 비활성 opacity .4). 화면 하단 고정, 패딩 8 16 28.

## 데이터 계약
- `likeCount`, `quoteCount`(링크 복사 누른 횟수 — **DB·API 아직 없음, 추가 예정. 그때까지 0 표시**), `viewCount`(**글을 눌러 상세로 들어온 횟수**. 노출 수 아님), `replyCount`.
- 댓글 = 같은 `StoryDto` 형태 + `parentId`. 별도 Comment 타입 만들지 말 것.
- `place` 있을 때만 제목/핀. `region`은 시각 옆 캡션으로만.

## 자주 틀리는 것
1. 댓글을 별도 카드/들여쓰기/말풍선으로 만들기 — 금지. 원글과 같은 컴포넌트를 구분선만 두고 아래로 이어 붙인다.
2. 상세에서 글마다 흰 카드 배경 넣기 — 없다. 배경은 화면 `#fffdf8` 하나.
3. 조회수를 노출(impression)로 세기 — 클릭(상세 진입) 기준.
4. 사진 없는 글에 회색 빈 커버 넣기 — tint 카드 + 본문 크게.
5. 장소 없는 글 제목을 비워두기 — `「OO의 기록」`.
6. 입력창을 입력 + 별도 「게시」 버튼 두 조각으로 쪼개기 — 알약 하나 안에 아바타·입력·↑ 원 버튼.
7. 폰트: Pretendard(`fonts/` 동봉). Regular 400 · Medium 500 · SemiBold 600 · Bold 700. 시스템 폰트 폴백으로 두지 말 것.
8. 데스크톱 TopNav는 2단(유틸 36 + 주 내비 60). 옛 56 단일 바 아님.

## 파일
- `Feed v2.dc.html`, `FeedDetail.dc.html`, `FeedList.dc.html` — 시안 (support.js, ios-frame.jsx, browser-window.jsx 필요)
- `assets/` 로고·탭 아이콘(1× 저해상도, 원본 에셋으로 교체) · `fonts/` Pretendard woff2
- `screenshots/` 참고 캡처
- 토큰: 프로젝트 `src/design/tokens.ts` 그대로 (navy #0b1d3a · orange #f26532 · canvas #fffdf8 · tint #fff1e8 · soft #f8f3eb · field #e4ddd3 · border #e8e4dd)
