#!/usr/bin/env bash
# cron 이 부르는 껍데기 — 아침 점검을 보낸다 (S15P21E201-633)
#
# 🔴 cron 에는 로그인 셸의 환경변수가 **하나도** 없다. 웹훅 주소를 여기서 직접 읽어
#    넘겨야 한다. 이걸 모르면 "손으로 돌리면 되는데 cron 으로는 안 간다" 가 된다.
#
# 🔴 `.env` 를 통째로 읽는다. 그 안에 인증키도 있지만 이 프로세스 밖으로 안 나간다.
#    파일 권한은 600 이다.
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "🔴 .env 가 없습니다. 웹훅 주소를 넣을 자리입니다." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

exec python3 morning-report.py "$@"
