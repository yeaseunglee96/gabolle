"""보정 작업의 공용 틀 (S15P21E201-1692).

작업 하나 = jobs/<이름>/ 폴더 하나: extract.sql(운영 뷰에서 읽을 것) · compute.py(PySpark 계산) · checks.json(검사 기준).
이 틀이 나머지를 한다 — Spark 를 띄우고, 뽑고, 검사하고, 결과 표(calibration_value)에 새 판을 쌓고, 요약을 찍는다.

🔴 판은 쌓기만 한다. 지우지도 고치지도 않는다. 되돌리기는 사람이 그 판을 REVOKED 로 바꾸는 것이다(README.md).
🔴 DB 는 Spark 의 JDBC 로 읽고 쓴다 — 드라이버는 백엔드가 쓰는 것과 같은 PostgreSQL 드라이버다. 파이썬 DB 꾸러미를 더 깔지 않는다.
"""
from __future__ import annotations

import datetime as dt
import importlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent

# 백엔드 부트 jar 에 든 드라이버와 같은 판(postgresql-42.7.13). Spark 가 처음 한 번 Maven 창고에서 받는다.
JDBC_DRIVER = 'org.postgresql:postgresql:42.7.13'

ENV = ('CALIBRATION_DB_URL', 'CALIBRATION_DB_USER', 'CALIBRATION_DB_PASSWORD')


@dataclass(frozen=True)
class Stat:
    """계산 한 줄 — 무엇(key)을 측정/추정(basis)으로 몇 개(sample_size)에서 얼마(value)로 쟀나."""
    key: str
    basis: str
    value: float | None
    sample_size: int


@dataclass(frozen=True)
class Decision:
    key: str
    basis: str
    status: str
    value: float | None
    sample_size: int
    note: str | None


def decide(stats: list[Stat], previous: dict[str, float], checks: dict) -> list[Decision]:
    """계산마다 반영할지 정한다. previous 는 key → 직전 PASSED 값(측정).

    측정값만 반영 후보다. 차례로 본다 — 표본이 모자라면 보류, 범위 밖이면 보류, 직전 값(없으면 기본값)에서 한 번에
    움직이는 폭을 넘으면 폭까지만 반영. 보류는 새 판에 HELD 로 남고 엔진은 그 갈래의 직전 PASSED 를 계속 쓴다.

    단위(unit)와 소수 자리(decimals)는 작업마다 checks.json 에 둔다 — 체류는 「분」·한 자리, 이동 배율은 「배」·두 자리.
    """
    out = []
    ratio = float(checks['max_change_ratio'])
    unit = checks.get('unit', '분')
    decimals = int(checks.get('decimals', 1))
    for s in stats:
        value = None if s.value is None else round(s.value, decimals)
        if s.basis == 'ESTIMATED':
            out.append(Decision(s.key, s.basis, 'REFERENCE', value, s.sample_size, '추정 — 반영 안 함'))
            continue
        if s.sample_size < checks['min_samples']:
            out.append(Decision(s.key, s.basis, 'HELD', value, s.sample_size,
                                '표본 부족 %d/%d' % (s.sample_size, checks['min_samples'])))
            continue
        if not checks['min_value'] <= value <= checks['max_value']:
            out.append(Decision(s.key, s.basis, 'HELD', value, s.sample_size,
                                '범위 밖 %g%s (허용 %g~%g%s)' % (value, unit, checks['min_value'], checks['max_value'], unit)))
            continue
        if s.key in previous:
            base, where = float(previous[s.key]), '직전 판'
        else:
            base, where = float(checks['baseline'].get(s.key, checks['baseline_default'])), '기본값'
        low, high = base * (1 - ratio), base * (1 + ratio)
        if value < low or value > high:
            clamped = round(min(max(value, low), high), decimals)
            out.append(Decision(s.key, s.basis, 'PASSED', clamped, s.sample_size,
                                '폭 제한: 계산 %g%s → %g%s (%s %g%s의 ±%d%%)'
                                % (value, unit, clamped, unit, where, base, unit, round(ratio * 100))))
            continue
        out.append(Decision(s.key, s.basis, 'PASSED', value, s.sample_size, None))
    return out


# ── Spark 와 DB ─────────────────────────────────────────────────────────────

def session():
    from pyspark.sql import SparkSession
    os.environ.setdefault('PYSPARK_PYTHON', sys.executable)
    builder = (SparkSession.builder.master('local[2]').appName('gabolle-calibration')
               .config('spark.driver.memory', '2g')
               .config('spark.sql.session.timeZone', 'UTC')
               .config('spark.ui.enabled', 'false'))
    jar = os.environ.get('CALIBRATION_JDBC_JAR')
    if jar:
        # 윈도 개발 PC 용 — 창고에서 받은 파일을 Spark 가 옮길 때 권한을 바꾸려다(Hadoop winutils) 멈춘다.
        # 이미 받아 둔 jar 를 드라이버에 바로 물리면 옮기지 않는다. 한 대(local)라 드라이버에만 있으면 된다.
        builder = builder.config('spark.driver.extraClassPath', jar)
    else:
        builder = builder.config('spark.jars.packages', JDBC_DRIVER)
    spark = builder.getOrCreate()
    spark.sparkContext.setLogLevel('WARN')
    return spark


