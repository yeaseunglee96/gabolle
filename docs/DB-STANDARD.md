# 데이터베이스 기준 — PostgreSQL 16

S15P21E201-554 의 결정 기록이다. **무엇을 쓰기로 했는지와 왜 그렇게 됐는지**를 한 곳에 둔다.

이 문서가 있는 이유는 하나다 — 같은 데이터를 서로 다르게 적은 문서가 셋 있었고, 그 때문에
사람마다 다른 DB 를 전제하고 일했다. 다시 갈라지지 않게 여기로 모은다.

---

## 1. 결정 — 운영 DB 는 PostgreSQL 16 이다

| | |
|---|---|
| 운영·개발·테스트 | **PostgreSQL 16** |
| 접근 방식 | Spring Data JPA + Flyway |
| 백엔드 전용 schema | **`gabolle`** (2절) |
| MySQL · SQLite | **쓰지 않는다** |

실측 근거다. 문서가 아니라 저장소와 서버에서 읽은 것이다.

```
backend/build.gradle              org.postgresql:postgresql
                                  flyway-database-postgresql
                                  🔴 MySQL 드라이버가 없다 — 붙일 수도 없다
운영 서버 컨테이너                  postgres:16.4
v1.1 확정 문서 4종                 PostgreSQL 명시
```

> **schema**(스키마) — 한 데이터베이스 안에서 표를 묶는 폴더 같은 것. 같은 이름의 표를
> 서로 다른 schema 에 따로 둘 수 있다. PostgreSQL 은 기본으로 `public` 을 쓴다.

---

## 2. 🔴 백엔드 표는 `gabolle` schema 에 있다 — `public` 이 아니다

운영 `app_db` 는 schema 를 둘로 나눠 쓴다 (S15P21E201-583).

| schema | 누가 쓰나 |
|---|---|
| `gabolle` | **백엔드가 만드는 표 전부.** Flyway 가 여기에 적용한다 |
| `public` | 개인화 파이프라인(Airflow · MLflow)이 쓴다 |

설정은 이렇게 걸려 있다.

```properties
spring.flyway.schemas=${GABOLLE_DB_SCHEMA:gabolle}
spring.flyway.default-schema=${GABOLLE_DB_SCHEMA:gabolle}
spring.flyway.create-schemas=false
spring.jpa.properties.hibernate.default_schema=${GABOLLE_DB_SCHEMA:gabolle}
```

🔴 **`create-schemas=false` 다.** schema 가 미리 없으면 앱이 기동하지 못한다. 운영에서는
DB 관리자가 먼저 만들고 권한을 준다. 로컬은
`backend/docker/postgres/init/01-create-gabolle-schema.sql` 이 처리하지만, **그 파일은 새
볼륨을 만들 때만 돌고 기존 DB 에는 재실행되지 않는다.**

### 🔴 여기서 실제로 한 번 깨졌다 — raw SQL 은 이 설정을 물려받지 않는다

위 설정이 걸리는 곳은 **Flyway 와 Hibernate 뿐**이다. `JdbcTemplate` 으로 던지는
**수식 없는 SQL** 은 그것을 물려받지 않고 연결의 `search_path`(기본 `"$user", public`)를
쓴다. 그래서 표를 못 찾는다.

**그리고 테스트로는 안 잡힌다.** 테스트 데이터소스에는 `default_schema` 가 없어서 표가
전부 `public` 에 생기고 초록이 뜬다. **초록인데 운영에서 깨지는 조합**이다 —
S15P21E201-546 품질 게이트가 그렇게 머지됐고 나중에 실측으로 찾았다 (MR !115).

앞으로 raw SQL 을 쓸 때 셋 중 하나를 해야 한다.

1. **데이터소스 수준에서 맞춘다** ← 올바른 방법. 한 줄이면 전부 해결된다
   ```properties
   spring.datasource.hikari.schema=${GABOLLE_DB_SCHEMA:gabolle}
   ```
2. 트랜잭션 안에서 `SET LOCAL search_path TO "<schema>", public` — 1번이 들어오기 전의
   임시 조치. 🔴 `SET LOCAL` 이어야 한다. 그냥 `SET` 은 **연결 풀에 남아** 그 연결을 다음에
   빌려 쓰는 남의 코드까지 바꿔 놓는다
3. 표 이름을 `gabolle.` 로 수식한다 — 🔴 권하지 않는다. schema 이름을 바꾸는 날 모든
   파일을 다시 뒤져야 하고 `GABOLLE_DB_SCHEMA` 변수가 무의미해진다

현재 백엔드에서 raw SQL 을 쓰는 파일은 `dataquality/EventQualityGate.java` **하나뿐**이다.
나머지는 전부 JPA 라서 이 함정에 걸리지 않는다.

---

## 3. 타입 기준

