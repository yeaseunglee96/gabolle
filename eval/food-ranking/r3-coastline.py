#!/usr/bin/env python3
"""
라운드3 — **해안선**을 뽑는다. 라운드2 가 "가장 아까운 미사용 신호" 로 꼽은 것이다.

라운드2 는 정답이 바닷가에 몰려 있는데 해안선 레이어가 없어서 그것을 못 쟀다
(docs/FOOD-RANKING-EVAL.md 12절 ③). 관광 기준점 345개 중 해수욕장은 1건뿐이라
`touristM`(관광지까지 거리)이 바다를 대신하지 못했다.

여기서 만드는 것: OSM `natural=coastline`(해안선) 의 **꼭짓점 좌표 목록**.
`bigData/collect/pbf_boundary.py` 가 부산 경계를 만들 때 쓰는 것과 같은 태그다 —
그쪽은 육지 폴리곤을 만들고, 여기는 거리를 재려고 점만 필요하다.

  py r3-coastline.py
  py r3-coastline.py --pbf <경로>

출력: data/r3-coastline.json  { "points": [[lat, lon], ...] }
종료 코드: 0 성공 · 2 입력 없음
"""
import argparse
import json
import math
import os
import sys
import time

import osmium

HERE = os.path.dirname(os.path.abspath(__file__))

# 부산 수집 범위 — bigData/config/area.json 의 target.bbox 와 같은 값이다.
# 해안선은 부산 밖에서 이어져 들어오므로 여유를 조금 준다.
S, W, N, E = 34.80, 128.66, 35.48, 129.40

# 꼭짓점이 촘촘하면 거리 계산이 느려진다. 이 간격보다 가까운 점은 건너뛴다.
# 30 m 는 우리가 재려는 거리(수백 m ~ 수 km)에 견주면 반올림 오차 수준이다.
MIN_SPACING_M = 30.0
R_EARTH = 6371000.0


def haversine(a_lat, a_lon, b_lat, b_lon):
    d_lat = math.radians(b_lat - a_lat)
    d_lon = math.radians(b_lon - a_lon)
    h = (math.sin(d_lat / 2) ** 2
         + math.cos(math.radians(a_lat)) * math.cos(math.radians(b_lat)) * math.sin(d_lon / 2) ** 2)
    return 2 * R_EARTH * math.asin(min(1.0, math.sqrt(h)))


class CoastHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.points = []
        self.ways = 0

    def way(self, w):
        if w.tags.get('natural') != 'coastline':
            return
        try:
            nodes = [(n.lat, n.lon) for n in w.nodes if n.location.valid()]
        except osmium.InvalidLocationError:
            return
        if not nodes:
            return
        kept = 0
        last = None
        for lat, lon in nodes:
            if not (S <= lat <= N and W <= lon <= E):
                last = None
                continue
            if last is not None and haversine(last[0], last[1], lat, lon) < MIN_SPACING_M:
                continue
            self.points.append((round(lat, 6), round(lon, 6)))
            last = (lat, lon)
            kept += 1
        if kept:
            self.ways += 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pbf', default='C:/Users/SSAFY/Desktop/S15P21E201-bigdata/bigData/data/raw/osm/south-korea-latest.osm.pbf')
    ap.add_argument('--out', default=os.path.join(HERE, 'data', 'r3-coastline.json'))
    args = ap.parse_args()

    if not os.path.exists(args.pbf):
        sys.exit(f"PBF 가 없습니다: {args.pbf}")

    print(f"PBF : {args.pbf} ({os.path.getsize(args.pbf) / 1e6:.0f} MB)")
    print(f"범위: ({S},{W}) ~ ({N},{E}) · 꼭짓점 최소 간격 {MIN_SPACING_M:.0f} m")
    t0 = time.time()
    h = CoastHandler()
    # locations=True 는 way 의 각 노드에 좌표를 채워 준다 (노드를 한 번 더 훑는다)
    h.apply_file(args.pbf, locations=True, idx='flex_mem')
    dt = time.time() - t0

    if not h.points:
        sys.exit("🔴 해안선을 한 점도 못 찾았습니다 — 태그나 범위를 확인하세요")

    lats = [p[0] for p in h.points]
    lons = [p[1] for p in h.points]
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, 'w', encoding='utf-8') as f:
        json.dump({
            'builtAt': time.strftime('%Y-%m-%dT%H:%M:%S'),
            'source': 'OSM natural=coastline (south-korea-latest.osm.pbf)',
            'bbox': {'south': S, 'west': W, 'north': N, 'east': E},
            'minSpacingM': MIN_SPACING_M,
            'ways': h.ways,
            'count': len(h.points),
            'points': h.points,
        }, f)

    print(f"해안선 way {h.ways}개 · 꼭짓점 {len(h.points)}개  ({dt:.0f}초)")
    print(f"  위도 {min(lats):.4f}~{max(lats):.4f} · 경도 {min(lons):.4f}~{max(lons):.4f}")
    print(f"→ {args.out}")


if __name__ == '__main__':
    main()
