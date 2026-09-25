"""체류 계산 (S15P21E201-1692) — Spark 를 이 PC 안에서 띄워 돈다. pyspark 가 없으면 건너뛴다(건너뛴 수가 찍힌다)."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    from pyspark.sql import SparkSession
except ImportError:  # pragma: no cover
    SparkSession = None

CHECKS = json.loads((Path(__file__).resolve().parents[1] / 'jobs' / 'stay_minutes' / 'checks.json')
                    .read_text(encoding='utf-8'))


@unittest.skipIf(SparkSession is None, 'pyspark 가 없다 — pip install -r backend/calibration/requirements.txt')
class ComputeTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        import os
        os.environ.setdefault('PYSPARK_PYTHON', sys.executable)
        cls.spark = (SparkSession.builder.master('local[1]').appName('calibration-test')
                     .config('spark.ui.enabled', 'false').getOrCreate())
        cls.spark.sparkContext.setLogLevel('ERROR')

    @classmethod
    def tearDownClass(cls):
        cls.spark.stop()

    def aggregate(self, rows):
        from jobs.stay_minutes.compute import aggregate
        df = self.spark.createDataFrame(rows, 'category string, source string, stay_minutes double')
        return {(s.key, s.basis): s for s in aggregate(df, CHECKS)}

    def test_outliers_beyond_one_and_a_half_quartile_ranges_are_dropped(self):
        # 카페 40~60분 스물 + 휴대폰을 두고 나온 듯한 600분 하나. 600분이 남으면 평균이 77분이 된다.
        cafe = [('CAFE_HEALING', 'MEASURED', 40.0 + i) for i in range(21)] + [('CAFE_HEALING', 'MEASURED', 600.0)]
        stat = self.aggregate(cafe)[('CAFE_HEALING', 'MEASURED')]
        self.assertEqual(21, stat.sample_size)
        self.assertAlmostEqual(50.0, stat.value)

    def test_categories_and_sources_are_kept_apart(self):
        rows = [('FOOD', 'MEASURED', 60.0), ('FOOD', 'MEASURED', 70.0), ('FOOD', 'ESTIMATED', 200.0),
                ('SEA_BEACH', 'MEASURED', 90.0)]
        stats = self.aggregate(rows)
        self.assertEqual({('FOOD', 'MEASURED'), ('FOOD', 'ESTIMATED'), ('SEA_BEACH', 'MEASURED')}, set(stats))
        self.assertEqual((2, 65.0), (stats[('FOOD', 'MEASURED')].sample_size, stats[('FOOD', 'MEASURED')].value))
        self.assertEqual(200.0, stats[('FOOD', 'ESTIMATED')].value)

    def test_no_rows_gives_no_stats(self):
        self.assertEqual({}, self.aggregate([]))


if __name__ == '__main__':
    unittest.main()
