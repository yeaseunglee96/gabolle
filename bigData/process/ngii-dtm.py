"""국토지리정보원 1:5,000 수치지형도 → 5m 지면 고도(DTM) → terrarium z15 타일 — S15P21E201-2039.

DTM(지면 모델)은 건물·나무를 뺀 맨땅 높이다. 지금까지 쓴 SRTM 30m 는 DSM(표면 모델 — 건물 꼭대기까지 잰 높이)에 가까워
건물이 빽빽한 평지 도심에 가짜 경사를 만들었다. 수치지형도의 등고선·표고점은 사람이 측량·도화한 땅 높이라 건물이 없다.

입력(커밋하지 않는다 — data/ 는 .gitignore)
  <raw>/<도엽번호>/N3L_F0010000.shp   등고선(주곡선 5m · 계곡선 25m), 칸 「등고수치」
  <raw>/<도엽번호>/N3P_F0020000.shp   표고점, 칸 「수치」
  <raw>/<도엽번호>/N3L_E0080000.shp   해안선 → 0m 로 쓴다
  좌표계는 .prj 의 Korea_2000_East_Belt_2010(EPSG:5187, 동부원점 TM).
  높이 0 이하인 등고선·표고점은 버린다 — 1,265줄이 0 이었고 내륙에서 값이 빠진 것과 구분이 안 된다. 바닷가 0m 는 해안선이 준다.

보간(점 사이를 채우는 것)
  타일마다 둘레 400m 안의 점으로 TIN(점을 삼각형으로 이어 그 면으로 높이를 매기는 방법)을 만들고, 그 면을
  가우스 흐림(σ = 2픽셀 ≈ 8m)으로 살짝 다듬는다. 흐림은 삼각형 모서리의 꺾임을 지우려는 것이고 100m 기준선 경사에는
  거의 영향이 없다. TIN 밖은 가장 가까운 점 값. 가장 가까운 점이 300m 보다 먼 픽셀(받은 도엽 바깥)은 「고도 없음」(-32768)으로 쓴다.

출력: <out>/15/<x>_<y>.png — slope.mjs·walk-graph-dtm-slope.mjs 가 읽는 terrarium(높이 = R*256 + G + B/256 - 32768).

  python -I process/ngii-dtm.py <raw 폴더> <tiles.txt> <out 폴더>
종료 코드 0 정상 · 1 실패 · 2 입력 없음
"""
import glob
import math
import os
import sys
import warnings

import numpy as np
import shapefile
from PIL import Image
from pyproj import Transformer
from scipy.interpolate import LinearNDInterpolator, NearestNDInterpolator
from scipy.ndimage import gaussian_filter
from scipy.spatial import Delaunay, cKDTree

warnings.filterwarnings('ignore')
ZOOM, TILE, PAD, BUF_M, CELL = 15, 256, 16, 400.0, 500.0
DENSIFY_M = 10.0
NODATA_M = 300.0  # 가장 가까운 측량 점이 이보다 멀면 고도 없음(도엽 밖). 표고점 간격이 평균 120m 쯤이다


def densify(points, z, out):
    for (x0, y0), (x1, y1) in zip(points[:-1], points[1:]):
        d = math.hypot(x1 - x0, y1 - y0)
        k = max(1, int(d // DENSIFY_M))
        for i in range(k):
            t = i / k
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z))
    if points:
        out.append((points[-1][0], points[-1][1], z))


