#!/usr/bin/env python3
"""
V-World **GIS건물통합정보**(부산 전역) SHP+DBF → 우리 NDJSON 스키마.

왜 이게 있나:
  지금까지 건물 높이는 OSM 태그로만 알았고 `height` 보유율이 **2.1%** 였다
  (data/raw/pbf/_building-summary.json — 52,556채 중 1,098채).
  그래서 process/shadow.mjs 는 층수에 **층당 2.8m 를 일괄로 곱했고**, 그 파일
  주석에 *"입력 스키마에 용도를 가를 태그가 아예 없다"* 고 적혀 있다.

  이 파일에는 **실측 높이와 건축물용도명이 둘 다** 들어 있다. 그래서 층당 높이를
  손으로 정하는 대신 **데이터가 정하게 만들 수 있다** —
  그 계산은 process/height-calibrate.mjs 가 한다. 여기는 변환만 한다.

입력  data/raw/building/AL_D010_26_20260809.{shp,dbf,shx,prj}
  레코드 472,608건 · 좌표계 **EPSG:5186**(Korea 2000 중부원점 TM — 경도 127°를
  기준선으로 삼아 미터로 펴 놓은 평면 좌표) · 문자 인코딩 **CP949**(UTF-8 아니다).
  라이선스 **CC BY** — 출처: 국토교통부 GIS건물통합정보 (V-World).

출력  data/raw/building/gis-building.ndjson  — 한 줄에 객체 하나(NDJSON):
  {"id":…,"topic":"building","levels":…,"heightTag":…,"areaM2":…,"ring":[[lat,lon],…],
   "useName":…,"useCode":…,"structName":…,"grossAreaM2":…,"approvedAt":…,
   "basement":…,"pnu":…}

  🔴 앞의 6개 키는 data/raw/pbf/building.ndjson 과 **이름·의미가 같다.**
     process/shadow.mjs 가 그 스키마를 그대로 읽으므로 바꾸지 않는다.
     뒤의 7개가 새로 생긴 것이고, 그중 useName 이 이 작업의 목적이다.

  요약  data/raw/building/_gis-building-summary.json
        (collect/pbf_buildings.py 의 _building-summary.json 과 같은 형식)

필요한 것 (requirements.txt 는 건드리지 않았다 — 손으로 설치한다):
  py -m pip install pyshp pyproj

실행:
  py collect/shp_buildings.py
  py collect/shp_buildings.py --limit 5000        # 표본만 (검증용)
  py collect/shp_buildings.py --shp <경로> --out <폴더>
"""
import argparse, json, math, os, sys, time
from collections import Counter

import shapefile          # pyshp — SHP/DBF 를 레코드 단위로 흘려 읽는다
import pyproj             # 좌표계 변환 (PROJ 바인딩)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

DEFAULT_SHP = os.path.join(ROOT, 'data/raw/building/AL_D010_26_20260809.shp')

# ── 컬럼 이름 ────────────────────────────────────────────────────────────────
# 🔴 이 파일의 DBF 컬럼 이름은 `A0`~`A28` 뿐이고, **배포된 컬럼정의서와 실제
#    파일이 어긋난다.** 아래는 실제 레코드로 대조해 확정한 것이다.
#    정의서를 다시 해석하지 말고 이 표를 쓴다.
#
#    A0  원천도형ID          A10 건축물구조코드     A20 위반건축물여부
#    A1  GIS건물통합식별번호  A11 건축물구조명       A21 참조체계연계키
#    A2  고유번호(PNU)        A12 건축물면적(㎡)     A22 데이터기준일자
#    A3  법정동코드           A13 사용승인일자       A23 원천시도시군구코드
#    A4  법정동명             A14 연면적             A24 건물명
#    A5  지번                 A15 대지면적(㎡)       A25 건물동명
#    A6  특수지코드           A16 높이(m)  ★        A26 지상층_수 ★
#    A7  특수지구분명         A17 건폐율(%)          A27 지하층_수
#    A8  건축물용도코드       A18 용적율(%)          A28 데이터생성변경일자
#    A9  건축물용도명 ★       A19 건축물ID
F = dict(pnu='A2', dongName='A4', useCode='A8', useName='A9', structName='A11',
         approvedAt='A13', grossArea='A14', height='A16',
         levels='A26', basement='A27')

