#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE app_db;
    CREATE USER app_user WITH PASSWORD '${APP_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE app_db TO app_user;
    \connect app_db
    GRANT ALL ON SCHEMA public TO app_user;

    \connect postgres
    CREATE DATABASE airflow_db;
    CREATE USER airflow_user WITH PASSWORD '${AIRFLOW_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE airflow_db TO airflow_user;
    \connect airflow_db
    GRANT ALL ON SCHEMA public TO airflow_user;

    \connect postgres
    CREATE DATABASE mlflow_db;
    CREATE USER mlflow_user WITH PASSWORD '${MLFLOW_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE mlflow_db TO mlflow_user;
    \connect mlflow_db
    GRANT ALL ON SCHEMA public TO mlflow_user;
EOSQL