def load(raw):
    pts, counts = [], {'contours': 0, 'contoursDroppedZero': 0, 'spots': 0, 'spotsDropped': 0, 'coast': 0}
    for d in sorted(glob.glob(os.path.join(raw, '*'))):
        # 압축 안에 도엽 폴더가 한 겹 더 있는 도엽이 있다(35913025) — 그 안으로 들어간다
        inner = os.path.join(d, os.path.basename(d))
        if os.path.isdir(inner) and not glob.glob(os.path.join(d, '*.shp')):
            d = inner
        p = os.path.join(d, 'N3L_F0010000')
        if os.path.exists(p + '.shp'):
            for sr in shapefile.Reader(p, encoding='euc_kr').iterShapeRecords():
                z = float(sr.record[1])
                if z <= 0:
                    counts['contoursDroppedZero'] += 1
                    continue
                counts['contours'] += 1
                densify(sr.shape.points, z, pts)
        p = os.path.join(d, 'N3P_F0020000')
        if os.path.exists(p + '.shp'):
            for sr in shapefile.Reader(p, encoding='euc_kr').iterShapeRecords():
                z = float(sr.record[0])
                if z <= 0:
                    counts['spotsDropped'] += 1
                    continue
                counts['spots'] += 1
                x, y = sr.shape.points[0]
                pts.append((x, y, z))
        p = os.path.join(d, 'N3L_E0080000')
        if os.path.exists(p + '.shp'):
            for sh in shapefile.Reader(p, encoding='euc_kr').iterShapes():
                counts['coast'] += 1
                densify(sh.points, 0.0, pts)
    a = np.array(pts, dtype=np.float64)
    # 같은 자리 점(도엽 경계에서 겹친 것) 하나로
    key = np.round(a[:, :2], 1)
    _, idx = np.unique(key, axis=0, return_index=True)
    return a[np.sort(idx)], counts


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    raw, tiles_txt, out = sys.argv[1:]
    if not os.path.isdir(raw) or not os.path.exists(tiles_txt):
        print('입력이 없다', file=sys.stderr)
        return 2
    pts, counts = load(raw)
    print('점', len(pts), counts, flush=True)
    cell = {}
    cx = np.floor(pts[:, 0] / CELL).astype(np.int64)
    cy = np.floor(pts[:, 1] / CELL).astype(np.int64)
    order = np.lexsort((cy, cx))
    cxs, cys = cx[order], cy[order]
    bounds = np.flatnonzero(np.r_[True, (cxs[1:] != cxs[:-1]) | (cys[1:] != cys[:-1]), True])
    for s, e in zip(bounds[:-1], bounds[1:]):
        cell[(int(cxs[s]), int(cys[s]))] = order[s:e]

    to5187 = Transformer.from_crs('EPSG:4326', 'EPSG:5187', always_xy=True)
    os.makedirs(os.path.join(out, str(ZOOM)), exist_ok=True)
    keys = [l.strip() for l in open(tiles_txt) if l.strip()]
    n = 2 ** ZOOM * TILE
    empty = 0
    for t, k in enumerate(keys):
        tx, ty = map(int, k.split('_'))
        # 패딩 포함 픽셀 중심 → 위경도 → 5187
        ii = np.arange(-PAD, TILE + PAD) + 0.5
        X, Y = np.meshgrid(tx * TILE + ii, ty * TILE + ii)
        lon = X / n * 360 - 180
        lat = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * Y / n))))
        ex, ny = to5187.transform(lon, lat)
        x0, x1, y0, y1 = ex.min() - BUF_M, ex.max() + BUF_M, ny.min() - BUF_M, ny.max() + BUF_M
        sel = [cell[c] for c in ((i, j) for i in range(int(x0 // CELL), int(x1 // CELL) + 1)
                                 for j in range(int(y0 // CELL), int(y1 // CELL) + 1)) if c in cell]
        if sel:
            idx = np.concatenate(sel)
            p = pts[idx]
            m = (p[:, 0] >= x0) & (p[:, 0] <= x1) & (p[:, 1] >= y0) & (p[:, 1] <= y1)
            p = p[m]
        if not sel or len(p) < 4:
            empty += 1
            continue  # 자료가 없는 타일은 쓰지 않는다 — 읽는 쪽이 「고도 없음」으로 원래 값을 지킨다
        q = np.column_stack([ex.ravel(), ny.ravel()])
        tri = Delaunay(p[:, :2])
        z = LinearNDInterpolator(tri, p[:, 2])(q)
        bad = np.isnan(z)
        if bad.any():
            z[bad] = NearestNDInterpolator(p[:, :2], p[:, 2])(q[bad])
        far = (cKDTree(p[:, :2]).query(q, distance_upper_bound=NODATA_M)[0] == np.inf).reshape(X.shape)
        z = gaussian_filter(z.reshape(X.shape), 2.0)
        z[far] = -32768  # 수치지형도 밖(받은 도엽 바깥) — 읽는 쪽이 「고도 없음」으로 본다
        z = z[PAD:PAD + TILE, PAD:PAD + TILE]
        v = np.clip(z + 32768, 0, 65535.99)
        r = np.floor(v / 256)
        g = np.floor(v - r * 256)
        b = np.floor((v - r * 256 - g) * 256)
        img = np.dstack([r, g, b]).astype(np.uint8)
        Image.fromarray(img, 'RGB').save(os.path.join(out, str(ZOOM), f'{k}.png'))
        if t % 100 == 0:
            print(f'{t}/{len(keys)}', flush=True)
    print(f'완료 {len(keys) - empty}장 · 자료 없어 건너뜀 {empty}장')
    return 0


if __name__ == '__main__':
    sys.exit(main())
