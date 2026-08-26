# LOCAL ROUTE 실행 기획서 v4

**부제** — 현지인이 다시 가는 곳으로, 구글맵이 못 찾는 길도 걱정 없이

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v4.0 (실행 기획서) |
| 작성일 | 2026-08-25 (화) |
| 대상 독자 | LOCAL ROUTE 개발팀 4인 (DB / INFRA / FE / BE) |
| 이 문서의 범위 | 팀 역할, 역할별 프레임워크와 기술 스택, 기능 명세, 페이지 트리, 일자별 실행 계획, 웹·Android·iOS 배포 절차 |
| 이 문서가 대체하는 것 | v3 기획서의 27~35장 (팀·일정·배포). **1~26장의 서비스 설계·알고리즘·데이터 모델·API 명세·수익모델·광고 신뢰 정책은 v2를 그대로 유지한다.** |
| 전제 | 이 문서 하나만 보고 웹 개발 → 앱 개발 → Android·iOS 스토어 배포까지 수행할 수 있어야 한다 |

---

## 문서 표기 규칙

| 표기 | 의미 |
| --- | --- |
| **[확정 사실]** | 공식 문서로 확인한 사실. 출처와 확인일을 함께 적는다. |
| **[코드 실측]** | 2026-08-25 기준 저장소 코드를 직접 세어 확인한 값. |
| **[설계 제안]** | 이 문서에서 새로 제안하는 구조·정책. 팀 논의로 변경 가능. |
| **[가설·추정]** | 검증되지 않은 수치. 실측 전까지 의사결정 근거로 쓰지 않는다. |
| **[추가 확인 필요]** | 공식 문서에서 확정하지 못한 항목. 착수 전 반드시 재확인. |
| **[위험]** | 일정이나 출시를 막을 수 있는 항목. |

---

## 1. 실행 요약

### 1.1 세 문장 요약

1. 8월 25일부터 9월 1일까지 **7일** 동안 4명이 웹 MVP를 배포 가능한 상태로 만들고, 9월 2일부터 9월 9일까지 **8일** 동안 Expo 기반 Android·iOS 앱을 완성한다.
2. 서비스 로직·API·데이터 모델은 이미 상당 부분 구현되어 있다. 남은 일은 **화면을 페이지 트리로 재편하고, DB를 PostgreSQL로 올리고, 클라우드에 배포하고, 같은 페이지 트리를 앱으로 옮기는 것**이다.
3. **9월 9일에 앱이 완성되어도 Google Play 정식 출시는 빨라야 9월 말이다.** 신규 개인 개발자 계정은 12명이 14일 연속 참여하는 비공개 테스트를 마쳐야 프로덕션 액세스를 신청할 수 있고, 그 심사에 최대 7일이 더 걸린다. 이 제약 때문에 **비공개 테스트는 앱이 완성되기 전에 시작해야 한다.**

### 1.2 지금 당장 결정해야 하는 3가지

| # | 결정 사항 | 마감 | 이유 |
| --- | --- | --- | --- |
| 1 | Google Play 개발자 계정을 **개인으로 만들 것인가, 조직으로 만들 것인가** | 8/25 (오늘) | 개인 계정이면 12명·14일 비공개 테스트가 의무다. 계정 생성 자체가 결제·본인확인을 포함하므로 오늘 시작하지 않으면 14일 카운트가 늦어진다. |
| 2 | Apple Developer Program을 **개인으로 가입할 것인가, 조직으로 가입할 것인가** | 8/25 (오늘) | 조직 가입에는 D-U-N-S 번호, 조직 도메인 업무 이메일, 공개된 조직 웹사이트가 필요하다. 준비 기간이 길어 9/9 일정에 맞추려면 사실상 개인 가입이 유일한 선택지다. |
| 3 | 앱 v1의 **화면 범위를 몇 개로 자를 것인가** | 8/27 | 8일 안에 React Native로 전체 화면을 다시 만드는 것은 불가능하다. 14장의 범위 조정 권고안 중 하나를 선택해야 한다. |

### 1.3 현재 코드 자산 [코드 실측]

일정이 짧아 보이지만, 바닥에서 시작하는 것이 아니다.

| 영역 | 실측 수치 |
| --- | --- |
| 서버 API 엔드포인트 | **57개** (`/health` 포함, 7개 라우터) |
| 서버 소스 | 3,794줄 (routes 6파일, services 12파일, lib 2파일, workers 2파일) |
| Prisma 데이터 모델 | **26개 모델** |
| DB 마이그레이션 | 10개 적용 완료 |
| 시드 데이터 | Place 272건, Trip 44건, Itinerary 151건 |
| 웹 컴포넌트·페이지 | 20개 파일, 1,297줄 |
| 웹 API 클라이언트 함수 | 35개 |
| 코스 카테고리 | 10종 (설정 파일 기반) |
| CI | GitHub Actions 워크플로 1개 |
| 컨테이너 | Dockerfile 2개, docker-compose 2개, nginx.conf 1개 (SPA fallback 포함) |

즉 **백엔드 엔진은 거의 완성되어 있고, 부족한 것은 화면 구조·운영 인프라·앱 껍데기**다. 이 문서의 일정은 그 전제 위에 설계되었다.

---

## 2. 확정 사실과 일정 제약

이 장의 내용은 일정을 결정하는 외부 제약이다. 팀이 통제할 수 없으므로 계획을 여기에 맞춰야 한다.

### 2.1 Google Play — 신규 개인 개발자 계정의 테스트 의무 [확정 사실]

> 출처: Google Play Console 고객센터 「새로운 개인 개발자 계정의 앱 테스트 요구사항」 (2026-08-25 확인)

| 항목 | 요구사항 |
| --- | --- |
| 적용 대상 | **2023년 11월 13일 이후에 생성된 개인 Google Play Console 계정** |
| 테스터 수 | **최소 12명** |
| 기간 | **연속 14일**, 12명이 그 기간 동안 지속적으로 참여해야 함 |
| 방식 | 비공개 테스트(Closed testing) |
| 이후 절차 | Play Console 대시보드 → 「프로덕션 신청」 → 3단계 신청서(비공개 테스트 정보 / 앱·게임 정보 / 프로덕션 준비 상태) |
| 신청 검토 기간 | "일반적으로 7일 이내, 경우에 따라 더 오래 걸릴 수 있음" |
| 조직 계정 | **이 요구사항이 적용되지 않음** |

**[위험] 일정 영향**: 9/9에 앱이 완성되고 그날 비공개 테스트를 시작하면 → 14일 후인 **9/23**에 프로덕션 신청 → 최대 7일 검토 → **9/30 이후** 정식 출시. 9월 내 출시가 불가능해질 수 있다.

**[설계 제안] 대응**: 비공개 테스트는 "완성된 앱"이 아니라 "설치되고 크래시 없이 실행되는 앱"이면 시작할 수 있다. 따라서 **9/2에 첫 EAS 빌드가 나오는 즉시 내부 테스트 트랙에 올리고 비공개 테스트를 시작**한다. 그러면 14일 카운트가 9/2부터 시작되어 **9/16**에 프로덕션 신청, 9/23 전후 출시가 가능하다. 테스트 기간 중에도 앱 업데이트는 계속 올릴 수 있다.

### 2.2 Apple Developer Program 가입 요건 [확정 사실]

> 출처: Apple Developer 「Program enrollment」 (2026-08-25 확인)

| 항목 | 개인(Individual) | 조직(Organization) |
| --- | --- | --- |
| 연회비 | 99 USD | 99 USD |
| App Store 판매자 표기 | **본인의 법적 실명** | 법인명 |
| D-U-N-S 번호 | 불필요 | **필수** (Dun & Bradstreet 발급, 대부분 지역에서 무료) |
| 법인 실체 | 불필요 | 계약 체결이 가능한 법적 실체여야 함. DBA·상호·지점 불가 |
| 업무 이메일 | 불필요 | **조직 도메인 이메일** 필요 |
| 웹사이트 | 불필요 | **공개·작동하는 조직 도메인 웹사이트** 필요. SNS 링크나 내용이 빈약한 사이트는 거부 |
| 승인 기간 | 공식 명시 없음. "구매 후 24시간 내 멤버십 확인 메일이 오지 않으면 문의" 안내 | Apple Developer Support가 정보를 검증한 뒤 다음 단계 메일을 보냄 (기간 명시 없음) |

**[설계 제안]**: 9/9 일정에서는 **개인 가입**이 유일하게 현실적이다. 조직 가입은 D-U-N-S 발급과 웹사이트 준비만으로도 수 주가 걸릴 수 있다. 단, 개인 가입 시 App Store 판매자 이름에 **개인 실명이 공개**된다는 점을 팀이 합의해야 한다. 실명 표기가 곤란하면 일정을 늦추고 조직 가입을 진행해야 한다.

**[추가 확인 필요]**: 개인 가입의 실제 승인 소요 시간. 신분 확인이 걸리면 며칠이 소요될 수 있다. **8/25에 즉시 가입을 시작한다.**

### 2.3 App Store 심사 소요 시간 [확정 사실 / 업계 집계]

> 출처: LOW/CODE 「App Store Review Time for Mobile Apps in 2026」 (2026-08-25 확인). 업계 집계치이며 Apple 공식 SLA가 아니다.

- Apple App Store 평균 **24~48시간**
- Google Play 수 시간 ~ 3일
- **신규 앱 최초 심사는 더 오래 걸림.** 최초 출시나 대규모 업데이트는 최대 72시간 소요 가능
- 리젝 상위 사유: ① 심사 중 앱 충돌·프리징 ② 개인정보처리방침 누락 ③ 로그인 요구 시 데모 계정 미제공 ④ 오도적 스크린샷·설명 ⑤ 인앱 결제 구현 오류 ⑥ 접근성 미흡
- **리젝 1회로 총 심사 기간이 1~2주 연장될 수 있음**

**[설계 제안]**: 11.6절의 리젝 방지 체크리스트를 제출 전에 전부 통과시킨다. 특히 ①②③은 이 서비스에서 실제로 걸릴 가능성이 높다.

### 2.4 제약이 만드는 실제 출시 일정

| 마일스톤 | 날짜 | 근거 |
| --- | --- | --- |
| 웹 MVP 공개 배포 | 2026-09-01 (화) | 팀 목표 |
| 앱 기능 완료 (feature freeze) | 2026-09-09 (수) | 팀 목표 |
| Play 비공개 테스트 시작 | **2026-09-02 (수)** | 첫 EAS 빌드 직후. 14일 카운트를 앞당기기 위함 |
| Play 비공개 테스트 14일 충족 | 2026-09-16 (수) | 9/2 + 14일 |
| Play 프로덕션 신청 | 2026-09-16 (수) | 테스트 요건 충족 즉시 |
| Play 정식 출시 (예상) | **2026-09-23 (수) ~ 9/30** | 신청 검토 최대 7일 |
| iOS App Store 제출 | 2026-09-10 (목) | 앱 완료 다음 날 |
| iOS 승인 (예상) | **2026-09-11 ~ 9/14** | 신규 앱 최초 심사 24~72시간, 리젝 시 연장 |

> **핵심**: iOS가 Android보다 먼저 출시된다. Android는 비공개 테스트 14일이 병목이다.

---

## 3. 마일스톤 정의

| 코드 | 이름 | 마감 | 정의(Definition of Done) |
| --- | --- | --- | --- |
| **M1** | 웹 MVP | 2026-09-01 (화) | 공개 도메인에서 HTTPS로 접속 가능. PostgreSQL 운영 DB. 여행 조건 입력 → 일정 생성 → 결과 확인 → 공유까지 끊김 없이 동작. 페이지 트리 라우팅 적용. 백엔드 미연결 시 사용자에게 명확히 표시. |
| **M2** | 앱 빌드 | 2026-09-09 (수) | Expo 앱이 Android·iOS 실기기에서 크래시 없이 실행. M2 범위 화면 전부 동작. EAS Build로 프로덕션 빌드 산출. Play 내부·비공개 트랙 및 TestFlight 업로드 완료. |
| **M3** | 스토어 심사 통과 | 2026-09-30 목표 | 개인정보처리방침·이용약관 공개 URL, 계정·데이터 삭제 경로, UGC 신고·검토 기능, 권한 사용 목적 고지, 심사용 데모 계정 준비 완료. 양 스토어 승인. |
| **M4** | 후속 | 10월 이후 | KPI 대시보드, 로컬 등급, 광고 캠페인 콘솔, 다국어 확장 |

각 마일스톤은 **날짜가 아니라 위 DoD로 판정**한다. 날짜가 되었는데 DoD를 못 채우면 마일스톤을 미루는 것이 아니라 **범위를 자른다**(14장 참조).

---

## 4. 4인 팀 구성과 역할

### 4.1 팀 구조

```mermaid
graph TD
    subgraph Team["LOCAL ROUTE 4인"]
        DB["DB<br/>데이터 모델·마이그레이션<br/>쿼리 튜닝·캐시"]
        INFRA["INFRA<br/>클라우드·CI/CD<br/>컨테이너·보안·앱 빌드"]
        FE["FE<br/>웹 React·앱 React Native<br/>지도·페이지 트리"]
        BE["BE<br/>Express API·추천·최적화<br/>외부 API 연동"]
    end

    DB -->|"Prisma 스키마 · 마이그레이션 · 쿼리 계약"| BE
    BE -->|"REST API · 응답 타입 정의"| FE
    INFRA -->|"실행 환경 · 배포 파이프라인 · 시크릿"| BE
    INFRA -->|"웹 호스팅 · EAS 빌드 · 스토어 계정"| FE
    DB -->|"RDS 인스턴스 요구사항"| INFRA
    FE -->|"프로덕션 번들"| Store["Android · iOS<br/>스토어"]
    INFRA -->|"Fastlane / EAS Submit"| Store
```

### 4.2 역할 배분 원칙 [설계 제안]

4명 · 15일이라는 조건에서는 역할을 엄격히 나누면 병목이 생긴다. 다음 3가지 원칙을 적용한다.

1. **1차 책임 + 2차 백업.** 모든 영역에 주 담당자와 백업 담당자를 둔다. 주 담당자가 막히면 백업이 즉시 붙는다.
2. **인터페이스 우선.** 화면과 API가 동시에 개발되므로, **타입 정의를 먼저 합의하고 각자 목(mock)으로 진행**한다. `web/src/types.ts`와 `server/src/types.ts`가 계약서다.
3. **후반 전원 FE 투입.** 9/5 이후 DB·INFRA의 주 업무가 끝나면 두 사람은 앱 화면 구현에 합류한다. 이것이 8일 앱 일정의 유일한 현실적 근거다.

### 4.3 DB — 데이터베이스 담당

| 구분 | 내용 |
| --- | --- |
| **한 문장 정의** | 데이터가 정확하고 빠르게 저장·조회되도록 스키마와 쿼리를 책임진다. |
| **핵심 책임** | ① SQLite → PostgreSQL 전환 ② 26개 모델의 인덱스 설계 ③ 지리 좌표 기반 근접 검색 최적화 ④ 캐시 계층 설계 ⑤ 마이그레이션 안전성(전진·롤백) ⑥ 시드·실데이터 적재 |
| **주 프레임워크** | **Prisma ORM 5.22** — 스키마 정의, 마이그레이션, 타입 생성 |
| **DBMS** | **PostgreSQL 16** (개발: Docker, 운영: 관리형 인스턴스) |
| **캐시** | **Redis 7** — 이동시간 행렬, 날씨 1시간 캐시, 장소 이미지 24시간 캐시, 세션 |
| **보조 도구** | `prisma migrate` / `prisma studio` / `pg_dump` / `EXPLAIN ANALYZE` |
| **M1 산출물** | PostgreSQL 스키마 확정, 마이그레이션 10개 전환 완료, 인덱스 정의서, 시드 스크립트 6종 동작, 운영 DB에 272개 장소 적재 |
| **M2 산출물** | 앱 트래픽 대비 쿼리 튜닝, Redis 캐시 적용, 백업·복구 절차 문서 |
| **백업 담당** | BE |
| **가장 큰 리스크** | SQLite와 PostgreSQL의 타입 차이(특히 `DateTime`, `Json`, `Decimal`)로 마이그레이션이 깨지는 것. 8/26에 전환을 끝내고 8/27 하루를 검증에 쓴다. |

