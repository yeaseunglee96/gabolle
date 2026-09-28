"""이동 배율 작업 (S15P21E201-1700) — 규칙은 Spark 없이, 계산은 이 PC 안의 Spark 로(pyspark 가 없으면 건너뛴다)."""
import json
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from framework import Stat, decide  # noqa: E402

try:
    from pyspark.sql import SparkSession
except ImportError:  # pragma: no cover
    SparkSession = None

CHECKS = json.loads((Path(__file__).resolve().parents[1] / 'jobs' / 'travel_multiplier' / 'checks.json')
                    .read_text(encoding='utf-8'))


def one(stat, previous=None):
    [decision] = decide([stat], previous or {}, CHECKS)
    return decision


class TravelDecideTest(unittest.TestCase):

    def test_first_run_moves_at_most_twenty_percent_from_one(self):
        # 배율이 없던 때는 1.0(어림 그대로)이다. 계산 1.34 여도 첫 판은 1.2 까지만.
        d = one(Stat('BUS', 'MEASURED', 1.3378, 80))
        self.assertEqual(('PASSED', 1.2), (d.status, d.value))
        self.assertEqual('폭 제한: 계산 1.34배 → 1.2배 (기본값 1배의 ±20%)', d.note)

    def test_within_twenty_percent_passes_with_two_decimals(self):
        d = one(Stat('BUS', 'MEASURED', 1.3378, 80), {'BUS': 1.2})
        self.assertEqual(('PASSED', 1.34, None), (d.status, d.value, d.note))

    def test_outside_half_to_double_is_held_for_a_person(self):
        self.assertEqual('범위 밖 2.1배 (허용 0.5~2배)', one(Stat('WALK', 'MEASURED', 2.1, 90)).note)
        self.assertEqual('HELD', one(Stat('WALK', 'MEASURED', 0.49, 90)).status)

    def test_too_few_samples_is_held(self):
        self.assertEqual('HELD', one(Stat('PRIVATE_CAR', 'MEASURED', 1.1, 29)).status)


@unittest.skipIf(SparkSession is None, 'pyspark 가 없다 — pip install -r backend/calibration/requirements.txt')
class TravelComputeTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        os.environ.setdefault('PYSPARK_PYTHON', sys.executable)
        cls.spark = (SparkSession.builder.master('local[1]').appName('calibration-travel-test')
                     .config('spark.ui.enabled', 'false').getOrCreate())
        cls.spark.sparkContext.setLogLevel('ERROR')

    @classmethod
    def tearDownClass(cls):
        cls.spark.stop()

    def aggregate(self, rows):
        from jobs.travel_multiplier.compute import aggregate
        df = self.spark.createDataFrame(rows, 'mode string, estimated_minutes double, actual_minutes double')
        return {s.key: s for s in aggregate(df, CHECKS)}

    def test_multiplier_is_total_actual_over_total_estimate_after_dropping_outliers(self):
        # 버스 스무 구간은 어림의 1.2~1.4배. 하나는 어림 10분에 실제 120분(12배 — 중간에 밥을 먹은 것 같은 기록).
        bus = [('BUS', 20.0, 20.0 * (1.2 + 0.01 * i)) for i in range(21)] + [('BUS', 10.0, 120.0)]
        stat = self.aggregate(bus)['BUS']
        self.assertEqual(21, stat.sample_size)
        self.assertAlmostEqual(1.3, stat.value)

    def test_long_legs_weigh_more_than_short_ones(self):
        # 비율 평균이면 (4 + 1) / 2 = 2.5 배. 합으로 나누면 (8 + 30) / (2 + 30) = 1.1875 배.
        rows = [('WALK', 2.0, 8.0), ('WALK', 30.0, 30.0)]
        self.assertAlmostEqual(1.1875, self.aggregate(rows)['WALK'].value)

    def test_modes_are_kept_apart(self):
        rows = [('BUS', 10.0, 12.0), ('WALK', 10.0, 10.0)]
        self.assertEqual({'BUS', 'WALK'}, set(self.aggregate(rows)))


if __name__ == '__main__':
    unittest.main()
