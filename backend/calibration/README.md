# 보정 작업 — 실제 기록으로 추천의 숫자를 스스로 고친다

S15P21E201-1692. 첫 작업은 **체류 시간**이다. 일정 조립은 갈래마다 「여기서 몇 분 머문다」를 기본값(`StayDefaults`, 카페 45분·바다 90분 …)으로 깐다.
이 작업은 사람들이 실제로 찍은 도착·출발에서 갈래별 체류를 재어, 검사를 통과한 값만 엔진에 넘긴다.

```
[운영 DB] ─ 뷰 calibration_stay_source (갈래 · 머문 분 · 측정/추정 · 주) ─▶ [러너: PySpark 계산 → 검사]
                                                                              │ 새 판을 쌓는다
[일정 조립] ◀── 갈래마다 가장 최근 PASSED (없으면 기본값) ── calibration_value ◀┘
```

## 🔴 개인정보

- 뷰가 내주는 칸은 **넷뿐**이다 — 갈래 · 머문 분 · 측정/추정 · 주(그 주 월요일). 사람·일정·장소 번호와 정확한 시각은 뷰 밖으로 안 나간다.
- 모든 사용자의 기록을 쓰되 **익명 집계로만** 쓴다(2026-09-25 결정 — 「동의하면 개인 취향까지 가고, 동의하지 않으면 추천 품질만 잰다」).
- 운영에서 켜는 것은 **처리방침에 「품질 개선을 위한 익명 통계」가 나간 뒤**다.

## 폴더

| | |
|---|---|
| `run.py` | 입구. `python run.py stay_minutes [--dry-run]` · `python run.py travel_multiplier [--dry-run]` |
| `framework.py` | 공용 틀 — Spark 띄우기 · 뽑기 · 검사(`decide`) · 새 판 쌓기 · 요약 |
| `jobs/stay_minutes/extract.sql` | 운영 DB 에서 읽을 것(창구 뷰 하나) |
| `jobs/stay_minutes/compute.py` | Spark 계산 — 갈래 · 측정/추정마다 튀는 값을 버리고 평균 |
| `jobs/stay_minutes/checks.json` | 검사 기준 (아래) |
| `tests/` | `python -m unittest discover -s backend/calibration/tests` — 규칙은 Spark 없이, 계산은 Spark 로 |

다음 작업(인기 점수 · 설문 편향 보정 …)도 `jobs/<이름>/` 폴더 하나를 더하고 같은 결과 표에 `job` 이름만 달리해 쌓는다.

## 검사 — `checks.json`

| 검사 | 값 | 못 넘으면 |
|---|---|---|
| 갈래별 최소 표본(튀는 값을 버린 뒤) | 30 | 그 갈래 **보류(HELD)** — 엔진은 그 갈래의 직전 PASSED(없으면 기본값)를 계속 쓴다 |
| 튀는 값 | 사분위 범위의 1.5배 밖 | 버린다(휴대폰을 두고 나왔거나 출발을 다음 날 누른 기록) |
| 범위 | 20~180분 | 보류 |
| 한 번에 움직이는 폭 | 직전 PASSED(없으면 기본값)의 ±20% | 폭까지만 반영하고 `note` 에 계산값을 남긴다 |
| 기간 | 최근 84일(12주) | — |
| 추정(출발 없음) | — | 늘 **참고(REFERENCE)** 로만 남는다. 쉬는 시간·이동 지연이 섞여 부풀기 쉽다 |

`baseline` 은 `StayDefaults` 와 같아야 한다 — `StayCalibrationBaselineTest` 가 둘을 맞대 본다.

계산이 오류로 멈추면 **판을 쓰지 않는다** — 엔진은 지금 판을 그대로 쓰고, 종료 코드가 0 이 아니라 파이프라인이 빨개진다.
기간 안에 기록이 없으면 판을 쓰지 않고 0 으로 끝난다. 보류는 실패가 아니다.

## 엔진 쪽

- 스위치: `gabolle.calibration.stay.enabled`(환경변수 **`GABOLLE_CALIBRATION_STAY_ENABLED`**). **기본은 꺼짐** — 꺼져 있으면 읽는 부품이 아예 안 생기고 기본값만 쓴다.
- 읽기: 갈래마다 가장 최근 `PASSED`(측정). 10분 동안 기억한다.