**DB 담당이 실제로 만지는 파일**

```
server/prisma/schema.prisma          ← 데이터 모델 26개
server/prisma/migrations/            ← 마이그레이션 10개
server/prisma/seed.ts                ← 기본 시드
server/prisma/ensure-*.ts            ← 기능별 추가 시드 4종
server/prisma/import-tourapi.ts      ← 관광공사 데이터 적재
server/prisma/import-kakao-reviews.ts ← 승인된 리뷰 집계 적재
server/src/db.ts                     ← Prisma 클라이언트
```

### 4.4 INFRA — 인프라·배포 담당

| 구분 | 내용 |
| --- | --- |
| **한 문장 정의** | 코드가 사용자에게 도달하는 모든 경로(빌드·배포·도메인·스토어)를 책임진다. |
| **핵심 책임** | ① 클라우드 환경 구성 ② HTTPS 도메인 ③ CI/CD 파이프라인 ④ 시크릿 관리 ⑤ 모니터링·로그 ⑥ **Android·iOS 스토어 계정과 빌드 파이프라인** ⑦ 앱 서명 키 관리 |
| **컨테이너** | **Docker + Docker Compose** (이미 구성됨: `server/Dockerfile`, `web/Dockerfile`, `web/nginx.conf`) |
| **웹 서버** | **Nginx** — 정적 파일 서빙, `/api` 프록시, SPA fallback(`try_files $uri /index.html`, 이미 적용됨) |
| **CI/CD** | **GitHub Actions** (`.github/workflows/ci.yml` 존재) — 테스트·빌드·배포 |
| **앱 빌드** | **Expo EAS Build** — macOS 장비 없이 iOS 빌드 가능. `eas.json`으로 프로필 관리 |
| **앱 제출** | **EAS Submit** — Play Console·App Store Connect 자동 업로드 |
| **HTTPS** | Let's Encrypt (certbot) 또는 관리형 로드밸런서 인증서 |
| **모니터링** | 1차: 컨테이너 로그 + `/health` 헬스체크. 2차(M4): Prometheus + Grafana |
| **M1 산출물** | 공개 도메인 HTTPS 접속, 자동 배포 파이프라인, 시크릿 주입, PostgreSQL·Redis 인스턴스 기동 |
| **M2 산출물** | EAS Build 프로필 3종(development/preview/production), Android 서명 키 생성·보관, iOS 인증서·프로비저닝, 첫 빌드 스토어 업로드 |
| **M3 산출물** | 개인정보처리방침·이용약관 공개 URL, 스토어 등록 정보·스크린샷·심사 메모 |
| **백업 담당** | BE |
| **가장 큰 리스크** | **스토어 계정 개설 지연**. 결제 수단·본인 확인·D-U-N-S 등 팀이 통제할 수 없는 대기가 발생한다. 8/25 최우선으로 착수한다. |

**[설계 제안] 클라우드 선택**: v3에서는 AWS(VPC·RDS·ECS Fargate·S3·CloudFront·ALB)를 적었다. 7일 안에 이 구성을 처음부터 세우는 것은 INFRA 1명에게 과중하다. 다음 중 하나를 8/25에 결정한다.

| 안 | 구성 | 구축 시간 | 적합성 |
| --- | --- | --- | --- |
| **A. 단일 VM (권장)** | 클라우드 VM 1대 + Docker Compose + Nginx + Let's Encrypt + 같은 VM의 PostgreSQL·Redis 컨테이너 | 반나절 | M1·M2에 충분. 시연·심사에 문제없음 |
| B. VM + 관리형 DB | VM 1대 + 관리형 PostgreSQL | 1일 | 백업·복구가 자동. 비용 상승 |
| C. 컨테이너 오케스트레이션 | ECS Fargate + RDS + ALB + CloudFront | 2~4일 | 발표 자료로는 좋으나 **9/1 일정을 위협** |

**권고: A로 시작하고, M4에 B 또는 C로 이전한다.** 아키텍처 다이어그램에는 목표 구성(C)을 그리되, 실제 배포는 A로 한다. 발표에서는 "현재 A, 확장 경로 C"로 설명하면 된다.

### 4.5 FE — 프론트엔드 담당

| 구분 | 내용 |
| --- | --- |
| **한 문장 정의** | 사용자가 보는 모든 화면을 웹과 앱 양쪽에서 같은 페이지 트리로 구현한다. |
| **핵심 책임** | ① 페이지 트리 유지·확장 ② 웹 화면 구현 ③ **React Native 앱 화면 구현** ④ 지도 인터페이스 ⑤ 다국어(한/영) ⑥ 접근성·반응형 |
| **웹 프레임워크** | **React 18.3** + **TypeScript** |
| **웹 빌드** | **Vite 8.2** (rolldown 기반) |
| **웹 라우팅** | **react-router-dom 7** — `web/src/routes/` (본 문서 7장, **구현 완료**) |
| **앱 프레임워크** | **React Native + Expo SDK** |
| **앱 라우팅** | **Expo Router** — 파일 기반. 웹 페이지 트리와 1:1 대응(7.5절) |
| **상태 관리** | React Context + 로컬 상태. 전역 스토어는 필요해질 때 도입 (**현재 불필요**) |
| **스타일** | CSS Variables + 순수 CSS (`styles.css`, `ux-overrides.css`). 앱은 StyleSheet |
| **지도** | 웹: **Kakao Maps JavaScript SDK** / 앱: WebView 지도 또는 `react-native-maps` (8/27 결정) |
| **M1 산출물** | 페이지 트리 라우팅(완료), 시작 화면, 4단계 위저드 URL 분리, 약관·정책 화면 |
| **M2 산출물** | Expo 앱에서 M2 범위 화면 동작, 딥링크, 앱 온보딩·권한 화면 |
| **백업 담당** | 9/5 이후 DB·INFRA 전원 |
| **가장 큰 리스크** | **8일 안에 RN으로 화면을 다시 만드는 것.** 14장의 범위 조정이 반드시 필요하다. |

**FE 담당이 실제로 만지는 파일 (현재 구조)**

```
web/src/routes/routeTree.ts     ← 페이지 트리 정의 (단일 진실 공급원)
web/src/routes/AppRouter.tsx    ← 라우팅 구성
web/src/routes/AppShell.tsx     ← 전역 레이아웃·오프라인 배너
web/src/routes/paths.ts         ← 타입 안전 경로 빌더
web/src/pages/**                ← 라우트별 화면 (30개+)
web/src/components/**           ← 재사용 컴포넌트 (17개)
web/src/api/client.ts           ← API 클라이언트 (35개 함수)
web/src/types.ts                ← BE와 공유하는 타입 계약
web/src/i18n.ts                 ← 다국어
```

### 4.6 BE — 백엔드 담당

| 구분 | 내용 |
| --- | --- |
| **한 문장 정의** | 여행 일정을 실제로 계산해내는 로직과 그것을 노출하는 API를 책임진다. |
| **핵심 책임** | ① 추천 엔진 ② 경로·시간 최적화 ③ REST API ④ 외부 API 연동 ⑤ 익명 세션 인증 ⑥ 광고 신뢰 정책의 코드 강제 ⑦ 이벤트 파이프라인 |
| **런타임** | **Node.js + TypeScript 5.6 (ES Modules)** |
| **프레임워크** | **Express 4.21** |
| **보안 미들웨어** | **helmet 8**, **cors 2.8**, **express-rate-limit 7.5** (모두 적용됨) |
| **ORM** | **Prisma Client 5.22** (스키마 소유권은 DB 담당) |
| **최적화** | 자체 스케줄러(`services/schedule.ts`, 551줄) + 휴리스틱 폴백. OR-Tools 연계는 선택 |
| **외부 API** | 카카오맵·카카오 로컬·카카오 모빌리티, 한국관광공사 TourAPI, 기상청, 네이버/구글 이미지 검색 |
| **이벤트** | **KafkaJS** + DB Outbox 패턴 (Kafka 장애 시에도 이벤트 보존) |
| **테스트** | **Node.js 내장 test runner** + **supertest** |
| **개발 실행** | **tsx watch** |
| **M1 산출물** | PostgreSQL 대응 완료, 운영 환경변수 정리, CORS·rate limit 운영값, `/health` 강화 |
| **M2 산출물** | 앱용 딥링크 대응, 푸시 토큰 등록 API(선택), 심사용 데모 데이터 |
| **백업 담당** | DB |
| **가장 큰 리스크** | 외부 API 키 쿼터·요금. 카카오·TourAPI 일일 호출 한도를 8/26에 확인한다. |

**BE 담당이 실제로 만지는 파일**

```
server/src/index.ts              ← 앱 부트스트랩, 미들웨어, 라우터 마운트
server/src/routes/               ← trips(627) community(160) collaboration(131)
                                    social(124) commerce(114) experience(89) analytics(59)
server/src/services/             ← schedule(551) kakao(499) recommend(334) placeImages(162)
                                    generate(167) optimizer(110) events(109) 외 5종
server/src/lib/tourapi.ts        ← 관광공사 API 클라이언트
server/src/workers/              ← 이벤트 디스패처, 개인정보 정리 배치
```

### 4.7 인터페이스 계약 — 누가 무엇을 언제 넘기는가

| 넘기는 사람 | 받는 사람 | 산출물 | 마감 | 형식 |
| --- | --- | --- | --- | --- |
| DB | BE | PostgreSQL 스키마 확정본 | 8/26 18:00 | `schema.prisma` 머지 |
| DB | INFRA | DB 인스턴스 요구사항 (버전·용량·확장) | 8/26 12:00 | 이슈 코멘트 |
| BE | FE | API 응답 타입 확정본 | 8/27 18:00 | `web/src/types.ts` 머지 |
| INFRA | 전원 | staging 접속 정보·배포 방법 | 8/28 18:00 | README 갱신 |
| FE | 전원 | 페이지 트리 확정본 | **완료 (8/25)** | `routeTree.ts` |
| INFRA | FE | EAS 빌드 프로필·스토어 계정 접근 | 9/2 12:00 | `eas.json` + 계정 초대 |
| FE | INFRA | 앱 아이콘·스플래시·스크린샷 원본 | 9/7 18:00 | 저장소 `assets/` |
| INFRA | 전원 | 스토어 제출 완료 보고 | 9/10 | 채널 공지 |

**규칙**: 마감을 못 지킬 것 같으면 마감 **전날**에 알린다. 당일에 알리면 뒷사람이 하루를 통째로 잃는다.

### 4.8 RACI

R=실행, A=최종책임, C=자문, I=공유

| 작업 | DB | INFRA | FE | BE |
| --- | :-: | :-: | :-: | :-: |
| PostgreSQL 전환 | **A/R** | C | I | R |
| 인덱스·쿼리 튜닝 | **A/R** | I | I | C |
| 클라우드 배포 | C | **A/R** | I | C |
| CI/CD | I | **A/R** | C | C |
| HTTPS·도메인 | I | **A/R** | I | I |
| 페이지 트리 | I | I | **A/R** | C |
| 웹 화면 구현 | I | I | **A/R** | C |
| 앱 화면 구현 | R | R | **A/R** | R |
| EAS 빌드·서명 | I | **A/R** | C | I |
| 스토어 등록·심사 | I | **A/R** | C | I |
| 추천·최적화 로직 | C | I | I | **A/R** |
| API 설계 | C | I | C | **A/R** |
| 외부 API 연동 | I | C | I | **A/R** |
| 개인정보·약관 문서 | C | **A/R** | R | C |
| 테스트·품질 게이트 | R | R | R | **A/R** |

---

## 5. 기술 스택 확정

### 5.1 전체 스택 한눈에 보기

| 계층 | 기술 | 버전 | 주 담당 | 선정 사유 | 대안 |
| --- | --- | --- | :-: | --- | --- |
| 웹 UI | React | 18.3.1 | FE | 이미 전 화면이 React로 구현됨. 앱(RN)과 개념·컴포넌트 사고방식 공유 | Vue, Svelte (재작성 비용) |
| 웹 언어 | TypeScript | 5.6 / 7.x | FE·BE | 타입이 FE·BE 계약서 역할. 4인 병렬 작업의 안전장치 | JavaScript (계약 깨짐 감지 불가) |
| 웹 빌드 | Vite | 8.2.2 | FE | rolldown 기반. 82모듈 프로덕션 빌드 1.3초 | Next.js (SSR 불필요, 학습비용) |
| 웹 라우팅 | react-router-dom | 7.x | FE | 중첩 라우트·레이아웃 라우트가 페이지 트리 구조와 정확히 일치 | TanStack Router |
| 앱 프레임워크 | React Native + Expo | Expo SDK 최신 안정판 | FE | macOS 없이 iOS 빌드 가능(EAS). 웹 React 지식 재사용 | Flutter(언어 재학습), Capacitor(네이티브감↓) |
| 앱 라우팅 | Expo Router | Expo SDK 동봉 | FE | 파일 기반 라우팅이 `routeTree.ts` 와 1:1 대응 | React Navigation 직접 구성 |
| 지도(웹) | Kakao Maps JS SDK | v3 | FE | 국내 좌표·POI 정확도. 이미 `KakaoMap.tsx` 구현됨 | Naver Maps, Google Maps(국내 제약) |
| 서버 런타임 | Node.js | 20 LTS 이상 | BE | FE와 언어 통일. 타입 공유 | — |
| 서버 프레임워크 | Express | 4.21 | BE | 57개 엔드포인트가 이미 Express로 구현됨 | Fastify, NestJS (재작성) |
| 보안 미들웨어 | helmet / cors / express-rate-limit | 8.1 / 2.8 / 7.5 | BE | 이미 적용. 심사 시 보안 헤더 근거 | — |
| ORM | Prisma | 5.22 | DB | 스키마 하나로 마이그레이션·타입 생성. DB↔BE 계약 자동화 | TypeORM, Drizzle |
| DBMS | PostgreSQL | 16 | DB | 동시 쓰기, 트랜잭션, 인덱스, 백업. SQLite로는 운영 불가 | MySQL |
| 캐시 | Redis | 7 | DB | 이동시간 행렬·날씨·이미지 캐시. 외부 API 호출 절감 | 인메모리(다중 인스턴스에서 깨짐) |
| 이벤트 | KafkaJS + DB Outbox | 2.2 | BE | Kafka 장애 시에도 이벤트 보존. 이미 구현 | 단순 DB 테이블만 사용 |
| 컨테이너 | Docker / Compose | — | INFRA | 이미 Dockerfile 2종·compose 2종 존재 | — |
| 웹 서버 | Nginx | stable | INFRA | 정적 서빙·API 프록시·SPA fallback 이미 구성 | Caddy(자동 HTTPS) |
| CI/CD | GitHub Actions | — | INFRA | 저장소가 GitHub. 워크플로 이미 존재 | — |
| 앱 빌드·제출 | EAS Build / EAS Submit | — | INFRA·FE | macOS 없이 iOS 빌드. 스토어 자동 업로드 | Fastlane 직접 구성(macOS 필요) |
| 테스트 | node:test + supertest | 내장 / 7.1 | BE | 추가 의존성 없이 API 통합 테스트 | Jest, Vitest |

