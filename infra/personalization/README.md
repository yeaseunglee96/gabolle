# infra/personalization

개인화 추천 시스템 인프라(PostgreSQL, Redis, MinIO, MLflow, Airflow)의
Docker Compose 정의. 전체 구축 배경과 절차, 자원 계획, 포트/보안 원칙은
[`docs/SERVER-SETUP.md`](../../docs/SERVER-SETUP.md)를 먼저 읽는다
(S15P21E201-531 · S15P21E201-573).

## 구조

```
infra/personalization/
  compose.yaml          PostgreSQL·Redis·MinIO·MLflow·Airflow 전체 정의
  .env.example           필요한 환경변수 이름 목록 (실제 값은 없음)
  postgres/init/          앱/Airflow/MLflow용 DB·사용자 초기화 스크립트
  mlflow/Dockerfile        MLflow + PostgreSQL/S3 드라이버
  airflow/Dockerfile        Airflow + 팀 전용 provider 패키지
  airflow/requirements.txt  Airflow 추가 패키지 고정 목록
  airflow/dags/            DAG 파일 위치 (8절 샘플 파이프라인에서 채움)
```

## 서버에 배포하는 법 (지금은 손으로, 9절에서 Jenkins로 자동화)

> 🔴 **배포 위치는 git clone 자체(`/opt/local-route/repository`)다.** 예전에는
> `/opt/local-route/personalization`이라는 별도 배포 디렉터리를 두려 했지만
> (git 저장소가 아닌 채로), Jenkinsfile의 `git pull`이 실제로는 한 번도
> 실행된 적이 없어서 그 디렉터리와 git clone이 어긋나 있었다 (S15P21E201-579
> 에서 발견). `compose.yaml`의 `name:` 필드가 볼륨·네트워크 이름을 고정하므로,
> 실행 위치를 옮겨도 기존 데이터/컨테이너에는 영향이 없다.

```bash
cd /opt/local-route/repository/infra/personalization
git pull origin common/dev

docker compose --env-file /etc/local-route/personalization.env config --quiet
docker compose up -d postgres redis minio
docker compose up -d mlflow
docker compose up -d airflow-broker
docker compose up airflow-init      # 최초 1회, DB 마이그레이션 + 관리자 계정
docker compose up -d airflow-scheduler airflow-dag-processor airflow-api-server airflow-worker
```

`/etc/local-route/personalization.env`는 저장소에 없다 — 서버에서 직접 생성하고
0640 권한으로 제한한다 (`.env.example` 참고).

## 포트

| 서비스 | 호스트 바인딩 | 비고 |
|---|---|---|
| MinIO Console | `127.0.0.1:9001` | SSH 터널로만 접근 |
| MLflow | `127.0.0.1:5000` | SSH 터널로만 접근 |
| Airflow API Server | `127.0.0.1:8082` | SSH 터널로만 접근. 8081은 Jenkins가 이미 사용 중 |

PostgreSQL·Redis·MinIO API·Airflow 내부 브로커는 호스트에 노출하지 않는다
(컨테이너 내부 네트워크로만 통신).

---

## 문제 해결 기록 — 실제로 겪은 것 (S15P21E201-576)

배포하면서 겪은 문제 중, **원인을 알아야 다음에 안 헤매는 것 세 가지**만 남긴다.

### ① PostgreSQL 15+는 `public` 스키마에 기본적으로 아무도 못 쓴다

`GRANT ALL PRIVILEGES ON DATABASE app_db TO app_user`만으로는 부족하다. 이건
"이 DB에 접속할 권한"이지 "이 DB 안에 테이블을 만들 권한"이 아니다.
PostgreSQL 15부터 `public` 스키마의 `CREATE` 권한이 DB 소유자에게만 주어지도록
바뀌어서, `app_user`로 `CREATE TABLE`을 하면 `permission denied for schema public`
이 난다.

