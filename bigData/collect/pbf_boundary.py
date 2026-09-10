#!/usr/bin/env python3
"""
전국 PBF 에서 부산광역시의 **실제 행정경계**를 뽑아 config/boundary.geojson 을 만든다.

  py collect/pbf_boundary.py
  py collect/pbf_boundary.py --pbf <경로> --out <경로>

──────────────────────────────────────────────────────────────────────────────
왜 bbox 로는 안 되나
──────────────────────────────────────────────────────────────────────────────
config/area.json 의 `target.bbox` 는 **수집 범위**다 — "이 사각형 안의 것을
PBF·고도타일에서 긁어 온다". 사각형은 3,040 km2 라 바다와 김해·양산이 섞여 든다.
이 파일이 만드는 경계는 **거르는 범위**다 — "긁어온 것 중 부산인 것만 남긴다".

둘 다 필요하다. bbox 는 사각형이라야 타일 API·osmium 필터에 그대로 넣을 수 있고
(폴리곤으로는 못 받는다), 경계는 폴리곤이라야 바다와 옆 도시를 잘라낸다.
그래서 경계는 bbox 를 **대체하지 않고 그 옆에 추가된다.** area.json 은 고치지 않는다.

──────────────────────────────────────────────────────────────────────────────
🔴 OSM 의 한국 행정경계는 **바다를 포함한다**
──────────────────────────────────────────────────────────────────────────────
이 스크립트를 만들면서 실측한 것이다. relation 2396450(부산광역시) 을 그대로
폴리곤으로 만들면 **2,021 km2** 가 나온다 — 부산 실면적 770 km2 의 2.6배다.
한국의 광역시 경계는 해상 관할구역(기장군 앞바다·가덕도 앞바다)까지 그려져 있고,
그래서 기장군이 566 km2(실제 218), 영도구가 110 km2(실제 14) 로 나온다.

행정경계만 쓰면 김해·양산은 걸러지지만 **바다는 그대로 남는다.** 그래서 한 단계
더 한다 — OSM 의 `natural=coastline`(해안선) 으로 육지 폴리곤을 만들어 교집합을
취한다. 해안선은 OSM 규약상 **진행 방향의 왼쪽이 육지**이므로, 각 선분의 왼쪽·
오른쪽에 탐침점을 찍어 다수결로 면을 육지/바다로 분류한다 (좌표를 지어내지 않고
데이터가 스스로 판정하게 하는 방법이다).

──────────────────────────────────────────────────────────────────────────────
필요한 것
──────────────────────────────────────────────────────────────────────────────
  osmium (requirements.txt 에 있다) · shapely · numpy
  🔴 shapely·numpy 는 requirements.txt 에 없다. 이 저장소에는 이미 설치되어 있어
     그대로 썼다. 없으면 `py -m pip install shapely numpy`.

종료 코드: 0 성공 · 1 불변식 실패 · 2 입력 없음
"""
import argparse
import json
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

R_EARTH = 6371008.8  # m, IUGG 평균 반지름
BUSAN_NAME = '부산광역시'
BUSAN_NAME_EN = 'Busan'
REF_AREA_KM2 = 770.1        # 부산광역시 공식 면적. 불변식 1 의 기준선일 뿐 계산에 안 쓴다
AREA_TOL = 0.15             # 실면적 대비 허용 오차 (±15%)
COAST_MARGIN_DEG = 0.05     # 해안선을 긁을 때 행정경계 bbox 를 이만큼 넓힌다
PROBE_EPS_DEG = 8e-5        # 해안선 좌/우 탐침점 거리 (~9 m)
COORD_PRECISION = 6         # 소수점 6자리 = 약 0.1 m


# ── 면적 (구면 초과 공식) ────────────────────────────────────────────────────
def _ring_area_km2(ring):
    s = 0.0
    for i in range(len(ring) - 1):
        x1, y1 = math.radians(ring[i][0]), math.radians(ring[i][1])
        x2, y2 = math.radians(ring[i + 1][0]), math.radians(ring[i + 1][1])
        s += (x2 - x1) * (2 + math.sin(y1) + math.sin(y2))
    return abs(s * R_EARTH * R_EARTH / 2.0) / 1e6