### 5.2 역할별 "내가 알아야 하는 것" 요약

각자 이 목록만 확실히 하면 된다.

**DB 담당**

```
필수  Prisma schema 문법, migrate dev / migrate deploy 차이, PostgreSQL 인덱스(B-tree, GIN),
      EXPLAIN ANALYZE 읽는 법, pg_dump/pg_restore, Redis 기본 명령(SET/GET/EXPIRE)
선택  PostGIS (근접 검색을 하버사인 SQL로 처리하면 없어도 됨)
금지  운영 DB에 migrate dev 실행 (반드시 migrate deploy)
```

**INFRA 담당**

```
필수  Docker / docker compose, Nginx 리버스 프록시, Let's Encrypt(certbot),
      GitHub Actions 워크플로 문법, 시크릿 관리, Expo EAS(eas.json, build/submit),
      Android keystore, iOS 인증서·프로비저닝 프로파일 개념
선택  Prometheus/Grafana (M4)
금지  시크릿을 저장소에 커밋 (.env 는 .gitignore 에 이미 포함됨)
```

**FE 담당**

```
필수  React 훅, react-router-dom 7 중첩 라우트/Outlet/useParams,
      TypeScript 제네릭 수준, Expo Router 파일 규칙, RN 스타일링,
      Kakao Maps SDK 마커·폴리라인
선택  Zustand (현재 Context로 충분)
금지  경로 문자열 하드코딩 (반드시 paths.ts 사용)
```

**BE 담당**

```
필수  Express 라우터·미들웨어, Prisma Client 쿼리, async 에러 처리,
      SSE(Server-Sent Events), 외부 API 호출 재시도·타임아웃, node:test
선택  KafkaJS (Outbox만으로 시연 가능)
금지  광고 업종 화이트리스트 우회 (25장 정책은 코드로 강제한다)
```

### 5.3 공통 개발 규약 [설계 제안]

| 항목 | 규칙 |
| --- | --- |
| 브랜치 | `main` 보호. 작업은 `feat/`, `fix/`, `chore/` 브랜치에서. PR로만 머지 |
| 커밋 메시지 | `type(scope): 한 줄 요약` + 빈 줄 + **왜 바꿨는지** 본문 |
| PR 규칙 | 최소 1인 리뷰. CI 통과 필수. 15분 내 리뷰하지 못하면 "지금 못 본다"고 답한다 |
| 머지 시각 | 매일 18:00 이전. 그 이후 머지는 다음 날 아침으로 미룬다 (밤중 배포 사고 방지) |
| 타입 변경 | `types.ts` 를 바꾸면 **PR 제목에 `[TYPES]` 를 붙이고** 채널에 알린다 |
| 시크릿 | 절대 커밋 금지. `.env.example` 만 저장소에 둔다 |
| 데일리 | 매일 10:00 15분. ① 어제 한 것 ② 오늘 할 것 ③ **막힌 것** — 세 번째가 가장 중요하다 |
| 데일리 데모 | 매일 18:00, staging에서 5분간 동작 시연. 말이 아니라 화면으로 진척을 확인한다 |

---

## 6. 기능 명세

### 6.1 기능 목록 읽는 법

기능 ID는 `F-영역-번호` 형식이다. 영역 코드는 다음과 같다.

| 코드 | 영역 | 주 담당 |
| --- | --- | :-: |
| PLN | 여행 계획 입력 | FE·BE |
| GEN | 추천·최적화 생성 | BE |
| ITN | 일정 열람·편집 | FE·BE |
| MAP | 지도·경로 | FE·BE |
| NAV | 외국인 실시간 내비게이션 | BE·FE |
| FGN | 외국인 지원 | BE·FE |
| LOC | 로컬 검증 | BE |
| EXP | 지역 체험(축제·기념품) | BE·FE |
| WTH | 날씨 | BE |
| COL | 동행 공동 편집·공유 | BE·FE |
| SOC | 소셜(스토리·팔로우) | BE·FE |
| ADS | 광고·예약 제휴 | BE |
| OPS | 운영·신고 검토 | BE·FE |
| SYS | 시스템·인프라·배포 | INFRA·DB |

### 6.2 이미 구현된 기능 [코드 실측]

아래는 2026-08-25 기준 코드에 실제로 존재하는 기능이다. 이 목록이 남은 일정의 출발점이다.

| ID | 기능 | 근거 (서버 파일 / 엔드포인트) | 담당 |
| --- | --- | --- | :-: |
| F-PLN-01 | 여행 조건 4단계 입력 (기간·인원·예산·이동 시간대·취향·동반 조건) | `POST /api/trips`, `TripFormPage.tsx` | FE·BE |
| F-PLN-02 | 출발지 자연어 검색 | `GET /api/locations/search`, `services/kakao.ts` | BE |
| F-PLN-03 | 10종 코스 카테고리 선택 | `GET /api/course-categories`, `services/courseCategories.ts` | BE |
| F-PLN-04 | 숙소 지정 | `GET /api/places?category=LODGING` | BE |
| F-GEN-01 | 규칙 기반 추천 점수 계산 (7개 가중치 축) | `services/recommend.ts` (334줄) | BE |
| F-GEN-02 | 시간창 제약 일정 스케줄링 + 휴리스틱 폴백 | `services/schedule.ts` (551줄), `services/optimizer.ts` | BE |
| F-GEN-03 | 비동기 생성 job + SSE 진행률 | `GET /api/itinerary-jobs/:jobId/events`, `services/jobs.ts` | BE |
| F-GEN-04 | 3가지 추천 모드 (관광 필수 / 현지인 / 초행 외국인 안심) | `services/generate.ts` | BE |
| F-ITN-01 | 일정 결과 조회 (일자별 방문지·시각·이동·비용) | `GET /api/trips/:id/itinerary` | BE |
| F-ITN-02 | 장소 고정(PIN) / 제외(REMOVE) / 교체(REPLACE) 후 **해당 날짜만** 부분 재최적화 | `POST /api/itineraries/:id/days/:dayIndex/reoptimize`, `services/reoptimize.ts` | BE |
| F-ITN-03 | 대체 장소 후보 조회 | `GET /api/itineraries/:id/items/:itemId/alternatives` | BE |
| F-ITN-04 | 최근 변경 되돌리기 | `POST /api/itineraries/:id/undo`, `ItineraryRevision` 모델 | BE |
| F-MAP-01 | 카카오맵 마커·폴리라인 경로 표시 | `KakaoMap.tsx` (155줄), `MapPanel.tsx` | FE |
| F-MAP-02 | 자차·대중교통 경로 조회 (13장 내비게이션의 확장 기반) | `GET /api/routes/directions`, `services/kakao.ts` (499줄) | BE |
| F-FGN-01 | 한/영 UI 전환 | `i18n.ts`, `PlaceTranslation` 모델 | FE·BE |
| F-FGN-02 | 영어 메뉴·해외카드 결제 조건 필터 | `lib/foreignConvenience.ts` | BE |
| F-LOC-01 | GPS 방문 인증 | `POST /api/places/:id/visits/verify`, `VisitVerification` 모델 | BE |
| F-LOC-02 | 인증 기반 리뷰 | `POST /api/places/:id/reviews` | BE |
| F-LOC-03 | 로컬 점수·등급 | `LocalProfile` 모델, `GET /api/local-profile` | BE |
| F-EXP-01 | 여행 기간과 겹치는 지역 축제 조회·일정 추가 | `GET /api/festivals`, `POST /api/itineraries/:id/festivals/:placeId` | BE |
| F-EXP-02 | 기념품샵 반경 검색 | `GET /api/shops/souvenir` | BE |
| F-WTH-01 | 날짜별 날씨 (1시간 캐시, 추정값 명시) | `GET /api/weather` | BE |
| F-COL-01 | 동행자 초대 (편집자/열람자, 7일 링크) | `POST /api/trips/:tripId/members/invite` | BE |
| F-COL-02 | 항목 단위 공동 편집 + 버전 낙관적 락(409) | `PATCH /api/itineraries/:id/items/:itemId/collaborate` | BE |
| F-COL-03 | 읽기 전용 공유 링크 (30일, 개인정보 제외) | `POST /api/itineraries/:id/share`, `GET /api/s/:slug` | BE |
| F-COL-04 | 공유본 복제 후 내 조건으로 재계산 | `POST /api/s/:slug/clone` | BE |
| F-SOC-01 | 장소 기반 스토리 등록 (EXIF 제거, 지연 공개) | `POST /api/stories` | BE |
| F-SOC-02 | 팔로우·시간순 피드 | `POST / DELETE /api/users/:id/follow`, `GET /api/stories` | BE |
| F-SOC-03 | 스토리 신고 | `POST /api/stories/:id/report` | BE |
| F-ADS-01 | 여행 필수 업종만 광고 노출 (음식점·카페·기념품샵 API 단계 거부) | `services/ads.ts`, `GET /api/ads` | BE |
| F-ADS-02 | 노출·클릭 계측 | `POST /api/ads/:id/impressions · clicks`, `AdLedger` 모델 | BE |
| F-ADS-03 | 숙소 예약 제휴 링크 | `POST /api/bookings/start`, `BookingPartner` 모델 | BE |
| F-OPS-01 | 신고 검토 큐 (API) | `GET /api/moderation/stories`, `PATCH /api/moderation/reports/:id` | BE |
| F-OPS-02 | 이벤트 Outbox + Kafka 발행 | `EventOutbox` 모델, `workers/event-dispatcher.ts` | BE |
| F-OPS-03 | 개인정보 보존기간 정리 배치 | `workers/privacy-cleanup.ts` | BE |
| F-SYS-01 | 익명 세션 토큰 인증 | `POST /api/auth/anonymous`, `services/auth.ts` | BE |
| F-SYS-02 | 보안 헤더·CORS·Rate limit | `index.ts` (helmet, cors, express-rate-limit) | BE |
| F-SYS-03 | 요청 추적 ID (`x-request-id`) | `index.ts` | BE |
| F-SYS-04 | **페이지 트리 기반 라우팅** | `web/src/routes/` (본 문서 7장) | FE |
| F-SYS-05 | 백엔드 미연결 시 사용자 안내 배너 | `routes/AppShell.tsx`, `api/client.ts` | FE |

**정리: 서비스 로직은 이미 대부분 존재한다. 남은 일은 화면·인프라·앱이다.**

**[설계 제안] 방향 전환에 따른 정리**: 8/25 방향 전환으로 반려동물 동반 기능(구 F-PET-01~03, `PlacePetPolicy`·`PetPolicyReport` 모델, `/api/places/:id/pet-policy`·`/api/pet-safety` 엔드포인트)은 목표 범위에서 제외한다. 코드 자체는 아직 저장소에 남아 있으므로 실제 삭제·마이그레이션은 별도 작업으로 진행하고, 이 문서의 M1~M3 계획에는 반영하지 않는다. 대신 같은 자리를 6.3장의 F-NAV-01~03(외국인 실시간 내비게이션)이 대체한다.

### 6.3 M1 (9/1 웹 MVP)에서 새로 만들 기능

| ID | 기능 | 왜 필요한가 | 담당 | 예상 |
| --- | --- | --- | :-: | --- |
| F-SYS-10 | **SQLite → PostgreSQL 전환** | SQLite는 동시 쓰기와 백업에 취약해 공개 서비스 운영이 불가능하다 | DB | 1.5일 |
| F-SYS-11 | 인덱스 설계·적용 | 272→수천 건으로 늘어날 때 근접 검색이 느려진다 | DB | 0.5일 |
| F-SYS-12 | Redis 캐시 적용 (이동시간·날씨·이미지) | 외부 API 호출량·요금·응답시간을 줄인다 | DB·BE | 0.5일 |
| F-SYS-13 | **클라우드 배포 + HTTPS 도메인** | 스토어 심사와 시연에 공개 접속이 필요하다 | INFRA | 1.5일 |
| F-SYS-14 | CI/CD 자동 배포 | 15일 동안 수십 번 배포한다. 수동 배포는 사고를 부른다 | INFRA | 1일 |
| F-SYS-15 | 시크릿 관리 (카카오·TourAPI 키, DB 비밀번호) | 현재 `.env` 로컬 파일에만 존재한다 | INFRA | 0.5일 |
| F-PLN-10 | **시작 화면** (`/`) | 현재 첫 화면이 곧바로 입력 폼이다. 서비스 설명이 없어 이탈한다 | FE | 0.5일 |
| F-PLN-11 | 위저드 단계 URL 분리 (`/plan/basic` 등) | 뒤로가기·새로고침·딥링크가 동작해야 한다 | FE | **완료** |
| F-MAP-10 | 지도 전체보기 (`/trips/:id/map`) | 좁은 화면에서 동선을 확인하기 어렵다 | FE | 0.5일 |
| F-OPS-10 | 404 화면 | 잘못된 링크에서 흰 화면이 뜬다 | FE | 0.2일 |
| F-SYS-16 | 운영 환경 로그·헬스체크 강화 | 장애 시 원인을 못 찾으면 복구가 불가능하다 | BE·INFRA | 0.5일 |
| F-NAV-01 | **도보·대중교통 턴바이턴 안내 (영어)** — `GET /api/routes/directions` 확장 | 구글맵이 한국에서 도보·대중교통 길찾기를 제대로 제공하지 못해 외국인이 현지에서 길을 잃는다(v2 3장·13장) | BE·FE | 1.5일 |
| F-NAV-02 | 택시 기사용 한글 목적지 카드 생성 — 신규 `GET /api/routes/taxi-card` | 한국어를 못 읽는 외국인이 택시 기사에게 목적지를 전달할 방법이 없다 | BE·FE | 0.5일 |
| F-NAV-03 | 환승 난이도 요약 (환승 횟수·도보 난이도, 경로 응답 필드 확장) | 대중교통 초행자에게 환승 부담을 사전에 알려준다. 별도 DB 테이블 불필요(실시간 API 응답 기반, v2 13.4장) | BE | 0.5일 |

### 6.4 M2 (9/9 앱)에서 만들 기능

| ID | 기능 | 담당 | 비고 |
| --- | --- | :-: | --- |
| F-APP-01 | Expo 프로젝트 초기화 + 공유 타입·API 클라이언트 연결 | FE·INFRA | `web/src/types.ts`, `api/client.ts` 재사용 |
| F-APP-02 | Expo Router 페이지 트리 구성 | FE | 7.5절 파일 트리 그대로 |
| F-APP-03 | 앱 온보딩·권한 요청 화면 | FE | 위치·알림 권한 목적 고지 (심사 필수) |
| F-APP-04 | 앱 내 지도 (WebView 또는 네이티브 지도) | FE | 8/27 방식 결정 |
| F-APP-05 | 딥링크·유니버설 링크 (`/s/:slug`, `/invite/:token`) | FE·INFRA | 공유 링크가 앱으로 열려야 한다 |
| F-APP-06 | 앱 아이콘·스플래시 | FE | EAS 빌드 필수 자산 |
| F-APP-07 | EAS Build 프로필 3종 | INFRA | development / preview / production |
| F-APP-08 | Android 서명 키 생성·보관 | INFRA | 분실 시 앱 업데이트 불가 |
| F-APP-09 | iOS 인증서·프로비저닝 프로파일 | INFRA | EAS 자동 관리 사용 |
| F-SOC-10 | 스토리 상세 화면 | FE | 피드에서 진입 |
| F-LOC-10 | 장소 상세 화면 | FE·BE | 리뷰 탭 포함 |
| F-COL-10 | 공동 편집 전용 화면 | FE·BE | 현재 준비 화면에 섞여 있음 |