**증상**: MLflow가 `mlflow_db`에 처음 접속해 테이블을 만들 때
`psycopg2.errors.InsufficientPrivilege: permission denied for schema public`.

**고치는 법** — `postgres/init/01-init-databases.sh`에서 DB를 만든 직후,
그 DB로 접속을 바꿔서(`\connect`) 스키마 권한을 따로 준다.

```sql
CREATE DATABASE app_db;
CREATE USER app_user WITH PASSWORD '...';
GRANT ALL PRIVILEGES ON DATABASE app_db TO app_user;
\connect app_db
GRANT ALL ON SCHEMA public TO app_user;
```

이 스크립트는 데이터가 이미 있는 볼륨에는 다시 안 돈다 — 이미 떠 있는
DB라면 `ALTER USER`가 아니라 `docker compose exec postgres psql -U postgres -d <db> -c "GRANT ALL ON SCHEMA public TO <user>;"`로 직접 준다.

### ② LightGBM은 `libgomp.so.1`(OpenMP 런타임)이 없으면 아예 로드가 안 된다

`apache/airflow` 베이스 이미지는 최소 구성이라 이 시스템 라이브러리가 없다.
`pip install lightgbm`은 성공하지만, `import lightgbm` 시점에 죽는다.

**증상**: `OSError: libgomp.so.1: cannot open shared object file: No such file or directory`

**고치는 법** — `airflow/Dockerfile`에서 pip 설치 전에 apt로 설치한다 (root로
잠깐 전환해야 한다).

```dockerfile
USER root
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*
USER airflow
```

**진단 팁**: DAG이 Airflow 목록에 "Import Errors" 배너만 띄우고 구체적인
내용을 안 보여줄 때는, 컨테이너 안에서 그 파일을 직접 실행해보면 전체
트레이스백이 나온다.

```bash
docker compose exec airflow-scheduler python /opt/airflow/dags/<파일명>.py
```

### ③ Airflow 3.x는 worker가 DB에 직접 안 쓰고 API Server에게 HTTP로 보고한다

Airflow 2.x와 가장 크게 달라진 점이다. 이것 때문에 **두 단계**로 문제가 났다.

**1단계 증상**: 태스크가 시작하자마자 `ConnectError: [Errno 111] Connection refused`.
원인은 worker가 API Server 주소를 모른다는 것 — 기본값이 사실상 `localhost`인데,
컨테이너 안에서 `localhost`는 자기 자신이다 (Jenkins 때 겪은 것과 같은 유형).

```yaml
AIRFLOW__CORE__EXECUTION_API_SERVER_URL: http://airflow-api-server:8080/execution/
```

**2단계 증상**: 위를 고치면 연결은 되는데 `ServerResponseError: Invalid auth
token: Signature verification failed` (HTTP 403)로 다시 실패한다. worker와
api-server가 요청에 서명할 JWT 키를 **각자 자동 생성**해서 서로 다르게 갖고
있기 때문이다. 이 키는 `[api] secret_key`(`AIRFLOW__WEBSERVER__SECRET_KEY`로
넘어오는 값)와는 **완전히 별개**의 `[api_auth] jwt_secret` 설정이라, 흔히 아는
웹서버 시크릿 키를 맞춰도 해결이 안 된다.

```yaml
AIRFLOW__API_AUTH__JWT_SECRET: ${AIRFLOW_WEBSERVER_SECRET}
```

이 값을 모든 Airflow 컴포넌트(scheduler·dag-processor·api-server·worker)가
공유하는 `x-airflow-common` 환경변수에 넣어야 한다 (개별 서비스에 따로 넣으면
또 어긋난다).

**진단 팁**: 컨테이너별로 실제 적용된 값을 직접 비교하면 바로 알 수 있다.

```bash
docker compose exec airflow-worker airflow config get-value api_auth jwt_secret
docker compose exec airflow-api-server airflow config get-value api_auth jwt_secret
```

