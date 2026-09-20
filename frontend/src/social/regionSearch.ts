// 「지역」 검색 — S15P21E201-1145.
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { searchOrigins, type OriginCandidate } from '@/plan/origins';

export type RegionCandidate = {
  /** 화면에 크게 보이는 이름. */
  name: string;
  address: string;
  /**
   * 우리 DB 장소일 때만 있다. 있으면 글에 그 장소를 잇고, 없으면 지역 글자만 남는다.
   * 카카오 결과에는 일부러 넣지 않는다 — 넣을 값이 있어도 저장하면 안 되기 때문이다.
   */
  placeId?: string;
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
  kakao: Pick<OriginCandidate, 'name' | 'address'>[],
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
    merged.push({ name: item.name, address: item.address });
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