### 6.5 M3 (스토어 심사 통과)에 반드시 필요한 기능

**이 항목들이 빠지면 심사에서 리젝된다.** 기능이 아니라 통과 조건으로 취급한다.

| ID | 항목 | 근거 | 담당 |
| --- | --- | --- | :-: |
| F-LGL-01 | **개인정보 처리방침 공개 URL** | 리젝 상위 사유 2위. 앱 내 링크와 스토어 등록란 양쪽에 필요 | INFRA·FE |
| F-LGL-02 | 이용약관 | UGC(스토리) 서비스는 금지행위·신고 정책 명시 필요 | FE |
| F-LGL-03 | 오픈소스 고지 | 라이선스 준수 | INFRA |
| F-OPS-11 | **UGC 신고·차단·삭제 경로** | 사용자 생성 콘텐츠 앱의 필수 요건 | FE·BE |
| F-OPS-12 | 신고 검토 큐 화면 (24시간 내 처리) | 운영 실효성 증명 | FE·BE |
| F-SYS-20 | **계정·데이터 삭제 경로** | 앱 내에서 데이터 삭제를 요청할 수 있어야 한다 | FE·INFRA |
| F-SYS-21 | 권한 사용 목적 고지 (위치·알림·사진) | iOS `Info.plist` usage description, Android 권한 근거 | FE |
| F-SYS-22 | **심사용 데모 계정·데이터** | 리젝 상위 사유 3위. 심사자가 즉시 기능을 볼 수 있어야 한다 | BE·INFRA |
| F-SYS-23 | 크래시 없는 실행 | 리젝 1위 사유 | 전원 |
| F-ADS-10 | 광고 표기 (`광고` 라벨 + 자연 추천과 분리 고지) | 이미 구현. 심사 시 스크린샷 근거로 사용 | BE·FE |

---

## 7. 페이지 트리

### 7.1 왜 페이지 트리를 먼저 만드는가

라우터 도입 전 이 프로젝트의 화면 전환은 다음과 같았다.

```tsx
// 이전 App.tsx — 화면을 useState 로 갈아끼웠다
type ViewState =
  | { kind: "form" }
  | { kind: "loading" }
  | { kind: "result"; tripId: string; itinerary: ItineraryOutput }
  | { kind: "error"; message: string };
```

이 구조에는 네 가지 문제가 있었다.

1. **URL이 화면을 나타내지 못한다.** 뒤로가기·새로고침·북마크·딥링크가 전부 깨진다. 앱에서는 공유 링크로 특정 일정을 열 수 없다.
2. **화면 단위로 일을 나눌 수 없다.** 모든 화면이 `App.tsx` 한 파일과 `ResultDashboard.tsx` 한 파일에 몰려 있어, 4명이 동시에 손대면 충돌한다.
3. **권한을 화면 단위로 걸 수 없다.** 열람자와 편집자를 나누려면 화면마다 조건문을 흩뿌려야 한다.
4. **앱으로 옮길 기준이 없다.** "무엇을 옮겨야 하는가"의 목록 자체가 없다.

**페이지 트리는 이 네 가지를 한 번에 해결한다.** 화면 하나가 경로 하나, 파일 하나, 담당자 한 명, 마일스톤 하나에 대응한다.

### 7.2 단일 진실 공급원 원칙

페이지 트리는 문서가 아니라 **코드**에 있다.

```
web/src/routes/routeTree.ts   ← 여기가 유일한 정의
```

이 파일 하나가 네 가지를 동시에 정의한다.

| 용도 | 방식 |
| --- | --- |
| 웹 라우팅 | `AppRouter.tsx` 가 같은 경로 목록으로 `<Route>` 를 구성 |
| 앱 라우팅 | 각 노드의 `expoPath` 가 Expo Router 파일 경로와 1:1 대응 |
| 작업 분배 | `owners`, `milestone` 로 누가 언제까지 만들지 결정 |
| 문서 | `node web/scripts/print-route-tree.mjs table` 로 이 장의 표를 재생성 |

**규칙: 화면을 추가할 때는 반드시 `routeTree.ts` 에 노드를 먼저 추가한 뒤 페이지 파일을 만든다.** 순서를 지키지 않으면 문서와 코드가 갈라진다.

노드 하나의 정의는 다음과 같다.

```ts
export interface RouteNode {
  id: string;          // 고유 식별자. 분석 이벤트의 screen_id 로도 사용
  path: string;        // react-router 경로 (부모 기준 상대)
  expoPath: string;    // Expo Router 파일 경로
  titleKo: string;
  titleEn: string;
  purpose: string;     // 이 화면이 사용자에게 무엇을 해주는가
  platform: "WEB" | "APP" | "BOTH";
  access: "PUBLIC" | "SESSION" | "TRIP_VIEWER" | "TRIP_EDITOR" | "TRIP_OWNER" | "ADMIN";
  apis: string[];      // 이 화면이 호출하는 엔드포인트
  owners: ("FE" | "BE" | "DB" | "INFRA")[];
  milestone: "M1_WEB" | "M2_APP" | "M3_REVIEW" | "M4_LATER";
  status: "DONE" | "PARTIAL" | "TODO";
  children?: RouteNode[];
}
```

### 7.3 전체 페이지 트리

총 **41개 화면**이다. 마일스톤별로 M1 17개, M2 13개, M3 8개, M4 3개.

```
/                                        시작 화면
├── /onboarding                          앱 온보딩·권한 요청            [앱 전용]
│
├── /plan                                여행 조건 입력 (위저드)
│   ├── /plan/basic                      1단계 · 기본 정보
│   ├── /plan/taste                      2단계 · 취향·코스
│   ├── /plan/conditions                 3단계 · 이용 조건
│   └── /plan/confirm                    4단계 · 최종 확인
│
├── /generating/:tripId                  일정 생성 진행 (SSE)
│
├── /trips/:tripId                       여행 대시보드 (공통 레이아웃)
│   ├── overview                         홈 · 여행 요약
│   ├── schedule                         일정 · 동선 편집
│   │   └── schedule/:dayIndex           특정 날짜 (딥링크)
│   ├── map                              지도 전체보기
│   ├── discover                         로컬 · 축제와 기념품
│   │   ├── discover/festivals           축제 목록
│   │   └── discover/souvenirs           기념품샵 지도
│   ├── together                         함께 · 여행 기록
│   ├── prep                             여행 준비
│   └── collaborate                      동행자 공동 편집          [편집자 이상]
│
├── /places/:placeId                     장소 상세
│   └── /places/:placeId/reviews         리뷰와 방문 인증
│
├── /trips/:tripId/schedule/:dayIndex/navigate  경로 상세 · 턴바이턴 내비게이션
│
├── /stories                             스토리 피드
│   ├── /stories/new                     스토리 작성
│   └── /stories/:storyId                스토리 상세
├── /users/:userId                       사용자 프로필
│
├── /me                                  내 정보
│   ├── /me/trips                        내 여행 목록
│   ├── /me/local                        내 로컬 등급
│   └── /me/settings                     설정 (언어·알림·데이터 삭제)
│
├── /s/:slug                             공유된 일정 (읽기 전용, 30일)
├── /invite/:inviteToken                 동행 초대 수락 (7일)
│
├── /admin                               운영 콘솔                     [웹 전용]
│   ├── /admin/moderation                신고 검토 큐
│   ├── /admin/kpis                      KPI 대시보드
│   └── /admin/ads                       광고 캠페인 관리
│
├── /legal                               약관·정책
│   ├── /legal/terms                     이용약관
│   ├── /legal/privacy                   개인정보 처리방침
│   └── /legal/open-source               오픈소스 고지
│
└── /*                                   페이지를 찾을 수 없음
```

### 7.4 페이지별 명세

> 이 표는 `node web/scripts/print-route-tree.mjs table` 출력이다. 코드가 바뀌면 이 명령으로 갱신한다.

