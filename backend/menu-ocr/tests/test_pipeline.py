"""짝짓기·보정·사전 시험 — 모델 없이 돈다. 판정은 종료 코드다.

    python -m unittest discover -s tests -v

기준 숫자는 2026-09-23 운영 서버 실측에서 왔다 (S15P21E201-1538). 규칙을 고쳐 이 숫자가 내려가면
그 고침은 되돌린다 — 올라가면 기준을 올린다.
"""
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "app"))

from pipeline import PRICE, Corrector, Dictionary, build_lines, to_boxes  # noqa: E402

BAOHAUS = {
    "클래식바오": ["5.0"], "새우바오": ["5.0"], "루로우판": ["7.0", "11.9"], "볶음밥": ["9.0"], "밥": ["1.5"],
    "마파두부밥": ["11.9"], "마파두부&볶음밥": ["15.9"], "어향가지튀김": ["9.0", "14.0"],
    "토마토달걀볶음": ["10.0"], "우육미엔": ["11.9"], "딴딴미엔": ["11.9"], "바오맥주": ["7.5"],
    "애플시나몬소다": ["6.0"], "바오하이볼": ["8.0"], "토닉하이볼": ["8.0"], "진저하이볼": ["8.0"],
    "대만맥주": ["8.0"], "대만망고비어": ["7.0"], "콜라/사이다": ["2.5"],
}


def fixture(name):
    with open(os.path.join(HERE, "fixtures", name), encoding="utf-8") as f:
        return json.load(f)


def norm(s):
    return re.sub(r"\(.*?\)", "", "".join(s.split()))


def read(fixture_name, language="en"):
    r = fixture(fixture_name)
    lines, unread = build_lines(to_boxes(r["boxes"], r["txts"], r["scores"]), language,
                                Corrector.load(), Dictionary.load())
    food = {norm(l["name"]): l for l in lines if l["name"]}
    return lines, food, unread


class PriceTest(unittest.TestCase):

    def test_reads_the_price_shapes_seen_on_real_menus(self):
        cases = {
            "8,000": ["8,000"], "11.9": ["11.9"], "12000원": ["12000"],
            "34,00029,000 24,000": ["34,000", "29,000", "24,000"],  # 大中小 를 못 읽고 붙은 것
            "9.0()/14.0()": ["9.0", "14.0"], "(pe) 5.0": ["5.0"], "600w8.0": ["8.0"],
        }
        for text, want in cases.items():
            self.assertEqual(PRICE.findall(text), want, text)

    def test_does_not_invent_a_price_from_a_merged_number(self):
        # 「330ml 7.0」 을 붙여 읽은 것 — 07.0 을 뽑으면 틀린 값을 자신 있게 보여 준다
        self.assertEqual(PRICE.findall("1307.0"), [])
        self.assertEqual(PRICE.findall("2026"), [])


class CorrectorTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.c = Corrector.load()

    def test_fixes_one_jamo_misreads(self):
        self.assertEqual(self.c.correct("대만액주"), "대만맥주")
        self.assertEqual(self.c.correct("바오하이봄"), "바오하이볼")
        self.assertEqual(self.c.correct("볶을밥"), "볶음밥")
        self.assertEqual(self.c.correct("떡만듯국"), "떡만둣국")

    def test_leaves_real_words_alone(self):
        # 처음 판은 이것들을 망가뜨렸다 — 애플→와플, 우육→수육, 하이볼→파이볼
        for word in ("애플시나몬소다", "우육미엔", "마파두부밥", "새우바오", "바오하이볼"):
            self.assertEqual(self.c.correct(word), word)


class GroupingTest(unittest.TestCase):

    def test_synthetic_menu_same_row_layout(self):
        truth = fixture("synthetic-truth.json")["dishes"]
        _, food, _ = read("synthetic-photo.ocr.json")
        pairs = [n for n in truth if n in food and food[n]["price"].split(" / ") == truth[n]]
        self.assertGreaterEqual(len(pairs), 29, f"합성 메뉴판 짝 {len(pairs)}/30")

    def test_real_menu_stacked_layout(self):
        _, food, _ = read("baohaus-2x.ocr.json")
        pairs = [n for n in BAOHAUS if n in food and food[n]["price"].split(" / ") == BAOHAUS[n]]
        self.assertGreaterEqual(len(pairs), 12, f"실사진 짝 {len(pairs)}/19 — {pairs}")

    def test_never_attaches_a_wrong_price_to_a_right_name(self):
        # 틀린 값을 자신 있게 보여 주는 것이 빈 값보다 나쁘다
        _, food, _ = read("baohaus-2x.ocr.json")
        wrong = {n: food[n]["price"] for n in BAOHAUS if n in food and food[n]["price"].split(" / ") != BAOHAUS[n]}
        self.assertEqual(wrong, {})

    def test_correction_touches_only_dish_names(self):
        # 안내문 「24시간 영업·포장 가능」 의 「포장」 을 「초장」 으로 바꾸던 것
        lines, _, _ = read("synthetic-photo.ocr.json")
        notices = [l["text"] for l in lines if not l["name"]]
        self.assertTrue(any("포장" in t for t in notices), notices)
        self.assertFalse(any("초장" in l["text"] for l in lines))


class DictionaryTest(unittest.TestCase):

    def test_translates_known_dishes_and_leaves_unknown_blank(self):
        d = Dictionary.load()
        self.assertTrue(d.lookup("돼지국밥", "en"))
        self.assertTrue(d.lookup("돼지국밥", "zh-Hant"))
        self.assertEqual(d.lookup("딴딴미엔", "en"), "")  # 사전에 없으면 백엔드가 GMS 로 채운다
        self.assertEqual(d.lookup("돼지국밥", "fr"), "")

    def test_korean_request_keeps_the_name(self):
        _, food, _ = read("synthetic-photo.ocr.json", language="ko")
        self.assertEqual(food["돼지국밥"]["translatedName"], "돼지국밥")


if __name__ == "__main__":
    unittest.main()