def _jdbc():
    return {'url': os.environ['CALIBRATION_DB_URL'], 'user': os.environ['CALIBRATION_DB_USER'],
            'password': os.environ['CALIBRATION_DB_PASSWORD'], 'driver': 'org.postgresql.Driver',
            'currentSchema': os.environ.get('CALIBRATION_DB_SCHEMA', 'gabolle')}


def _without_comments(sql: str) -> str:
    # Spark 는 질의를 괄호로 감싸 부른다. 끝 줄이 주석이면 닫는 괄호까지 주석이 된다.
    return '\n'.join(line for line in sql.splitlines() if not line.lstrip().startswith('--')).strip()


def read(spark, sql: str):
    return spark.read.format('jdbc').options(**_jdbc(), query=_without_comments(sql)).load()


def latest_passed(spark, job: str) -> dict[str, float]:
    rows = read(spark, "SELECT DISTINCT ON (key) key, value FROM calibration_value "
                       "WHERE job = '%s' AND basis = 'MEASURED' AND status = 'PASSED' ORDER BY key, version DESC" % job).collect()
    return {r['key']: float(r['value']) for r in rows}


def next_version(spark, job: str) -> int:
    return int(read(spark, "SELECT COALESCE(MAX(version), 0) + 1 AS v FROM calibration_value WHERE job = '%s'" % job)
               .collect()[0]['v'])


def write(spark, job: str, version: int, window_from: dt.date, window_to: dt.date, decisions: list[Decision],
          commit: str | None):
    from pyspark.sql.types import DateType, DoubleType, IntegerType, StringType, StructField, StructType
    schema = StructType([
        StructField('job', StringType(), False), StructField('version', IntegerType(), False),
        StructField('key', StringType(), False), StructField('basis', StringType(), False),
        StructField('status', StringType(), False), StructField('value', DoubleType(), True),
        StructField('sample_size', IntegerType(), False), StructField('window_from', DateType(), False),
        StructField('window_to', DateType(), False), StructField('note', StringType(), True),
        StructField('source_commit', StringType(), True)])
    rows = [(job, version, d.key, d.basis, d.status, d.value, d.sample_size, window_from, window_to, d.note, commit)
            for d in decisions]
    # 한 조각으로 쓴다 — Spark 는 조각마다 트랜잭션 하나라, 한 판이 반만 들어가는 일이 없다.
    (spark.createDataFrame(rows, schema).coalesce(1).write.format('jdbc')
     .options(**_jdbc(), dbtable='calibration_value').mode('append').save())


def source_commit() -> str | None:
    if os.environ.get('CI_COMMIT_SHA'):
        return os.environ['CI_COMMIT_SHA']
    r = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=HERE, capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None


# ── 한 번 돌리기 ─────────────────────────────────────────────────────────────

def run(job: str, dry_run: bool) -> int:
    if not re.fullmatch(r'[a-z_]+', job) or not (HERE / 'jobs' / job).is_dir():
        print('모르는 작업: %s (jobs/ 아래 폴더 이름)' % job, file=sys.stderr)
        return 2
    missing = [name for name in ENV if not os.environ.get(name)]
    if missing:
        print('환경변수가 없다: %s' % ', '.join(missing), file=sys.stderr)
        return 2
    job_dir = HERE / 'jobs' / job
    checks = json.loads((job_dir / 'checks.json').read_text(encoding='utf-8'))
    window_to = dt.datetime.now(dt.timezone.utc).date()
    window_from = window_to - dt.timedelta(days=int(checks['window_days']))
    extract = (job_dir / 'extract.sql').read_text(encoding='utf-8').replace(':window_from', window_from.isoformat())

    spark = session()
    try:
        compute = importlib.import_module('jobs.%s.compute' % job)
        stats = compute.aggregate(read(spark, extract), checks)
        decisions = decide(stats, latest_passed(spark, job), checks)
        version = next_version(spark, job)
        summary(job, version, window_from, window_to, decisions, dry_run)
        if decisions and not dry_run:
            write(spark, job, version, window_from, window_to, decisions, source_commit())
    finally:
        spark.stop()
    return 0


def summary(job, version, window_from, window_to, decisions, dry_run):
    head = '%s 판 %d · 기간 %s ~ %s%s' % (job, version, window_from, window_to, ' · 시험 삼아(쓰지 않음)' if dry_run else '')
    print(head)
    if not decisions:
        print('  기간 안에 기록이 없다 — 새 판을 쓰지 않는다. 엔진은 지금 판을 그대로 쓴다.')
        return
    for d in decisions:
        print('  %-16s %-9s %-9s %8s  n=%-4d %s' % (d.key, d.basis, d.status, '-' if d.value is None else '%g' % d.value,
                                                   d.sample_size, d.note or ''))
    counts = {s: sum(1 for d in decisions if d.status == s) for s in ('PASSED', 'HELD', 'REFERENCE')}
    print('  반영 %(PASSED)d · 보류 %(HELD)d · 참고 %(REFERENCE)d' % counts)
