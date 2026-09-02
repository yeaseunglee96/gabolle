#!/bin/bash
# PostgreSQL 백업 (S15P21E201-578)
#
# 정책 (팀 확정, 2026-09-01):
#   - 일 7개 + 주 2개 로테이션
#   - 주 4 -> 2 로 줄인 이유는 용량이 아니라 일정이다 — M4 완료가 9/23 이라
#     프로젝트 전체가 4주가 안 된다. 주 4개는 애초에 만들어질 수 없다.
#
# app_db / airflow_db / mlflow_db 세 DB를 모두 백업하고, 백업 서버(J15E201A)로
# rsync 전송한다. cron 으로 매일 돌린다 — infra/personalization/README.md 참고.

set -euo pipefail

COMPOSE_DIR="/opt/local-route/repository/infra/personalization"
BACKUP_DIR="/var/backups/local-route/postgres"
ENV_FILE="/etc/local-route/personalization.env"
REMOTE_HOST="j15e201a.p.ssafy.io"
REMOTE_DIR="/opt/backups/local-route/postgres"
SSH_KEY="$HOME/.ssh/backup_to_j15e201a"

DATE="$(date +%Y%m%d)"
DOW="$(date +%u)"  # 1=Mon .. 7=Sun
DATABASES=(app_db airflow_db mlflow_db)

mkdir -p "$BACKUP_DIR"
cd "$COMPOSE_DIR"

for db in "${DATABASES[@]}"; do
    out="$BACKUP_DIR/${db}_daily_${DATE}.dump"
    echo "[backup-postgres] dumping ${db} -> ${out}"
    docker compose --env-file "$ENV_FILE" exec -T postgres \
        pg_dump -U postgres -Fc "$db" > "$out"
done

# 일요일에만 주간 스냅샷도 남긴다
if [ "$DOW" = "7" ]; then
    for db in "${DATABASES[@]}"; do
        cp "$BACKUP_DIR/${db}_daily_${DATE}.dump" "$BACKUP_DIR/${db}_weekly_${DATE}.dump"
    done
    echo "[backup-postgres] weekly snapshot taken (Sunday)"
fi

# 로테이션 — DB별로 daily 는 최근 7개, weekly 는 최근 2개만 남긴다
#
# 🔴 ls 는 매칭되는 파일이 하나도 없으면(예: weekly 스냅샷이 아직 한 번도
#   안 만들어졌을 때) exit code 2 를 낸다. 2>/dev/null 은 에러 "메시지"만
#   지울 뿐 exit code 는 그대로라서, pipefail 때문에 파이프 전체가 실패로
#   처리되고 set -e 가 여기서 스크립트를 조용히 죽인다 (rsync 까지 못 감).
#   그래서 파이프 전체를 || true 로 감싼다 — "지울 게 없으면 그냥 넘어간다"는
#   정상 상황이지 에러가 아니다.
for db in "${DATABASES[@]}"; do
    ls -1t "$BACKUP_DIR/${db}_daily_"*.dump 2>/dev/null | tail -n +8 | xargs -r rm -f || true
    ls -1t "$BACKUP_DIR/${db}_weekly_"*.dump 2>/dev/null | tail -n +3 | xargs -r rm -f || true
done

echo "[backup-postgres] syncing to backup server (${REMOTE_HOST})"
rsync -a --delete \
    -e "ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no" \
    "$BACKUP_DIR"/ "ubuntu@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "[backup-postgres] done"