def multipolygon_area_km2(coords):
    """coords = MultiPolygon 의 coordinates. [lon, lat] 순서를 전제한다."""
    total = 0.0
    for poly in coords:
        total += _ring_area_km2(poly[0])
        for hole in poly[1:]:
            total -= _ring_area_km2(hole)
    return total


# ── 1단계: admin_level 을 실측한다 ───────────────────────────────────────────
def scan_admin(pbf):
    """boundary=administrative relation 을 전부 훑어 부산을 찾는다.

    admin_level 을 코드에 박지 않는다. 데이터에 적힌 값을 읽어서 쓴다.
    """
    import osmium
    t0 = time.time()
    rels = []
    fp = (osmium.FileProcessor(pbf)
          .with_filter(osmium.filter.EntityFilter(osmium.osm.RELATION))
          .with_filter(osmium.filter.TagFilter(('boundary', 'administrative'))))
    for r in fp:
        t = dict(r.tags)
        rels.append({
            'id': r.id,
            'name': t.get('name'),
            'nameEn': t.get('name:en'),
            'adminLevel': t.get('admin_level'),
            'subareas': [m.ref for m in r.members if m.type == 'r' and m.role == 'subarea'],
        })
    levels = {}
    for x in rels:
        levels[x['adminLevel']] = levels.get(x['adminLevel'], 0) + 1
    print(f"  boundary=administrative relation {len(rels):,}개 / {time.time() - t0:.0f}s")
    print(f"  admin_level 분포(실측): "
          + ', '.join(f"{k}={v}" for k, v in sorted(levels.items(), key=lambda kv: (kv[0] or ''))))

    busan = next((x for x in rels
                  if x['name'] == BUSAN_NAME or x['nameEn'] == BUSAN_NAME_EN), None)
    if busan is None:
        print(f"🔴 {BUSAN_NAME} relation 을 PBF 에서 찾지 못했습니다", file=sys.stderr)
        sys.exit(2)

    peers = sorted(x['name'] for x in rels if x['adminLevel'] == busan['adminLevel'])
    print(f"  ⭐ 부산 = relation {busan['id']}, **admin_level={busan['adminLevel']}** (실측)")
    print(f"     같은 레벨 {len(peers)}개: {', '.join(p for p in peers if p)[:120]}…")
    print(f"     subarea(하위 구·군) {len(busan['subareas'])}개 — 이것도 데이터에서 읽었다")
    return busan, rels


# ── 2단계: 폴리곤 조립 ───────────────────────────────────────────────────────
def assemble(pbf, ids):
    """relation → MultiPolygon. osmium 의 area assembler 가 way 들을 링으로 잇는다."""
    import osmium
    t0 = time.time()
    fac = osmium.geom.GeoJSONFactory()
    fp = (osmium.FileProcessor(pbf)
          .with_areas(osmium.filter.IdFilter(ids))
          .with_filter(osmium.filter.EntityFilter(osmium.osm.AREA)))
    want = set(ids)
    out = {}
    for a in fp:
        if a.from_way():
            continue
        oid = a.orig_id()
        if oid not in want:
            continue
        t = dict(a.tags)
        out[oid] = {
            'name': t.get('name'),
            'nameEn': t.get('name:en'),
            'adminLevel': t.get('admin_level'),
            'geom': json.loads(fac.create_multipolygon(a)),
        }
    print(f"  폴리곤 {len(out)}/{len(ids)}개 조립 / {time.time() - t0:.0f}s")
    missing = want - set(out)
    if missing:
        print(f"  ⚠️ 조립 실패한 relation: {sorted(missing)}")
    return out