두 값이 다르면 그게 원인이다. 어느 섹션 소속인지 모르겠으면
`airflow config list`(섹션 헤더 `[api_auth]` 포함 전체 출력)에서 줄 번호로
대조하면 된다.

---

## 백업 · 최소 모니터링 · 개인정보 삭제 절차 (S15P21E201-578, 문서 11절)

### 11.1 최소 모니터링

지금은 별도 모니터링 스택(Prometheus/Grafana 등)을 두지 않는다. 아래 최소
지표만 SSH로 접속해 손으로 확인한다 — 자동화는 필요해지면 후속 작업으로 넣는다.

| 확인할 것 | 명령 |
|---|---|
| 컨테이너 전부 살아있는가 | `docker compose ps` (모든 서비스가 `Up`) |
| Airflow DAG 실패 여부 | Airflow UI (`127.0.0.1:8082`, SSH 터널) → DAGs 목록의 최근 실행 상태 |
| 디스크 여유 공간 | `df -h /var/lib/docker /var/backups` (백업이 쌓이는 두 경로) |
| MLflow 응답 | `curl -f http://127.0.0.1:5000/health` |

### 11.2 백업

**대상과 정책** (팀 확정, 2026-09-01):

| 대상 | 정책 | 이유 |
|---|---|---|
| PostgreSQL (`app_db`/`airflow_db`/`mlflow_db`) | 일 7개 + 주 2개 로테이션 | 같은 데이터의 사본이라 오래된 것은 지워도 된다. 주 2개인 이유는 용량이 아니라 일정 — M4 완료가 2026-09-23이라 프로젝트 전체가 4주가 안 되고, 그래서 주 4개는 애초에 만들어질 수 없다 |
| MinIO 모델 아티팩트 (`mlflow-artifacts` 버킷) | 로테이션 없이 전체 미러링 | 배포된 적 있는 버전은 프로젝트 종료까지 보존해야 롤백이 가능하다(DR-09). "실험 중간 산출물만 최근 5개로 줄인다"는 세부 규칙은 MLflow Model Registry의 배포/실험 태깅 체계가 아직 없어서, 그게 생긴 뒤 후속 작업으로 넣는다. 그때까지는 아무것도 안 지우는 쪽이 안전하다 |
| 원본 이벤트(raw event) | 90일 보관 후 삭제 | 실제 `app_db` 이벤트 테이블은 아직 없다 — 스키마가 생기면 BE/Data 쪽에서 로테이션 잡을 추가한다. 여기 적힌 것은 INFRA가 지켜야 할 상한이다 |

**저장 위치**: 로컬(EC2 `J15E201`)에서 `/var/backups/local-route/{postgres,minio}/`로
백업한 뒤, 별도 EC2 `J15E201A`(백업 전용 서버)로 `rsync` 전송한다. 같은 서버
안에만 있으면 그 서버가 죽었을 때 백업도 같이 사라지기 때문이다.

**스크립트**: `scripts/backup-postgres.sh`, `scripts/backup-minio.sh`.
매일 `cron`으로 돌리고, Jenkins 배포 파이프라인의 `Backup` 단계에서도 배포
직전에 한 번 더 실행한다 (`Jenkinsfile` 참고).

> **실제 서버 배포 확인 (2026-09-02)**: 두 스크립트 모두 J15E201에서 직접 실행해
> `pg_dump`/`mc mirror` → J15E201A로 `rsync` 전송까지 끝까지 성공했다. 최초
> 실행에서 `backup-postgres.sh`의 로테이션 부분이 weekly 스냅샷이 하나도 없는
> 상태에서 `ls`가 exit code 2를 내고 `pipefail` 때문에 `set -e`가 스크립트를
> 조용히 죽이는 버그가 나왔고(rsync 전송 전 단계에서 멈춤), 로테이션 파이프라인에
> `|| true`를 붙여 고쳤다 — 자세한 원인은 스크립트 내 주석 참고. cron은
> `0 4 * * *`(postgres), `30 4 * * *`(minio)로 등록했다.

