"""반영할지 정하는 규칙 (S15P21E201-1692) — Spark 없이 돈다.

    python -m unittest discover -s backend/calibration/tests
"""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from framework import Stat, decide  # noqa: E402

CHECKS = json.loads((Path(__file__).resolve().parents[1] / 'jobs' / 'stay_minutes' / 'checks.json')
                    .read_text(encoding='utf-8'))


def one(stat, previous=None):
    [decision] = decide([stat], previous or {}, CHECKS)
    return decision


class DecideTest(unittest.TestCase):

    def test_enough_samples_within_range_and_change_passes_as_is(self):
        d = one(Stat('CAFE_HEALING', 'MEASURED', 49.96, 41))
        self.assertEqual(('PASSED', 50.0, 41, None), (d.status, d.value, d.sample_size, d.note))

    def test_too_few_samples_is_held(self):
        d = one(Stat('CAFE_HEALING', 'MEASURED', 50.0, 29))
        self.assertEqual('HELD', d.status)
        self.assertEqual('표본 부족 29/30', d.note)

    def test_out_of_range_is_held_even_with_many_samples(self):
        self.assertEqual('HELD', one(Stat('SEA_BEACH', 'MEASURED', 181.0, 200)).status)
        self.assertEqual('HELD', one(Stat('CITY', 'MEASURED', 19.9, 200)).status)
        self.assertEqual('PASSED', one(Stat('CITY', 'MEASURED', 20.0, 200), {'CITY': 22.0}).status)

    def test_first_run_moves_at_most_twenty_percent_from_the_default(self):
        # 카페 기본값 45분 → 계산 70분이어도 첫 판은 54분까지만.
        d = one(Stat('CAFE_HEALING', 'MEASURED', 70.0, 60))
        self.assertEqual(('PASSED', 54.0), (d.status, d.value))
        self.assertIn('기본값 45분', d.note)

    def test_later_runs_move_at_most_twenty_percent_from_the_previous_pass(self):
        up = one(Stat('FOOD', 'MEASURED', 100.0, 60), {'FOOD': 70.0})
        down = one(Stat('FOOD', 'MEASURED', 30.0, 60), {'FOOD': 70.0})
        self.assertEqual((84.0, 56.0), (up.value, down.value))
        self.assertIn('직전 판 70분', up.note)

    def test_unknown_category_uses_the_default_baseline(self):
        d = one(Stat('LODGING', 'MEASURED', 120.0, 60))
        self.assertEqual(72.0, d.value)   # 모르는 갈래 기본 60분의 +20%

    def test_estimates_are_stored_for_reference_only(self):
        d = one(Stat('SEA_BEACH', 'ESTIMATED', 250.0, 3))
        self.assertEqual(('REFERENCE', 250.0), (d.status, d.value))


if __name__ == '__main__':
    unittest.main()