# ── 3단계: 해안선으로 육지를 만든다 ──────────────────────────────────────────
def build_land(pbf, bounds):
    """natural=coastline 으로 육지 폴리곤을 만든다. bounds = (W, S, E, N)."""
    import osmium
    from shapely.geometry import LineString, Point, box
    from shapely.ops import polygonize, unary_union
    from shapely.strtree import STRtree

    W, S, E, N = bounds
    t0 = time.time()
    fp = (osmium.FileProcessor(pbf).with_locations()
          .with_filter(osmium.filter.EntityFilter(osmium.osm.WAY))
          .with_filter(osmium.filter.TagFilter(('natural', 'coastline'))))
    ways, scanned = [], 0
    for w in fp:
        scanned += 1
        try:
            pts = [(n.lon, n.lat) for n in w.nodes]
        except osmium.InvalidLocationError:
            continue
        if len(pts) < 2:
            continue
        if not any(W <= x <= E and S <= y <= N for x, y in pts):
            continue
        ways.append(pts)
    print(f"  해안선 way {scanned:,}개 중 구역 내 {len(ways)}개 "
          f"(선분 {sum(len(p) - 1 for p in ways):,}) / {time.time() - t0:.0f}s")
    if not ways:
        print("🔴 해안선을 하나도 못 찾았습니다 — PBF 가 잘못되었거나 구역이 틀렸습니다",
              file=sys.stderr)
        sys.exit(2)

    clip = box(W, S, E, N)
    faces = [f for f in polygonize(unary_union([LineString(p) for p in ways] + [clip.boundary]))
             if f.intersects(clip)]

    # OSM 규약: 해안선은 진행 방향 왼쪽이 육지, 오른쪽이 바다.
    land_probes, sea_probes = [], []
    for p in ways:
        for i in range(len(p) - 1):
            (x1, y1), (x2, y2) = p[i], p[i + 1]
            dx, dy = x2 - x1, y2 - y1
            L = math.hypot(dx, dy)
            if L == 0:
                continue
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            nx, ny = -dy / L, dx / L          # 진행 방향의 왼쪽 법선
            land_probes.append(Point(mx + nx * PROBE_EPS_DEG, my + ny * PROBE_EPS_DEG))
            sea_probes.append(Point(mx - nx * PROBE_EPS_DEG, my - ny * PROBE_EPS_DEG))

    tree = STRtree(faces)
    score = [0] * len(faces)
    for probes, sign in ((land_probes, 1), (sea_probes, -1)):
        for pt in probes:
            for idx in tree.query(pt):
                if faces[idx].contains(pt):
                    score[idx] += sign
                    break

    land = [faces[i] for i in range(len(faces)) if score[i] > 0]
    sea = [faces[i] for i in range(len(faces)) if score[i] < 0]
    undecided = [faces[i] for i in range(len(faces)) if score[i] == 0]
    slivers = sum(1 for f in undecided if f.area < 1e-9)
    print(f"  면 {len(faces)}개 → 육지 {len(land)} · 바다 {len(sea)} · "
          f"미판정 {len(undecided)}(그중 {slivers}개는 면적 0 조각)")
    if len(undecided) - slivers > 0:
        big = sorted((f for f in undecided if f.area >= 1e-9), key=lambda f: -f.area)
        print(f"  ⚠️ 면적이 있는 미판정 면 {len(big)}개 — 버린다. "
              f"가장 큰 것 {big[0].area:.6f} deg2")
    if not land:
        print("🔴 육지로 판정된 면이 없습니다", file=sys.stderr)
        sys.exit(1)
    return unary_union(land)


# ── 좌표 정리 ────────────────────────────────────────────────────────────────
def to_multipolygon_coords(geom, precision=COORD_PRECISION):
    """shapely 도형 → GeoJSON MultiPolygon coordinates. [lon, lat] 순서를 지킨다."""
    from shapely.geometry import mapping
    m = mapping(geom)
    if m['type'] == 'Polygon':
        polys = [m['coordinates']]
    elif m['type'] == 'MultiPolygon':
        polys = list(m['coordinates'])
    elif m['type'] == 'GeometryCollection':
        polys = []
        for g in m['geometries']:
            if g['type'] == 'Polygon':
                polys.append(g['coordinates'])
            elif g['type'] == 'MultiPolygon':
                polys.extend(g['coordinates'])
    else:
        polys = []
    out = []
    for poly in polys:
        rings = []
        for ring in poly:
            r = [[round(float(x), precision), round(float(y), precision)] for x, y in ring]
            # 반올림으로 이웃한 점이 같아지면 접는다
            dedup = [r[0]]
            for c in r[1:]:
                if c != dedup[-1]:
                    dedup.append(c)
            if dedup[0] != dedup[-1]:
                dedup.append(list(dedup[0]))
            if len(dedup) >= 4:
                rings.append(dedup)
        if rings:
            out.append(rings)
    return out


