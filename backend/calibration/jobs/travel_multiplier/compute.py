"""이동 작업의 계산 (S15P21E201-1700) — 수단마다 실제 ÷ 어림 배율.

튀는 값: 구간마다 비율(실제 ÷ 어림)을 내고, 그 비율의 사분위 범위(Q1~Q3) iqr_multiplier 배 밖을 버린다.
배율은 남은 구간의 「실제 합 ÷ 어림 합」이다 — 구간 비율의 평균을 내면 2분 어림에 8분 걸린 짧은 구간(4배)이
30분짜리 구간들과 같은 무게가 된다. 합으로 나누면 긴 구간이 더 무겁고, 뜻도 「전체 이동을 몇 배로 잡아야 맞나」가 된다.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from framework import Stat


def aggregate(source: DataFrame, checks: dict) -> list[Stat]:
    k = float(checks['iqr_multiplier'])
    rated = source.withColumn('ratio', F.col('actual_minutes') / F.col('estimated_minutes'))
    quartiles = (rated.groupBy('mode')
                 .agg(F.percentile_approx('ratio', [0.25, 0.75], 10000).alias('q'))
                 .select('mode', F.col('q')[0].alias('q1'), F.col('q')[1].alias('q3')))
    spread = F.col('q3') - F.col('q1')
    kept = (rated.join(quartiles, 'mode')
            .where((F.col('ratio') >= F.col('q1') - k * spread) & (F.col('ratio') <= F.col('q3') + k * spread)))
    rows = (kept.groupBy('mode')
            .agg(F.count('*').alias('n'), F.sum('actual_minutes').alias('actual'),
                 F.sum('estimated_minutes').alias('estimated'))
            .collect())
    return [Stat(key=r['mode'], basis='MEASURED', value=float(r['actual']) / float(r['estimated']), sample_size=int(r['n']))
            for r in sorted(rows, key=lambda r: r['mode'])]
