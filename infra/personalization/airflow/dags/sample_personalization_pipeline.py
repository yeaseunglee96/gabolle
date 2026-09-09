"""문서 8절 — 샘플 개인화 파이프라인 (S15P21E201-576)

실제 이벤트/모델 코드가 아직 없는 상태에서, 합성(synthetic) 데이터로
Raw Event -> Staging -> Feature -> Training -> MLflow -> Redis 전 구간이
실제로 끝까지 통과하는지 검증한다. 실제 운영 이벤트 스키마가 아니라
문서 8.1절의 최소 계약(event_id/event_type/user_id 등)만 쓴다.

수동 트리거 전용이다 (schedule=None) — 정기 실행용 DAG이 아니라
인프라가 살아있는지 확인하는 스모크 테스트다.
"""

import datetime as dt
import json
import os
import pickle
import random
import string

import lightgbm as lgb
import mlflow
import mlflow.lightgbm
import pandas as pd
import psycopg2
import redis
from airflow import DAG
from airflow.operators.python import PythonOperator

APP_DB_DSN = (
    f"host=postgres dbname=app_db user=app_user "
    f"password={os.environ['APP_DB_PASSWORD']}"
)
REDIS_HOST = "redis"
REDIS_PASSWORD = os.environ["REDIS_PASSWORD"]
MODEL_PATH = "/tmp/sample_personalization_model.pkl"


def get_conn():
    return psycopg2.connect(APP_DB_DSN)


def ingest_raw_events(**_):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS raw_event (
            event_id TEXT PRIMARY KEY,
            event_type TEXT NOT NULL,
            user_id TEXT NOT NULL,
            route_id TEXT,
            event_time TIMESTAMPTZ NOT NULL,
            context JSONB,
            source TEXT,
            schema_version INT
        )
        """
    )
    conn.commit()

    users = [f"user_{i:03d}" for i in range(20)]
    now = dt.datetime.now(dt.timezone.utc)
    rows = []
    for _ in range(300):
        user = random.choice(users)
        event_id = "evt_" + "".join(random.choices(string.hexdigits, k=16))
        event_type = random.choice(["route_view", "place_visit"])
        event_time = now - dt.timedelta(hours=random.randint(0, 24 * 7))
        rows.append(
            (
                event_id,
                event_type,
                user,
                f"route_{random.randint(1, 50)}",
                event_time,
                json.dumps({"device": "web"}),
                "backend",
                1,
            )
        )

    cur.executemany(
        """
        INSERT INTO raw_event
            (event_id, event_type, user_id, route_id, event_time, context, source, schema_version)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (event_id) DO NOTHING
        """,
        rows,
    )
    conn.commit()
    cur.close()
    conn.close()


def validate_contract(**_):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        "SELECT count(*) FROM raw_event "
        "WHERE user_id IS NULL OR event_type IS NULL OR event_time IS NULL"
    )
    missing = cur.fetchone()[0]
    cur.execute("SELECT count(*) FROM raw_event WHERE event_time > now()")
    future = cur.fetchone()[0]
    cur.close()
    conn.close()

    if missing > 0:
        raise ValueError(f"필수값 결측 {missing}건 발견")
    if future > 0:
        raise ValueError(f"미래 시각 이벤트 {future}건 발견")
    print(f"contract OK — missing={missing}, future={future}")


def build_staging(**_):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        "CREATE TABLE IF NOT EXISTS staging_event AS SELECT * FROM raw_event WHERE false"
    )
    conn.commit()
    cur.execute("TRUNCATE staging_event")
    cur.execute(
        """
        INSERT INTO staging_event
        SELECT DISTINCT ON (event_id)
            event_id, event_type, lower(trim(user_id)), route_id,
            event_time, context, source, schema_version
        FROM raw_event
        """
    )
    conn.commit()
    cur.close()
    conn.close()


def build_features(**_):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS user_feature (
            user_id TEXT PRIMARY KEY,
            visit_count_7d INT,
            unique_routes_7d INT,
            updated_at TIMESTAMPTZ
        )
        """
    )
    conn.commit()
    cur.execute(
        """
        INSERT INTO user_feature (user_id, visit_count_7d, unique_routes_7d, updated_at)
        SELECT user_id, count(*), count(DISTINCT route_id), now()
        FROM staging_event
        WHERE event_time > now() - interval '7 days'
        GROUP BY user_id
        ON CONFLICT (user_id) DO UPDATE
        SET visit_count_7d = EXCLUDED.visit_count_7d,
            unique_routes_7d = EXCLUDED.unique_routes_7d,
            updated_at = EXCLUDED.updated_at
        """
    )
    conn.commit()
    cur.close()
    conn.close()