# 🔴 **id 는 SHP 의 레코드 순번(1부터)에 아래 값을 더한 것이다.**
#    왜 원천 컬럼을 쓰지 않았나 — 실측(472,608건 전수):
#      A0 원천도형ID          서로 다른 값이 63,720개뿐이다. **중복 키다**
#      A1 GIS건물통합식별번호  28자리 숫자 문자열이라 JS 정수(2^53)를 넘는다
#    그래서 순번을 쓴다. 같은 파일이면 같은 순번이 나오므로 재현된다
#    (그 파일의 sha256 은 _run.json 에 남는다 — mlops/manifest.mjs).
#
#    🔴 왜 그냥 1,2,3… 이 아닌가: OSM 쪽 산출물(data/raw/pbf/building.ndjson)의
#    id 가 OSM way id(작은 값부터 14억까지)다. 두 파일을 하나의 Map 에 넣는
#    코드가 나중에 생기면 **말없이 건물이 사라진다.** 겹치지 않는 자리로
#    옮겨 둔다 (26 = 부산 시도코드, 2^53 보다 한참 아래다).
ID_BASE = 26_000_000_000

# ring 좌표 상한. collect/pbf_buildings.py 와 같은 값을 쓴다 — shadow.mjs 가 두
# 파일을 같은 규칙으로 읽어야 한다. 🔴 면적은 **솎기 전 원본 ring** 으로 잰다.
MAX_RING_PTS = 64

# 좌표 소수 자리. 6자리 ≈ 0.11 m — 건물 외곽선에 충분하다.
COORD_DIGITS = 6

# 첫 레코드 검증 — 좌표 변환이 조용히 틀리는 것을 여기서 잡는다.
# 🔴 변환이 틀려도 숫자는 그럴듯해 보인다. 축이 바뀌면 위도 129 가 나오고,
#    원점이 다르면 수백 km 옆에 떨어진다. 둘 다 "그림자가 전부 엉뚱한 곳에
#    생긴다" 로 끝나는데, 파일만 보면 정상으로 보인다.
PROBE_DONG   = '보수동1가'    # 첫 레코드의 법정동명에 이것이 들어 있어야 한다
PROBE_LAT    = 35.10          # 부산 중구
PROBE_LON    = 129.02
PROBE_TOL    = 0.15           # 도. 약 15 km — 구 하나 크기

# bbox 적중률 하한. 이 파일은 부산 전역이고 config/area.json 의 target bbox 는
# 부산 실면적의 4배(바다·김해·양산 일부 포함)라, 정상이면 100% 에 가깝다.
# 크게 미달하면 좌표계나 파일이 우리가 생각하는 그것이 아니다.
MIN_BBOX_HIT = 0.95


# ── 값 파싱 ──────────────────────────────────────────────────────────────────
# 🔴 이 파일에서 **높이·대지면적·건폐율·용적율의 0 은 "없음"** 이다.
#    실측: 472,608건 중 높이가 0 인 것이 323,371건(68.4%)이고, 표본 2번은
#    단독주택인데 대지면적·높이·건폐율·용적율이 **전부 0** 이었다.
#    0 을 실제 높이 0m 로 쓰면 "높이 0m 건물 수십만 채" 가 생기고, shadow.mjs 는
#    그것을 그림자를 만들지 않는 건물로 취급한다 — **그림자가 통째로 사라진다.**
#    그래서 전부 None 으로 만든다. 0 이 아니다.

