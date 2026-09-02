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

```bash
cd /opt/local-route/personalization
git pull   # 또는 이 디렉터리 내용을 최신으로 맞춤

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
