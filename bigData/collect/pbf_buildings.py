#!/usr/bin/env python3
"""
전국 PBF 에서 부산 bbox 안의 **건물**을 뽑는다.

왜 따로 있나:
  collect/pbf_extract.py 는 길·정류장·POI 를 뽑는다. 건물은 목적이 다르다 —
  **그림자**를 계산하려고 뽑는다 (docs/WALKABILITY.md 2.2). 그림자에 필요한 것은
  높이(층수)와 **바닥 면적**이고, 길 데이터에는 그 둘이 없다.
  같은 파일을 고치지 않고 새로 만든 이유는 pbf_extract.py 가 이미 돌아가는
  산출물을 만들고 있어서다 — 건드리면 그쪽이 같이 무너진다.

무엇을 뽑나:
  `building` 태그가 붙은 **way 와 multipolygon relation** 을 함께 뽑는다.
  pyosmium 의 area(**닫힌 way 와 multipolygon relation 을 같은 폴리곤 객체로
  합쳐 주는 osmium 의 기능**) 처리를 쓰면 둘을 한 갈래로 다룰 수 있다.

출력  data/raw/pbf/building.ndjson  — 한 줄에 객체 하나(NDJSON):
  {"id":<정수 OSM id>,"topic":"building","levels":<정수|null>,
   "heightTag":<미터 실수|null>,"areaM2":<실수>,"ring":[[lat,lon],...]}

  🔴 이 스키마는 다른 작업이 그대로 읽는다. 키 이름을 바꾸지 않는다.

  py collect/pbf_buildings.py
  py collect/pbf_buildings.py --pbf <경로> --out <폴더>
"""
import argparse, json, math, os, sys, time
import osmium

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# ring 좌표 상한. 건물 외곽선은 대개 5~10점이라 대부분 그대로 나가고,
# 성곽·공장처럼 수백 점짜리만 솎아낸다. 🔴 면적은 **솎기 전 원본 ring** 으로
# 계산하므로 이 값이 areaM2 를 바꾸지 않는다. 솎은 개수는 요약에 적는다.
MAX_RING_PTS = 64

# 좌표 소수 자리. 6자리 = 위도 1도의 1e-6 ≈ 0.11 m — 건물 외곽선에 충분하고
# 파일 크기를 절반 아래로 줄인다.
COORD_DIGITS = 6

FEET = 0.3048


# ── 태그 파싱 ────────────────────────────────────────────────────────────────
# 🔴 파싱 실패는 전부 None 이다. 0 이 아니다.
#    0 으로 채우면 "높이를 모른다" 와 "높이가 0 이다" 가 같은 값이 되고,
#    그 뒤로는 어느 쪽인지 되찾을 방법이 없다.

def parse_levels(raw):
    """`building:levels` → 정수 층수 또는 None."""
    if raw is None:
        return None
    s = raw.strip().replace(',', '.')
    if not s:
        return None
    try:
        v = float(s)                 # "3.5" 같은 값도 있다 (중2층)
    except ValueError:
        return None                  # "1;2", "여러 층" 등은 버린다
    if not math.isfinite(v) or v < 0 or v > 200:
        return None                  # 200층 넘는 건물은 한국에 없다 — 오타다
    return int(round(v))


def parse_height(raw):
    """`height` → 미터 실수 또는 None. 피트 표기(`40'`, `40 ft`)는 환산한다."""
    if raw is None:
        return None
    s = raw.strip().lower().replace(',', '.')
    if not s:
        return None
    mult = 1.0
    for suf, m in (("'", FEET), ('ft', FEET), ('feet', FEET), ('foot', FEET),
                   ('m', 1.0), ('meter', 1.0), ('meters', 1.0),
                   ('metre', 1.0), ('metres', 1.0)):
        if s.endswith(suf):
            s, mult = s[:-len(suf)].strip(), m
            break
    try:
        v = float(s)
    except ValueError:
        return None                  # "~10", "3;6", "low" 등은 버린다
    if not math.isfinite(v) or v <= 0 or v > 1000:
        return None
    return round(v * mult, 3)


# ── 면적 ────────────────────────────────────────────────────────────────────
# 🔴 위경도를 그대로 곱하면 안 된다.
#    부산(위도 35.1°)에서 경도 1도는 약 91,060 m, 위도 1도는 약 110,940 m 다.
#    경도가 18% 짧다. 도(degree) 단위로 신발끈 공식을 돌리면 면적이 약 22%
#    부풀고, 그 오차가 그대로 "이 건물은 그림자를 만든다/못 만든다" 판정에
#    실린다. 그래서 폴리곤마다 **그 폴리곤의 평균 위도**에서 도→미터 환산
#    계수를 구해 평면 좌표로 바꾼 뒤 넓이를 잰다.
#
#    환산 계수는 WGS84 타원체의 표준 급수식이다 (지구를 완전한 구로 보면
#    위도 1도에서 0.23% 어긋난다 — 건물 크기에서는 무시할 만하지만 공짜다).

