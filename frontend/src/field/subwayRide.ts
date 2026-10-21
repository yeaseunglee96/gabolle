// 지하철 한 구간 — 어느 방면을 타고, 몇 정거장, 어느 출구로 — S15P21E201-1881 (UI 캔버스 ⑲-1).
//
// 🔴 외국인이 지하철에서 가장 많이 틀리는 것은 «반대 방향» 과 «출구» 다. 전에는 「1호선 · 서면에서 타서 자갈치에서 내림」
//    뿐이라, 승강장에서 어느 쪽 열차인지(전광판은 「다대포해수욕장 방면」으로 쓴다) 스스로 알아내야 했다.
//
// 근거 — 지어낸 값은 없다:
//   - 방면·정거장 수: 부산교통공사 열차시각표로 확인된 역 순서(busanSubwayLines.json). 탄 역에서 내린 역 쪽 끝이 방면이다.
//   - 출구: OpenStreetMap 의 출구 번호·좌표(busanSubwayExits.json, ODbL — 앱 「공공데이터 출처」에 표기). 목적지까지
//     직선거리가 가장 짧은 출구다. 실제 걷는 길과 다를 수 있어 「가장 가까운 출구」라고만 말한다.
//   - 승강장 모양(섬식·상대식)과 화장실 위치는 확인된 자료가 없어 말하지 않는다(시안 ⑲-3 은 자료가 생기면).
import linesData from '@/field/busanSubwayLines.json';
import exitsData from '@/field/busanSubwayExits.json';

const LINES = linesData.lines as Record<string, string[]>;
const EXITS = exitsData.exits as Array<[string, string, number, number]>;

/** 「서면역(1호선)」·「서면역」 → 「서면」. 서버 노선망의 환승역 이름에는 호선 표시가 붙는다. */
export function bareStation(name: string): string {
  return name.trim().replace(/\s*\([^)]*\)$/, '').replace(/역$/, '').trim();
}

/** 서버 경로 단계의 안내 문장(TransitRouteAdapter.toLeg)에서 탄 역·내린 역. 모양이 다르면 null. */
export function parseRideGuidance(guidance: string): { from: string; to: string } | null {
  const m = guidance.trim().match(/^(.+?)에서 .+?을\(를\) 타고 (.+?)에서 내립니다\.?$/);
  return m ? { from: bareStation(m[1]), to: bareStation(m[2]) } : null;
}

export type SubwayRide = {
  /** 호선 숫자 — 「1」 */
  line: string;
  from: string;
  to: string;
  /** 타야 하는 열차의 방면(내리는 쪽 종점) — 전광판 글자와 같다 */
  towards: string;
  /** 반대 방면 — 이쪽을 타면 멀어진다 */
  opposite: string;
  /** 지나는 정거장 수(내리는 역까지) */
  stopCount: number;
};

/** 「1호선」 구간의 방면·정거장 수. 역을 역 순서에서 못 찾으면 null — 짐작하지 않는다. */
export function subwayRide(lineName: string, from: string, to: string): SubwayRide | null {
  const line = lineName.replace(/호선$/, '').trim();
  const order = LINES[line];
  if (!order) return null;
  const i = order.indexOf(bareStation(from));
  const j = order.indexOf(bareStation(to));
  if (i < 0 || j < 0 || i === j) return null;
  const forward = j > i;
  return {
    line,
    from: order[i],
    to: order[j],
    towards: forward ? order[order.length - 1] : order[0],
    opposite: forward ? order[0] : order[order.length - 1],
    stopCount: Math.abs(j - i),
  };
}

const metres = (aLat: number, aLng: number, bLat: number, bLng: number) => {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
};

/** 목적지에서 직선거리가 가장 가까운 그 역의 출구. 그 역의 출구 자료가 없으면 null. */
export function nearestExit(station: string, lat: number, lng: number): { ref: string; distanceM: number } | null {
  const name = bareStation(station);
  let best: { ref: string; distanceM: number } | null = null;
  for (const [st, ref, eLat, eLng] of EXITS) {
    if (st !== name) continue;
    const d = metres(lat, lng, eLat, eLng);
    if (!best || d < best.distanceM) best = { ref, distanceM: d };
  }
  // 1.5km 넘게 떨어진 목적지면 「이 출구로」가 뜻이 없다 — 지하철 뒤에 버스·걷기가 더 있는 경로다.
  return best && best.distanceM <= 1500 ? best : null;
}