def count_points(coords):
    return sum(len(r) for poly in coords for r in poly)


# ── 불변식 검사 ──────────────────────────────────────────────────────────────
class Checks:
    def __init__(self):
        self.rows = []

    def add(self, ok, name, detail):
        self.rows.append((bool(ok), name, detail))
        print(f"  {'✅' if ok else '🔴'} {name} — {detail}")

    def failed(self):
        return [r for r in self.rows if not r[0]]


def check_rings_closed(ck, coords):
    bad = 0
    for poly in coords:
        for ring in poly:
            if ring[0] != ring[-1] or len(ring) < 4:
                bad += 1
    ck.add(bad == 0, '링 닫힘',
           f"링 {sum(len(p) for p in coords)}개 중 안 닫힌 것 {bad}개")


def check_area(ck, area_km2):
    lo, hi = REF_AREA_KM2 * (1 - AREA_TOL), REF_AREA_KM2 * (1 + AREA_TOL)
    err = (area_km2 - REF_AREA_KM2) / REF_AREA_KM2 * 100
    ck.add(lo <= area_km2 <= hi, '면적',
           f"{area_km2:.1f} km2 (실제 {REF_AREA_KM2} km2, 오차 {err:+.1f}%, "
           f"허용 {lo:.0f}~{hi:.0f})")


def check_known_points(ck, prepared, area_cfg):
    """area.json 에서 뽑은 좌표로 안/밖을 검사한다. 좌표를 지어내지 않는다."""
    from shapely.geometry import Point
    tb, fb = area_cfg['target']['bbox'], area_cfg['focus']['bbox']
    inside_cases = [
        ('focus(중구·동구) 중심', (fb['west'] + fb['east']) / 2, (fb['south'] + fb['north']) / 2),
    ]
    outside_cases = [
        ('target bbox 남서 모서리(바다)', tb['west'], tb['south']),
        ('target bbox 남동 모서리(바다)', tb['east'], tb['south']),
        ('target bbox 북서 모서리(김해 쪽)', tb['west'], tb['north']),
        ('target bbox 북동 모서리(울산 쪽)', tb['east'], tb['north']),
    ]
    bad = []
    for label, lon, lat in inside_cases:
        if not prepared.contains(Point(lon, lat)):
            bad.append(f"안이어야 하는데 밖: {label}")
    for label, lon, lat in outside_cases:
        if prepared.contains(Point(lon, lat)):
            bad.append(f"밖이어야 하는데 안: {label}")
    ck.add(not bad, 'bbox·focus 기준점',
           f"안 {len(inside_cases)} · 밖 {len(outside_cases)} 검사, "
           + ('전부 통과' if not bad else '; '.join(bad)))