def deg_scale(lat_deg):
    """(위도 1도의 m, 경도 1도의 m) — 주어진 위도에서."""
    p = math.radians(lat_deg)
    m_lat = (111132.92 - 559.82 * math.cos(2 * p)
             + 1.175 * math.cos(4 * p) - 0.0023 * math.cos(6 * p))
    m_lon = (111412.84 * math.cos(p) - 93.5 * math.cos(3 * p)
             + 0.118 * math.cos(5 * p))
    return m_lat, m_lon


def ring_area_m2(pts, lat0, m_lat, m_lon):
    """신발끈 공식. pts = [(lat, lon), ...]. 닫혀 있든 아니든 같은 값이 나온다."""
    n = len(pts)
    if n < 3:
        return 0.0
    lon0 = pts[0][1]
    s = 0.0
    for i in range(n):
        y1 = (pts[i][0] - lat0) * m_lat
        x1 = (pts[i][1] - lon0) * m_lon
        j = (i + 1) % n
        y2 = (pts[j][0] - lat0) * m_lat
        x2 = (pts[j][1] - lon0) * m_lon
        s += x1 * y2 - x2 * y1
    return abs(s) / 2.0


def decimate(pts, limit):
    """점이 너무 많은 ring 을 균등하게 솎는다. 모양을 유지하려고 끝점을 남긴다."""
    if len(pts) <= limit:
        return pts, False
    step = len(pts) / (limit - 1)
    out = [pts[min(int(i * step), len(pts) - 1)] for i in range(limit - 1)]
    out.append(pts[-1])
    return out, True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pbf', default=os.path.join(ROOT, 'data/raw/osm/south-korea-latest.osm.pbf'))
    ap.add_argument('--out', default=os.path.join(ROOT, 'data/raw/pbf'))
    args = ap.parse_args()

    area_cfg = json.load(open(os.path.join(ROOT, 'config/area.json'), encoding='utf-8'))
    b = area_cfg['target']['bbox']
    S, W, N, E = b['south'], b['west'], b['north'], b['east']

    if not os.path.exists(args.pbf):
        # 🔴 입력이 없으면 조용히 통과하지 않는다.
        sys.stderr.write(f"PBF 가 없습니다: {args.pbf}\n  npm run collect:osm 을 먼저 돌리세요\n")
        sys.exit(2)
    os.makedirs(args.out, exist_ok=True)

    md5_path = args.pbf + '.md5'
    pbf_md5 = None
    if os.path.exists(md5_path):
        pbf_md5 = open(md5_path, encoding='utf-8').read().split()[0]

    print(f"대상: {area_cfg['target']['name']}  bbox=({S},{W},{N},{E})")
    print(f"PBF : {args.pbf}  ({os.path.getsize(args.pbf)/1e6:.0f} MB)")

    out_path = os.path.join(args.out, 'building.ndjson')
    t0 = time.time()

    st = dict(areasSeen=0, kept=0, fromWay=0, fromRelation=0,
              skippedNotBuilding=0, skippedBuildingNo=0, skippedOutside=0,
              skippedBadGeom=0, skippedZeroArea=0, ringDecimated=0,
              withLevels=0, withHeight=0, levelsUnparsable=0,
              heightUnparsable=0, holesSubtracted=0)

    # 🔴 with_areas(KeyFilter('building')) 의 필터는 **믿을 수 없다.**
    #    실측(2026-08-27): 이 필터를 걸어도 building 태그가 없는 area 가 그대로
    #    나온다 — 부산 bbox 안에서만 14,593개(주차장·공원·논밭·물)가 섞여 나왔고,
    #    그것들은 넓고 층수 태그가 없어서 **가설 검증을 통째로 뒤집을 뻔했다.**
    #    필터는 area 를 만들 후보를 줄이는 데만 쓰고, **실제 채택 여부는 아래
    #    루프에서 태그를 직접 보고 정한다.**
    fp = (osmium.FileProcessor(args.pbf)
          .with_areas(osmium.filter.KeyFilter('building'))
          .with_filter(osmium.filter.EntityFilter(osmium.osm.AREA)))

    with open(out_path, 'w', encoding='utf-8') as fo:
        for a in fp:
            st['areasSeen'] += 1
            if st['areasSeen'] % 1_000_000 == 0:
                print(f"  area {st['areasSeen']:,} 훑음 / 채택 {st['kept']:,} "
                      f"({time.time()-t0:.0f}s)", flush=True)

            tags = dict(a.tags)
            bv = tags.get('building')
            if bv is None:
                st['skippedNotBuilding'] += 1
                continue
            if bv == 'no':
                # `building=no` 는 "여기는 건물이 아니다" 라는 뜻이다. 건물이 아니다.
                st['skippedBuildingNo'] += 1
                continue

            # ── 외곽선(들) 과 구멍 ──────────────────────────────────────────
            outers, inners, inside = [], [], False
            try:
                for r in a.outer_rings():
                    pts = [(p.lat, p.lon) for p in r]
                    if len(pts) >= 3:
                        outers.append(pts)
                        if not inside:
                            inside = any(S <= la <= N and W <= lo <= E for la, lo in pts)
                    for ir in a.inner_rings(r):
                        ipts = [(p.lat, p.lon) for p in ir]
                        if len(ipts) >= 3:
                            inners.append(ipts)
            except (osmium.InvalidLocationError, RuntimeError):
                st['skippedBadGeom'] += 1
                continue

            if not outers:
                st['skippedBadGeom'] += 1
                continue
            if not inside:
                st['skippedOutside'] += 1
                continue

            # 면적 기준 위도 — 폴리곤 전체의 평균 위도 하나로 통일한다.
            all_lat = [p[0] for ring in outers for p in ring]
            lat0 = sum(all_lat) / len(all_lat)
            m_lat, m_lon = deg_scale(lat0)

            # 🔴 면적은 **솎기 전 원본 ring** 으로 잰다.
            area_m2 = sum(ring_area_m2(r, lat0, m_lat, m_lon) for r in outers)
            hole = sum(ring_area_m2(r, lat0, m_lat, m_lon) for r in inners)
            if hole > 0:
                st['holesSubtracted'] += 1
            area_m2 = max(0.0, area_m2 - hole)
            if area_m2 <= 0:
                st['skippedZeroArea'] += 1
                continue

            # 대표 ring = 가장 넓은 외곽선 하나 (relation 은 동 여러 채일 수 있다)
            biggest = max(outers, key=lambda r: ring_area_m2(r, lat0, m_lat, m_lon))
            ring, cut = decimate(biggest, MAX_RING_PTS)
            if cut:
                st['ringDecimated'] += 1

            lv_raw, h_raw = tags.get('building:levels'), tags.get('height')
            levels, height = parse_levels(lv_raw), parse_height(h_raw)
            if levels is not None:
                st['withLevels'] += 1
            elif lv_raw is not None:
                st['levelsUnparsable'] += 1
            if height is not None:
                st['withHeight'] += 1
            elif h_raw is not None:
                st['heightUnparsable'] += 1

            # area id 는 osmium 이 만든 것이라 OSM id 가 아니다.
            # orig_id() 가 원래 way/relation 의 id 다. relation 은 음수로 적어
            # way 와 섞이지 않게 한다 (OSM 에서 way 1 과 relation 1 은 다른 것이다).
            oid = a.orig_id()
            if a.from_way():
                st['fromWay'] += 1
            else:
                st['fromRelation'] += 1
                oid = -oid

            fo.write(json.dumps({
                'id': oid,
                'topic': 'building',
                'levels': levels,
                'heightTag': height,
                'areaM2': round(area_m2, 2),
                'ring': [[round(la, COORD_DIGITS), round(lo, COORD_DIGITS)] for la, lo in ring],
            }, ensure_ascii=False) + '\n')
            st['kept'] += 1

    elapsed = round(time.time() - t0, 1)
    if st['kept'] == 0:
        sys.stderr.write('건물이 하나도 추출되지 않았습니다 — bbox 나 PBF 를 확인하세요\n')
        sys.exit(1)

    summary = {
        'at': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'step': 'pbf-buildings',
        'area': area_cfg['target']['name'],
        'bbox': b,
        'source': {'file': os.path.basename(args.pbf),
                   'bytes': os.path.getsize(args.pbf), 'md5': pbf_md5},
        'params': {'maxRingPts': MAX_RING_PTS, 'coordDigits': COORD_DIGITS,
                   'insideTest': 'ring 점 하나라도 bbox 안 (pbf_extract.py 와 같은 규칙)',
                   'areaMethod': '폴리곤 평균 위도에서 도→m 환산 후 신발끈, 구멍은 뺀다'},
        'note': 'bbox 추출. 실제 부산 경계로 자르는 것은 staging 단계에서 한다.',
        'counts': st,
        'outBytes': os.path.getsize(out_path),
        'elapsedSec': elapsed,
    }
    json.dump(summary, open(os.path.join(args.out, '_building-summary.json'), 'w',
                            encoding='utf-8'), ensure_ascii=False, indent=2)

    print('─' * 52)
    print(f"  건물 채택        {st['kept']:>9,}  (way {st['fromWay']:,} / relation {st['fromRelation']:,})")
    print(f"  building:levels  {st['withLevels']:>9,}  ({st['withLevels']/st['kept']*100:.1f}%)")
    print(f"  height           {st['withHeight']:>9,}  ({st['withHeight']/st['kept']*100:.1f}%)")
    print(f"  ring 솎음        {st['ringDecimated']:>9,}  (>{MAX_RING_PTS}점)")
    print(f"  bbox 밖 버림     {st['skippedOutside']:>9,}")
    print(f"→ {out_path}  ({os.path.getsize(out_path)/1e6:.1f} MB)  {elapsed}초")


if __name__ == '__main__':
    main()
