# Handoff: 여행 일정 추천 결과 — B안 「경로 노선도」 · 데스크톱 1440 + 폰 390

## Overview
추천이 끝난 뒤 보는 **일정 초안 화면**. 장소 사진이 DB에 없으므로(`photo_url` 항상 빈 값, 티켓 -480 전) **텍스트 + 경로**만으로 구성했다. 지도 대신 **노선도**: 정차역(장소)을 선으로 잇고, 구간 라벨은 도보 거리.

프로토타입 `가볼래 일정 추천.dc.html` 에서 **1c(데스크톱) · 1d(폰)** 이 확정안. A(1a/1b)·C(1e/1f)는 비교용으로 남겨 두었고 구현 대상 아님. 일차 탭 · 잠금 · 펼침 실제 동작.

## 1. 데이터 계약 — 전부 `src/plan/itinerary.ts` `ItineraryDto` 안
| 화면 요소 | 필드 |
|---|---|
| 제목 · 초안 v{n} · 「모델 추천」 라벨 | `title` · `version` · `fallbackMode` (MODEL→「모델 추천」, RULE→「규칙 추천」, BASELINE→「기본 추천」) |
| 일차 탭 · 날짜 | `days[].date` |
| 정차 시각 · 이름 · 설명 | `items[].startsAt` · `title` · `description` |
| 정차 비용 (「무료」 / 「18,000원」) | `items[].estimatedCostKrw` (0 → 「무료」, null → 표시 안 함) |
| 구간 라벨 「도보 1.2km」 | `items[].walkingMeters` (다음 정차까지. 1000 이상 km 한 자리, 미만 m) |
| 확인됨 / 추정 / 미확인 배지 | `items[].dataStatus` VERIFIED / ESTIMATED / UNKNOWN |
| 🔒 잠금 | `items[].locked` → `POST /items/{id}/lock` (`setItineraryItemLocked`) |
| 이유 딱지 `#자연·산책 취향` | 추천 결과 `RecommendationCourseDto.reasonCodes` → 코드→문구 매핑 (기존 추천 후보 화면과 같은 사전) |
| 혼잡도 | `crowdLevel` LOW/MEDIUM/HIGH → 낮음/보통/높음 (success/heading/orange) |
| 헤더 요약 「도보 n km · 약 n만원」 | `totalWalkingMeters` · `totalEstimatedCostKrw` |
| 헤더 「n곳」 | `days[].items` 합 |

**없어서 안 그린 것 (지어내지 않음)**
- 장소 카테고리 · 주소 · 사진 → 응답에 없음. 사진이 생기면 정차 목록의 40px 번호 원 자리를 사진(40 원형)으로 교체.
- 이동 수단(버스/지하철) · 대중교통 시간 → 「제안 · API 없음」 회색 배지 그대로 두기. 도보 거리만 표시.
- 예상 이동 시간 → 추천 결과 `estimatedTravelMinutes` 가 있을 때만 (프로토타입 A/C 헤더에만 씀, B는 미사용).
- `description` 이 null → 설명 줄 생략(줄 높이 접기).
- `warnings`(영업시간 위반) / `notChecked` → 편집 응답에만 옴. 정차 행 오른쪽 시각 옆에 orange 「영업시간 확인」 배지 제안 — 프로토타입엔 없음.

