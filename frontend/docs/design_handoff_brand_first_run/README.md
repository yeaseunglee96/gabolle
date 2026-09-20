# 브랜드 자산 · 첫 실행 화면 시안 — 2026-09-20 (S15P21E201-1357)

팀이 고른 것만 코드에 넣었다. 나머지 후보는 「무엇 중에 골랐는지」를 남기려고 같이 둔다.

| 파일 | 무엇 | 고른 것 |
|---|---|---|
| `00a-welcome.png` | 첫 화면 — 영상 배경 + 동백이 워드마크 + 언어 카드 + 흰 시작하기 (시안 4 의 00a) | 그대로 |
| `00b-welcome-language-sheet.png` | 언어 카드를 누르면 뜨는 5개국어 시트 (시안 4 의 00b) | 그대로 |
| `app-icons-4-options.png` | 앱 아이콘 후보 A·B·C·D | **B — 광안대교 사진 위 로고** |
| `ticket-stamps-options.png` | 여행표 도장 후보 1·2·2b·3 | **2b — BUSAN · 날짜 · GAB동백이LLE** |
| `09-states-sad-dongbaek.png` | 상태 모음 — 연결 실패에 우는 동백이 | 우는 동백이만 |

## 자산이 어디로 갔나

| 자산 | 파일 | 만든 도구 |
|---|---|---|
| 로고(먹색) · 밤 로고(흰색) | `assets/brand/gabolle-logo-hd.png` · `gabolle-logo-night.png` | `brand-assets.source.cjs` |
| 앱 아이콘 · 스토어 아이콘 | `assets/icon.png` · `assets/store-icon.png` | `app-icons.source.cjs` (B3) |
| 안드로이드 적응형(앞면 · 바탕 · 단색) · 스플래시 · 파비콘 | `assets/android-icon-*.png` · `assets/splash-icon.png` · `assets/favicon.png` | `brand-assets.source.cjs` |
| 여행표 도장 | `assets/brand/busan-arrived-stamp.png` | `ticket-stamp.source.cjs` (S2b) |
| 동백이(꽃) 대기·인사·생각 · **우는 동백이** | `assets/mascot/dongbaek-{idle,open,thinking,sad}.png` | 팀 원본 그림 |

**워드마크 규칙** — `GAB` + 동백이 + `LLE`. Montserrat 800, 자간 8%. 자간이 B 뒤에 붙어 동백이가
오른쪽으로 치우치므로 왼쪽 여백만 음수(-6%)로 상쇄한다. 픽셀로 재면 B→동백이 · 동백이→L 사이가 같아야 한다
(`brand-assets.source.cjs` 가 그 값으로 뽑는다).

도구(`.cjs`)는 Playwright 로 그린다 — 저장소 `frontend/node_modules` 를 `NODE_PATH` 로 주고 돌린다.
경로가 만든 사람의 임시 폴더를 가리키므로 **그대로는 안 돈다.** 무엇을 어떻게 그렸는지 읽는 용도다.

## 실측 캡처 (`captures/`) — 2026-09-20, 웹 빌드 390px

MR 설명이 이 그림들을 가리킨다. 폰 폭 웹 빌드를 Playwright 로 찍은 것이고, 서버는 가짜 응답(피드·홈)이다.
안드로이드·iOS 실기기는 **확인 못 함** — 특히 안드로이드 낱말 이음표(U+2060)와 첫 화면 영상 재생은 실기기에서 봐야 한다.
