"""OCR 이 돌려준 글자 상자들을 «음식 한 줄»로 묶는다 — 모델 없이 규칙만으로.

OCR 모델은 상자마다 글자와 좌표만 준다. 무엇이 음식 이름이고 어느 값이 그 이름의 가격인지는
여기서 정한다. 메뉴판 배치가 둘로 갈린다:

  같은 줄 오른쪽에 가격   「물밀면 ········ 8,000」
  이름 아래에 가격        「마파두부밥 / MAPO TOFU / 11.9」 (이름 → 영어 → 설명 → 가격)

규칙 (위에서부터 먼저 본다)
  1. 한 상자 안에 이름과 가격이 붙어 있으면 그대로 한 줄이다 (「해물순두부찌개9,500」)
  2. 가격 상자는 **같은 높이의 왼쪽 이름**이 있으면 그 이름의 것이다
  3. 없으면 **위쪽에서 가장 가까운 이름**의 것이다. 이때 작은 글씨(설명 줄)는 이름 후보에서 뺀다
     🔴 두 언어 메뉴판에서는 설명 줄이 이름보다 **아주 조금만** 작아(실측 34 대 36픽셀) 높이로 못 가른다.
     대신 진짜 이름 바로 아래에는 영어 줄이 붙어 있다(「우육미엔 / BEEF NOODLE SOUP」). 그런 후보를 먼저 고른다
  4. 이름이 이미 가격을 가졌는데 새 가격이 그 아래로 한 줄 넘게 떨어져 있으면 그 이름의 것이 아니다.
     주인 없는 가격은 «못 읽은 줄»로 센다
  5. 영어 낱말과 가격이 한 상자에 있으면(「RICE 1.5」) 그 자체가 한 줄이다 — 한글 이름(「밥」)을 OCR 이
     놓친 자리다. 위쪽 이름(「볶음밥」)에 붙이면 틀린 값을 자신 있게 보여 준다
  6. 음식 이름에만 사전 보정을 댄다. 안내문(「포장 가능」)에 대면 「초장」으로 바뀐다 — 실측으로 봤다
"""
import json
import os
import re
from dataclasses import dataclass, field

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")

HANGUL = re.compile(r"[가-힣]")
LATIN_WORD = re.compile(r"[A-Za-z]{3,}")
# 8,000 · 11.9(천 원 단위) · 9000(쉼표 없이 00 으로 끝나는 네다섯 자리)
# 🔴 소수 앞에 숫자가 붙어 있으면 가격으로 보지 않는다 — 「330ml 7.0」을 붙여 읽은 「1307.0」에서
#    「07.0」을 뽑으면 틀린 값을 자신 있게 보여 준다. 빈 값이 그보다 낫다
PRICE = re.compile(r"\d{1,3}(?:,\d{3})+|(?<![\d,.])\d{1,2}\.\d(?![\d])|(?<![\d.,])\d{2,3}00(?![\d.,])")
# 가격 옆에 붙는 표식 — 이름에서 떼어 낸다
PRICE_LABELS = re.compile(r"\(\s*\d*\s*p[ce]s?\s*\)|원|[大中小]|(?<![가-힣])[대중소특](?![가-힣])", re.IGNORECASE)
# OCR 이 한자(小·大)를 못 읽고 남긴 빈 괄호 — 「(·)」「()」「(-)」
EMPTY_PAREN = re.compile(r"[(（][^가-힣A-Za-z0-9]*[)）]")

MIN_SCORE = 0.5          # 이보다 낮은 상자는 믿지 않고 «못 읽은 줄»로 센다
SMALL_TEXT = 0.75        # 한글 상자 높이 중앙값의 이 비율보다 작으면 설명 줄로 본다
ABOVE_WINDOW = 6.0       # 가격 위로 몇 줄 안에서 이름을 찾는가
STACK_GAP = 1.5          # 같은 이름의 가격끼리 이만큼 넘게 떨어지면 다른 음식의 것이다
ROW_GAP = 10.0           # 같은 줄 왼쪽 이름과 가격 사이가 글줄 높이의 이 배수를 넘으면 다른 단의 것이다
                         # (합성 메뉴판 실측 약 6배, 바오하우스에서 반대편 단을 잡은 것은 20배 넘게 떨어져 있었다)