| 쓰는 것 | 쓰지 않는 것 | 이유 |
|---|---|---|
| `UUID` | `bigint auto_increment` | 분산 생성. 순서가 필요한 자리는 정수 `version` 을 **함께** 둔다 |
| `TIMESTAMPTZ` | `timestamp` (시간대 없음) | 시간대가 없으면 일정이 9시간 밀린다 |
| `JSONB` | `json` · 문자열 | 안쪽 값으로 검색·색인(GIN)이 된다 |
| `VARCHAR(n)[]` | 콤마로 이어 붙인 문자열 | 배열 안의 값까지 `CHECK` 로 검사할 수 있다 |
| `BOOLEAN` | `TINYINT(1)` · 0/1 | PostgreSQL 에는 진짜 참/거짓 타입이 있다 |
| `BYTEA` | base64 문자열 | 암호문을 바이트로 둔다 |
| `CHECK` 제약 | 응용 계층 검사만 | 배치·손 SQL 이 옆으로 걸어 들어오는 것을 막는다 |
| 부분 색인 (`WHERE` 붙은 UNIQUE) | — | 🔴 MySQL 에 없던 기능. NULL 이 섞인 유일성을 이것으로만 막을 수 있다 |

### 길이 제한을 신경 쓰지 않아도 된다

`varchar(191)` 은 MySQL 의 색인 키 길이 제한 때문에 생긴 숫자다. PostgreSQL 에는 그 제한이
없으므로 **그 숫자를 따라 쓰지 않는다.** 옛 ERD 에 `varchar(191)` 이 보이면 MySQL 시절의
흔적이다.

### 글자 집합도 신경 쓰지 않아도 된다

`utf8mb4` 는 MySQL 의 `utf8` 이 진짜 UTF-8 이 아니라서 필요했던 것이다. PostgreSQL 은
데이터베이스 인코딩이 UTF-8 이면 이모지와 한글이 그대로 들어간다.

### 🔴 검증 현황 — 다 된 것이 아니다

| 타입 | 엔티티 매핑 검증 | 비고 |
|---|---|---|
| `UUID` · `TIMESTAMPTZ` · `JSONB` · `VARCHAR[]` · `CHECK` | ✅ | `recommendation_job` · `recommendation_candidate` · `event_outbox` · auth 표에서 실제 PostgreSQL 로 검증 |
| **`BYTEA`** | ❌ **미검증** | `constraint_answer.other_allergy_ciphertext` 에만 있고 **그 표에는 엔티티가 없다**. S15P21E201-461 이 매핑을 만든 뒤에 검증한다 |

취향·제약 스냅샷 표에 엔티티를 일부러 만들지 않았다 — 여행 저장은 S15P21E201-461 자리이고
표에 주인이 둘이면 안 된다. `ddl-auto=validate` 는 엔티티 없는 표를 문제 삼지 않는다.

---

## 4. 좌표 — M1 은 `DOUBLE PRECISION` 이다. PostGIS 를 쓰지 않는다

```sql
origin_lat  DOUBLE PRECISION
origin_lng  DOUBLE PRECISION
```

> **PostGIS** — PostgreSQL 에 지리 계산(거리·포함·교차)을 더해 주는 확장. 좌표를
> `geography` 라는 한 칸에 담고 "5km 안" 같은 질의를 색인으로 빨리 한다.

**M1 에서 안 쓰는 이유.** 지금 좌표로 하는 일은 저장과 출발지 표시뿐이다. 근접 검색은
아직 없고, 확장을 켜면 운영 DB 에 설치·권한·백업 절차가 늘어난다. 필요해지지 않은 비용이다.

**켜야 할 때의 신호.** "반경 안의 장소 찾기" 를 SQL 로 해야 할 때다. 그때
`origin_point geography(Point, 4326)` 으로 바꾸고 두 칸을 채운 데이터를 옮긴다.
마이그레이션 하나로 되도록 두 칸을 나눠 둔 것이다.

🔴 반쪽 좌표는 저장되지 않는다 — `CHECK ((origin_lat IS NULL) = (origin_lng IS NULL))`.
위도만 있는 좌표는 오류가 아니라 **틀린 답**을 만든다.

---

## 5. 마이그레이션 경로는 하나다

```
개발 · 테스트 · 스테이징 · 운영  →  Flyway  →  backend/src/main/resources/db/migration
```

| | |
|---|---|
| 버전 표기 | **UTC 시각** (`V20260903140000__…`). 순번(`V1`·`V2`)은 쓰지 않는다 |
| 왜 | 여러 티켓이 동시에 올릴 때 순번은 **반드시** 충돌하지만 시각은 안 겹친다 |
| 적용 schema | `gabolle` (2절) |
| 로컬 | `docker compose up` — Flyway 가 기동 시 적용 |
| 테스트 | `GABOLLE_TEST_DB_URL` 로 버려도 되는 DB 를 준다. 이름에 `test` 가 없으면 `TestDatabase` 가 거부한다 (운영 URL 붙여넣기 사고 방지) |

### 🔴 순서가 낮은 마이그레이션을 뒤늦게 올리지 않는다

Flyway 는 **이미 적용된 것보다 낮은 번호가 나중에 오면 거부한다.** 새 파일을 만들 때
`db/migration` 에 있는 가장 높은 번호보다 위로 잡는다. 열린 브랜치에 있는 것까지 봐야 한다 —
남이 먼저 머지하면 내 번호가 낮아질 수 있다.