## 되돌리기

그 판을 `REVOKED` 로 바꾼다. 다음 읽기부터(늦어도 10분) 갈래마다 직전 PASSED 가 나온다.

```sql
UPDATE calibration_value
   SET status = 'REVOKED', note = coalesce(note || ' · ', '') || '되돌림 <이유>'
 WHERE job = 'stay_minutes' AND version = <판> AND status = 'PASSED';
```

이동 배율도 같다 — `job = 'travel_multiplier'`.

## 두 번째 작업 — 이동 시간 배율 (`jobs/travel_multiplier`, S15P21E201-1700)

엔진이 어림한 구간 이동 시간이 실제와 얼마나 다른지 **수단마다** 배율(실제 ÷ 어림)로 잰다.

- **실제 이동**: 같은 날 실제로 도착한 순서에서 앞 곳 출발 → 다음 곳 도착. 계획 순서가 아니다.
  - 앞 곳 출발이 비면 그 구간은 뺀다(지어내지 않는다).
  - 어림은 계획에서 붙어 있던 두 곳 사이에만 있다. 건너뛰거나 순서를 바꿔 다닌 쌍은 어림이 없어 빠진다.
- **창구 뷰** `calibration_travel_source`: 수단 · 거리 구간 · 어림 분 · 실제 분 · 주. 거리 구간은 뷰에만 있고 계산 열쇠로는 아직 안 쓴다 — 수단마다 표본이 쌓이면 나눈다.
- **배율** = 튀는 비율을 버린 뒤 「실제 합 ÷ 어림 합」. 비율 평균이면 2분 어림에 8분 걸린 짧은 구간이 30분 구간과 같은 무게가 된다.
- **검사**: 체류와 같은 틀 — 표본 30 · 사분위 1.5배 밖 버림 · 직전 배율(없으면 1.0 = 어림 그대로)의 ±20% ·
  **범위 0.5~2.0배**. 어림이 두 배 넘게 틀리면(또는 절반 아래) 배율로 조용히 덮을 일이 아니라 어림 쪽 결함이다 — 보류하고 사람이 본다.
- **스위치**: `GABOLLE_CALIBRATION_TRAVEL_ENABLED`(기본 꺼짐). 체류와 따로다.

### 🔴 「보정 전 이동 분」 칸 (`itinerary_leg.uncalibrated_duration_min`)

배율을 적용하면 구간의 이동 시간(`duration_min`)은 고친 값이 된다 — 화면의 「이동 25분」과 시각 깔기가 이 값을 쓴다.
계산은 실제를 **고치기 전 어림**과 견줘야 한다. 고친 값과 견주면 배율이 겹쳐 곱해지거나 1 로 수렴한다.
그래서 엔진이 처음 어림한 값을 옆 칸에 늘 남긴다(보정이 꺼져 있으면 두 칸이 같다). 이 칸이 생기기 전의 구간은 비어 있고, 그때는 이동 시간이 곧 어림이다.
판을 옮기는 모든 자리(새 일정 · 하루 다시 짜기 · 고정·빼기 등의 판 복사 · 저장/읽기)가 이 칸을 넘긴다 — 한 자리라도 흘리면 고친 값이 어림으로 읽힌다.

## 돌리기

```bash
pip install -r backend/calibration/requirements.txt     # Java 17 이 있어야 한다
cd backend/calibration
CALIBRATION_DB_URL=jdbc:postgresql://<호스트>:<포트>/<DB> CALIBRATION_DB_USER=… CALIBRATION_DB_PASSWORD=… \
  python run.py stay_minutes --dry-run
```

- DB 는 Spark 의 JDBC 로 읽고 쓴다. 드라이버는 백엔드와 같은 PostgreSQL 드라이버(42.7.13)를 Spark 가 처음 한 번 Maven 창고에서 받는다.
- 🔴 **윈도 PC** 에서는 그 받기가 Hadoop 의 윈도 도구(winutils)를 찾다 멈춘다. 이미 받아 둔 jar 를 `CALIBRATION_JDBC_JAR` 로 주면 된다
  (예: Gradle 캐시의 `postgresql-42.7.13.jar`). 리눅스 러너에서는 필요 없다.

