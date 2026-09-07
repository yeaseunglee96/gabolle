#!/usr/bin/env python3
"""
전국 PBF 에서 부산 전역을 뽑는다.

왜 Overpass 가 아니라 PBF 인가:
  부산 전역은 중구·동구의 241배다. 그것을 공용 Overpass 서버에 물으면
  막히고, 막히면 팀 전체가 몇 시간 못 쓴다. PBF 는 이미 받아둔 파일이라
  네트워크를 쓰지 않고, 몇 번을 다시 돌려도 남에게 폐가 되지 않는다.

출력은 NDJSON(한 줄에 객체 하나) 이다. Overpass 와 같은 모양을 유지하되
줄 단위로 흘려보낸다 — 전역 데이터를 통째로 JSON.parse 하면 메모리가 터진다.

  py collect/pbf_extract.py
  py collect/pbf_extract.py --pbf <경로>
"""
import argparse, json, os, sys, time
import osmium

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

WALK = {'footway', 'path', 'pedestrian', 'living_street', 'corridor'}
TRANSIT_NODE_KEYS = ('public_transport', 'railway')
POI_KEYS = ('amenity', 'shop', 'tourism', 'leisure')


def classify(tags):
    hw = tags.get('highway')
    if hw == 'steps':
        return 'stairs'
    if hw in WALK:
        return 'walk'
    if tags.get('foot') in ('yes', 'designated'):
        return 'walk'
    if hw:
        return 'road'
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pbf', default=os.path.join(ROOT, 'data/raw/osm/south-korea-latest.osm.pbf'))
    ap.add_argument('--out', default=os.path.join(ROOT, 'data/raw/pbf'))
    args = ap.parse_args()

    area = json.load(open(os.path.join(ROOT, 'config/area.json'), encoding='utf-8'))
    b = area['target']['bbox']
    S, W, N, E = b['south'], b['west'], b['north'], b['east']

    if not os.path.exists(args.pbf):
        sys.exit(f"PBF 가 없습니다: {args.pbf}\n  npm run collect:osm 을 먼저 돌리세요")
    os.makedirs(args.out, exist_ok=True)

    print(f"대상: {area['target']['name']}  bbox=({S},{W},{N},{E})")
    print(f"PBF : {args.pbf}  ({os.path.getsize(args.pbf)/1e6:.0f} MB)")

    files, counts = {}, {}

    def emit(topic, obj):
        if topic not in files:
            files[topic] = open(os.path.join(args.out, f'{topic}.ndjson'), 'w', encoding='utf-8')
            counts[topic] = 0
        files[topic].write(json.dumps(obj, ensure_ascii=False) + '\n')
        counts[topic] += 1

    # ── 1차: highway 가 붙은 way ────────────────────────────────────────────
    t0 = time.time()
    fp = (osmium.FileProcessor(args.pbf)
          .with_locations()
          .with_filter(osmium.filter.EntityFilter(osmium.osm.WAY))
          .with_filter(osmium.filter.KeyFilter('highway', 'foot')))
    seen = 0
    for w in fp:
        seen += 1
        if seen % 2_000_000 == 0:
            print(f"  way {seen:,} 훑음 / 채택 {sum(counts.values()):,} ({time.time()-t0:.0f}s)")
        tags = dict(w.tags)
        topic = classify(tags)
        if topic is None:
            continue
        geom = []
        inside = False
        try:
            for n in w.nodes:
                lat, lon = n.lat, n.lon
                geom.append({'lat': lat, 'lon': lon})
                if S <= lat <= N and W <= lon <= E:
                    inside = True
        except osmium.InvalidLocationError:
            continue                      # 위치 없는 노드가 섞인 way 는 버린다
        if not inside or len(geom) < 2:
            continue
        emit(topic, {'type': 'way', 'id': w.id, 'geometry': geom, 'tags': tags})
    print(f"  way 통과: {seen:,} 훑음, {time.time()-t0:.0f}s")

    # ── 2차: 정류장·POI 노드 ────────────────────────────────────────────────
    t1 = time.time()
    fp2 = (osmium.FileProcessor(args.pbf)
           .with_filter(osmium.filter.EntityFilter(osmium.osm.NODE))
           .with_filter(osmium.filter.EmptyTagFilter()))
    seen2 = 0
    for n in fp2:
        seen2 += 1
        lat, lon = n.location.lat, n.location.lon
        if not (S <= lat <= N and W <= lon <= E):
            continue
        tags = dict(n.tags)
        rec = {'type': 'node', 'id': n.id, 'lat': lat, 'lon': lon, 'tags': tags}
        if tags.get('highway') == 'bus_stop' or any(k in tags for k in TRANSIT_NODE_KEYS):
            emit('transit', rec)
        elif any(k in tags for k in POI_KEYS):
            emit('poi', rec)
        elif 'barrier' in tags or tags.get('highway') in ('crossing', 'elevator'):
            emit('barrier', rec)
    print(f"  node 통과: {seen2:,} 훑음, {time.time()-t1:.0f}s")

    for f in files.values():
        f.close()

    summary = {
        'at': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'area': area['target']['name'], 'bbox': b,
        'source': os.path.basename(args.pbf),
        'note': 'bbox 추출. 실제 부산 경계로 자르는 것은 staging 단계에서 한다.',
        'counts': counts, 'elapsedSec': round(time.time() - t0, 1),
    }
    json.dump(summary, open(os.path.join(args.out, '_summary.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=2)

    print('─' * 46)
    for k, v in sorted(counts.items(), key=lambda x: -x[1]):
        print(f'  {k:<10} {v:>9,}')
    print(f'총 {time.time()-t0:.0f}초')
    if not counts:
        sys.exit('아무것도 추출되지 않았습니다')


if __name__ == '__main__':
    main()