def check_subway(ck, prepared):
    """지하철 담당이 지목한 양산 소재 역이 실제로 밖으로 판정되는가."""
    path = os.path.join(ROOT, 'data/staged/subway-entrance.ndjson')
    if not os.path.exists(path):
        ck.add(True, '지하철 출입구', 'data/staged/subway-entrance.ndjson 없음 — 건너뜀')
        return
    from shapely.geometry import Point
    busan_in = busan_tot = 0
    out_stations, in_stations = {}, {}
    for line in open(path, encoding='utf-8'):
        r = json.loads(line)
        lat, lon = r.get('lat'), r.get('lon')
        if lat is None or lon is None:
            continue
        addr = r.get('stationAddress') or ''
        inside = prepared.contains(Point(lon, lat))
        if '부산' in addr:
            busan_tot += 1
            busan_in += inside
        elif '양산' in addr or '김해' in addr or '경상남도' in addr:
            st = r.get('stationName') or '?'
            (in_stations if inside else out_stations).setdefault(st, 0)
            (in_stations if inside else out_stations)[st] += 1

    n_out, n_in = len(out_stations), len(in_stations)
    ck.add(n_in == 0, '양산·김해 역은 밖',
           f"경남 소재 역 {n_out + n_in}개 중 밖 {n_out}개 / 안 {n_in}개"
           + (f" — 밖: {', '.join(sorted(out_stations))}" if out_stations else '')
           + (f" 🔴 안으로 남은 것: {', '.join(sorted(in_stations))}" if in_stations else ''))
    if busan_tot:
        ratio = busan_in / busan_tot
        ck.add(ratio >= 0.98, '부산 소재 출입구는 안',
               f"{busan_in:,}/{busan_tot:,} = {ratio * 100:.2f}% 가 경계 안")


def check_segments(ck, land_geom, admin_geom):
    """bbox 로 뽑은 구간에 경계를 적용하면 몇 %가 걸러지나.

    두 갈래로 나눠 센다 — 행정경계 밖(김해·양산)으로 탈락한 것과, 행정경계 안이지만
    해안선 클립에서 탈락한 것. 뒤쪽은 **바다 위 교량 구간**이라 소수여야 정상이고,
    크면 육지/바다 판정이 뒤집혔다는 뜻이다.
    """
    slope = os.path.join(ROOT, 'data/staged/segment-slope.ndjson')
    if not os.path.exists(slope):
        ck.add(True, '구간 필터율', 'data/staged/segment-slope.ndjson 없음 — 건너뜀')
        return
    import numpy as np
    import shapely

    # 경사 파일에는 좌표가 없다. 원본 PBF 추출본에서 id → geometry 를 가져온다.
    need = {}
    for line in open(slope, encoding='utf-8'):
        r = json.loads(line)
        need.setdefault(r.get('topic'), set()).add(r['id'])
    total_ids = sum(len(v) for v in need.values())

    xs, ys, owner = [], [], []
    found = 0
    for topic, ids in need.items():
        raw = os.path.join(ROOT, 'data/raw/pbf', f'{topic}.ndjson')
        if not os.path.exists(raw):
            continue
        for line in open(raw, encoding='utf-8'):
            r = json.loads(line)
            if r['id'] not in ids:
                continue
            found += 1
            key = (topic, r['id'])
            for p in r['geometry']:
                xs.append(p['lon'])
                ys.append(p['lat'])
                owner.append(key)
    if not xs:
        ck.add(True, '구간 필터율', 'data/raw/pbf 에 좌표가 없어 건너뜀')
        return

    xs, ys = np.asarray(xs), np.asarray(ys)
    in_land = shapely.contains_xy(land_geom, xs, ys)
    in_admin = shapely.contains_xy(admin_geom, xs, ys)
    keep_land, keep_admin = set(), set()
    for k, a, b in zip(owner, in_land, in_admin):
        if a:
            keep_land.add(k)
        if b:
            keep_admin.add(k)
    n_seg = found
    drop = (n_seg - len(keep_land)) / n_seg * 100
    outside_admin = (n_seg - len(keep_admin)) / n_seg * 100
    sea_only = len(keep_admin - keep_land)
    sea_pct = sea_only / n_seg * 100
    ck.add(0 < drop < 50, '구간 필터율',
           f"{n_seg:,}개 중 {n_seg - len(keep_land):,}개 제외 = {drop:.1f}% "
           f"(0% 면 bbox 와 다를 게 없고 50% 넘으면 경계가 의심스럽다; "
           f"좌표를 못 찾은 id {total_ids - n_seg:,}개는 셈에서 뺐다)")
    ck.add(sea_pct < 2.0, '해안선 클립의 부작용',
           f"행정경계 안인데 해안선 클립에서 탈락한 구간 {sea_only}개 = {sea_pct:.2f}% "
           f"(바다 위 교량 구간이다. 2% 넘으면 육지/바다 판정이 뒤집힌 것). "
           f"행정경계 밖(김해·양산 등) 탈락은 {outside_admin:.1f}%")


