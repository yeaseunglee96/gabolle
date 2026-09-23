// 「지역」 검색 — S15P21E201-1145.
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { placeSnapshotOf, searchOrigins, type OriginCandidate, type PlaceSnapshot } from '@/plan/origins';

/** 기록에 싣는 장소 스냅샷 — 숙소와 같은 한 벌이다(plan/origins.ts 의 PlaceSnapshot · S15P21E201-1536). */
export type StoryPlaceSnapshot = PlaceSnapshot;

export type RegionCandidate = {
  /** 화면에 크게 보이는 이름. */
  name: string;
  address: string;
  /** 우리 DB 장소일 때만 있다. 있으면 글에 그 장소를 잇는다. */
  placeId?: string;
  /**
   * 카카오·대체 목록 결과일 때만 있다. 예전에는 합칠 때 버려서 지역 글자만 남았다 —
   * 원글 28건 중 장소가 이어진 것이 5건이었다(2026-09-23 운영 DB).
   */
  place?: StoryPlaceSnapshot;
};

/** 주소에서 「구·군」을 뽑는다 — 지역 칸에 넣을 말. */
export function regionFromAddress(address: string): string {
  const found = (address ?? '').match(/([가-힣]+[구군])(\s|$)/);
  return found ? found[1] : '';
}

/** 고른 것을 지역 칸에 적을 글자로 바꾼다. */
export function regionLabelOf(candidate: RegionCandidate): string {
  const district = regionFromAddress(candidate.address);
  const name = (candidate.name ?? '').trim();
  if (!name) return district;
  if (!district || name === district || name.includes(district)) return name;
  return `${name} · ${district}`;
}

/** 두 갈래를 한 목록으로 합친다. */
export function mergeRegionCandidates(
  ours: Pick<PlaceSearchItem, 'placeId' | 'nameKo' | 'address'>[],
  kakao: (Pick<OriginCandidate, 'name' | 'address'> & Partial<OriginCandidate>)[],
  limit = 8,
): RegionCandidate[] {
  const merged: RegionCandidate[] = ours.map((place) => ({
    name: place.nameKo,
    address: place.address,
    placeId: place.placeId,
  }));
  const seen = new Set(merged.map((item) => `${item.name}|${item.address}`));
  for (const item of kakao) {
    const key = `${item.name}|${item.address}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const place = placeSnapshotOf(item);
    merged.push(place ? { name: item.name, address: item.address, place } : { name: item.name, address: item.address });
  }
  return merged.slice(0, limit);
}

export type RegionSearchResult = { state: 'success'; items: RegionCandidate[] } | { state: 'error' };

/** 두 곳에 동시에 묻고 합친다. */
export async function searchRegions(
  query: string,
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<RegionSearchResult> {
  const trimmed = (query ?? '').trim();
  // 서버가 두 글자 미만이면 거절한다 — 그 호출 자체를 안 나가게 여기서 먼저 거른다.
  if (trimmed.length < 2) return { state: 'success', items: [] };

  const [ours, kakao] = await Promise.all([
    searchPlacesByName(trimmed, signal).catch(() => null),
    searchOrigins(trimmed, accessToken, signal).then((r) => (r.state === 'success' ? r.items : null)).catch(() => null),
  ]);

  if (ours === null && kakao === null) return { state: 'error' };
  return { state: 'success', items: mergeRegionCandidates(ours ?? [], kakao ?? []) };
}