def train_and_evaluate(**context):
    conn = get_conn()
    df = pd.read_sql(
        "SELECT user_id, visit_count_7d, unique_routes_7d FROM user_feature", conn
    )
    conn.close()

    if len(df) < 5:
        raise ValueError("학습할 사용자 피처가 너무 적습니다")

    median = df["visit_count_7d"].median()
    df["label"] = (df["visit_count_7d"] > median).astype(int)

    X = df[["visit_count_7d", "unique_routes_7d"]]
    y = df["label"]

    model = lgb.LGBMClassifier(n_estimators=20, max_depth=3)
    model.fit(X, y)
    accuracy = float((model.predict(X) == y).mean())

    with open(MODEL_PATH, "wb") as f:
        pickle.dump(model, f)

    ti = context["ti"]
    ti.xcom_push(key="accuracy", value=accuracy)
    ti.xcom_push(key="num_users", value=int(len(df)))
    print(f"train_and_evaluate OK — num_users={len(df)}, accuracy={accuracy:.3f}")


def register_candidate(**context):
    ti = context["ti"]
    accuracy = ti.xcom_pull(task_ids="train_and_evaluate", key="accuracy")
    num_users = ti.xcom_pull(task_ids="train_and_evaluate", key="num_users")

    with open(MODEL_PATH, "rb") as f:
        model = pickle.load(f)

    mlflow.set_tracking_uri(os.environ["MLFLOW_TRACKING_URI"])
    mlflow.set_experiment("sample-personalization")

    with mlflow.start_run(run_name=f"sample-{context['ds']}") as run:
        mlflow.log_param("num_users", num_users)
        mlflow.log_metric("accuracy", accuracy)
        mlflow.lightgbm.log_model(model, "model")
        run_id = run.info.run_id

    ti.xcom_push(key="run_id", value=run_id)
    print(f"register_candidate OK — run_id={run_id}")


def publish_online_features(**context):
    ti = context["ti"]
    run_id = ti.xcom_pull(task_ids="register_candidate", key="run_id")

    conn = get_conn()
    df = pd.read_sql(
        "SELECT user_id, visit_count_7d, unique_routes_7d FROM user_feature", conn
    )
    conn.close()

    r = redis.Redis(
        host=REDIS_HOST, port=6379, password=REDIS_PASSWORD, decode_responses=True
    )
    for _, row in df.iterrows():
        key = f"feature:v1:user:{row['user_id']}"
        value = json.dumps(
            {
                "visit_count_7d": int(row["visit_count_7d"]),
                "unique_routes_7d": int(row["unique_routes_7d"]),
                "model_run_id": run_id,
            }
        )
        r.set(key, value, ex=7 * 24 * 3600)

    ti.xcom_push(key="published_count", value=int(len(df)))
    print(f"publish_online_features OK — published={len(df)}")


def smoke_test(**context):
    ti = context["ti"]
    run_id = ti.xcom_pull(task_ids="register_candidate", key="run_id")
    published = ti.xcom_pull(task_ids="publish_online_features", key="published_count")

    client = mlflow.tracking.MlflowClient(tracking_uri=os.environ["MLFLOW_TRACKING_URI"])
    run = client.get_run(run_id)
    if run.info.status != "FINISHED":
        raise ValueError(f"MLflow run 상태 이상: {run.info.status}")

    r = redis.Redis(
        host=REDIS_HOST, port=6379, password=REDIS_PASSWORD, decode_responses=True
    )
    keys = r.keys("feature:v1:user:*")
    if not keys:
        raise ValueError("Redis 에 피처 키가 없습니다")

    print(
        f"smoke_test OK — mlflow run={run_id} status={run.info.status}, "
        f"redis_keys={len(keys)}, published={published}"
    )


default_args = {
    "owner": "infra",
    "retries": 0,
}

with DAG(
    dag_id="sample_personalization_pipeline",
    description="문서 8절 — 합성 데이터로 Raw Event -> Redis 전 구간 검증 (S15P21E201-576)",
    schedule=None,
    start_date=dt.datetime(2026, 1, 1),
    catchup=False,
    default_args=default_args,
    tags=["personalization", "sample"],
) as dag:
    t1 = PythonOperator(task_id="ingest_raw_events", python_callable=ingest_raw_events)
    t2 = PythonOperator(task_id="validate_contract", python_callable=validate_contract)
    t3 = PythonOperator(task_id="build_staging", python_callable=build_staging)
    t4 = PythonOperator(task_id="build_features", python_callable=build_features)
    t5 = PythonOperator(task_id="train_and_evaluate", python_callable=train_and_evaluate)
    t6 = PythonOperator(task_id="register_candidate", python_callable=register_candidate)
    t7 = PythonOperator(
        task_id="publish_online_features", python_callable=publish_online_features
    )
    t8 = PythonOperator(task_id="smoke_test", python_callable=smoke_test)

    t1 >> t2 >> t3 >> t4 >> t5 >> t6 >> t7 >> t8
