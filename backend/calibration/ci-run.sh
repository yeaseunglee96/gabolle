#!/usr/bin/env bash
# S15P21E201-1750 — 보정 계산 한 번. CI 예약 잡(ci/parts/backend.yml 의 calibration:run)과 이 PC 로컬 리허설이
# **같은 이 파일**을 돌린다 — 리허설에서 잰 것이 운영 예약에서도 그대로이게.
#
# 이미지: eclipse-temurin:17-jdk (PySpark 4 는 자바 17 이상). 파이썬 · ssh 는 여기서 깐다. root 로 돈다.
#
# 받는 환경변수
#   CALIBRATION_DB_URL · CALIBRATION_DB_USER · CALIBRATION_DB_PASSWORD   DB (framework.py 가 읽는다)
#   CALIB_SSH_KEY_B64 · CALIB_SSH_KNOWN_HOSTS · CALIB_SSH_TARGET          주면 SSH 터널을 먼저 연다(운영).
#                                                                          로컬 리허설은 안 준다.
#   CALIB_CACHE_DIR                                                        pip · JDBC 드라이버를 둘 곳(CI 캐시)
#
# 🔴 비밀값을 화면에 안 남긴다 — set -x 를 쓰지 않고, 열쇠는 임시 폴더(700)에 풀었다가 끝나면 지운다.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
CACHE=${CALIB_CACHE_DIR:-$HERE/.ci-cache}
JDBC_VERSION=42.7.13
# Maven Central 원본과 sha1 이 같은 파일(a6e1bd21…)의 sha256 — 원본은 sha256 을 따로 안 낸다. 다르면 받은 뒤 멈춘다.
JDBC_SHA256=6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93
START=$(date +%s)
phase() { echo "── $1 · 시작부터 $(( $(date +%s) - START ))초"; }

apt-get update -qq
apt-get install -y -qq --no-install-recommends python3 python3-venv openssh-client ca-certificates curl >/dev/null
phase "apt(파이썬 · ssh)"

python3 -m venv /opt/calib-venv
/opt/calib-venv/bin/pip install -q --disable-pip-version-check --cache-dir "$CACHE/pip" -r "$HERE/requirements.txt"
phase "pip(pyspark)"

mkdir -p "$CACHE"
JAR="$CACHE/postgresql-$JDBC_VERSION.jar"
if [ ! -f "$JAR" ] || ! echo "$JDBC_SHA256  $JAR" | sha256sum -c --quiet 2>/dev/null; then
  curl -fsSL -o "$JAR.part" "https://repo1.maven.org/maven2/org/postgresql/postgresql/$JDBC_VERSION/postgresql-$JDBC_VERSION.jar"
  echo "$JDBC_SHA256  $JAR.part" | sha256sum -c --quiet
  mv "$JAR.part" "$JAR"
fi
echo "JDBC 드라이버 $(stat -c %s "$JAR") 바이트 · 지문 맞음"
phase "JDBC 드라이버"
# 받아 둔 jar 를 드라이버에 바로 물린다 — spark.jars.packages 로 매번 창고를 뒤지지 않는다(framework.session).
export CALIBRATION_JDBC_JAR="$JAR"

if [ -n "${CALIB_SSH_KEY_B64:-}" ]; then
  KEYDIR=$(mktemp -d)
  chmod 700 "$KEYDIR"
  trap 'kill "${TUNNEL_PID:-0}" 2>/dev/null || true; rm -rf "$KEYDIR"' EXIT
  printf '%s' "$CALIB_SSH_KEY_B64" | base64 -d > "$KEYDIR/id"
  chmod 600 "$KEYDIR/id"
  printf '%s\n' "$CALIB_SSH_KNOWN_HOSTS" > "$KEYDIR/known_hosts"
  # 운영 쪽 열쇠는 포트 넘기기(127.0.0.1:15432)만 된다 — 셸도 다른 포트도 안 열린다(backend/calibration/README.md).
  ssh -i "$KEYDIR/id" -o UserKnownHostsFile="$KEYDIR/known_hosts" -o StrictHostKeyChecking=yes -o BatchMode=yes \
      -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -N -L 15432:127.0.0.1:15432 "$CALIB_SSH_TARGET" &
  TUNNEL_PID=$!
  for _ in $(seq 1 30); do
    if (echo > /dev/tcp/127.0.0.1/15432) 2>/dev/null; then break; fi
    kill -0 "$TUNNEL_PID" 2>/dev/null || { echo "터널이 열리기 전에 끝났다"; exit 1; }
    sleep 1
  done
  phase "SSH 터널"
fi

cd "$HERE"
for job in stay_minutes travel_multiplier; do
  /opt/calib-venv/bin/python run.py "$job"
  phase "$job"
done