**서버에 배포하는 법** (최초 1회):

스크립트는 별도로 복사하지 않는다 — git clone(`/opt/local-route/repository`)
안의 것을 그대로 cron/Jenkins가 실행한다. 실행 권한만 한 번 준다.

```bash
# J15E201 (메인 서버) 에서
chmod +x /opt/local-route/repository/infra/personalization/scripts/*.sh

# J15E201A(백업 서버)로 rsync 할 SSH 키가 없다면 새로 만들고,
# 공개키를 J15E201A의 ubuntu 계정 authorized_keys 에 등록한다
ssh-keygen -t ed25519 -f ~/.ssh/backup_to_j15e201a -N ""
ssh-copy-id -i ~/.ssh/backup_to_j15e201a.pub ubuntu@j15e201a.p.ssafy.io

# crontab 에 매일 새벽 등록 (예: 04:00 postgres, 04:30 minio)
crontab -e
# 0 4 * * *  /opt/local-route/repository/infra/personalization/scripts/backup-postgres.sh >> /var/log/local-route/backup-postgres.log 2>&1
# 30 4 * * * /opt/local-route/repository/infra/personalization/scripts/backup-minio.sh >> /var/log/local-route/backup-minio.log 2>&1
```

**복구 리허설**: 실제로 백업 파일이 복구 가능한지, 한 번은 직접 검증해야 한다.

```bash
# J15E201A 에서 임의의 daily 덤프 하나를 골라 새 컨테이너에 복구해본다
# (운영 중인 버전과 맞춘다 — compose.yaml 의 postgres 이미지 태그 참고)
docker run --rm -e POSTGRES_PASSWORD=temp -d --name restore-test postgres:16.4
docker cp /opt/backups/local-route/postgres/app_db_daily_<날짜>.dump restore-test:/tmp/app_db.dump
docker exec restore-test createdb -U postgres app_db_restored
docker exec restore-test pg_restore -U postgres -d app_db_restored /tmp/app_db.dump
docker exec restore-test psql -U postgres -d app_db_restored -c '\dt'   # 테이블 목록이 보이면 성공
docker rm -f restore-test
```

> 리허설 결과는 이 표에 기록한다 — 언제, 무엇을, 어떻게 확인했는지.

| 날짜 | 대상 | 방법 | 결과 |
|---|---|---|---|
| 2026-09-02 | `app_db_daily_20260902.dump` (J15E201A에서 다시 가져온 사본) | 메인 서버(J15E201)에서 `postgres:16.4` 임시 컨테이너에 `pg_restore`로 복구, `\dt`와 `count(*)`로 확인 | 성공 — `raw_event`(300행)·`staging_event`(300행)·`user_feature`(20행) 테이블 3개 전부 복구됨. `ALTER TABLE ... OWNER TO app_user`/`GRANT` 관련 에러 4건은 테스트 컨테이너에 `app_user` 계정이 없어서 난 것으로, 실제 서버 복구 시에는 해당 계정이 이미 있어 발생하지 않는다(`errors ignored on restore` 표시로 pg_restore가 계속 진행함을 확인) |

### 11.3 개인정보 삭제 절차 (회원 탈퇴 시)

**정책** (팀 확정, 2026-09-01):

- **완전 삭제(hard delete)**: 계정, 인증 정보, 프로필, 좋아요/즐겨찾기 등 기능
  데이터 — 탈퇴 즉시
- **익명화(anonymize, 삭제 아님)**: 추천 모델 학습에 쓰인 행동 이벤트
  (조회·클릭 등) — `userId`를 잘라내고, 위치를 넓은 구역 단위로 뭉개고,
  타임스탬프를 반올림한다. 완전히 지우지 않는 이유는 NFR-08(오프라인 평가
  재현성)을 지키기 위해서다 — 그 이벤트가 없으면 과거 모델 성능을 다시 계산할
  방법이 없어진다
