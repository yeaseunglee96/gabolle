# Handoff: 마이페이지 /me — 프로필 카드 + 모달 메뉴

## Overview
`app/(tabs)/me.tsx`(폰 메뉴 목록)와 `/me/profile`(데스크톱 리다이렉트 + 좌 사이드바 편집 폼)로 나뉘어 있던 마이페이지를 **하나의 /me 화면**으로 합친다.
1. 위: **프로필 카드** — 커버 사진이 아래로 흰색에 녹고, 아바타가 커버에 걸쳐 앉는다. 우측 「프로필 편집」 버튼 하나.
2. 아래: 기존 메뉴(내 계정 / 앱)를 그대로 두되, **각 행은 라우트 이동이 아니라 모달(폰은 바텀시트)** 로 연다.
3. 프로필 사진·배경 사진·닉네임·소개·거주지·언어는 **「프로필 편집」 모달에서만** 바꾼다. 카드 위 「커버 바꾸기」 버튼, 폰 톱니바퀴 아이콘은 없다.

## About the Design Files
- `MyPage.dc.html` — 데스크톱 1440 + 폰 390. 메뉴 행을 누르면 실제로 모달이 열린다(내용은 MODALS 맵 참고). 트윅 `hasCover`(커버 없음 상태).
- `support.js` `ios-frame.jsx` `assets/` `fonts/` — 프로토타입 렌더용. **HTML은 참조이고 Expo/RN(`Screen` `Button` `Text` `Modal`, `@/design/tokens`)로 다시 구현한다.**

## Fidelity
High-fidelity. 값은 모두 `src/design/tokens.ts`. 새 색 없음. 데스크톱(lg↑)과 폰은 같은 컴포넌트가 넓어지는 것.

---

## 1. 라우팅
- `app/(tabs)/me.tsx` 51행의 `isAtLeast(width,'lg') → Redirect /me/profile` **제거**. 데스크톱도 /me 본체를 그린다.
- `/me/profile` `/me/preferences` `/me/identities` `/me/blocked` `/me/terms` `/me/posts` 라우트는 딥링크용으로 남겨도 되지만, /me 안에서는 **push 하지 않고 모달**로 연다. 모달 상태 `{ modal: null | 'edit' | 'records' | 'followers' | 'following' | 'preferences' | 'conditions' | 'identities' | 'notifications' | 'blocked' | 'help' | 'terms' }`.

## 2. 프로필 카드 (새 `src/me/ProfileCard.tsx`)
### 구조
```
┌ 카드 radius 24 · card bg · border · shadow 0 8 24 rgba(11,29,58,.06) ┐
│ 커버 300(폰 240) — 사용자 커버 or 기본 부산 사진(assets/…)          │
│   overlay: linear-gradient(180deg, transparent 35%, rgba(255,255,255,.55) 70%, #fff 100%) │
│ 본문 padding 0 32 28 (폰 0 24 20), marginTop −72 (폰 −56)          │
│   [아바타 112 (폰 88), border 5px(4px) #fff, shadow]   [프로필 편집 알약 44 navy] │
│   이름 28/34 bold (폰 display 22/28)                                 │
│   한 줄 소개 body heading                                            │
│   메타 caption muted: 💼 역할·기록 n · 📍 거주지 · 📅 가입월        │
│   팔로잉 n · 팔로워 n · 기록 n (숫자 body bold heading, 라벨 caption muted; 각각 모달) │
└──────────────────────────────────────────────────────────────────────┘
```
- 커버 데이터: `user.coverImageUrl`(없으면 기본). 아바타: 기존 `avatarUri` 없으면 이니셜 navy 원.
- 한 줄 소개 `user.bio`(≤60), 거주지 `user.homeCity` — **서버 필드 추가 필요**(bio·homeCity·coverImageUrl). 없으면 줄 자체를 그리지 않는다.
- 폰: 카드는 화면 상단에 붙는다(radius 없음, 아래 border만). 커버 위 좌상단 「내 계정」 eyebrow 배지(ivory 90%).
- 데스크톱: 본문 maxWidth 1200, 2열 grid `minmax(0,1fr) 360px` gap 24 — 좌 카드 + 「내 기록」 미리보기 4열, 우 메뉴.

