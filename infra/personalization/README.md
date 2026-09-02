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