- **공개 SLA**: 삭제 요청 시 즉시 처리 시작, 백업까지 포함한 완전 삭제는 최대
  30일 이내 완료

**INFRA가 담당하는 부분과 아닌 부분**:

- 🔴 실제 익명화 SQL과 애플리케이션 로직은 **BE/Data 쪽 작업**이다 — 지금은
  실제 `app_db` 이벤트 스키마 자체가 없어서 INFRA가 미리 만들 수 없다. 스키마가
  생기면 그쪽 티켓에서 익명화 배치 잡을 추가해야 한다
- INFRA가 지금 보장하는 것은 **백업 로테이션이 30일 SLA를 넘기지 않는다는 것**
  이다 — PostgreSQL 백업은 최대 7일(daily) + 최근 2주(weekly)만 보관하므로,
  삭제 요청이 들어온 시점 이후 새로 도는 백업부터는 자동으로 반영된다.
  `rsync --delete`(로컬에서 지워진 파일은 원격에서도 지운다)를 쓰기 때문에
  백업 서버(J15E201A) 쪽도 로컬 로테이션을 그대로 따라간다
- 탈퇴 요청이 들어오면, BE가 익명화/하드삭제를 실행한 **이후** 첫 백업부터
  그 결과가 반영된다는 점을 팀에 공유해 둔다 — 요청 당일 백업에는 아직
  옛 데이터가 남아있을 수 있다는 뜻이다

---

## 배포 · 롤백 · 장애 대응 (S15P21E201-579, 문서 12절)

### 12.1 일반 배포

```bash
cd /opt/local-route/repository/infra/personalization
docker compose --env-file /etc/local-route/personalization.env config --quiet
docker compose pull <service>                      # postgres/redis/minio 등 공식 이미지
IMAGE_TAG=<git SHA> docker compose --env-file /etc/local-route/personalization.env build <service>   # mlflow/airflow 커스텀 빌드
IMAGE_TAG=<git SHA> docker compose --env-file /etc/local-route/personalization.env up -d --no-deps <service>
docker compose ps
docker compose logs --tail=100 <service>
```

이 흐름은 `Jenkinsfile`이 그대로 자동화한다 — 사람이 위 명령을 직접 칠 일은
Jenkins Job이 아직 연결 안 됐을 때의 수동 배포나, 디버깅 시 로그 확인 정도다.

### 12.2 롤백 원칙

문서 원문의 5가지 원칙을 우리 compose 구조에 맞게 적용한 것이다.

| 원칙 | 우리 환경에서의 적용 |
|---|---|
| 이미지는 직전 commit SHA 태그로 되돌리고 service 단위로 재기동 | `mlflow`/`airflow`는 커스텀 빌드라 `compose.yaml`에 `image: ...:${IMAGE_TAG}` 필드를 추가했다. `Jenkinsfile`의 `ROLLBACK_TAG` 파라미터에 이전 커밋의 짧은 SHA를 넣고 재실행하면, 재빌드 없이 이미 로컬에 있는 그 태그의 이미지로 `up -d --no-deps`만 한다 |
| DB migration은 자동 downgrade에 의존하지 않는다 | `scripts/backup-postgres.sh`(문서 11절)로 배포 직전 백업을 항상 남긴다. migration 실패 시 배포를 멈추고, 필요하면 11.2절의 복구 절차로 되돌린다 |
| 모델은 MLflow Production alias만 이전 버전으로 되돌린다 | MLflow UI 또는 CLI(`mlflow models set-alias`)로 alias만 옮긴다 — 모델 파일 자체를 지우거나 새로 배포하지 않는다 |
| Redis key는 `feature:vN`으로 버전 분리 | 이미 구현되어 있다 — `airflow/dags/sample_personalization_pipeline.py`가 `feature:v1:user:{user_id}` 형태로 쓴다. 신규 버전 배포가 실패해도 이전 버전 키가 그대로 남아 있어 API가 읽을 수 있다 |
| DAG rollback은 이전 릴리스의 DAG artifact를 재배포 | DAG 파일은 git으로 버전 관리된다 — 서버에서 이전 커밋으로 `git checkout`하면 `airflow-dag-processor`가 마운트된 `airflow/dags/`의 변경을 자동으로 다시 읽는다. 재기동은 필요 없다. 중복 실행 여부는 Airflow UI의 DAG 실행 이력에서 확인한다 |

