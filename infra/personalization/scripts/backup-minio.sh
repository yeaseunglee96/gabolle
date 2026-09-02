#!/bin/bash
# MinIO(모델 아티팩트) 백업 (S15P21E201-578)
#
# 정책 (팀 확정, 2026-09-01):
#   - 배포된 적 있는 버전 -> 프로젝트 종료까지 전부 보존
#   - 실험 중간 산출물     -> 최근 5개
#   - 평가 리포트·메타데이터 -> 전부 보존 (용량 작음)
#
#   DB 백업은 "같은 것의 사본"이라 옛것을 지워도 되지만, 모델은 "서로 다른
#   산출물"이라 지우면 그 버전으로 롤백이 불가능해진다 (DR-09 배포/롤백 상태
#   보존, NFR-08 평가 재현성).
#
# 🔴 지금 이 스크립트는 "배포됨 vs 실험 중"을 구분하는 태깅 체계가 아직 없어서
#   (MLflow Model Registry의 stage 승격 흐름이 아직 안 만들어짐), 안전한
#   기본값으로 전체를 로테이션 없이 통째로 미러링한다. "실험 중간 산출물만
#   5개로 줄이는" 것은 그 태깅 체계가 생긴 뒤 후속 작업으로 넣는다 — 모르는
#   상태에서 자동으로 지우는 것보다, 지금은 아무것도 안 지우는 쪽이 안전하다.

set -euo pipefail

COMPOSE_DIR="/opt/local-route/personalization"
ENV_FILE="/etc/local-route/personalization.env"
NETWORK="local-route-personalization_data_net"
LOCAL_MIRROR="/var/backups/local-route/minio"
REMOTE_HOST="j15e201a.p.ssafy.io"
REMOTE_DIR="/opt/backups/local-route/minio"
SSH_KEY="$HOME/.ssh/backup_to_j15e201a"

MINIO_USER="$(grep '^MINIO_ROOT_USER=' "$ENV_FILE" | cut -d '=' -f2-)"
MINIO_PW="$(grep '^MINIO_ROOT_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2-)"

mkdir -p "$LOCAL_MIRROR"

echo "[backup-minio] mirroring mlflow-artifacts bucket -> ${LOCAL_MIRROR}"
docker run --rm \
    --network "$NETWORK" \
    -v "${LOCAL_MIRROR}:/mirror" \
    -e MC_HOST_local="http://${MINIO_USER}:${MINIO_PW}@minio:9000" \
    minio/mc mirror --overwrite local/mlflow-artifacts /mirror

echo "[backup-minio] syncing to backup server (${REMOTE_HOST})"
rsync -a \
    -e "ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no" \
    "$LOCAL_MIRROR"/ "ubuntu@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "[backup-minio] done"
