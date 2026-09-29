// 지도의 「경사」·「그늘」 겹 — 여행 범위 여섯 곳의 구간 자료를 받아 정차지 둘레만 선으로 그린다 (S15P21E201-1569).
//
// 자료는 tools/build-mobility-layers.mjs 가 bigData 구간 자료에서 잘라 만든 public/layers/<지역>.json 이다.
// 웹은 같은 사이트, 앱은 API 주소(운영에서는 같은 사이트)에서 받는다.
//
// 🔴 선의 기준은 파일이 갖는다(steepPermille = 온톨로지의 휠체어 경사로 기준 1:12). 앱에 숫자를 따로 박지 않는다.
// 🔴 정차지 둘레(STOP_RADIUS_M)만 그린다 — 지역 전체(수천 구간)를 다 그리면 지도가 무겁고 여행과 상관없는 길까지 칠한다.
import { useEffect, useMemo, useState } from 'react';

import { API_BASE_URL } from '@/api/client';
import { color } from '@/design/tokens';
import type { MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';

export type MobilityLayerKind = 'slope' | 'shade';

/** [경사 천분율 | null, 건물 그림자 백분율 | null, [경도, 위도, 경도, 위도, …]] */
type Seg = [number | null, number | null, number[]];
export type MobilityLayerFile = { v: number; area: string; steepPermille: number; slopeBasis?: string; shadowBasis: string; segs: Seg[] };

/** 백엔드 TravelArea 와 같은 중심·반경(+1km, 파일을 만들 때와 같다). 정차지가 어느 파일에 드는지 가른다. */
const AREAS: Record<string, [number, number, number]> = {
  HAEUNDAE: [35.1587, 129.1604, 3500], GWANGALLI: [35.1532, 129.1186, 3000], NAMPO: [35.0980, 129.0306, 3000],
  SEOMYEON: [35.1578, 129.0594, 3000], YEONGDO: [35.0911, 129.0682, 4000], SONGJEONG: [35.1786, 129.1996, 3000],
};
/** 정차지에서 이만큼 안의 구간만 그린다. 걸어서 몇 분 거리. */
export const STOP_RADIUS_M = 600;

function meters(aLat: number, aLng: number, bLat: number, bLng: number) {
  const r = (d: number) => (d * Math.PI) / 180;
  const h = Math.sin(r(bLat - aLat) / 2) ** 2 + Math.cos(r(aLat)) * Math.cos(r(bLat)) * Math.sin(r(bLng - aLng) / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** 정차지가 드는 여행 범위 — 없으면 빈 목록(파일이 없는 곳은 겹을 못 그린다). */
export function areasFor(stops: MapStop[]): string[] {
  return Object.entries(AREAS)
    .filter(([, [lat, lng, radius]]) => stops.some((stop) => meters(lat, lng, stop.latitude, stop.longitude) <= radius))
    .map(([code]) => code);
}

/**
 * 파일을 선으로 — 정차지 둘레만.
 *   · 경사: 파일의 기준(steepPermille)을 «넘는» 구간만 빨강(> — 서버는 기준 «이하»를 통과로 본다, slopeGrades.ts 주석).
 *     파일의 경사는 천분율 정수라(83 = 8.3%) 83 은 안 칠하고 84(8.4%)부터 칠한다. 8.33~8.35% 는 반올림으로 83 이 되어
 *     안 칠해진다 — 정수로 적은 파일에서는 가를 수 없는 폭이다. 그 아래는 안 그린다 — 「가파른 길」을 보여 주는 겹이다
 *   · 그늘: 건물 그림자가 있는 구간을 파랑으로, 짙을수록 그늘이 많다. 선을 따로 긋지 않는다 — 온톨로지가 그늘 기준을 안 정했다
 */
export function layerLines(files: MobilityLayerFile[], kind: MobilityLayerKind, stops: MapStop[]): MapRouteLayer[] {
  const lines: MapRouteLayer[] = [];
  for (const file of files) {
    file.segs.forEach(([slope, shadow, flat], index) => {
      const draw = kind === 'slope' ? slope != null && slope > file.steepPermille : shadow != null && shadow > 0;
      if (!draw) return;
      const path = [];
      for (let i = 0; i + 1 < flat.length; i += 2) path.push({ latitude: flat[i + 1], longitude: flat[i] });
      if (path.length < 2) return;
      if (!path.some((p) => stops.some((stop) => meters(p.latitude, p.longitude, stop.latitude, stop.longitude) <= STOP_RADIUS_M))) return;
      lines.push({
        id: `${kind}-${file.area}-${index}`,
        color: kind === 'slope' ? color.state.danger : color.state.info,
        stops: [],
        path,
        estimated: false,
        weight: 4,
        opacity: kind === 'slope' ? 0.7 : 0.15 + 0.7 * ((shadow ?? 0) / 100),
      });
    });
  }
  return lines;
}

const cache = new Map<string, Promise<MobilityLayerFile | null>>();

/** 🔴 못 받은 것(null)은 캐시에서 지운다 — 한 번의 실패가 새로 고침 전까지 「못 불러옴」으로 굳지 않게. 끄고 다시 켜면 다시 묻는다. */
function loadArea(code: string): Promise<MobilityLayerFile | null> {
  let pending = cache.get(code);
  if (!pending) {
    pending = fetch(`${API_BASE_URL}/layers/${code}.json`)
      .then((response) => (response.ok ? (response.json() as Promise<MobilityLayerFile>) : null))
      .catch(() => null)
      .then((file) => {
        if (file == null || !Array.isArray(file.segs)) { cache.delete(code); return null; }
        return file;
      });
    cache.set(code, pending);
  }
  return pending;
}

/** 시험용. */
export function clearMobilityLayerCache() { cache.clear(); }

/**
 * 켠 겹의 형편 — 🔴 셋을 가른다(전에는 셋 다 「불러오는 중」이었다 — 영원히).
 *   · loading  받는 중
 *   · outside  정차지가 자료가 있는 여섯 지역 밖이다(areasFor 가 빈 목록) — 받을 파일이 없다
 *   · failed   받을 파일이 있는데 하나도 못 받았다
 *   · ready    받았다(partial 이면 일부 지역만)
 * off 는 켜지 않은 것.
 */
export type MobilityLayerStatus = 'off' | 'loading' | 'outside' | 'failed' | 'ready';

export type MobilityLayerResult = {
  lines: MapRouteLayer[];
  /** 파일이 적은 기준 한 줄(한국어 원문). 받기 전이거나 파일에 없으면 null. */
  basis: string | null;
  status: MobilityLayerStatus;
  /** 받을 파일 가운데 일부를 못 받았다 — 그린 것이 전부가 아니다. */
  partial: boolean;
};

/** 켠 겹의 선들 — 끄면 빈 목록. 받는 동안에도 빈 목록이다(지도는 그대로 뜬다). */
export function useMobilityLayer(kind: MobilityLayerKind | null, stops: MapStop[]): MobilityLayerResult {
  const codes = useMemo(() => (kind ? areasFor(stops) : []), [kind, stops]);
  const codesKey = codes.join(',');
  // 어느 지역 묶음을 받은 결과인지 같이 둔다 — 묶음이 바뀌면 새 결과가 올 때까지 「받는 중」이다.
  const [loaded, setLoaded] = useState<{ key: string; files: MobilityLayerFile[]; failed: number } | null>(null);
  useEffect(() => {
    if (!codes.length) { setLoaded(null); return undefined; }
    let alive = true;
    void Promise.all(codes.map(loadArea)).then((got) => {
      if (!alive) return;
      const files = got.filter((file): file is MobilityLayerFile => file != null);
      setLoaded({ key: codesKey, files, failed: got.length - files.length });
    });
    return () => { alive = false; };
  }, [codesKey]); // eslint-disable-line react-hooks/exhaustive-deps
  const current = loaded && loaded.key === codesKey ? loaded : null;
  const files = useMemo(() => current?.files ?? [], [current]);
  const lines = useMemo(() => (kind ? layerLines(files, kind, stops) : []), [files, kind, stops]);
  const status: MobilityLayerStatus = !kind ? 'off'
    : !codes.length ? 'outside'
      : !current ? 'loading'
        : current.files.length === 0 ? 'failed'
          : 'ready';
  const basis = kind === 'shade' ? files[0]?.shadowBasis ?? null : kind === 'slope' ? files[0]?.slopeBasis ?? null : null;
  return { lines, basis, status, partial: status === 'ready' && (current?.failed ?? 0) > 0 };
}
