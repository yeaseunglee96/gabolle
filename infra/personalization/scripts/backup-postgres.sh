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
#
# 🔴 2026-09-09 (S15P21E201-771) 설문 응답 DB를 백업 대상에 넣었다.
#
#   그때까지 설문 응답(survey)은 **아무 백업에도 안 들어 있었다.** 이 스크립트는
#   personalization compose 의 postgres 컨테이너만 떴고, 설문은 다른 compose
#   프로젝트(~/survey-recommend/compose.yaml)의 다른 컨테이너다. 계정도
#   postgres 가 아니라 survey 다. 그래서 위 세 DB 와 같은 방식으로는 못 뜬다.
#
#   설문 응답은 사람이 3분 써서 준 답이라 **다시 만들 수 없다.** 그래서 별도
#   단계로 두고, 파일 이름만 같은 규칙(survey_daily_*.dump)을 따르게 해서
#   로테이션과 rsync 는 기존 코드를 그대로 타게 했다.
#
#   🔴 설문 덤프가 실패해도 나머지 백업을 죽이지 않게 만들었다. 이 스크립트는
#      set -e 가 걸려 있어서, 설문 컨테이너가 안 떠 있는 날 여기서 멈추면
#      app_db·airflow_db·mlflow_db 의 로테이션과 rsync 까지 같이 안 돈다.
#      새 백업 하나를 얻으려고 있던 백업 셋을 잃는 것은 고치려던 것보다 나쁘다.

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

# 설문 응답 DB — 별도 compose 프로젝트의 컨테이너. survey-recommend/README.md 참고.
SURVEY_CONTAINER="survey-postgres"
SURVEY_DB="survey"
SURVEY_USER="survey"

# 로테이션과 주간 스냅샷이 훑을 이름 목록 (설문까지 포함)
ALL_NAMES=("${DATABASES[@]}" "$SURVEY_DB")

mkdir -p "$BACKUP_DIR"
cd "$COMPOSE_DIR"

for db in "${DATABASES[@]}"; do
    out="$BACKUP_DIR/${db}_daily_${DATE}.dump"
    echo "[backup-postgres] dumping ${db} -> ${out}"
    docker compose --env-file "$ENV_FILE" exec -T postgres \
        pg_dump -U postgres -Fc "$db" > "$out"
done

# ── 설문 응답 DB ────────────────────────────────────────────────────────────
#
# 🔴 실패해도 스크립트를 죽이지 않는다. 위 세 DB 덤프가 이미 끝난 뒤이므로,
#    여기서 set -e 로 죽으면 로테이션과 rsync 를 못 해 그 셋이 전송되지 않는다.
survey_out="$BACKUP_DIR/${SURVEY_DB}_daily_${DATE}.dump"
if docker ps --format '{{.Names}}' | grep -qx "$SURVEY_CONTAINER"; then
    echo "[backup-postgres] dumping ${SURVEY_DB} (${SURVEY_CONTAINER}) -> ${survey_out}"
    if docker exec -i "$SURVEY_CONTAINER" \
            pg_dump -U "$SURVEY_USER" -Fc "$SURVEY_DB" > "$survey_out"; then
        # 🔴 빈 파일을 남기지 않는다. pg_dump 가 실패하면 리다이렉트가 이미
        #    0바이트 파일을 만들어 두고, 그게 로테이션에서 "최근 백업" 한 자리를
        #    차지해 진짜 백업 하나를 밀어낸다.
        if [ ! -s "$survey_out" ]; then
            echo "[backup-postgres] 🔴 ${SURVEY_DB} 덤프가 빈 파일입니다 — 지웁니다"
            rm -f "$survey_out"
        fi
    else
        echo "[backup-postgres] 🔴 ${SURVEY_DB} 덤프 실패 — 나머지 백업은 계속합니다"
        rm -f "$survey_out"
    fi
else
    echo "[backup-postgres] ${SURVEY_CONTAINER} 가 안 떠 있습니다 — 설문 백업을 건너뜁니다"
fi

# 일요일에만 주간 스냅샷도 남긴다
if [ "$DOW" = "7" ]; then
    for db in "${ALL_NAMES[@]}"; do
        # 🔴 설문은 컨테이너가 안 떠 있으면 그날 daily 가 없다. 없는 것을 cp 하면
        #    set -e 가 스크립트를 죽인다 — 있을 때만 뜬다.
        [ -f "$BACKUP_DIR/${db}_daily_${DATE}.dump" ] || continue
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
for db in "${ALL_NAMES[@]}"; do
    ls -1t "$BACKUP_DIR/${db}_daily_"*.dump 2>/dev/null | tail -n +8 | xargs -r rm -f || true
    ls -1t "$BACKUP_DIR/${db}_weekly_"*.dump 2>/dev/null | tail -n +3 | xargs -r rm -f || true
done

echo "[backup-postgres] syncing to backup server (${REMOTE_HOST})"
rsync -a --delete \
    -e "ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no" \
    "$BACKUP_DIR"/ "ubuntu@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "[backup-postgres] done"