### 🔴 테스트를 돌릴 때 두 가지

1. **`cleanTest` 를 붙인다.** `./gradlew test` 만 하면 환경변수가 Gradle 의 up-to-date
   판정에 안 들어가서 `Task :test UP-TO-DATE` 로 건너뛰고도 `BUILD SUCCESSFUL` 이 난다.
   **아무것도 안 돌았는데 초록이다**
2. **이 테스트는 대상 DB 의 객체를 전부 지운다** (`flyway.clean-disabled=false`).
   같은 서버에 `airflow_db` · `app_db` · `mlflow_db` 가 살아 있다

### 🔴 CI 초록은 DB 를 검증하지 않는다 (2026-09-03 현재)

`backend:build` 잡(S15P21E201-266)은 `./gradlew build` 를 돌리지만 `services:` 도
`GABOLLE_TEST_DB_URL` 도 없다. 그래서 **DB 테스트가 전부 건너뜀으로 표시되고 잡은 초록이
된다.** 실측한 차이다.

| | 총 | 건너뜀 | 실제로 돈 것 |
|---|---|---|---|
| DB 없이 (= 지금 CI) | 137 | **51** | 86 |
| 진짜 PostgreSQL 로 | 137 | 0 | **137** |

DB 를 건드리는 변경은 **손으로 한 번 돌려 보고** 숫자를 MR 에 적는다. CI 가 볼 수 있게
만드는 것은 별건이다.

---

## 6. S15P21E201-262 — MySQL 전제 티켓. **종료 권고**

> S15P21E201-262 `[Chore][Back] 데이터 모델 26개를 MySQL 표와 JPA 엔티티로 옮긴다`
> 해야 할 일 · 담당자 없음 · Low · 스프린트 없음 · 보고자 장효준

554 작업 내용이 요구한 "충돌 범위 확인과 대체·종료·수정 결정" 을 여기 남긴다.

**충돌은 부분이 아니라 전부다.** 그 티켓의 완료 기준 넷이 전부 MySQL 고유 함정을 피하는
것인데, PostgreSQL 에서는 그 함정 자체가 없다.

| 262 가 막으려던 것 | PostgreSQL 에서 |
|---|---|
| `utf8mb4` 로 만들어야 이모지·한글 정렬이 된다 | UTF-8 이 기본. 그런 개념이 없다 |
| 색인 걸 문자 칼럼 길이를 미리 정해야 한다 (안 하면 생성 거부) | 길이 제한이 없다 |
| JSON 안은 색인이 기본으로 안 걸린다 | `JSONB` + GIN 으로 걸린다 |
| 진짜 참/거짓 타입이 없다 (0/1) | `BOOLEAN` 이 있다 |

**그리고 그 일은 이미 다른 방식으로 일어났다.** 인증(-312) · 추천 로깅(-543) ·
입력 스냅샷(-554) · 일정(-313)이 각각 PostgreSQL 표와 JPA 엔티티를 만들었다. 아무도 262 를
따르지 않았고 아무도 그것을 눈치채지 못했다.

**"모델 26개" 도 낡은 숫자다.** 참고 프로젝트(LOCAL_ROUTE)의 Prisma 모델 수이고,
확정된 GABOLLE v1.1 ERD 는 **19개** 표다. 262 는 그 ERD 보다 앞선다.

### 판단

**수정이 아니라 종료다.** PostgreSQL 판으로 다시 쓰면 이미 머지된 일을 중복해서 적는
티켓이 된다. 담당자도 없고 스프린트에도 없어서 아무도 기다리지 않는다.

🔴 **다만 함께 볼 것이 하나 있다 — S15P21E201-81.**
`[Chore][Data] 장소 데이터를 우리 MySQL 로 옮기고, 거기서 빈 칸을 다시 센다`.
262 의 연결 티켓이고 같은 MySQL 전제를 갖고 있으며 역시 해야 할 일이다.
**262 만 닫으면 그 전제가 옆에서 살아남는다.** 그리고 81 의 내용(장소 정본)은
S15P21E201-545 와 겹친다.

티켓 상태 변경은 보고자(장효준)와 팀의 몫이므로 여기서는 근거만 남긴다.

---

## 7. 이 문서가 다루지 않는 것

| | 왜 |
|---|---|
| `ref/local-route/**` 의 MySQL·SQLite 언급 | **참고 프로젝트(LOCAL_ROUTE)의 기획 문서**다. GABOLLE 의 활성 백엔드 문서가 아니라 손대지 않는다 |
| `ref/local-route/LOCAL_ROUTE_ERD_v1.1_FINAL.dbml` 의 `database_type: 'MySQL'` | 🔴 유일하게 남은 실제 MySQL 선언이지만 **`common/dev` 에 있고 진미리 님 파일**이다. `back/dev` MR 로는 닿지 않는다 |
| `BYTEA` 매핑 검증 | 엔티티가 아직 없다 (3절) |
| "새 환경에서 기동 성공" 증명 | 마이그레이션은 검증됐지만(7/7 새 DB · 6/6 운영) 기동은 실제 시크릿이 필요하고, 2026-09-03 현재 운영 health 가 502 다 |