## 3. 메뉴 (기존 me.tsx 항목 그대로 + 「여행 조건」 1행 추가)
그룹 카드: radius 20, card bg, border; 행 56, padding 0 20(폰 16), 하단 hairline `#f1ede6`; 좌 label body 500 + 선택적 caption 설명, 우 value caption muted. hover `surface.soft`.
| 그룹 | 행 | 우측 값 | 모달 |
|---|---|---|---|
| 내 계정 | 내 기록 | n개 | 기록 목록(보기) + 「첫 기록 남기기」 |
| | 팔로워 / 팔로잉 | n | 사람 목록 + 팔로우 버튼 |
| | 여행 취향 | 8 / 8 | 카테고리·기분·분위기·척도·음식 각 「수정」 |
| | **여행 조건** (신규) | 알레르기 없음 · 채식 | 알레르기·식단·이동 환경 「수정」 + **「다시 묻기」 켜기**(conditionsPromptState → null) |
| | 연결된 소셜 계정 | › | Google 연결됨 · Kakao/Apple 연결 |
| 앱 | 알림 | › | 댓글·좋아요 / 일정 리마인드 / 추천 소식 토글 |
| | 차단된 계정 | › | 목록(없으면 빈 상태 1행) |
| | 도움말·문의 | › (설명 1줄) | 앱 소개 · FAQ · 문의 메일 |
| | 약관·고지 | › | 이용약관 · 개인정보 · 데이터 출처 · 오픈소스 · **회원 탈퇴(danger)** |
- 그 아래 **「맞춤 추천」** 카드(기존 「행동으로 추천 다듬기」 라벨 변경): 제목 body bold + 설명 caption 「저장·제외·일정 수정·체크인 후기 같은 활동을 바탕으로 추천을 맞춰요. 이 설정은 이 기기에 저장돼요.」 + 토글 48×28(navy on / field off).
- 「로그아웃」 ghost 48 radius 14 → 기존 확인 모달.

## 4. 모달 셸 (새 `src/me/MeSheet.tsx`)
- 데스크톱: 센터 모달 520(프로필 편집은 640), max-height 88%, radius 24, ivory bg, shadow 0 24 64 rgba(11,29,58,.3). 배경 rgba(11,29,58,.45). 등장 popIn .28s(opacity 0→1, translateY 12→0, scale .98→1), 배경 fadeIn .2s.
- 폰: 바텀시트 radius 24 상단, max-height 92%, slideUp .3s(translateY 40→0). 하단 여백 36.
- 헤더 64: 제목 title bold 가운데, 우측 ✕ 44. 본문 스크롤 padding 24(폰 20/24).
- 일반 모달 본문: 설명 body muted + 행 목록(56, radius 14, card bg, border; 좌 label bold + caption, 우 액션 칩 caption bold — 기본 soft / navy(주요) / dangerBg+danger(파괴)).
- 배경 탭·✕로 닫힘. 안쪽 클릭 전파 차단.

## 5. 프로필 편집 모달
- 상단 미리보기 160 radius 20: 커버 + 우상단 「📷 배경 사진 바꾸기」 알약, 좌하단 아바타 88(카메라 배지 32) + 「사진 바꾸기」「기본으로」 칩.
- 필드(48, radius 14, field border): 닉네임(≤30, 카운터 「7 / 30 · 피드와 기록에 보여요」), 한 줄 소개(≤60), 거주지 / 이메일(변경 불가, muted) 2열, 언어(한국어 navy / English ghost 2열).
- 푸터: 「취소」 텍스트 + 「저장」 navy 48 radius 14. 저장 → 기존 `/me/profile` 저장 로직(닉네임·사진) + 신규 bio·homeCity·cover.
- 이미지 선택은 기존 `expo-image-picker` 흐름 재사용.

## Common Mistakes
- 카드 위에 「커버 바꾸기」를 다시 넣지 않기 — 편집은 모달 안에서만.
- 폰 헤더에 톱니바퀴/설정 아이콘 없음.
- 메뉴 행을 `router.push`로 보내지 않기 — 모달. (딥링크 라우트는 유지 가능)
- 「행동으로 추천 다듬기」 문구 쓰지 않기 → 「맞춤 추천」.
- bio·homeCity·coverImageUrl 이 null 이면 「미입력」이라 적지 말고 줄을 생략.
- 커버 오버레이는 흰색 그라데이션(라이트 테마만). 다크 테마 없음.