# ── main ─────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pbf', default=os.path.join(ROOT, 'data/raw/osm/south-korea-latest.osm.pbf'))
    ap.add_argument('--out', default=os.path.join(ROOT, 'config/boundary.geojson'))
    ap.add_argument('--simplify', type=float, default=0.0,
                    help='좌표 솎기 허용오차(도). 0 이면 안 솎는다')
    args = ap.parse_args()

    if not os.path.exists(args.pbf):
        print(f"PBF 가 없습니다: {args.pbf}\n  npm run collect:osm 을 먼저 돌리세요",
              file=sys.stderr)
        sys.exit(2)
    area_cfg = json.load(open(os.path.join(ROOT, 'config/area.json'), encoding='utf-8'))

    from shapely.geometry import shape
    from shapely.prepared import prep
    import shapely

    t0 = time.time()
    print(f"PBF : {args.pbf} ({os.path.getsize(args.pbf) / 1e6:.0f} MB)")

    print('\n[1/4] admin_level 실측 — boundary=administrative relation 훑기')
    busan, _ = scan_admin(args.pbf)

    print('\n[2/4] 폴리곤 조립 — 부산 + 하위 구·군')
    ids = [busan['id']] + busan['subareas']
    areas = assemble(args.pbf, ids)
    if busan['id'] not in areas:
        print('🔴 부산 폴리곤 조립에 실패했습니다', file=sys.stderr)
        sys.exit(1)

    raw_city = shape(areas[busan['id']]['geom'])
    if not raw_city.is_valid:
        raw_city = raw_city.buffer(0)
    raw_area = multipolygon_area_km2(areas[busan['id']]['geom']['coordinates'])
    W, S, E, N = raw_city.bounds
    print(f"  🔴 행정경계 그대로의 면적 = {raw_area:,.1f} km2 — 실면적 {REF_AREA_KM2} km2 의 "
          f"{raw_area / REF_AREA_KM2:.1f}배. 해상 관할구역이 들어 있다")

    print('\n[3/4] 해안선으로 육지만 남기기')
    land = build_land(args.pbf, (W - COAST_MARGIN_DEG, S - COAST_MARGIN_DEG,
                                 E + COAST_MARGIN_DEG, N + COAST_MARGIN_DEG))

    print('\n[4/4] 교집합 · GeoJSON 쓰기')
    features, city_coords, city_geom = [], None, None
    for oid in ids:
        if oid not in areas:
            continue
        a = areas[oid]
        g = shape(a['geom'])
        if not g.is_valid:
            g = g.buffer(0)
        before = multipolygon_area_km2(a['geom']['coordinates'])
        clipped = g.intersection(land)
        if args.simplify > 0:
            clipped = clipped.simplify(args.simplify, preserve_topology=True)
        coords = to_multipolygon_coords(clipped)
        if not coords:
            print(f"  ⚠️ {a['name']}: 육지 교집합이 비었다 — 건너뜀")
            continue
        after = multipolygon_area_km2(coords)
        is_city = (oid == busan['id'])
        if is_city:
            city_coords, city_geom = coords, clipped
        features.append({
            'type': 'Feature',
            'properties': {
                'role': 'target' if is_city else 'district',
                'name': a['name'],
                'nameEn': a['nameEn'],
                'adminLevel': int(a['adminLevel']) if a['adminLevel'] else None,
                'osmRelation': oid,
                'areaKm2': round(after, 2),
                'areaKm2BeforeSeaClip': round(before, 2),
                'polygonCount': len(coords),
                'pointCount': count_points(coords),
            },
            'geometry': {'type': 'MultiPolygon', 'coordinates': coords},
        })
        print(f"  {a['adminLevel']} {a['name']:<7} {before:8.1f} → {after:7.1f} km2  "
              f"폴리곤 {len(coords):>3}개  점 {count_points(coords):>5}개")

    # 해상 관할구역을 포함한 원본 경계도 함께 남긴다.
    # 광안대교·거가대교처럼 **양 끝만 육지에 닿는 교량**은 중간 way 가 통째로 바다 위라
    # 육지 경계로 자르면 사라진다. 실측으로 19개 구간이 그랬다. 그것을 살려야 하는
    # 단계는 이 폴리곤을 쓴다.
    admin_coords = to_multipolygon_coords(raw_city)
    features.append({
        'type': 'Feature',
        'properties': {
            'role': 'admin',
            'name': f"{busan['name']} (해상 관할구역 포함)",
            'nameEn': f"{busan['nameEn']} (incl. maritime jurisdiction)",
            'adminLevel': int(busan['adminLevel']),
            'osmRelation': busan['id'],
            'areaKm2': round(multipolygon_area_km2(admin_coords), 2),
            'polygonCount': len(admin_coords),
            'pointCount': count_points(admin_coords),
            'note': ('OSM 행정경계 그대로. 바다가 들어 있으니 면적·밀도 계산에 쓰면 안 된다. '
                     '바다 위 교량 구간을 살려야 할 때만 쓴다'),
        },
        'geometry': {'type': 'MultiPolygon', 'coordinates': admin_coords},
    })

    rank = {'target': 0, 'admin': 1, 'district': 2}
    features.sort(key=lambda f: (rank[f['properties']['role']], -f['properties']['areaKm2']))
    fc = {
        'type': 'FeatureCollection',
        'name': '부산광역시 행정경계 (OSM boundary=administrative ∩ natural=coastline)',
        'note': ('좌표는 GeoJSON 규격대로 [lon, lat] 이다 — 우리 ndjson 의 [lat, lon] 과 반대. '
                 'role=target 이 육지만 남긴 부산 전체(기본으로 이걸 쓴다), '
                 'role=admin 은 해상 관할구역까지 포함한 원본, '
                 'role=district 는 구·군(육지). '
                 'config/area.json 의 bbox 는 수집 범위, 이 파일은 거르는 범위다.'),
        'source': {
            'pbf': os.path.basename(args.pbf),
            'osmRelation': busan['id'],
            'adminLevel': busan['adminLevel'],
            'seaClip': 'natural=coastline, 진행방향 왼쪽=육지 규약으로 면을 분류',
            'coordPrecision': COORD_PRECISION,
            'simplifyDeg': args.simplify,
            'generatedBy': 'collect/pbf_boundary.py',
            'generatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        },
        'features': features,
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, 'w', encoding='utf-8') as f:
        json.dump(fc, f, ensure_ascii=False, separators=(',', ':'))
    size = os.path.getsize(args.out)
    print(f"  → {args.out}  {size / 1024:.0f} KB  feature {len(features)}개")

    print('\n[검사] 불변식')
    ck = Checks()
    city_area = multipolygon_area_km2(city_coords)
    check_area(ck, city_area)
    check_rings_closed(ck, city_coords)
    ck.add(len(city_coords) > 1, 'MultiPolygon',
           f"부산 폴리곤 {len(city_coords)}개 — 영도·가덕도 같은 섬이 있으므로 1개면 이상하다")
    written = shape({'type': 'MultiPolygon', 'coordinates': city_coords})
    if not written.is_valid:
        written = written.buffer(0)
    shapely.prepare(written)
    check_known_points(ck, prep(written), area_cfg)
    check_subway(ck, prep(written))
    shapely.prepare(raw_city)
    check_segments(ck, written, raw_city)

    bad = ck.failed()
    print(f"\n총 {time.time() - t0:.0f}초")
    if bad:
        print(f"🔴 불변식 {len(bad)}개 실패: " + '; '.join(b[1] for b in bad), file=sys.stderr)
        sys.exit(1)
    print('✅ 불변식 전부 통과')


if __name__ == '__main__':
    main()