def num_or_none(v, lo, hi):
    """숫자이고 lo < v <= hi 일 때만 실수. 그 밖(0·빈칸·None·오타)은 None."""
    if v is None:
        return None
    try:
        f = float(v)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(f) or f <= lo or f > hi:
        return None
    return f


def int_or_none(v, lo, hi):
    """정수이고 lo < v <= hi 일 때만 정수. 그 밖은 None."""
    f = num_or_none(v, lo, hi)
    return None if f is None else int(round(f))


def text_or_none(v):
    """빈 문자열은 None 으로. 🔴 '' 와 None 을 같은 것으로 취급한다 —
    둘 다 "적혀 있지 않다" 는 뜻이고, 구별해도 쓸 데가 없다."""
    if v is None:
        return None
    s = str(v).strip()
    return s or None


# ── 도형 ─────────────────────────────────────────────────────────────────────
# 🔴 EPSG:5186 은 이미 **미터 평면 좌표**다. 그래서 면적을 위경도로 바꾸기
#    **전에** 신발끈 공식으로 잰다 — collect/pbf_buildings.py 가 위경도에서
#    도→m 환산 계수를 구해 하던 일을 여기서는 안 해도 된다.
#    (TM 투영이라 기준선 127°에서 멀어지면 배율이 커진다. 부산 동쪽 끝
#     129.3°에서 면적이 약 0.09% 부푼다 — 건물 크기에서 무시할 만하다.)

def signed_area(pts):
    """신발끈 부호 있는 면적. ESRI SHP 규약: 외곽선은 시계방향(음수),
    구멍은 반시계방향(양수)이다."""
    s = 0.0
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        s += x1 * y2 - x2 * y1
    return s / 2.0


def split_rings(shape):
    """SHP 폴리곤을 (외곽선들, 구멍들) 로 가른다. 방향으로 판정한다."""
    pts = shape.points
    starts = list(shape.parts) or [0]
    bounds = starts + [len(pts)]
    outers, inners = [], []
    for i in range(len(starts)):
        ring = pts[bounds[i]:bounds[i + 1]]
        if len(ring) < 4:            # SHP 의 ring 은 첫 점을 끝에 한 번 더 적는다
            continue
        a = signed_area(ring)
        if a == 0:
            continue
        (inners if a > 0 else outers).append((ring, abs(a)))
    return outers, inners