## 운영에 켜기 전에 (이 MR 밖)

1. 처리방침 문구(품질 개선을 위한 익명 통계)가 운영에 나간다.
2. 러너가 운영 DB 에 닿는 길 — 포트 넘김만 되는 전용 계정·키(GitLab CI 변수 Masked·Protected) + DB 역할(창구 뷰 SELECT · 결과 표 INSERT 만). **iOS 심사 뒤.**
3. GitLab 예약 파이프라인(새벽 한 번, 백업 러너). 실패는 GitLab 메일로 온다.
4. 표본이 쌓이는지 본 뒤 `GABOLLE_CALIBRATION_STAY_ENABLED=true`.

## 예약 — 매일 03:00 운영에서 (S15P21E201-1750)

```
[GitLab 예약 03:00, ref back/dev, SCHEDULE_KIND=calibration]
   └▶ 잡 calibration:run — EC2 보조 러너(2065)만(태그 calibration) · CPU 2 · 4GB
        └▶ ci-run.sh: 파이썬 · pyspark · JDBC 드라이버(지문 확인) → SSH 터널 → run.py stay_minutes · travel_multiplier
             └▶ calib-tunnel@운영 ─(포트 넘기기만)─▶ 127.0.0.1:15432 ─ 중계 컨테이너 calib-db-relay ─▶ postgres:5432
                  └▶ 계정 calibration_job: 뷰 둘 SELECT · calibration_value SELECT · INSERT 만
```

잡과 이 PC 로컬 리허설은 **같은 `ci-run.sh`** 를 돌린다. 리허설(같은 이미지 · 같은 제한) 결과:
- 처음 81초(pyspark 받기 53초)
- 캐시가 있으면 31초
- 터널을 거치면 35초

### 🔴 뷰 · 결과 표를 다시 만들면 권한이 사라진다

보정 계정의 권한은 **그 개체에** 붙어 있다. 마이그레이션이 `calibration_stay_source` · `calibration_travel_source` · `calibration_value` 를 `DROP` 하고 다시 `CREATE` 하면 권한이 조용히 사라지고, 다음 새벽 예약이 `permission denied` 로 멈춘다. (`CREATE OR REPLACE VIEW` 는 권한을 지킨다.)

그런 마이그레이션은 끝에 이 조각을 더한다. 그 계정이 없는 개발 · 시험 DB 에서는 아무것도 안 한다.

```sql
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'calibration_job') THEN
    GRANT SELECT ON calibration_stay_source, calibration_travel_source TO calibration_job;
    GRANT SELECT, INSERT ON calibration_value TO calibration_job;
  END IF;
END $$;
```

`CalibrationGrantGuardTest` 가 이것을 지킨다 — 조각이 빠진 새 마이그레이션이면 빨개진다. 원래 뷰 마이그레이션 파일 머리에 적지 않은 까닭: 이미 운영에 적용된 파일이라 주석 한 줄에도 Flyway 지문 검사가 걸려 서버가 안 뜬다.

### 운영 쪽 준비 (한 번 · 사람이 · 사용자 확인 뒤)

🔴 비밀값(비밀번호 · 개인 열쇠)은 화면 · 파일 · 명령 줄에 남기지 않는다. 만든 자리에서 바로 CI 변수로 보낸다.

**① 보정 전용 DB 계정** — `app_user` 는 계정을 만들 권한이 없어 Flyway 로는 못 만든다. 슈퍼유저 `postgres` 로(컨테이너 안 소켓):

```sql
-- docker exec -i local-route-personalization-postgres-1 psql -U postgres -d app_db -v ON_ERROR_STOP=1
-- 비밀번호는 표준입력의 \set pw '…' 로만 넘긴다
CREATE ROLE calibration_job LOGIN PASSWORD :'pw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT CONNECTION LIMIT 2;
GRANT CONNECT ON DATABASE app_db TO calibration_job;
GRANT USAGE ON SCHEMA gabolle TO calibration_job;
GRANT SELECT ON gabolle.calibration_stay_source, gabolle.calibration_travel_source TO calibration_job;
GRANT SELECT, INSERT ON gabolle.calibration_value TO calibration_job;
ALTER ROLE calibration_job SET search_path = gabolle;
ALTER ROLE calibration_job SET statement_timeout = '5min';
```