| 경로 | 화면 | 플랫폼 | 권한 | 담당 | 마일스톤 | 상태 |
| --- | --- | :-: | :-: | :-: | :-: | :-: |
| `/` | 시작 화면 | 웹·앱 | PUBLIC | FE | 9/1 웹 | 예정 |
| `/onboarding` | 앱 온보딩·권한 요청 | 앱 | PUBLIC | FE | 심사 필수 | 예정 |
| `/plan` | 여행 조건 입력 | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/plan/basic` | 　1단계 · 기본 정보 | 웹·앱 | PUBLIC | FE | 9/1 웹 | 완료 |
| `/plan/taste` | 　2단계 · 취향·코스 | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/plan/conditions` | 　3단계 · 이용 조건 | 웹·앱 | PUBLIC | FE | 9/1 웹 | 완료 |
| `/plan/confirm` | 　4단계 · 최종 확인 | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/generating/:tripId` | 일정 생성 진행 | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/trips/:tripId` | 여행 대시보드 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/1 웹 | 완료 |
| `/trips/:tripId/overview` | 　홈 · 여행 요약 | 웹·앱 | TRIP_VIEWER | FE | 9/1 웹 | 완료 |
| `/trips/:tripId/schedule` | 　일정 · 동선 편집 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/1 웹 | 완료 |
| `/trips/:tripId/schedule/:dayIndex` | 　일정 · 특정 날짜 | 웹·앱 | TRIP_VIEWER | FE | 9/1 웹 | 완료 |
| `/trips/:tripId/map` | 　지도 전체보기 | 웹·앱 | TRIP_VIEWER | FE | 9/1 웹 | 부분 |
| `/trips/:tripId/discover` | 　로컬 · 축제와 기념품 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/1 웹 | 완료 |
| `/trips/:tripId/discover/festivals` | 　축제 목록 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/9 앱 | 부분 |
| `/trips/:tripId/discover/souvenirs` | 　기념품샵 지도 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/9 앱 | 부분 |
| `/trips/:tripId/together` | 　함께 · 여행 기록 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/9 앱 | 완료 |
| `/trips/:tripId/prep` | 　여행 준비 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/1 웹 | 완료 |
| `/trips/:tripId/collaborate` | 　동행자 공동 편집 | 웹·앱 | TRIP_EDITOR | FE·BE | 9/9 앱 | 부분 |
| `/places/:placeId` | 장소 상세 | 웹·앱 | PUBLIC | FE·BE | 9/9 앱 | 예정 |
| `/places/:placeId/reviews` | 　리뷰와 방문 인증 | 웹·앱 | SESSION | FE·BE | 9/9 앱 | 예정 |
| `/trips/:tripId/schedule/:dayIndex/navigate` | 경로 상세 · 턴바이턴 내비게이션 | 웹·앱 | TRIP_VIEWER | FE·BE | 9/9 앱 | 예정 |
| `/stories` | 스토리 피드 | 웹·앱 | PUBLIC | FE·BE | 9/9 앱 | 부분 |
| `/stories/new` | 　스토리 작성 | 웹·앱 | SESSION | FE·BE | 9/9 앱 | 부분 |
| `/stories/:storyId` | 　스토리 상세 | 웹·앱 | PUBLIC | FE | 9/9 앱 | 예정 |
| `/users/:userId` | 사용자 프로필 | 웹·앱 | PUBLIC | FE·BE | 9/9 앱 | 예정 |
| `/me` | 내 정보 | 웹·앱 | SESSION | FE·BE | 9/9 앱 | 예정 |
| `/me/trips` | 　내 여행 목록 | 웹·앱 | SESSION | FE·BE | 9/9 앱 | 예정 |
| `/me/local` | 　내 로컬 등급 | 웹·앱 | SESSION | FE·BE | 후속 | 예정 |
| `/me/settings` | 　설정 | 웹·앱 | SESSION | FE·INFRA | 심사 필수 | 예정 |
| `/s/:slug` | 공유된 일정 (읽기 전용) | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/invite/:inviteToken` | 동행 초대 수락 | 웹·앱 | PUBLIC | FE·BE | 9/1 웹 | 완료 |
| `/admin` | 운영 콘솔 | 웹 | ADMIN | FE·BE·INFRA | 심사 필수 | 예정 |
| `/admin/moderation` | 　신고 검토 큐 | 웹 | ADMIN | FE·BE | 심사 필수 | 예정 |
| `/admin/kpis` | 　KPI 대시보드 | 웹 | ADMIN | FE·BE·DB | 후속 | 예정 |
| `/admin/ads` | 　광고 캠페인 관리 | 웹 | ADMIN | FE·BE | 후속 | 예정 |
| `/legal` | 약관·정책 | 웹·앱 | PUBLIC | FE·INFRA | 심사 필수 | 예정 |
| `/legal/terms` | 　이용약관 | 웹·앱 | PUBLIC | FE | 심사 필수 | 예정 |
| `/legal/privacy` | 　개인정보 처리방침 | 웹·앱 | PUBLIC | FE·INFRA | 심사 필수 | 예정 |
| `/legal/open-source` | 　오픈소스 고지 | 웹·앱 | PUBLIC | INFRA | 심사 필수 | 예정 |
| `/*` | 페이지를 찾을 수 없음 | 웹·앱 | PUBLIC | FE | 9/1 웹 | 예정 |

### 7.5 화면별 API 매핑

각 화면이 어떤 엔드포인트를 쓰는지가 FE·BE 병렬 작업의 계약이다.

| 화면 | 호출 API |
| --- | --- |
| `/` | `GET /api/places?limit=1` · `GET /api/course-categories` |
| `/plan/basic` | `GET /api/locations/search` |
| `/plan/taste` | `GET /api/course-categories` |
| `/plan/conditions` | `GET /api/places?category=LODGING` |
| `/plan/confirm` | `POST /api/trips` · `POST /api/trips/:id/itineraries:generate` |
| `/generating/:tripId` | `GET /api/itinerary-jobs/:jobId` · `GET /api/itinerary-jobs/:jobId/events` (SSE) |
| `/trips/:tripId` (레이아웃) | `GET /api/trips/:id/itinerary` · `GET /api/itineraries/:id/collaboration` |
| `…/overview` | `GET /api/trips/:id/itinerary` · `GET /api/weather` |
| `…/schedule` | `POST /api/itineraries/:id/days/:dayIndex/reoptimize` · `GET /api/itineraries/:id/items/:itemId/alternatives` · `POST /api/itineraries/:id/undo` · `GET /api/routes/directions` |
| `…/map` | `GET /api/routes/directions` · `GET /api/shops/souvenir` |
| `…/discover` | `GET /api/festivals` · `GET /api/events` · `GET /api/shops/souvenir` · `POST /api/itineraries/:id/festivals/:placeId` |
| `…/together` | `GET /api/stories` · `POST /api/users/:id/follow` · `POST /api/stories/:id/report` |
| `…/prep` | `GET /api/ads` · `POST /api/ads/:id/impressions` · `POST /api/ads/:id/clicks` · `GET /api/places/:id/booking-options` · `POST /api/bookings/start` · `POST /api/itineraries/:id/share` |
| `…/collaborate` | `POST /api/trips/:tripId/members/invite` · `GET /api/itineraries/:id/collaboration` · `PATCH /api/itineraries/:id/items/:itemId/collaborate` |
| `/places/:placeId` | `GET /api/places/:id` · `GET /api/places/:id/image` |
| `/places/:placeId/reviews` | `GET / POST /api/places/:id/reviews` · `POST /api/places/:id/visits/verify` |
| `/trips/:tripId/schedule/:dayIndex/navigate` | `GET /api/routes/directions` · `GET /api/routes/taxi-card` |
| `/stories` · `/stories/new` · `/stories/:storyId` | `GET / POST /api/stories` · `POST /api/stories/:id/report` |
| `/users/:userId` | `GET /api/local-profile` · `GET /api/stories` · `POST / DELETE /api/users/:id/follow` |
| `/me/*` | `POST /api/auth/anonymous` · `GET /api/local-profile` |
| `/s/:slug` | `GET /api/s/:slug` · `POST /api/s/:slug/clone` |
| `/invite/:inviteToken` | `POST /api/collaboration/invites/:inviteToken/accept` |
| `/admin/moderation` | `GET /api/moderation/stories` · `PATCH /api/moderation/reports/:id` |
| `/admin/kpis` | `GET /api/analytics/kpis` · `GET /api/events/catalog` |
| `/admin/ads` | `POST /api/businesses` · `POST /api/businesses/:id/campaigns` · `GET /api/ads` |

### 7.6 웹 구현 구조 (완료)

```
web/src/
├── routes/
│   ├── routeTree.ts        페이지 트리 정의 — 단일 진실 공급원
│   ├── AppRouter.tsx       트리와 1:1 대응하는 react-router 구성
│   ├── AppShell.tsx        전역 레이아웃 · 장소수/숙소 조회 · 오프라인 배너
│   └── paths.ts            타입 안전 경로 빌더 (문자열 하드코딩 금지)
├── pages/
│   ├── HomePage.tsx                시작 화면
│   ├── PlanWizardPage.tsx          위저드 (단계를 URL 로 제어)
│   ├── GeneratingPage.tsx          SSE 진행률
│   ├── InviteAcceptPage.tsx        초대 수락
│   ├── SharedItineraryPage.tsx     공유 열람
│   ├── NotFoundPage.tsx
│   ├── trip/
│   │   ├── TripContext.tsx         하위 화면이 공유하는 상태 정의
│   │   ├── TripLayout.tsx          일정 로드 · 재최적화 · 모달 소유
│   │   ├── TripOverviewPage.tsx
│   │   ├── TripSchedulePage.tsx
│   │   ├── TripMapPage.tsx
│   │   ├── TripDiscoverPage.tsx
│   │   ├── TripFestivalsPage.tsx
│   │   ├── TripSouvenirsPage.tsx
│   │   ├── TripTogetherPage.tsx
│   │   ├── TripPrepPage.tsx
│   │   └── TripCollaboratePage.tsx
│   ├── place/  story/  user/  me/  admin/  legal/
└── components/
    └── RoutePlaceholder.tsx        미구현 화면의 작업 명세를 화면에 렌더
```

**세 가지 설계 결정**

1. **`TripLayout` 이 상태를 소유한다.** 일정 조회·부분 재최적화·되돌리기·제외 확인 모달·대체 장소 모달을 모두 레이아웃이 갖고, 하위 5개 화면은 `useTrip()` 으로 읽기만 한다. 화면을 추가해도 API를 다시 호출할 필요가 없다.

2. **미구현 화면이 곧 작업 명세다.** `RoutePlaceholder` 는 `routeTree.ts` 에서 해당 노드를 읽어 **경로·앱 파일 경로·담당·권한·호출할 API·마일스톤**을 화면에 그대로 출력한다. 팀원이 그 URL을 열면 무엇을 만들어야 하는지가 바로 보인다.

3. **구버전 링크를 버리지 않는다.** 라우터 도입 전 공유된 `?trip=` · `?share=` · `?invite=` 링크는 `LegacyQueryRedirect` 가 새 경로로 자동 이동시킨다.

### 7.7 앱 라우팅 대응 (Expo Router)

Expo Router는 파일 경로가 곧 URL이다. `routeTree.ts` 의 `expoPath` 를 그대로 만들면 웹과 같은 트리가 된다.

```
app/
├── _layout.tsx                       AppShell 대응 (전역 Provider)
├── index.tsx                         /
├── onboarding.tsx                    /onboarding
├── plan/
│   ├── _layout.tsx                   위저드 스택
│   ├── basic.tsx                     /plan/basic
│   ├── taste.tsx                     /plan/taste
│   ├── conditions.tsx                /plan/conditions
│   └── confirm.tsx                   /plan/confirm
├── generating/[tripId].tsx           /generating/:tripId
├── trips/
│   └── [tripId]/
│       ├── _layout.tsx               TripLayout 대응 (하단 탭 네비게이션)
│       ├── overview.tsx
│       ├── schedule/
│       │   ├── index.tsx
│       │   └── [dayIndex].tsx
│       ├── map.tsx
│       ├── discover/
│       │   ├── index.tsx
│       │   ├── festivals.tsx
│       │   └── souvenirs.tsx
│       ├── together.tsx
│       ├── prep.tsx
│       ├── collaborate.tsx
│       └── schedule/[dayIndex]/navigate.tsx
├── places/[placeId]/
│   ├── _layout.tsx
│   ├── index.tsx
│   └── reviews.tsx
├── stories/
│   ├── index.tsx
│   ├── new.tsx
│   └── [storyId].tsx
├── users/[userId].tsx
├── me/
│   ├── _layout.tsx
│   ├── trips.tsx
│   ├── local.tsx
│   └── settings.tsx
├── s/[slug].tsx
├── invite/[inviteToken].tsx
├── legal/
│   ├── _layout.tsx
│   ├── terms.tsx
│   ├── privacy.tsx
│   └── open-source.tsx
└── +not-found.tsx
```

**웹과 앱의 차이 (의도된 것)**

| 항목 | 웹 | 앱 |
| --- | --- | --- |
| 5개 서비스 탭 | 좌측 사이드바 | **하단 탭 바** (모바일 관행) |
| 운영 콘솔 `/admin/*` | 있음 | **없음** (웹에서만) |
| 온보딩·권한 요청 | 없음 | **있음** (심사 필수) |
| 지도 | Kakao Maps JS SDK | WebView 또는 네이티브 지도 |

**공유 가능한 것**: `types.ts`(타입 계약), `api/client.ts`(35개 호출 함수, `fetch` 기반이라 RN에서 그대로 동작), `i18n.ts`, 화면 로직·상태 흐름. **공유 불가능한 것**: JSX 태그(`div` → `View`), CSS(→ StyleSheet), 라우터 API.

### 7.8 딥링크 설계 [설계 제안]

공유 링크가 앱에서도 열려야 서비스가 완성된다.

| 링크 | 웹 | 앱 |
| --- | --- | --- |
| `https://<도메인>/s/:slug` | 읽기 전용 일정 | 앱 설치 시 앱으로, 미설치 시 웹으로 |
| `https://<도메인>/invite/:token` | 초대 수락 | 동일 |
| `https://<도메인>/trips/:id/schedule/:day` | 특정 날짜 | 동일 |

- **iOS**: Universal Links — `https://<도메인>/.well-known/apple-app-site-association` 를 INFRA가 배포
- **Android**: App Links — `https://<도메인>/.well-known/assetlinks.json`
- 두 파일 모두 **HTTPS·`Content-Type: application/json`·리다이렉트 없음**이어야 한다. Nginx 설정에 명시적으로 추가한다.
- 커스텀 스킴 `localroute://` 는 개발 편의용으로만 쓴다. 스토어 심사에서는 Universal/App Links를 권장한다.

---

## 8. 데이터베이스 계획 (DB 담당)

### 8.1 현재 상태 [코드 실측]

| 항목 | 값 |
| --- | --- |
| DBMS | SQLite (`server/prisma/dev.db`) |
| ORM | Prisma 5.22 |
| 모델 수 | 26개 |
| 마이그레이션 | 10개 |
| 데이터 | Place 272 · Trip 44 · Itinerary 151 |
| 시드 스크립트 | `seed` · `ensure-pet-safety`(방향 전환으로 MVP 범위 밖, 실행 불필요) · `ensure-translations` · `ensure-commerce` · `ensure-v2-features` · `import-tourapi` · `import-kakao-reviews` |

**SQLite로 운영할 수 없는 이유**

1. **동시 쓰기 직렬화.** 여러 사용자가 동시에 일정을 생성하면 쓰기 잠금이 걸린다. 시연 중 여러 명이 동시에 누르면 즉시 드러난다.
2. **백업·복구 절차 부재.** 파일 하나가 손상되면 전부 잃는다.
3. **다중 인스턴스 불가.** 서버를 2대로 늘리는 순간 데이터가 갈라진다.
4. **동시성 테스트 불가.** 부하 테스트(`npm run load:test`) 결과가 실제 운영을 반영하지 못한다.

### 8.2 도메인별 모델 분류 (26개)

| 도메인 | 모델 |
| --- | --- |
| 장소·콘텐츠 | `Place` `PlaceTranslation` `PlacePetPolicy`(방향 전환으로 MVP 범위 밖) `PetPolicyReport`(방향 전환으로 MVP 범위 밖) |
| 여행·일정 | `Trip` `TripPreference` `ItineraryJob` `Itinerary` `ItineraryDay` `ItineraryItem` `ItineraryRevision` |
| 사용자·검증 | `AnonymousSession` `VisitVerification` `Review` `LocalProfile` |
| 협업·공유 | `TripMember` `ItineraryShare` |
| 소셜 | `Story` `StoryReport` `Follow` |
| 커머스 | `Business` `AdCampaign` `AdLedger` `BookingPartner` `BookingRecord` |
| 시스템 | `EventOutbox` |

### 8.3 PostgreSQL 전환 절차 [설계 제안]

**8/26 (수) — 전환 실행**

```bash
# 1. 로컬에 PostgreSQL 16 컨테이너 기동
docker run -d --name lr-pg -e POSTGRES_PASSWORD=... -e POSTGRES_DB=local_route -p 5432:5432 postgres:16

# 2. Prisma datasource 변경
#    server/prisma/schema.prisma
#      provider = "postgresql"
#    .env
#      DATABASE_URL="postgresql://postgres:...@localhost:5432/local_route"

# 3. 기존 마이그레이션 폴더를 백업하고 초기 마이그레이션 재생성
#    (SQLite용 SQL 은 PostgreSQL 에서 그대로 실행되지 않는다)
mv server/prisma/migrations server/prisma/migrations.sqlite.bak
npx prisma migrate dev --name init_postgres --schema server/prisma/schema.prisma

# 4. 시드 전부 실행 (seed:pet-safety는 방향 전환으로 MVP 범위 밖 — 스킵 가능)
npm run seed --workspace server
npm run seed:translations --workspace server
npm run seed:commerce --workspace server
npm run seed:v2 --workspace server

# 5. 서버 테스트로 회귀 확인
npm test --workspace server
```

**전환 시 반드시 확인할 5가지 [위험]**

| # | 항목 | 확인 방법 |
| --- | --- | --- |
| 1 | `DateTime` 타임존 | SQLite는 문자열, PostgreSQL은 `timestamptz`. **일정 시각이 9시간 밀리는 버그**가 여기서 나온다. 생성된 일정의 `plannedArrival` 을 눈으로 대조한다 |
| 2 | `Json` 타입 | SQLite는 문자열로 저장. PostgreSQL은 `jsonb`. `tasteTags`, `allergies` 등을 확인 |
| 3 | `Float` / 비용 | 금액을 `Float` 로 두면 반올림 오차가 생긴다. 정수(원 단위) 또는 `Decimal` 로 통일 |
| 4 | 대소문자 정렬 | `ORDER BY nameKo` 결과가 달라질 수 있다. 한글 정렬을 확인 |
| 5 | 자동 증가 / cuid | Prisma `@default(cuid())` 는 문제없으나 `autoincrement()` 사용 모델이 있으면 시퀀스 확인 |

**8/27 (목) — 검증 전용 하루.** 전환 다음 날은 새 기능을 만들지 않고 **일정 생성 결과가 SQLite 시절과 동일한지** 대조한다. 같은 조건으로 일정을 생성해 방문지·시각·비용을 비교한다.

### 8.4 인덱스 설계 [설계 제안]

| 테이블 | 인덱스 | 이유 |
| --- | --- | --- |
| `Place` | `(category, isActive)` | 카테고리별 후보 조회가 추천 엔진의 첫 단계 |
| `Place` | `(lat, lng)` 복합 | 근접 검색의 1차 범위 필터(바운딩 박스) |
| `Place` | `(localScore DESC)` | 로컬 점수 상위 정렬 |
| `ItineraryItem` | `(itineraryDayId, sortOrder)` | 일자별 항목 정렬 조회 |
| `ItineraryDay` | `(itineraryId, dayIndex)` | 일자 조회 |
| `Trip` | `(sessionId, createdAt DESC)` | 내 여행 목록 |
| `Story` | `(publishedAt DESC)` where 공개 | 피드 시간순 |
| `Follow` | `(followerId, followingId)` unique | 중복 팔로우 방지 + 조회 |
| `EventOutbox` | `(dispatchedAt NULL, createdAt)` 부분 인덱스 | 미발행 이벤트만 스캔 |
| `AdLedger` | `(campaignId, eventType, createdAt)` | 집계 |

**근접 검색 방식 결정 [설계 제안]**: PostGIS 확장은 설치·학습 비용이 있다. 272~수천 건 규모에서는 **바운딩 박스로 1차 필터 후 하버사인 공식으로 정렬**하는 것으로 충분하다. PostGIS 도입은 M4로 미룬다.

```sql
-- 반경 R km 안의 후보를 좁힌 뒤 정확 거리로 정렬
WHERE lat BETWEEN :lat - :dLat AND :lat + :dLat
  AND lng BETWEEN :lng - :dLng AND :lng + :dLng
ORDER BY 6371 * acos(...)  -- 하버사인
LIMIT 50
```

### 8.5 캐시 설계 (Redis)

| 키 패턴 | 값 | TTL | 이유 |
| --- | --- | --- | --- |
| `route:{origin}:{dest}:{mode}` | 이동시간·거리 | 24시간 | 카카오 모빌리티 호출 절감. 도로는 하루 단위로 안 바뀐다 |
| `weather:{region}:{date}` | 예보 | 1시간 | 이미 코드에 1시간 캐시 개념 존재 |
| `placeimg:{placeId}` | 썸네일 URL·출처 | 24시간 | 이미지 검색 API 쿼터 보호 |
| `session:{tokenHash}` | 세션 메타 | 세션 수명 | DB 조회 절감 |
| `courses:v1` | 코스 카테고리 설정 | 무기한(배포 시 무효화) | 설정 파일 파싱 절감 |

**원칙**: 캐시가 없어도 서비스는 동작해야 한다. Redis 장애 시 원본 조회로 폴백한다.

### 8.6 백업·복구

| 항목 | 절차 |
| --- | --- |
| 정기 백업 | 매일 03:00 `pg_dump -Fc` → 별도 스토리지. 7일 보관 |
| 배포 전 백업 | 마이그레이션이 포함된 배포는 직전에 수동 `pg_dump` |
| 복구 리허설 | **9/1 이전에 최소 1회** 백업본으로 빈 DB 복원을 실습한다. 해보지 않은 복구 절차는 없는 것과 같다 |
| 운영 금지 사항 | 운영 DB에 `prisma migrate dev` 실행 금지. 반드시 `migrate deploy` |

---

## 9. 인프라 계획 (INFRA 담당)

### 9.1 M1 목표 아키텍처 (안 A · 단일 VM)

```mermaid
graph LR
    User["사용자 브라우저 · 앱"]
    subgraph VM["클라우드 VM 1대"]
        NGINX["Nginx<br/>443 HTTPS<br/>SPA fallback · /api 프록시"]
        WEB["web 컨테이너<br/>정적 빌드 산출물"]
        API["server 컨테이너<br/>Express :4000"]
        PG[("PostgreSQL 16<br/>컨테이너")]
        REDIS[("Redis 7<br/>컨테이너")]
    end
    EXT["외부 API<br/>카카오 · TourAPI · 기상청"]

    User -->|HTTPS| NGINX
    NGINX --> WEB
    NGINX -->|/api| API
    API --> PG
    API --> REDIS
    API --> EXT
```

이미 저장소에 `server/Dockerfile`, `web/Dockerfile`, `web/nginx.conf`, `docker-compose.yml` 이 존재한다. **compose에 PostgreSQL·Redis 서비스를 추가하고 `DATABASE_URL` 을 바꾸는 것이 주 작업**이다.

### 9.2 환경 구성

| 환경 | 목적 | DB | 도메인 | 배포 트리거 |
| --- | --- | --- | --- | --- |
| local | 개발 | Docker PostgreSQL | `localhost:5173` | 수동 (`npm run dev`) |
| **staging** | 매일 18:00 데모·통합 검증 | 별도 스키마 | `staging.<도메인>` | `main` 머지 시 자동 |
| **production** | 공개 서비스·스토어 심사 | 운영 DB | `<도메인>` | 태그 푸시 시 수동 승인 |

**staging이 반드시 필요한 이유**: 앱은 로컬 서버를 볼 수 없다. 실기기에서 앱을 테스트하려면 공개 접속 가능한 API가 필요하다. staging이 없으면 9/2부터 앱 개발이 막힌다.

### 9.3 CI/CD 파이프라인

```
push / PR
  └─ CI (GitHub Actions)
       ├─ npm ci
       ├─ npm test --workspace server
       ├─ npm run build --workspace server
       ├─ npm run build --workspace web
       ├─ npx tsc -b web            (타입 검사)
       └─ npm audit --audit-level=high

main 머지
  └─ staging 자동 배포
       ├─ docker build & push
       ├─ VM 에서 compose pull & up -d
       ├─ npx prisma migrate deploy
       └─ /health 확인 → 실패 시 이전 이미지로 롤백

태그 v* 푸시
  └─ production 배포 (수동 승인 후)
       └─ 배포 전 pg_dump 백업
```

### 9.4 시크릿 목록

| 이름 | 용도 | 보관 위치 |
| --- | --- | --- |
| `DATABASE_URL` | PostgreSQL 접속 | GitHub Actions Secrets + VM 환경변수 |
| `REDIS_URL` | 캐시 | 동일 |
| `KAKAO_REST_API_KEY` | 로컬·모빌리티 API | 동일 |
| `VITE_KAKAO_JS_KEY` | 지도 SDK (빌드 시 주입, 공개됨 → 도메인 제한 필수) | 동일 |
| `TOURAPI_SERVICE_KEY` | 관광공사 | 동일 |
| `NAVER_SEARCH_CLIENT_ID/SECRET` | 이미지 검색 | 동일 |
| `GOOGLE_CUSTOM_SEARCH_KEY/CX` | 이미지 검색 보조 | 동일 |
| `BOOKING_WEBHOOK_SECRET` | 예약 웹훅 서명 | 동일 |
| `ADMIN_TOKEN` | 운영 콘솔 | 동일 |
| `CORS_ORIGINS` | 허용 오리진 | 동일 |
| `EXPO_TOKEN` | EAS 빌드 자동화 | GitHub Actions Secrets |
| Android keystore | 앱 서명 | **EAS 관리 + 별도 안전 보관. 분실 시 앱 업데이트 영구 불가** |
| iOS 인증서·프로파일 | 앱 서명 | EAS 자동 관리 |

**[위험] `VITE_` 접두사 변수는 빌드 결과물에 그대로 박힌다.** 카카오 JS 키는 브라우저에 노출되므로 **카카오 개발자 콘솔에서 도메인 제한을 반드시 설정**한다. 이것을 놓치면 키가 도용된다.

### 9.5 비용 [가설·추정]

| 항목 | 월 예상 | 비고 |
| --- | --- | --- |
| 클라우드 VM (2vCPU/4GB) | 3~6만원 | 안 A 기준 |
| 도메인 | 연 1~2만원 | |
| HTTPS 인증서 | 0원 | Let's Encrypt |
| Apple Developer Program | 99 USD / 년 | **확정 사실** |
| Google Play 개발자 등록 | 25 USD 1회 | **[추가 확인 필요]** 현재 금액 재확인 |
| EAS Build | 무료 티어 있음 | 빌드 횟수 초과 시 과금. **[추가 확인 필요]** |
| 외부 API | 0원 (무료 쿼터 내) | 쿼터 초과 시 과금. 8/26 확인 |

**8/26에 반드시 확인**: 카카오 모빌리티 길찾기 API와 TourAPI의 일일 호출 한도. 한도를 넘으면 시연 중 지도가 죽는다.

### 9.6 보안 체크리스트

- [x] `helmet` 보안 헤더 (적용됨)
- [x] CORS 오리진 화이트리스트 (적용됨)
- [x] Rate limit (적용됨)
- [x] `.env` 가 `.gitignore` 에 포함 (확인됨 — 라이브 키는 저장소에 없음)
- [ ] HTTPS 강제 (HSTS) — 운영 배포 시 활성화
- [ ] 기본 시크릿 값 제거 (`BOOKING_WEBHOOK_SECRET` 의 `change-before-production` 등)
- [ ] `ADMIN_TOKEN` 을 추측 불가능한 값으로 설정
- [ ] `npm audit --audit-level=high` 통과
- [ ] 카카오 JS 키 도메인 제한
- [ ] DB 포트를 외부에 노출하지 않음 (compose 내부 네트워크만)

---

## 10. 일자별 실행 계획

### 10.1 전체 캘린더

```
8월                                     9월
  25 화  킥오프 · 스토어 계정 착수         1 화  ★ M1 웹 MVP 배포
  26 수  PostgreSQL 전환 · 배포 골격       2 수  ★ Expo 초기화 · 첫 빌드 · 비공개 테스트 시작
  27 목  DB 검증 · staging 기동            3 목  앱 페이지 트리 · 핵심 화면
  28 금  CI/CD · 화면 보강                 4 금  앱 일정·지도 화면
  29 토  (완충)                            5 토  앱 나머지 화면 · 전원 FE 투입
  30 일  (완충)                            6 일  (완충)
  31 월  통합 검증 · 리허설                 7 월  실기기 테스트 · 심사 자산 준비
                                          8 화  버그 수정 · 스토어 등록 정보 입력
                                          9 수  ★ M2 앱 완료 · feature freeze
                                         10 목  iOS 제출
                                         16 수  Play 비공개 테스트 14일 충족 → 프로덕션 신청
                                         23 수  Play 출시 (예상)
```

### 10.2 Phase 1 — 웹 MVP (8/25 화 ~ 9/1 화)

#### 8/25 (화) — 킥오프

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **전원** | 이 문서 정독 + 30분 킥오프. 14장 범위 조정안 중 하나 선택 | 앱 v1 화면 범위 합의 문서화 |
| **INFRA** | **최우선: Google Play 개발자 계정 생성 시작, Apple Developer Program 가입 시작** | 결제 완료, 심사 대기 상태 진입 |
| **INFRA** | 클라우드 VM 발급, 도메인 확보 | SSH 접속 가능, DNS A 레코드 등록 |
| **DB** | PostgreSQL 16 컨테이너 로컬 기동, `schema.prisma` provider 전환 착수 | 로컬에서 `prisma db push` 성공 |
| **BE** | 외부 API 쿼터 확인 (카카오 모빌리티, TourAPI), 환경변수 목록 정리 | 9.4절 표 확정 |
| **FE** | 페이지 트리 확인, 시작 화면(`/`) 착수 | `routeTree.ts` 리뷰 완료 |

> **[위험] 오늘 스토어 계정을 시작하지 않으면 9월 출시가 불가능해질 수 있다.** 계정 개설은 팀이 통제할 수 없는 대기 시간을 포함한다.

#### 8/26 (수) — PostgreSQL 전환 · 배포 골격

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **DB** | PostgreSQL 마이그레이션 재생성, 시드 6종 실행 | `npm test --workspace server` 통과 |
| **DB** | 12:00까지 INFRA에 DB 요구사항 전달 | 이슈 코멘트 |
| **DB** | 18:00까지 `schema.prisma` 머지 | PR 머지 |
| **INFRA** | compose에 postgres·redis 추가, VM에 Docker 설치 | `docker compose up` 으로 전체 기동 |
| **INFRA** | Nginx + Let's Encrypt HTTPS | `https://<도메인>` 접속 시 인증서 유효 |
| **BE** | PostgreSQL 대응 (쿼리·타입 이슈 수정), `/health` 에 DB·Redis 상태 추가 | `/health` 가 의존성 상태 반환 |
| **FE** | 시작 화면 완성, 404 화면, 약관·정책 화면 골격 | `/`, `/*`, `/legal/*` 동작 |

#### 8/27 (목) — DB 검증 · staging 기동

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **DB** | **8.3절 5가지 위험 항목 전수 검증.** 같은 조건으로 일정 생성해 SQLite 결과와 대조 | 시각·비용·방문지 일치 확인서 |
| **DB** | 인덱스 적용, `EXPLAIN ANALYZE` 로 근접 검색 확인 | 주요 쿼리 100ms 이하 |
| **INFRA** | **staging 환경 기동** | `https://staging.<도메인>` 에서 일정 생성 성공 |
| **BE** | 18:00까지 API 응답 타입 확정본 머지 | `web/src/types.ts` 갱신 |
| **BE** | 운영 환경변수·CORS·rate limit 값 조정 | staging에서 정상 동작 |
| **FE** | 지도 전체보기(`/trips/:id/map`) 구현 | 실기기 브라우저에서 동선 확인 |
| **전원** | **앱 지도 방식 결정** (WebView vs `react-native-maps`) | 결정 기록 |

#### 8/28 (금) — CI/CD · 화면 보강

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **INFRA** | GitHub Actions 배포 파이프라인 (main → staging 자동) | 커밋 후 5분 내 staging 반영 |
| **INFRA** | 18:00까지 staging 접속 정보·배포 방법 README 갱신 | 전원이 배포 상태를 확인 가능 |
| **INFRA** | 스토어 계정 진행 상황 점검 | 승인/대기 상태 보고 |
| **DB** | Redis 캐시 적용 (이동시간·날씨·이미지) | 캐시 히트 로그 확인 |
| **DB** | 백업 스크립트 + **복구 리허설 1회** | 빈 DB에 복원 성공 |
| **BE** | 로그 포맷 정리 (traceId 포함), 에러 응답 표준화 | 장애 재현 시 로그로 추적 가능 |
| **FE** | 위저드·결과 화면 반응형 점검, 접근성 (skip link·aria) | 모바일 폭 375px에서 가로 스크롤 없음 |

#### 8/29 (토) ~ 8/30 (일) — 완충

계획상 휴식일이다. **밀린 작업이 있으면 여기서 흡수하고, 없으면 쉰다.** 완충일을 처음부터 작업일로 잡으면 진짜 지연이 왔을 때 대응할 여지가 없어진다.

#### 8/31 (월) — 통합 검증 · 리허설

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **전원** | **전체 시나리오 리허설 3회.** 조건 입력 → 생성 → 편집 → 공유 → 초대 수락 | 3회 모두 끊김 없이 완주 |
| **전원** | 12장 품질 게이트 전 항목 통과 | 체크리스트 서명 |
| **INFRA** | production 환경 준비, 배포 롤백 절차 검증 | 롤백 1회 실습 |
| **BE·DB** | 부하 테스트 (`npm run load:test`) | 동시 10명 일정 생성 시 오류 없음 |
| **FE** | 실기기(안드로이드·아이폰 브라우저)에서 전 화면 확인 | 깨지는 화면 0건 |

#### 9/1 (화) — ★ M1 웹 MVP 배포

| 시각 | 할 일 |
| --- | --- |
| 10:00 | 최종 코드 프리즈. 이후 배포 차단 이슈 외 머지 금지 |
| 11:00 | production 배포 (DB 백업 → migrate deploy → 이미지 교체 → `/health`) |
| 13:00 | 전원 프로덕션에서 시나리오 확인 |
| 15:00 | 공개. 개인정보처리방침·이용약관 URL 활성화 확인 |
| 17:00 | **Phase 2 킥오프.** 앱 범위 최종 확정, 담당 배분 |

**M1 DoD 판정**: 3장의 M1 정의를 항목별로 확인해 서명한다. 하나라도 미달이면 그 항목을 M2로 넘길지, 9/1 배포를 미룰지 그 자리에서 결정한다.

### 10.3 Phase 2 — 앱 (9/2 수 ~ 9/9 수)

#### 9/2 (수) — Expo 초기화 · 첫 빌드 · **비공개 테스트 시작**

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **FE** | Expo 프로젝트 생성, `types.ts`·`api/client.ts` 이식, Expo Router 골격 | 시뮬레이터에서 `/` 화면 렌더 |
| **INFRA** | 12:00까지 `eas.json` 프로필 3종 + 스토어 계정 접근 공유 | FE가 `eas build` 실행 가능 |
| **INFRA** | **첫 EAS 프로덕션 빌드 → Play 내부 테스트 트랙 업로드 → 비공개 테스트 개설, 테스터 12명 초대** | **14일 카운트 시작** |
| **INFRA** | iOS 첫 빌드 → TestFlight 업로드 | 실기기 설치 확인 |
| **BE** | 앱에서 staging API 호출 가능하도록 CORS·네트워크 확인 | 실기기에서 API 응답 수신 |
| **DB** | 앱 트래픽 대비 쿼리 확인 | 느린 쿼리 목록 |

> **비공개 테스트의 앱은 완성품이 아니어도 된다.** 설치되고 크래시 없이 켜지면 된다. 이후 빌드를 계속 올려 갱신한다. **오늘 시작하지 않으면 9월 출시가 물 건너간다.**

#### 9/3 (목) — 앱 페이지 트리 · 핵심 화면

| 담당 | 할 일 |
| --- | --- |
| **FE** | `app/` 파일 트리 전체 생성(7.7절), 하단 탭 네비게이션, `/plan/*` 4단계 |
| **DB** | 쿼리 튜닝 마무리 → **이후 FE 지원으로 전환** |
| **BE** | 딥링크용 `assetlinks.json`·`apple-app-site-association` 내용 준비 |
| **INFRA** | 위 두 파일을 Nginx로 서빙 (HTTPS, JSON, 리다이렉트 없음) |

#### 9/4 (금) — 앱 일정·지도 화면

| 담당 | 할 일 |
| --- | --- |
| **FE** | `/generating`, `/trips/:id/overview`, `/trips/:id/schedule` |
| **FE** | 앱 지도 구현 (8/27 결정한 방식) |
| **BE·DB** | 앱 화면 지원, API 이슈 즉시 대응 |
| **INFRA** | 앱 아이콘·스플래시 자산 규격 정리, 스토어 스크린샷 규격 확인 |

#### 9/5 (토) — 앱 나머지 화면 · **전원 FE 투입**

| 담당 | 할 일 |
| --- | --- |
| **전원** | `/trips/:id/discover`, `/together`, `/prep`, `/s/:slug`, `/legal/*`, `/me/settings` 분담 구현 |
| **FE** | 온보딩·권한 요청 화면 |

**이 날이 8일 앱 일정의 성패를 가른다.** DB·INFRA의 주 업무가 끝났다는 전제 위에 계획이 서 있다. 두 사람의 업무가 안 끝났으면 14장의 범위 축소를 즉시 발동한다.

#### 9/6 (일) — 완충

#### 9/7 (월) — 실기기 테스트 · 심사 자산

| 담당 | 할 일 | 완료 기준 |
| --- | --- | --- |
| **전원** | Android·iOS 실기기에서 전 화면 확인 | 크래시 0건 |
| **FE** | 18:00까지 앱 아이콘·스플래시·스크린샷 원본 전달 | `assets/` 커밋 |
| **INFRA** | 스토어 등록 정보 작성 (설명·키워드·카테고리·연령등급·데이터 안전 양식) | 초안 완료 |
| **BE** | **심사용 데모 데이터 준비** (심사자가 즉시 일정을 볼 수 있는 시드) | 심사 메모에 절차 기재 |
| **FE·INFRA** | 딥링크 실기기 검증 (`/s/:slug` 가 앱으로 열리는지) | 양 OS 확인 |

#### 9/8 (화) — 버그 수정 · 스토어 등록

| 담당 | 할 일 |
| --- | --- |
| **전원** | 9/7 발견 버그 수정. **새 기능 추가 금지** |
| **INFRA** | Play Console·App Store Connect 등록 정보 입력 완료, 개인정보처리방침 URL 연결 |
| **전원** | 11.6절 리젝 방지 체크리스트 전 항목 점검 |

#### 9/9 (수) — ★ M2 앱 완료 · feature freeze

| 시각 | 할 일 |
| --- | --- |
| 10:00 | 최종 EAS 프로덕션 빌드 |
| 12:00 | Play 비공개 테스트 트랙에 최종 빌드 업로드 |
| 14:00 | TestFlight 최종 빌드 업로드, 내부 테스터 확인 |
| 16:00 | M2 DoD 판정 |
| 17:00 | Phase 3 준비 — 심사 제출 자료 최종 점검 |

### 10.4 Phase 3 — 스토어 심사 (9/10 ~)

| 날짜 | 할 일 | 담당 |
| --- | --- | --- |
| 9/10 (목) | **iOS App Store 심사 제출** | INFRA |
| 9/11~9/14 | iOS 심사 대응. 리젝 시 24시간 내 수정·재제출 | 전원 |
| 9/10~9/15 | Play 비공개 테스트 유지. 테스터 12명 참여 독려, 피드백 반영 빌드 업로드 | INFRA |
| **9/16 (수)** | **Play 프로덕션 신청** (14일 요건 충족) | INFRA |
| 9/16~9/23 | Play 신청 검토 대기 (최대 7일) | — |
| 9/23~9/30 | Play 정식 출시 | INFRA |

### 10.5 일일 운영 규칙

| 시각 | 활동 | 규칙 |
| --- | --- | --- |
| 10:00 | 데일리 15분 | ① 어제 한 것 ② 오늘 할 것 ③ **막힌 것**. 세 번째를 말하지 않는 것이 가장 큰 리스크다 |
| 상시 | PR 리뷰 | 15분 내 응답. 못 보면 "지금 못 본다"고 답한다 |
| 18:00 | 머지 마감 | 이후 머지는 다음 날 아침으로. 밤중 배포 사고 방지 |
| 18:00 | **데일리 데모 5분** | staging에서 실제로 동작시켜 보인다. 말이 아니라 화면으로 진척을 확인한다 |
| 매일 | 리스크 갱신 | 13장 리스크 등록부에 새 항목 추가·상태 갱신 |

---

## 11. 앱 배포 상세 (INFRA·FE)

### 11.1 Expo 프로젝트 설정

```
app.json / app.config.ts 핵심 항목
├── name / slug                          앱 표시 이름 · Expo 식별자
├── version                              사용자에게 보이는 버전 (1.0.0)
├── ios.bundleIdentifier                 예: com.<조직>.localroute  (변경 불가, 신중히)
├── ios.buildNumber                      제출마다 증가
├── android.package                      예: com.<조직>.localroute  (변경 불가)
├── android.versionCode                  제출마다 정수 증가
├── icon / splash                        1024x1024 아이콘, 스플래시
├── scheme                               localroute (개발용 딥링크)
├── ios.associatedDomains                applinks:<도메인>
├── android.intentFilters                App Links 설정
└── plugins                              expo-router, 위치·알림 등
```

**[위험] `bundleIdentifier` 와 `package` 는 스토어 등록 후 변경할 수 없다.** 9/2에 확정한다.

**권한 사용 목적 문구 (심사 필수)**

| 권한 | iOS 키 | 문구 예시 |
| --- | --- | --- |
| 위치 | `NSLocationWhenInUseUsageDescription` | "방문 인증과 현재 위치 주변 장소 추천에 사용합니다." |
| 사진 | `NSPhotoLibraryUsageDescription` | "여행 스토리에 올릴 사진을 선택하는 데 사용합니다." |
| 카메라 | `NSCameraUsageDescription` | "여행 스토리 사진을 촬영하는 데 사용합니다." |
| 알림 | (Android 13+ `POST_NOTIFICATIONS`) | "일정 시작 알림과 날씨 경고를 보내는 데 사용합니다." |

**요청하지 않는 권한은 선언하지 않는다.** 불필요한 권한 선언은 리젝 사유가 된다.

### 11.2 EAS Build / Submit

```jsonc
// eas.json (구조 예시)
{
  "build": {
    "development": { "developmentClient": true, "distribution": "internal" },
    "preview":     { "distribution": "internal" },
    "production":  { "autoIncrement": true }
  },
  "submit": {
    "production": {
      "android": { "track": "internal" },
      "ios": {}
    }
  }
}
```

```bash
eas login
eas build:configure
eas build --platform android --profile production
eas build --platform ios     --profile production
eas submit --platform android --latest    # Play Console 업로드
eas submit --platform ios     --latest    # App Store Connect 업로드
```

**[추가 확인 필요]**: EAS 무료 티어의 월 빌드 횟수. 9/2~9/9에 빌드를 여러 번 돌리므로 한도를 미리 확인한다. 한도 초과 시 유료 전환 또는 로컬 빌드(Android는 가능, iOS는 macOS 필요)로 전환해야 한다.

### 11.3 Android 배포 절차

| 단계 | 내용 | 담당 | 시점 |
| --- | --- | --- | --- |
| 1 | Google Play Console 개발자 계정 생성 (등록비 결제, 본인 확인) | INFRA | **8/25** |
| 2 | 앱 생성, 패키지명 확정 | INFRA | 9/2 |
| 3 | 서명 키 생성 (EAS 관리 권장) + **별도 안전 보관** | INFRA | 9/2 |
| 4 | 내부 테스트 트랙에 AAB 업로드 | INFRA | 9/2 |
| 5 | **비공개 테스트 개설, 테스터 12명 초대 → 14일 연속** | INFRA | **9/2 시작** |
| 6 | 스토어 등록 정보: 설명, 스크린샷(폰·태블릿), 그래픽 이미지, 카테고리, 연령등급 설문 | INFRA | 9/7~9/8 |
| 7 | **데이터 안전(Data safety) 양식** — 수집 항목·목적·공유 여부·삭제 요청 경로 | INFRA | 9/8 |
| 8 | 개인정보처리방침 URL 등록 | INFRA | 9/8 |
| 9 | 프로덕션 신청 (3단계 양식) | INFRA | **9/16** |
| 10 | 검토 대기 (최대 7일) → 출시 | INFRA | 9/23~ |

**[위험] 서명 키 분실 = 앱 업데이트 영구 불가.** EAS 관리 키를 쓰더라도 `eas credentials` 로 백업을 내려받아 팀이 접근 가능한 안전한 곳에 이중 보관한다.

**데이터 안전 양식에 정직하게 기재해야 할 항목** (이 서비스 기준)

| 수집 항목 | 목적 | 필수 여부 |
| --- | --- | --- |
| 대략적 위치 | 주변 장소 추천 | 선택 |
| 정확한 위치 | GPS 방문 인증 | 선택 |
| 사진 | 여행 스토리 | 선택 |
| 사용자 생성 콘텐츠 | 스토리·리뷰 | 선택 |
| 기기 식별자 | 익명 세션 | 필수 |
| 앱 상호작용 이벤트 | 서비스 개선 | 필수 |

### 11.4 iOS 배포 절차

| 단계 | 내용 | 담당 | 시점 |
| --- | --- | --- | --- |
| 1 | Apple Developer Program 가입 (99 USD/년, 개인) | INFRA | **8/25** |
| 2 | App Store Connect에 앱 레코드 생성, Bundle ID 확정 | INFRA | 9/2 |
| 3 | 인증서·프로비저닝 (EAS 자동 관리) | INFRA | 9/2 |
| 4 | TestFlight 빌드 업로드, 내부 테스터 등록 | INFRA | 9/2 |
| 5 | 스토어 등록 정보: 이름, 부제, 설명, 키워드, 스크린샷(6.7"·6.5"·5.5" 등 필수 크기), 프로모션 텍스트 | INFRA | 9/7~9/8 |
| 6 | **App Privacy(개인정보 수집 유형) 응답** | INFRA | 9/8 |
| 7 | 연령 등급 설문 (UGC 포함 → 해당 항목 정직 기재) | INFRA | 9/8 |
| 8 | **심사 메모 작성 + 데모 계정·데이터 안내** | INFRA·BE | 9/8 |
| 9 | 심사 제출 | INFRA | **9/10** |
| 10 | 승인 → 출시 | INFRA | 9/11~9/14 |

**심사 메모에 반드시 쓸 내용 [설계 제안]**

```
- 이 앱은 회원가입 없이 사용할 수 있습니다. 별도 로그인 계정이 필요 없습니다.
- 기능 확인 절차:
  1) 앱 실행 → "여행 일정 만들기"
  2) 출발지 "부산역", 기간 2박 3일, 인원 2명, 예산 30만원 입력
  3) 취향 선택 후 "조건 기반 일정 계산"
  4) 약 10초 내 일정이 생성됩니다
- 위치 권한은 방문 인증 기능에만 사용하며, 거부해도 일정 생성은 정상 동작합니다.
- 사용자 생성 콘텐츠(스토리)는 신고 기능이 있으며 24시간 내 검토합니다.
- 앱 내 "광고" 라벨이 붙은 항목은 유료 노출이며, 추천 일정의 순위에는 개입하지 않습니다.
```

### 11.5 스토어 등록 자산 체크리스트

| 자산 | Android | iOS | 담당 |
| --- | --- | --- | :-: |
| 앱 아이콘 | 512×512 PNG | 1024×1024 PNG (알파 없음) | FE |
| 스크린샷 | 폰 최소 2장 (16:9 또는 9:16) | 6.7" · 6.5" 필수 | FE |
| 그래픽 이미지 | 1024×500 | — | FE |
| 짧은 설명 | 80자 | 부제 30자 | INFRA |
| 전체 설명 | 4000자 | 4000자 | INFRA |
| 키워드 | — | 100자 | INFRA |
| 개인정보처리방침 URL | 필수 | 필수 | INFRA |
| 지원 URL | — | 필수 | INFRA |
| 카테고리 | 여행 및 지역정보 | Travel | INFRA |
| 연령 등급 | 설문 | 설문 | INFRA |
| 데이터 수집 양식 | Data safety | App Privacy | INFRA |

### 11.6 리젝 방지 체크리스트

> 2.3절의 상위 리젝 사유에 대응한다. **제출 전 전 항목을 실기기에서 직접 확인한다.**

- [ ] **크래시 없음** — 전 화면을 실기기에서 1회씩 진입. 네트워크를 끈 상태에서도 크래시하지 않고 안내를 표시
- [ ] **개인정보처리방침** — 앱 내 링크(`/legal/privacy`)와 스토어 등록란 양쪽에 공개 URL 연결
- [ ] **데모 계정 불필요 명시** — 로그인이 없다는 점을 심사 메모에 명확히 기재
- [ ] **스크린샷 정확성** — 실제 앱 화면과 일치. 존재하지 않는 기능을 보여주지 않음
- [ ] **인앱 결제 없음** — 결제 기능이 없다는 점을 명시. 예약 제휴는 외부 브라우저로 이동하는 링크임을 설명
- [ ] **접근성** — 주요 버튼에 레이블, 대비 확보
- [ ] **권한 목적 고지** — 요청하는 모든 권한에 목적 문구. 안 쓰는 권한은 선언 삭제
- [ ] **UGC 안전장치** — 스토리 신고 버튼, 차단, 24시간 내 검토 정책이 앱 내에 존재하고 약관에 기재
- [ ] **계정·데이터 삭제 경로** — `/me/settings` 에서 데이터 삭제 요청 가능
- [ ] **빈 상태 처리** — 데이터가 없을 때 흰 화면이 아니라 안내가 뜬다
- [ ] **외부 링크** — 앱 내 브라우저 또는 시스템 브라우저로 정상 이동
- [ ] **광고 표기** — `광고` 라벨과 "자연 추천과 분리된 유료 노출" 고지가 화면에 보인다

---

## 12. 테스트와 품질 게이트

### 12.1 자동 검증 (CI에서 매 PR마다)

```bash
npm ci
npm test --workspace server          # node:test + supertest 통합 테스트
npm run build --workspace server     # 서버 타입·빌드
npx tsc -b web                       # 웹 타입 검사
npm run build --workspace web        # 웹 프로덕션 빌드
npm audit --audit-level=high         # 취약점
```

**하나라도 실패하면 머지하지 않는다.** 예외 없다.

### 12.2 마일스톤 게이트

#### M1 (9/1) 게이트 — 전 항목 통과해야 배포

| # | 항목 | 확인 방법 |
| --- | --- | --- |
| 1 | 공개 도메인 HTTPS 접속 | 외부 네트워크에서 브라우저 접속 |
| 2 | PostgreSQL 운영 DB 사용 | `/health` 응답에 DB 상태 |
| 3 | 조건 입력 → 생성 → 결과 확인 완주 | 3회 연속 성공 |
| 4 | 일정 시각·비용이 SQLite 시절과 동일 | 동일 조건 결과 대조표 |
| 5 | 부분 재최적화(고정·제외·교체) 동작 | 각 1회 |
| 6 | 되돌리기 동작 | 1회 |
| 7 | 공유 링크 생성·열람 | 다른 브라우저에서 확인 |
| 8 | 초대 링크 수락 | 다른 브라우저에서 확인 |
| 9 | 뒤로가기·새로고침·딥링크 정상 | 모든 라우트 |
| 10 | 구버전 `?trip=` 링크 리다이렉트 | 1회 |
| 11 | 404 화면 | 임의 경로 |
| 12 | 백엔드 정지 시 안내 배너 표시 | 서버 중단 후 확인 |
| 13 | 모바일 폭 375px 가로 스크롤 없음 | 실기기 |
| 14 | 광고 라벨·분리 고지 노출 | `/trips/:id/prep` |
| 15 | 개인정보처리방침·이용약관 URL 활성 | 공개 접속 |
| 16 | 동시 10명 일정 생성 시 오류 없음 | `npm run load:test` |
| 17 | DB 백업·복구 리허설 완료 | 실습 기록 |
| 18 | `npm audit --audit-level=high` 통과 | CI |

#### M2 (9/9) 게이트

| # | 항목 |
| --- | --- |
| 1 | Android 실기기에서 앱 실행, 크래시 0건 |
| 2 | iOS 실기기에서 앱 실행, 크래시 0건 |
| 3 | 앱에서 조건 입력 → 생성 → 결과 확인 완주 |
| 4 | 앱 지도에 동선 표시 |
| 5 | 딥링크 `/s/:slug` 가 앱으로 열림 (양 OS) |
| 6 | 네트워크 차단 상태에서 안내 표시 (크래시 없음) |
| 7 | 권한 거부 시에도 핵심 기능 동작 |
| 8 | EAS 프로덕션 빌드 산출 |
| 9 | Play 비공개 트랙·TestFlight 업로드 완료 |
| 10 | 11.6절 리젝 방지 체크리스트 전 항목 통과 |

### 12.3 수동 시나리오 테스트 (매일 18:00 데모에서 수행)

| 시나리오 | 검증 항목 |
| --- | --- |
| S1 외국인 내비게이션 | 대중교통 포함 일정 생성 → 환승 안내가 실제 카카오모빌리티 응답과 일치, 도보 경로 없는 구간은 택시 대안 제시 |
| S2 외국인 | 언어를 EN으로 → 영어 UI, 영어 메뉴·해외카드 조건 반영 |
| S3 예산 초과 | 예산을 낮게 → 예산 초과 경고가 표시되고 임의로 늘어나지 않음 |
| S4 동행 편집 | 편집자 초대 → 동시 편집 시 409 충돌 처리 |
| S5 열람자 권한 | 열람자로 접속 → 편집 버튼 비활성, 서버가 403 |
| S6 공유 개인정보 | 공유 링크에 출발지·연락처·정확 위치가 **없음** |
| S7 광고 정책 | 음식점·카페·기념품샵 캠페인 등록 시도 → API가 거부 |
| S8 스토리 안전 | 사진 업로드 시 EXIF 제거, 위치는 지역 단위, 신고 → 검토 큐 이동 |
| S9 데이터 정직성 | 추정값에 "추정" 표기, 경로 API 오류·타임아웃 시 임의로 경로를 추정하지 않고 "경로 없음"으로 명시 |

**S6·S7·S8은 심사와 신뢰 정책의 핵심이므로 매일 확인한다.**

---

## 13. 리스크 등록부

| ID | 리스크 | 영향 | 가능성 | 대응 | 담당 | 트리거 |
| --- | --- | :-: | :-: | --- | :-: | --- |
| R-01 | **Play 12명·14일 요건으로 9월 출시 실패** | 치명 | 높음 | 9/2에 첫 빌드로 비공개 테스트 즉시 시작. 테스터 12명을 8/28까지 확보 | INFRA | 9/2에 테스트 미개설 |
| R-02 | **스토어 계정 개설·승인 지연** | 치명 | 중간 | 8/25 즉시 착수. 개인 가입으로 진행. 3일 내 승인 안 나면 지원팀 문의 | INFRA | 8/28까지 미승인 |
| R-03 | **8일 안에 RN 앱 완성 실패** | 치명 | **매우 높음** | 14장 범위 축소안 발동. 앱 v1을 6화면으로 제한 | FE | 9/5 저녁 진척률 <50% |
| R-04 | PostgreSQL 전환 시 일정 계산 결과 변형 | 높음 | 중간 | 8/27 하루를 검증 전용으로 배정. 결과 대조표 작성 | DB | 8/27 대조 불일치 |
| R-05 | 외부 API 쿼터 초과로 시연 중 지도·경로 실패 | 높음 | 중간 | 8/26 쿼터 확인. Redis 캐시로 호출 절감. 폴백 경로 유지 | BE·DB | 8/26 확인 결과 |
| R-06 | 카카오 JS 키 도용 (도메인 제한 누락) | 중간 | 중간 | 카카오 콘솔에서 도메인 제한 설정 | INFRA | M1 게이트 |
| R-07 | 앱 서명 키 분실 | 치명 | 낮음 | EAS 관리 + 별도 이중 백업 | INFRA | 9/2 |
| R-08 | iOS 심사 리젝 | 높음 | 중간 | 11.6절 체크리스트 사전 통과. 리젝 시 24시간 내 재제출 | INFRA | 9/11~ |
| R-09 | 팀원 이탈·부재 (질병·개인사정) | 높음 | 중간 | 모든 영역에 백업 담당 지정(4.3~4.6). 작업은 반드시 PR로 남긴다 | 전원 | 상시 |
| R-10 | 완충일(8/29·30, 9/6)까지 소진 | 높음 | 높음 | 완충일이 사라지면 그 시점에 범위를 줄인다. 야근으로 메우지 않는다 | 전원 | 완충일 사용 시 |
| R-11 | staging 부재로 앱 개발 지연 | 높음 | 낮음 | 8/27까지 staging 필수 기동. 미완 시 임시 터널링으로라도 공개 엔드포인트 확보 | INFRA | 8/27 |
| R-12 | 개인 실명이 App Store 판매자로 공개되는 것에 대한 팀 이견 | 중간 | 중간 | 8/25 킥오프에서 합의. 곤란하면 일정을 늦추고 조직 가입 | 전원 | 8/25 |
| R-13 | 심사에서 UGC 안전장치 미비 지적 | 높음 | 중간 | 신고·차단·24시간 검토를 M3 필수로 고정 | FE·BE | 9/8 |
| R-14 | 데이터 정직성 원칙 훼손 (추정값을 확정처럼 표시) | 중간 | 낮음 | S9 시나리오를 매일 확인 | 전원 | 매일 |

**리스크 등록부는 매일 10:00 데일리에서 갱신한다.** 새 리스크가 없다는 보고도 보고다.

---

## 14. 일정 현실성 평가와 범위 조정 권고

이 장은 팀이 듣기 좋은 말 대신 **실제로 일어날 일**을 적는다.

### 14.1 웹 MVP (7일) — 달성 가능

| 근거 | 내용 |
| --- | --- |
| 서버 | 57개 엔드포인트, 3,794줄이 이미 동작한다 |
| 화면 | 핵심 흐름(입력→생성→결과→공유)이 이미 구현되어 있다 |
| 라우팅 | 페이지 트리 라우팅이 8/25에 이미 완료되었다 |
| 남은 일 | PostgreSQL 전환(1.5일) + 배포(1.5일) + 화면 보강(1.5일) + 검증(1일) |

**판정: 달성 가능.** 단, DB 전환과 배포가 병렬로 진행되어야 하고, 8/27에 staging이 반드시 떠야 한다.

### 14.2 앱 (8일, React Native 신규 구현) — **현재 범위로는 달성 불가**

솔직하게 계산해 보자.

| 항목 | 수치 |
| --- | --- |
| 전체 화면 | 41개 |
| 앱에 필요한 화면 (`platform: BOTH` 또는 `APP`) | **37개** |
| 가용 일수 | 8일 (9/2~9/9), 그중 1일은 완충, 1일은 실기기 테스트, 1일은 버그 수정 → **실제 구현 5일** |
| 인원 | FE 1명 + 9/5부터 3명 합류 |
| 필요 속도 | 37화면 ÷ 5일 = **하루 7.4화면** |

여기에 더해 5일 안에 다음을 처음부터 해야 한다: Expo 프로젝트 구성, 네비게이션 구조, 앱 지도 연동, 딥링크, 권한 처리, 아이콘·스플래시, 빌드·서명·스토어 업로드.

**판정: 현재 범위로는 불가능하다.** 무리하게 밀어붙이면 크래시가 남은 앱이 나오고, 그것은 2.3절의 리젝 1위 사유에 정면으로 걸린다. **리젝 1회로 1~2주가 날아가므로, 급하게 만든 앱은 오히려 출시를 늦춘다.**

### 14.3 범위 조정 3안 [설계 제안]

**8/25 킥오프에서 하나를 선택한다.**

#### 안 1 — 앱 v1을 6화면으로 제한 (권장)

앱에 다음 6개만 넣는다.

```
/                          시작
/plan/*                    조건 입력 (4단계를 1화면 스크롤로 압축)
/generating/:tripId        생성 진행
/trips/:tripId/overview    여행 요약
/trips/:tripId/schedule    일정 · 지도
/legal/*                   약관·정책 (심사 필수)
+ /me/settings             설정·데이터 삭제 (심사 필수)
```

나머지(로컬·함께·여행 준비·장소 상세·스토리·프로필·공동 편집)는 **앱 안에서 웹뷰로 열거나** v1.1로 미룬다.

| 장점 | 단점 |
| --- | --- |
| 8일 안에 **크래시 없는 앱**을 만들 수 있다 | 앱 기능이 웹보다 적다 |
| 심사 통과 확률이 가장 높다 | |
| v1.1(9월 말)에 화면을 추가하면 된다 | |

> 앱은 한 번에 완성하는 것이 아니라 **출시한 뒤 업데이트하는 것**이다. 첫 심사만 통과하면 이후 업데이트 심사는 훨씬 빠르다(2.3절).

#### 안 2 — 앱 완료일을 9/16으로 연기

| 장점 | 단점 |
| --- | --- |
| 전체 화면을 앱에 담을 수 있다 | Play 비공개 테스트 14일이 9/16부터 시작되면 **출시가 10월로 밀린다** |
| | (완화: 9/2에 껍데기 빌드로 테스트를 먼저 시작하면 이 단점은 사라진다) |

**안 2를 택하더라도 9/2 비공개 테스트 시작은 그대로 유지해야 한다.**

#### 안 3 — 앱을 WebView 하이브리드로 전환

| 장점 | 단점 |
| --- | --- |
| 웹 화면 41개가 즉시 앱이 된다 | "웹사이트를 감싸기만 한 앱"은 App Store 심사에서 지적받을 수 있다 |
| 8일 안에 확실히 완성된다 | 네이티브 느낌이 떨어진다 |
| | 완화: 온보딩·권한·푸시·오프라인 처리를 네이티브로 구현하면 통과 가능성이 올라간다 |

**권고: 안 1.** 네이티브로 만드는 화면을 최소로 줄이되, 그 6개는 제대로 만든다. 나머지는 v1.1에서 채운다.

### 14.4 팀에 드리는 솔직한 조언

1. **완충일을 지켜라.** 8/29·30과 9/6은 계획상 쉬는 날이다. 이 3일을 작업일로 쓰기 시작하면, 진짜 지연이 왔을 때 대응할 수단이 없어진다. 15일짜리 일정에서 예상치 못한 일은 반드시 생긴다.

2. **9/2 비공개 테스트가 가장 중요한 하루다.** 이날 Play 비공개 테스트를 시작하지 못하면 9월 출시가 사라진다. 다른 모든 작업보다 우선한다.

3. **"거의 다 됐다"는 보고를 믿지 마라.** 매일 18:00 데모에서 실제로 동작하는 화면만 진척으로 센다. 코드가 있는 것과 동작하는 것은 다르다.

4. **범위를 줄이는 것은 실패가 아니다.** 크래시 나는 앱으로 리젝당해 2주를 잃는 것이 실패다. 6화면짜리 앱이 스토어에 올라가는 것이 성공이다.

5. **DB·INFRA 담당은 9/5부터 프론트엔드에 합류할 준비를 미리 하라.** 그날 처음 React Native를 열면 늦다. 9/3~9/4 저녁에 30분씩이라도 Expo 문서를 봐 둔다.

---

## 15. 부록

### 15.1 명령어 모음

```bash
# 개발 (서버+웹 동시 실행)
npm run dev

# 서버만 / 웹만
npm run dev:server
npm run dev:web

# DB
npx prisma migrate dev --name <이름>    # 개발용. 운영 금지
npx prisma migrate deploy               # 운영 배포용
npx prisma studio                       # 데이터 확인
npm run seed --workspace server         # 기본 시드
npm run seed:translations --workspace server
npm run seed:commerce --workspace server
npm run seed:v2 --workspace server
npm run import:tourapi --workspace server

# 검증
npm test --workspace server
npm run build --workspace server
npm run build --workspace web
npx tsc -b web
npm audit --audit-level=high
npm run load:test --workspace server

# 페이지 트리
node web/scripts/print-route-tree.mjs tree     # 트리 형태
node web/scripts/print-route-tree.mjs table    # 마크다운 표 (기획서 갱신용)
node web/scripts/print-route-tree.mjs stats    # 집계

# 배치
npm run privacy:cleanup --workspace server     # 매일 실행
npm run events:dispatch --workspace server

# 컨테이너
docker compose up --build                      # 웹 :8080, API :4000
docker compose -f docker-compose.yml -f docker-compose.streaming.yml up --build

# 앱
eas build --platform android --profile production
eas build --platform ios --profile production
eas submit --platform android --latest
eas submit --platform ios --latest
```

### 15.2 새 화면을 추가하는 절차

```
1. web/src/routes/routeTree.ts 에 노드 추가
   (path, expoPath, titleKo, purpose, platform, access, apis, owners, milestone, status)

2. web/src/routes/paths.ts 에 경로 빌더 추가

3. 페이지 파일 생성
   구현 전이면:  export function XPage() { return <RoutePlaceholder routeId="x" />; }

4. web/src/routes/AppRouter.tsx 에 <Route> 추가

5. 구현 완료 시 routeTree.ts 의 status 를 DONE 으로 변경

6. 기획서 7장 표 갱신
   node web/scripts/print-route-tree.mjs table
```

### 15.3 용어

| 용어 | 의미 |
| --- | --- |
| 페이지 트리 | 서비스의 모든 화면을 계층 구조로 정의한 것. `routeTree.ts` 가 정의 |
| 부분 재최적화 | 일정 전체가 아니라 **해당 날짜만** 다시 계산하는 것. 고정 장소와 다른 날짜는 유지 |
| 로컬 점수 | 현지인이 반복 방문했는지를 나타내는 지표. 광고비는 이 점수에 개입하지 않는다 |
| 자연 추천 | 광고와 무관하게 추천 엔진이 선정한 결과 |
| 익명 세션 | 회원가입 없이 기기 단위로 발급되는 해시 기반 토큰 |
| 지연 공개 | 스토리를 여행 종료 후에 공개하는 기본 설정. 실시간 위치 노출을 막는다 |
| DoD | Definition of Done. 마일스톤을 날짜가 아니라 조건으로 판정하는 기준 |
| EAS | Expo Application Services. macOS 없이 iOS 빌드를 만들어 주는 빌드 서비스 |
| AAB | Android App Bundle. Play Store 업로드 형식 |
| 비공개 테스트 | Google Play의 Closed testing. 신규 개인 계정은 12명·14일이 의무 |

### 15.4 이 문서를 갱신하는 규칙

| 변경 | 갱신할 곳 |
| --- | --- |
| 화면 추가·삭제 | `routeTree.ts` → 7.3·7.4절 재생성 |
| API 추가·변경 | 7.5절 + `web/src/types.ts` |
| 기술 스택 변경 | 5.1절 + 변경 사유 기록 |
| 일정 변경 | 10장 + 3장 마일스톤 DoD |
| 리스크 발생·해소 | 13장 (매일) |
| 범위 축소 결정 | 14.3절에 선택안과 결정일 기록 |

---

## 결론

이 문서는 **4명이 15일 안에 웹과 앱을 만들고 스토어에 올리기 위한 실행 계획**이다. 세 가지만 기억하면 된다.

1. **백엔드는 이미 있다.** 57개 엔드포인트와 26개 데이터 모델이 동작한다. 남은 일은 화면·인프라·앱이다.
2. **페이지 트리가 작업 분배의 기준이다.** 41개 화면 각각에 경로·담당·API·마일스톤이 붙어 있다. `routeTree.ts` 를 열면 누가 무엇을 언제까지 만들어야 하는지가 전부 나온다.
3. **9/2에 Play 비공개 테스트를 시작하지 못하면 9월 출시는 없다.** 이것이 이 일정에서 가장 중요한 단 하나의 날짜다.

일정이 빡빡하다. 그래서 범위를 줄일 준비를 미리 해 두는 것이 계획의 일부다. **줄이는 것은 실패가 아니라 출시하는 방법이다.**

---

*LOCAL ROUTE 실행 기획서 v4.0 · 2026-08-25*