## 2. 데스크톱 1440 (1c) — 위→아래
1. 웹 내비 56 (「내 여행」 활성 · 오렌지 18×3 바)
2. **네이비 헤더** `#0b1d3a` · 패딩 48/80: 배지(초안 v · 추천 모드) → 제목 36/42 → 요약 18 (72% 흰색) · 오른쪽 [다시 추천받기 outline 44] [이 일정으로 저장 orange 44]
3. 일차 탭: 헤더 하단에 붙은 탭 (48, 상단 반경 14). 활성 = ivory 배경 navy 글자, 비활성 = 흰색 10%
4. **노선 스트립** 패딩 40/80: 정차 노드 40 원(첫 정차 orange, 이후 navy) + 이름 15/700 + 「09:30 · 무료」 11. 구간 = 3px navy 실선 120px, 가운데 신발 아이콘 + 「1.2km」. 가로 스크롤(정차 6곳 이상)
5. 2열 `1fr 360px` 갭 48:
   - 왼쪽 정차 목록: 행 패딩 20/0 · 1px 하단선 · [번호 원 40] [이름 18 + 상태 배지 · 설명 15 body · #이유] [시각 18 navy · 비용 11 · 잠금 pill 32]
   - 오른쪽 sticky: 「n일차 이동 요약」 카드(도보 합계 · 예상 비용 · 혼잡도 · 대중교통 「API 없음」 · 정차별 도보 비중 바 8px) → 동백 한마디 soft 카드 → 「순서 직접 수정」 outline 44

## 3. 폰 390 (1d)
1. 네이비 헤더: [‹ 44] [배지] [⋯ 44] → 제목 28/34 → 요약 15 → 일차 탭 3등분 44
2. 「9월 19일 (금)」 15/700 · 오른쪽 「도보 n km · n원」 11
3. 세로 노선: [노드 32 원] [이름 15/700 · 설명 11 한 줄 말줄임] [시각 15 navy · 🔒/🔓 44 터치]. 구간 = 3px navy 세로선 44 높이 + 「도보 400m」 11/700 navy
4. 이동 요약 카드(비중 바 6px · 도보/혼잡 · 대중교통 API 없음)
5. 하단 고정: CTA 줄 [순서 수정 outline] [저장 orange 1.4배] (bottom 88) + 탭바 64 (bottom 16, 「내 여행」 활성). 스크롤 하단 여백 176

## 4. 상호작용
- 일차 탭: 클라이언트 상태. 스트립·목록·요약이 함께 바뀜.
- 잠금: 낙관적 토글 → `POST lock { locked, baseVersion }`. 409 conflict → 「다른 변경이 먼저 반영됐어요」 토스트 후 최신 버전 재로드. 잠긴 행은 pill이 tint 배경 + orange 테두리 「🔒 잠김」.
- 다시 추천받기: 잠긴 항목 유지하고 `replan`. 잡 폴링은 기존 「생성중」 화면 재사용.
- 이 일정으로 저장: 초안 → 내 여행으로 확정. 이후 「일정 완성(보딩패스)」 화면으로.
- 순서 직접 수정: 편집 모드(드래그 정렬)는 별도 화면 — 이번 인계 범위 밖.

## 5. 상태
- 로딩: 헤더 텍스트 · 노드 · 행을 soft 색 스켈레톤.
- `unavailable`/`offline`/`error`: 헤더는 그대로, 스트립 자리에 동백 thinking + `message` + 「다시 시도」.
- 정차 1곳: 스트립은 노드 하나만, 구간 없음. 이동 요약은 「도보 0km」.
- `fallbackMode` RULE/BASELINE: 헤더 배지 문구만 바뀜(색 동일).

## 6. 규격
토큰: navy `#0b1d3a` · orange `#f26532` · ivory `#fffdf8` · card `#fff` · tint `#fff1e8` · soft `#f8f3eb` · field `#e4ddd3` · border `#e8e4dd` · heading `#152238` · body `#667882` · muted `#64748b` · success `#30a687` / `#e9f7f2` · 추정(amber) `#b8860b` / `#fdf5dc`
Pretendard · 11/14 · 15/22 · 18/24 · 22/28 · 28/34 (폰 제목) · 36/42 (데스크톱 제목)
반경 6/14/16/20/full · 노드 원 40(데스크톱)/32(폰), 테두리 ivory 4/3px + 링 2px 노드색 · 노선 3px navy · 터치 44

## Files
- `가볼래 일정 추천.dc.html` — 1c · 1d 확정, 1a/1b/1e/1f 참고.
- `assets/icons/shoes.svg` — 도보 아이콘. `assets/`, `support.js` — 열 때 필요.