뷰는 만든 사람(`app_user`) 권한으로 돈다. 그래서 뷰만 주면 원 표(방문 기록 · 장소 · 사람)는 안 보인다. 리허설에서 원 표 조회와 결과 표 `DELETE` 가 `permission denied` 인 것을 확인했다.

**② 중계 컨테이너** — DB 5432 는 호스트에 공개돼 있지 않다(도커 내부망만). 호스트 루프백에만 여는 중계를 둔다. 별명 `postgres` 로 가므로 DB 컨테이너를 다시 만들어 IP 가 바뀌어도 된다. 이미지는 **지문으로 고정**한다 — alpine/socat 1.8.1.1(2026-06-20). 당일 올라온 latest 는 피했다.

```bash
docker run -d --name calib-db-relay --restart unless-stopped \
  --network local-route-personalization_data_net -p 127.0.0.1:15432:5432 --memory 32m \
  alpine/socat@sha256:7f9a06753033f2b7de18edc2353f2c15153413d95a039163c6db270fc7a6c3b0 \
  TCP-LISTEN:5432,fork,reuseaddr TCP:postgres:5432
```

**③ 터널 전용 계정** — 비밀번호 없음 · 셸 없음. 열쇠는 포트 넘기기(127.0.0.1:15432)만 된다.

```bash
sudo adduser --system --group --shell /usr/sbin/nologin --home /home/calib-tunnel calib-tunnel
sudo install -d -m 700 -o calib-tunnel -g calib-tunnel /home/calib-tunnel/.ssh
# authorized_keys 한 줄(공개 열쇠만):
#   restrict,port-forwarding,permitopen="127.0.0.1:15432" ssh-ed25519 AAAA… calibration-ci
```

리허설(운영 흉내 컨테이너)에서 두 가지를 확인했다. 같은 열쇠로 셸을 열면 「This account is not available」, 다른 포트는 「administratively prohibited」로 거부된다.

**④ GitLab** (Maintainer)
- CI 변수(프로젝트, **Protected**):

| 변수 | Masked | 값 |
|---|---|---|
| `CALIBRATION_DB_PASSWORD` | ✔ | ① 의 비밀번호 |
| `CALIB_SSH_KEY_B64` | ✔ | 개인 열쇠 base64. 만든 PC 의 파일은 바로 지운다 |
| `CALIB_SSH_KNOWN_HOSTS` | | 운영 호스트 키 한 줄 |

- 러너 2065 에 태그 `calibration` 을 더한다(`heavy` 는 그대로).
- 예약: `0 3 * * *` · Asia/Seoul · ref `back/dev` · 변수 `SCHEDULE_KIND=calibration`.

### 확인

- 예약을 손으로 한 번 돌린다. CI 잡 로그에 단계별 시간이 찍히고, `calibration_value` 에 체류 보류(HELD) 줄이 쌓인다.
- 이동은 원천이 0줄이면 줄을 쓰지 않는다 — 출발 기록이 쌓이기 전에는 정상이다.

### 되돌리기 (거꾸로)

```bash
# GitLab: 예약 끄기/지우기 · CI 변수 셋 지우기 · 러너 2065 태그에서 calibration 빼기
sudo deluser --remove-home calib-tunnel
docker rm -f calib-db-relay
docker image rm alpine/socat@sha256:7f9a06753033f2b7de18edc2353f2c15153413d95a039163c6db270fc7a6c3b0
# docker exec -i local-route-personalization-postgres-1 psql -U postgres -d app_db
#   REVOKE ALL ON gabolle.calibration_stay_source, gabolle.calibration_travel_source, gabolle.calibration_value FROM calibration_job;
#   REVOKE USAGE ON SCHEMA gabolle FROM calibration_job; REVOKE CONNECT ON DATABASE app_db FROM calibration_job;
#   DROP ROLE calibration_job;
```

쌓인 `calibration_value` 줄은 지우지 않아도 된다. 앱은 스위치(`GABOLLE_CALIBRATION_*_ENABLED`)가 켜져 있고 `PASSED` 인 값만 읽는다.