def decimate(pts, limit):
    """점이 너무 많은 ring 을 균등하게 솎는다. 끝점을 남겨 모양을 지킨다.
    (collect/pbf_buildings.py 의 같은 함수와 동일한 규칙)"""
    if len(pts) <= limit:
        return pts, False
    step = len(pts) / (limit - 1)
    out = [pts[min(int(i * step), len(pts) - 1)] for i in range(limit - 1)]
    out.append(pts[-1])
    return out, True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--shp', default=DEFAULT_SHP)
    ap.add_argument('--out', default=os.path.join(ROOT, 'data/raw/building'))
    ap.add_argument('--limit', type=int, default=0, help='0 이면 전부')
    args = ap.parse_args()

    base = args.shp[:-4] if args.shp.lower().endswith('.shp') else args.shp
    missing = [base + ext for ext in ('.shp', '.dbf', '.shx')
               if not os.path.exists(base + ext)]
    if missing:
        # 🔴 입력이 없으면 조용히 통과하지 않는다.
        sys.stderr.write('SHP 세트가 없습니다:\n')
        for m in missing:
            sys.stderr.write(f'  {m}\n')
        sys.stderr.write('  V-World 에서 GIS건물통합정보(부산)를 받아 풀어 두세요\n')
        sys.exit(2)

    area_cfg = json.load(open(os.path.join(ROOT, 'config/area.json'), encoding='utf-8'))
    b = area_cfg['target']['bbox']
    S, W, N, E = b['south'], b['west'], b['north'], b['east']

    os.makedirs(args.out, exist_ok=True)
    out_path = os.path.join(args.out, 'gis-building.ndjson')

    # EPSG:5186 → EPSG:4326(WGS84 위경도). always_xy=True 로 축 순서를
    # (동쪽, 북쪽) → (경도, 위도) 로 고정한다 — EPSG 정의상의 축 순서에
    # 끌려다니지 않게 하는 것이다. 축이 뒤집히면 모든 건물이 남태평양에 간다.
    tr = pyproj.Transformer.from_crs('EPSG:5186', 'EPSG:4326', always_xy=True)

    r = shapefile.Reader(base, encoding='cp949')
    total = len(r)
    print(f"입력: {base}.shp  레코드 {total:,}건  shapeType={r.shapeType}")
    print(f"원본 bbox(EPSG:5186): {r.bbox}")
    print(f"대상 bbox(WGS84)    : S={S} W={W} N={N} E={E}  ({area_cfg['target']['name']})")

    t0 = time.time()
    st = dict(seen=0, kept=0, skippedNoGeom=0, skippedZeroArea=0,
              ringDecimated=0, holesSubtracted=0, multiOuter=0,
              withHeight=0, withLevels=0, withBoth=0, withUseName=0,
              heightZeroed=0, levelsZeroed=0, insideBbox=0, outsideBbox=0)
    uses = Counter()
    probe = None

    with open(out_path, 'w', encoding='utf-8') as fo:
        for sr in r.iterShapeRecords():
            st['seen'] += 1
            if args.limit and st['seen'] > args.limit:
                st['seen'] -= 1
                break
            if st['seen'] % 100_000 == 0:
                print(f"  {st['seen']:,} / {total:,} 훑음 · 채택 {st['kept']:,} "
                      f"({time.time() - t0:.0f}s)", flush=True)

            rec, shape = sr.record, sr.shape
            outers, inners = split_rings(shape)
            if not outers:
                st['skippedNoGeom'] += 1
                continue
            if len(outers) > 1:
                st['multiOuter'] += 1

            # 🔴 면적은 **솎기 전 원본 ring** 으로, **투영 좌표(미터)** 에서 잰다.
            area_m2 = sum(a for _, a in outers) - sum(a for _, a in inners)
            if inners:
                st['holesSubtracted'] += 1
            if area_m2 <= 0:
                st['skippedZeroArea'] += 1
                continue

            # 대표 ring = 가장 넓은 외곽선 하나 (동이 여러 채인 레코드가 있다)
            biggest = max(outers, key=lambda t: t[1])[0]
            ring_xy, cut = decimate(biggest, MAX_RING_PTS)
            if cut:
                st['ringDecimated'] += 1

            xs = [p[0] for p in ring_xy]
            ys = [p[1] for p in ring_xy]
            lons, lats = tr.transform(xs, ys)

            # bbox 적중 — 점 하나라도 안에 들어오면 안으로 센다
            #             (pbf_buildings.py 의 insideTest 와 같은 규칙)
            if any(S <= la <= N and W <= lo <= E for la, lo in zip(lats, lons)):
                st['insideBbox'] += 1
            else:
                st['outsideBbox'] += 1

            h_raw, lv_raw = rec[F['height']], rec[F['levels']]
            height = num_or_none(h_raw, 0, 1000)      # 0 은 "없음" → None
            levels = int_or_none(lv_raw, 0, 200)      # 지상층수 0 은 불가능 → None
            if height is None and h_raw is not None and float(h_raw or 0) == 0:
                st['heightZeroed'] += 1
            if levels is None and lv_raw is not None and float(lv_raw or 0) == 0:
                st['levelsZeroed'] += 1
            if height is not None:
                st['withHeight'] += 1
            if levels is not None:
                st['withLevels'] += 1
            if height is not None and levels is not None:
                st['withBoth'] += 1

            use_name = text_or_none(rec[F['useName']])
            if use_name:
                st['withUseName'] += 1
                uses[use_name] += 1

            # 🔴 지하층수의 0 은 높이의 0 과 다르다. **0 은 "지하가 없다" 는 실제
            #    값이다** — 건물은 지하가 없을 수 있다. 반대로 지상층수 0 은
            #    있을 수 없으므로 그쪽만 "없음" 으로 본다. 비어 있는 칸(None)은
            #    여기서도 None 이다.
            bs_raw = rec[F['basement']]
            try:
                basement = None if bs_raw is None else int(bs_raw)
            except (TypeError, ValueError):
                basement = None
            if basement is not None and not (0 <= basement <= 100):
                basement = None

            row = {
                'id': ID_BASE + st['seen'],
                'topic': 'building',
                'levels': levels,
                'heightTag': None if height is None else round(height, 3),
                'areaM2': round(area_m2, 2),
                'ring': [[round(la, COORD_DIGITS), round(lo, COORD_DIGITS)]
                         for la, lo in zip(lats, lons)],
                'useName': use_name,
                'useCode': text_or_none(rec[F['useCode']]),
                'structName': text_or_none(rec[F['structName']]),
                'grossAreaM2': num_or_none(rec[F['grossArea']], 0, 5_000_000),
                'approvedAt': text_or_none(rec[F['approvedAt']]),
                'basement': basement,
                'pnu': text_or_none(rec[F['pnu']]),
            }
            if row['grossAreaM2'] is not None:
                row['grossAreaM2'] = round(row['grossAreaM2'], 2)

            if probe is None:
                probe = dict(dongName=text_or_none(rec[F['dongName']]),
                             lat=row['ring'][0][0], lon=row['ring'][0][1],
                             height=row['heightTag'], levels=row['levels'],
                             useName=row['useName'])

            fo.write(json.dumps(row, ensure_ascii=False) + '\n')
            st['kept'] += 1

    elapsed = round(time.time() - t0, 1)

    # ── 불변식 ────────────────────────────────────────────────────────────────
    fails = []
    if st['kept'] == 0:
        fails.append('건물이 하나도 변환되지 않았다 — SHP 나 좌표계를 확인하라')

    # 1) 좌표 변환. 첫 레코드는 부산 중구 보수동1가여야 한다.
    if probe is None:
        fails.append('첫 레코드를 못 읽었다')
    else:
        print('─' * 60)
        print(f"좌표 검증 (첫 레코드)  법정동명={probe['dongName']}  "
              f"용도={probe['useName']}")
        print(f"  변환 결과  lat={probe['lat']}  lon={probe['lon']}"
              f"   기대 ≈ ({PROBE_LAT}, {PROBE_LON}) ±{PROBE_TOL}")
        if not probe['dongName'] or PROBE_DONG not in probe['dongName']:
            fails.append(f"첫 레코드의 법정동명이 '{PROBE_DONG}' 가 아니다: "
                         f"{probe['dongName']!r} — CP949 디코딩이나 레코드 순서가 다르다")
        if (abs(probe['lat'] - PROBE_LAT) > PROBE_TOL
                or abs(probe['lon'] - PROBE_LON) > PROBE_TOL):
            fails.append(f"좌표 변환이 틀렸다: 첫 레코드가 "
                         f"({probe['lat']}, {probe['lon']}) 로 떨어졌다 — "
                         f"부산 중구({PROBE_LAT}, {PROBE_LON}) 가 아니다")

    # 2) bbox 적중률
    hit = st['insideBbox'] / st['kept'] if st['kept'] else 0.0
    if hit < MIN_BBOX_HIT:
        fails.append(f"bbox 적중률 {hit*100:.2f}% < {MIN_BBOX_HIT*100:.0f}% — "
                     f"이 파일이 부산이 아니거나 좌표계가 EPSG:5186 이 아니다")

    summary = {
        'at': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'step': 'shp-buildings',
        'area': area_cfg['target']['name'],
        'bbox': b,
        'source': {
            'file': os.path.basename(base) + '.shp',
            'shpBytes': os.path.getsize(base + '.shp'),
            'dbfBytes': os.path.getsize(base + '.dbf'),
            'records': total,
            'crs': 'EPSG:5186 (Korea 2000 중부원점 TM)',
            'encoding': 'CP949',
            'license': 'CC BY — 출처: 국토교통부 GIS건물통합정보 (V-World)',
        },
        'params': {
            'idBase': ID_BASE,
            'idRule': 'ID_BASE + SHP 레코드 순번(1부터). A0 는 중복 키, A1 은 2^53 초과',
            'maxRingPts': MAX_RING_PTS,
            'coordDigits': COORD_DIGITS,
            'areaMethod': 'EPSG:5186 미터 평면에서 신발끈, 구멍은 뺀다, 솎기 전 원본 ring',
            'zeroMeansMissing': ['A16 높이', 'A26 지상층_수'],
            'zeroIsReal': ['A27 지하층_수 (0 = 지하 없음)'],
            'insideTest': 'ring 점 하나라도 bbox 안 (pbf_buildings.py 와 같은 규칙)',
            'minBboxHit': MIN_BBOX_HIT,
        },
        'probe': probe,
        'counts': st,
        'bboxHitRate': round(hit, 6),
        'coverage': {
            'height': round(st['withHeight'] / st['kept'], 6) if st['kept'] else None,
            'levels': round(st['withLevels'] / st['kept'], 6) if st['kept'] else None,
            'both': round(st['withBoth'] / st['kept'], 6) if st['kept'] else None,
            'useName': round(st['withUseName'] / st['kept'], 6) if st['kept'] else None,
            'osmBaseline': {'height': 0.021, 'levels': 0.308,
                            'note': 'data/raw/pbf/_building-summary.json 실측 (52,556채)'},
        },
        'topUseNames': uses.most_common(30),
        'outBytes': os.path.getsize(out_path),
        'elapsedSec': elapsed,
        'fails': fails,
    }
    json.dump(summary, open(os.path.join(args.out, '_gis-building-summary.json'), 'w',
                            encoding='utf-8'), ensure_ascii=False, indent=2)

    pct = lambda k: f"{st[k] / st['kept'] * 100:5.1f}%" if st['kept'] else '  n/a'
    print('─' * 60)
    print(f"  변환 채택        {st['kept']:>9,} / {st['seen']:,}")
    print(f"  높이(A16)        {st['withHeight']:>9,}  {pct('withHeight')}   ← OSM height 2.1%")
    print(f"  지상층수(A26)    {st['withLevels']:>9,}  {pct('withLevels')}   ← OSM levels 30.8%")
    print(f"  둘 다            {st['withBoth']:>9,}  {pct('withBoth')}   ← 층당 높이 표본")
    print(f"  건축물용도명     {st['withUseName']:>9,}  {pct('withUseName')}")
    print(f"  0→null 로 바꿈   높이 {st['heightZeroed']:,} · 지상층수 {st['levelsZeroed']:,}")
    print(f"  bbox 적중        {st['insideBbox']:>9,}  ({hit*100:.2f}%)  밖 {st['outsideBbox']:,}")
    print(f"  ring 솎음        {st['ringDecimated']:>9,}  (>{MAX_RING_PTS}점)")
    print(f"  구멍 뺌          {st['holesSubtracted']:>9,} · 외곽선 여러 개 {st['multiOuter']:,}")
    print(f"→ {out_path}  ({os.path.getsize(out_path)/1e6:.1f} MB)  {elapsed}초")

    if fails:
        sys.stderr.write('\n🔴 불변식 실패\n')
        for f in fails:
            sys.stderr.write(f'  - {f}\n')
        sys.exit(1)
    print('불변식 통과')


if __name__ == '__main__':
    main()
