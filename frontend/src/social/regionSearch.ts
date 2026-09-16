// 「지역」 검색 — S15P21E201-1145.
//
// 결과가 두 갈래이고 **취급이 다르다.**
//
//   우리 DB 장소 (GET /api/v1/places?query=)  → placeId 가 온다. 글에 이을 수 있다
//   카카오 중개  (GET /api/v1/origins?query=) → 이름·주소만 쓴다. 저장하지 않는다
//
// 🔴 카카오 결과를 우리 DB 에 장소로 저장하면 팀 규칙 위반이다. bigData/CLAUDE.md 1절 —
// *"네이버·카카오 지도 데이터를 저장하지 않는다. 카카오 공식 답변 기준으로 Local API
// 응답은 별도 저장이 금지되고, 로드뷰 이미지의 수집·재배포도 금지다."*
//
// 그래서 카카오 결과는 **사람이 고른 글자를 region 에 옮겨 적는 데까지만** 쓴다.
// 화면에 보여주고 고르게 하는 것은 저장이 아니다.
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { searchOrigins, type OriginCandidate } from '@/plan/origins';

export type RegionCandidate = {
  /** 화면에 크게 보이는 이름. */
  name: string;
  address: string;
  /**
   * 🔴 우리 DB 장소일 때만 있다. 있으면 글에 그 장소를 잇고, 없으면 지역 글자만 남는다.
   * 카카오 결과에는 **일부러 넣지 않는다** — 넣을 값이 있어도 저장하면 안 되기 때문이다.
   */
  placeId?: string;
};

/**
 * 주소에서 「구·군」을 뽑는다 — 지역 칸에 넣을 말.
 *
 * 「부산광역시 해운대구 우동 1394」 → 「해운대구」
 *
 * 🔴 못 뽑으면 주소를 통째로 쓰지 않고 **빈 값**을 준다. 부르는 쪽이 장소 이름으로
 * 대신 채운다 — 지역 칸에 전체 주소가 들어가면 카드가 주소로 뒤덮인다.
 */
export function regionFromAddress(address: string): string {
  const found = (address ?? '').match(/([가-힣]+[구군])(\s|$)/);
  return found ? found[1] : '';
}

/** 고른 것을 지역 글자로 바꾼다 — 구·군이 있으면 그것, 없으면 이름. */
export function regionLabelOf(candidate: RegionCandidate): string {
  return regionFromAddress(candidate.address) || candidate.name;
}

/**
 * 두 갈래를 한 목록으로 합친다.
 *
 * 🔴 **우리 장소가 먼저다.** 글에 이을 수 있는 쪽이 위에 있어야 사람이 그걸 고르고,
 * 그래야 「이 글이 어느 장소 이야기인가」가 남는다. 부산 장소가 2,700곳 가까이 있어
 * 대부분 여기서 잡힌다.
 *
 * 같은 곳이 양쪽에 있으면 **우리 것만 남긴다** — 이름과 주소가 같으면 같은 곳으로 본다.
 */
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

/**
 * 두 곳에 동시에 묻고 합친다.
 *
 * 🔴 한쪽이 실패해도 다른 쪽 결과는 보여준다. 카카오가 죽었다고 우리 장소까지 안 보이면
 * 사용자는 「검색이 고장났다」고 읽는다 — 실제로는 절반만 고장난 것이다.
 */
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
