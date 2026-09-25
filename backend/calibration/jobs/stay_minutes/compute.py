"""체류 작업의 계산 (S15P21E201-1692) — 갈래 · 측정/추정마다 튀는 값을 버리고 평균을 낸다.

튀는 값: 사분위 범위(Q1~Q3)의 iqr_multiplier 배 밖. 휴대폰을 두고 나왔거나 출발을 다음 날 누른 것 같은 기록이
평균을 끌고 가지 않게 한다. 사분위는 버리기 전 전체로 잰다.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from framework import Stat


def aggregate(source: DataFrame, checks: dict) -> list[Stat]:
    k = float(checks['iqr_multiplier'])
    quartiles = (source.groupBy('category', 'source')
                 .agg(F.percentile_approx('stay_minutes', [0.25, 0.75], 10000).alias('q'))
                 .select('category', 'source', F.col('q')[0].alias('q1'), F.col('q')[1].alias('q3')))
    spread = F.col('q3') - F.col('q1')
    kept = (source.join(quartiles, ['category', 'source'])
            .where((F.col('stay_minutes') >= F.col('q1') - k * spread)
                   & (F.col('stay_minutes') <= F.col('q3') + k * spread)))
    rows = (kept.groupBy('category', 'source')
            .agg(F.count('*').alias('n'), F.avg('stay_minutes').alias('mean'))
            .collect())
    return [Stat(key=r['category'], basis=r['source'], value=float(r['mean']), sample_size=int(r['n']))
            for r in sorted(rows, key=lambda r: (r['category'], r['source']))]