@dataclass
class Box:
    text: str
    score: float
    x0: float
    y0: float
    x1: float
    y1: float
    prices: list = field(default_factory=list)
    name_part: str = ""

    @property
    def h(self):
        return max(self.y1 - self.y0, 1.0)

    @property
    def cx(self):
        return (self.x0 + self.x1) / 2

    @property
    def cy(self):
        return (self.y0 + self.y1) / 2


def to_boxes(raw_boxes, txts, scores):
    boxes = []
    for pts, text, score in zip(raw_boxes, txts, scores):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        b = Box(text=text.strip(), score=float(score), x0=min(xs), y0=min(ys), x1=max(xs), y1=max(ys))
        b.prices = PRICE.findall(b.text)
        rest = PRICE.sub(" ", b.text)
        rest = PRICE_LABELS.sub(" ", rest)
        rest = EMPTY_PAREN.sub("", rest)
        b.name_part = " ".join(rest.split()).strip(" /·,.-")
        boxes.append(b)
    return boxes


def has_hangul(s):
    return bool(HANGUL.search(s))


def _median(values):
    v = sorted(values)
    return v[len(v) // 2] if v else 1.0


def _x_overlaps(a, b, slack):
    return a.x0 - slack <= b.x1 and b.x0 - slack <= a.x1


def _is_latin_line(b):
    return not has_hangul(b.text) and not b.prices and bool(LATIN_WORD.search(b.text))


def _same_row(a, b):
    overlap = min(a.y1, b.y1) - max(a.y0, b.y0)
    return overlap >= 0.5 * min(a.h, b.h)


def group(boxes):
    """상자들을 음식 줄과 그 밖의 글자로 나눈다.

    돌려주는 것: (음식 [(이름 상자, [가격 문자열])], 그 밖의 한글 상자, 못 읽은 수)
    """
    trusted = [b for b in boxes if b.score >= MIN_SCORE]
    unread = sum(1 for b in boxes if b.score < MIN_SCORE and (has_hangul(b.text) or b.prices))

    line_h = _median([b.h for b in trusted if has_hangul(b.name_part)])
    small = SMALL_TEXT * line_h

    dishes = {}      # id(이름 상자) → [가격 상자들]
    combined = []    # 규칙 1 — 이름과 가격이 한 상자에
    names = []       # 가격을 받을 수 있는 이름 후보
    price_boxes = []
    others = []
    for b in trusted:
        named = has_hangul(b.name_part)
        if named and b.prices:
            combined.append(b)
        elif b.prices and LATIN_WORD.search(b.name_part):
            combined.append(b)  # 규칙 5 — 「RICE 1.5」
        elif b.prices and not named:
            price_boxes.append(b)
        elif named:
            names.append(b)
            others.append(b)

    owners = [n for n in names if n.h >= small]
    latin = [b for b in trusted if _is_latin_line(b)]

    def english_below(n):
        return any(l.cy > n.cy and l.y0 - n.y1 <= 0.8 * line_h and _x_overlaps(n, l, slack=0) for l in latin)

    for p in sorted(price_boxes, key=lambda b: (b.cy, b.cx)):
        owner = None
        # 규칙 2 — 같은 높이의 왼쪽, 가장 가까운 것
        row = [n for n in owners if _same_row(n, p) and n.x1 <= p.x0 + 0.5 * p.h
               and p.x0 - n.x1 <= ROW_GAP * line_h]
        if row:
            owner = max(row, key=lambda n: n.x1)
        else:
            # 규칙 3 — 위쪽으로 가장 가까운 것 (가로로 겹쳐야 한다)
            above = [n for n in owners
                     if n.cy < p.cy and (p.cy - n.cy) <= ABOVE_WINDOW * line_h
                     and _x_overlaps(n, p, slack=line_h)]
            if above:
                marked = [n for n in above if english_below(n)]
                owner = max(marked or above, key=lambda n: n.cy)
        if owner is None:
            unread += 1
            continue
        taken = dishes.setdefault(id(owner), [])
        # 규칙 4 — 앞 가격과 한 줄 넘게 떨어졌으면 이 이름의 것이 아니다
        if taken and not _same_row(taken[-1], p) and p.y0 - taken[-1].y1 > STACK_GAP * line_h:
            unread += 1
            continue
        taken.append(p)

    food = [(b, list(b.prices)) for b in combined]
    for n in names:
        if id(n) in dishes:
            # 같은 줄의 값은 왼쪽부터 — 「大 38,000 中 32,000」 이 뒤집혀 나오지 않게
            ordered = sorted(dishes[id(n)], key=lambda p: (round(p.cy / line_h), p.x0))
            prices = [t for p in ordered for t in p.prices]
            food.append((n, prices))
    owned = {id(n) for n, _ in food}
    rest = [b for b in others if id(b) not in owned and b.h >= small]
    food.sort(key=lambda item: (item[0].cy, item[0].cx))
    rest.sort(key=lambda b: (b.cy, b.cx))
    return food, rest, unread


# ── 사전 보정 — 닮은 한 글자 ─────────────────────────────────────────────────

def _jamo(ch):
    code = ord(ch) - 0xAC00
    if not 0 <= code < 11172:
        return None
    return code // 588, (code % 588) // 28, code % 28


def _one_jamo_apart(a, b):
    ja, jb = _jamo(a), _jamo(b)
    if ja is None or jb is None:
        return False
    return sum(x != y for x, y in zip(ja, jb)) == 1


class Corrector:
    """자모 하나만 다른 한 글자를 사전 낱말로 되돌린다.

    고치는 조건 — 멀쩡한 이름을 망가뜨리지 않는 쪽으로 좁게 건다.
      1. 한 구간이 사전 낱말과 글자 수가 같고 한 글자만 다르다
      2. 그 글자도 자모(초성·중성·종성) 중 딱 하나만 다르다 — 맥↔액, 볼↔봄, 갈↔걀
      3. 그 구간이 이미 사전 낱말이거나 사전 낱말의 일부면 건드리지 않는다(「애플」은 「애플파이」의 일부)
      4. 후보가 둘 이상이면 고치지 않는다
      5. 한 번 고친 자리는 다시 건드리지 않는다
    """

    def __init__(self, words):
        self.words = {w for w in words if len(w) >= 2}
        self.by_len = {}
        for w in self.words:
            self.by_len.setdefault(len(w), []).append(w)
        self.pieces = {w[i:j] for w in self.words for i in range(len(w)) for j in range(i + 2, len(w) + 1)}

    @classmethod
    def load(cls):
        with open(os.path.join(DATA, "dish-vocab.json"), encoding="utf-8") as f:
            return cls(json.load(f)["words"])

    def correct(self, text):
        s = text
        locked = set()
        for L in sorted(self.by_len, reverse=True):
            i = 0
            while i + L <= len(s):
                window = s[i:i + L]
                if window in self.pieces or locked & set(range(i, i + L)):
                    i += 1
                    continue
                cands = []
                for w in self.by_len[L]:
                    diff = [k for k in range(L) if w[k] != window[k]]
                    if len(diff) == 1 and _one_jamo_apart(w[diff[0]], window[diff[0]]):
                        cands.append(w)
                if len(cands) == 1:
                    s = s[:i] + cands[0] + s[i + L:]
                    locked |= set(range(i, i + L))
                    i += L
                else:
                    i += 1
        return s


# ── 번역 사전 ────────────────────────────────────────────────────────────────

LANGS = ("en", "ja", "zh-Hans", "zh-Hant")


class Dictionary:
    """한식진흥원 800선. 사전에 없으면 빈 문자열 — 번역은 부르는 쪽(백엔드)이 채운다."""

    def __init__(self, items):
        self.items = items

    @classmethod
    def load(cls):
        with open(os.path.join(DATA, "hansik800.json"), encoding="utf-8") as f:
            return cls(json.load(f)["items"])

    def lookup(self, name, language):
        if language not in LANGS:
            return ""
        key = "".join(name.split())
        entry = self.items.get(key) or self.items.get(re.sub(r"\(.*?\)", "", key))
        return (entry or {}).get(language, "")


def build_lines(boxes, language, corrector, dictionary):
    """메뉴판 한 장의 결과. 백엔드의 MenuScanResponse.Line 과 칸 이름이 같다."""
    food, rest, unread = group(boxes)
    lines = []
    for box, prices in food:
        name = corrector.correct(box.name_part.replace(" ", "")) if " " not in box.name_part \
            else " ".join(corrector.correct(w) for w in box.name_part.split())
        price = " / ".join(prices)
        translated = name if language in (None, "", "ko") else dictionary.lookup(name, language)
        lines.append({
            "text": f"{name} {price}".strip(),
            "name": name,
            "price": price,
            "translatedName": translated,
            "translationSource": "dictionary" if translated and language in LANGS else None,
        })
    for box in rest:
        lines.append({"text": box.text, "name": "", "price": "", "translatedName": "", "translationSource": None})
    return lines, unread
