<div align="center">

# 🌸 가볼래 GABOLLE

**부산을 처음 찾은 여행자를 위한 로컬 여행 앱**

조건만 말하면 일정을 짜 주고, 현장에서는 길 안내·통역·편의시설까지 돕습니다.

[![App Store](https://img.shields.io/badge/App_Store-출시-0D96F6?style=flat-square&logo=appstore&logoColor=white)](https://apps.apple.com/kr/app/id6811252919)
[![Google Play](https://img.shields.io/badge/Google_Play-출시-34A853?style=flat-square&logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.gabolle.app)
[![Web](https://img.shields.io/badge/Web-gabolle.kr-E5484D?style=flat-square&logo=googlechrome&logoColor=white)](https://gabolle.kr)

삼성 청년 SW·AI 아카데미(SSAFY) 15기 특화 프로젝트 · 2026.08 – 2026.10 · 6인 팀

</div>

---

## 무엇을 하는 앱인가

| | |
|---|---|
| 🗺 **일정 만들기** | 날짜·인원·취향·이동 조건(휠체어·유아차·큰 짐 등)을 고르면 부산 로컬 장소로 하루 일정을 짠다. 직접 짜기도 된다 |
| 🚌 **길 안내** | 대중교통·택시·자동차·도보를 비교. 시각표가 없는 부산 버스를 배차간격으로 계산하고, 지하철·경전철·동해선 환승까지 |
| 🧭 **현장 도구** | 근처 주차장·환전소·화장실·짐보관, 근처 병원·약국·경찰서, 긴급 도움(119·112·1330), 환율 계산, 메뉴판 번역, 한국어로 말하기 |
| 📸 **여행 기록 피드** | 다녀온 곳을 사진과 함께 남기고, 다른 여행자의 코스를 내 일정으로 가져온다 |
| 🌏 **5개 언어** | 한국어 · English · 日本語 · 中文 · Tiếng Việt |

## 기술

| 영역 | 사용 기술 |
|---|---|
| 앱·웹 | Expo(React Native) · TypeScript · Expo Router · 카카오맵 |
| 서버 | Spring Boot · Java 17 · PostgreSQL · Redis · Flyway |
| 데이터 | 한국관광공사 TourAPI · 부산 BIMS · 부산교통공사 · 레드테이블 · 행정안전부 인허가 · OpenStreetMap |
| 인프라 | AWS EC2 · Docker · GitLab CI/CD · Jenkins · EAS(앱 빌드) |

## 폴더

| 폴더 | 내용 |
|---|---|
| [`frontend/`](frontend/) | 앱·웹 화면 (Expo) |
| [`backend/`](backend/) | API 서버 (Spring Boot) |
| [`bigData/`](bigData/) | 공공데이터 수집 · 경사·소요시간 계산 |
| [`infra/`](infra/) | 서버 배포 설정 |
| [`docs/`](docs/) | 설계·협업·운영 문서 — 팀 내부 안내는 [`docs/TEAM-README.md`](docs/TEAM-README.md) |

## 팀

장효준 · 이예승 · 진미리 · 박재현 · 모진성 · 고지혁

> 이 저장소는 SSAFY GitLab 에서 진행한 원본을 포트폴리오용으로 옮긴 것입니다.
> 운영 데이터와 내부 운영 문서는 뺐고, 팀원 개인 메일은 가렸습니다. 비밀값은 커밋에 없습니다 — 서버가 환경 변수로 읽습니다.