**실제 배포 → 롤백 리허설 (2026-09-02)**: 서버(J15E201)에서 `mlflow` 이미지로
검증했다 (`airflow-common`도 같은 `image:`/`IMAGE_TAG` 메커니즘을 공유하므로
동일하게 동작한다 — 4개 서비스가 셋 다 무거워서 빠른 서비스인 mlflow로만
1회 리허설했다). 태그 v1(`6a950edcce75`, 실제 커밋 SHA)으로 빌드·배포 →
태그 v2-test(가상 배포)로 다시 빌드·배포 → **재빌드 없이** `IMAGE_TAG=6a950edcce75`로
`up -d --no-deps`만 실행해 v1으로 롤백. 매 단계 `docker inspect --format
'{{.Config.Image}}'`로 실제 태그를 확인했고, 롤백 후 `curl -f
http://127.0.0.1:5000/health`로 서비스 정상 동작까지 확인했다.

> 🔴 **리허설 중 별도로 발견한 문제**: 배포 디렉터리로 쓰려던
> `/opt/local-route/personalization`이 git 저장소가 아니었다 — Jenkinsfile의
> "Sync Repository on Server"(`git pull`) 단계가 Jenkins Job이 아직 연결된 적
> 없어 한 번도 실행되지 않았고, 그래서 이 어긋남이 지금까지 안 드러났었다.
> 배포 위치를 git clone 자체(`/opt/local-route/repository/infra/personalization`)로
> 통일해 고쳤다 (12.1절 명령, `Jenkinsfile`, 백업 스크립트, cron 전부 이 경로
> 기준으로 갱신함). 옛 디렉터리는 파일 내용이 git과 동일함을 `diff`로 확인한
> 뒤 삭제했다.

| 날짜 | 대상 | 방법 | 결과 |
|---|---|---|---|
| 2026-09-02 | `mlflow` 이미지 (v1 `6a950edcce75` → v2-test → v1 롤백) | `IMAGE_TAG`를 바꿔가며 `docker compose build`/`up -d --no-deps mlflow` 실행, 매 단계 `docker inspect`로 태그 확인, 롤백 후 `/health` 확인 | 성공 — v1 빌드·배포, v2-test 빌드·배포, v1으로 **재빌드 없이** 롤백까지 전부 태그가 의도대로 전환됐고 롤백 후 헬스체크도 `OK` |

### 12.3 대표 장애 대응

| 증상 | 우선 확인 | 조치 |
|---|---|---|
| OOM/컨테이너 재시작 | `docker inspect`, host memory/swap | 무거운 DAG 중지, concurrency 축소 |
| Airflow task 적체 | scheduler heartbeat, worker, pool | Worker 재기동 전 DB/queue 연결과 task 멱등성 확인 |
| MLflow artifact 실패 | MinIO bucket/credential/용량 | credential rotation 여부 확인, DB run과 artifact 불일치 기록 |
| Redis miss 급증 | TTL, evicted_keys, feature freshness | rebuild DAG 실행, API fallback 활성화 |
| DB 용량 급증 | raw partition, vacuum, long query | 보존정책에 따른 파티션 정리, 임의 DELETE 금지 |
| 추천 품질 급락 | input drift, data delay, model version | 모델 alias 롤백, 비개인화 fallback, 원인 분석 |
