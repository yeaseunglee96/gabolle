// 택시 목적지 고르기 — S15P21E201-1141.
//
// 🔴 현장 도구의 택시 탭은 **어디를 가든 흰여울문화마을을 보여줬다.** 목적지·주소·안내문이
//    파일에 박혀 있었다. 다른 곳에 가려는 사람에게는 「틀린 주소를 자신 있게 보여주는 화면」
//    이고, 이 앱에서 가장 나쁜 실패다 — 그 카드를 기사에게 보여주면 엉뚱한 데로 간다.
//
// 🔴 진짜 택시 카드는 이미 있었다 (`app/taxi-card/[id].tsx`, 서버는
//    `GET /api/v1/places/{placeId}/taxi-card`). 가는 길이 장소 상세와 경로 상세 둘뿐이라
//    현장 도구에서 「택시」를 누른 사람은 그 카드를 영영 못 만났다. 길을 하나 낸다.
//
// 🔴 이 파일은 **화면이 없다.** 검색을 언제 쏘고 결과를 어떻게 읽을지만 담는다 —
//    그래야 「두 글자 미만이면 안 쏜다」 같은 것을 눈이 아니라 시험으로 잴 수 있다.
import { ApiClientError } from '@/api/client';
import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';

export type TaxiDestinationOutcome =
  | { state: 'idle' }
  | { state: 'ready'; items: PlaceSearchItem[] }
  | { state: 'empty' }
  | { state: 'blocked'; reason: 'signed-out' | 'not-built' | 'server' | 'error' };

/**
 * 입력한 글자를 검색어로 다듬는다.
 *
 * 🔴 사이 공백을 하나로 줄인다. 「해운대  해수욕장」처럼 두 칸을 친 사람이 결과를 못 받는
 * 일이 실제로 생긴다 — 사람은 자기가 공백을 두 번 쳤다는 것을 모른다.
 */
export function normalizeDestinationQuery(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ');
}

/**
 * 지금 검색을 쏠 것인가.
 *
 * 🔴 한 글자로는 안 쏜다. 「부」 한 글자에 부산 전체가 걸려 목록이 의미를 잃고, 글자를 칠
 * 때마다 서버를 때린다. **두 글자**가 한국어에서 뜻이 생기는 최소 단위다.
 *
 * 🔴 공백만 친 것은 입력이 아니다. 그걸 검색으로 치면 「결과가 없어요」가 떠서, 사용자는
 * 자기가 아무것도 안 쳤다는 것을 모른 채 앱이 고장났다고 읽는다.
 */
export const MIN_DESTINATION_QUERY_LENGTH = 2;

export function canSearchDestination(raw: string): boolean {
  return normalizeDestinationQuery(raw).length >= MIN_DESTINATION_QUERY_LENGTH;
}

function blockedReason(error: unknown): Extract<TaxiDestinationOutcome, { state: 'blocked' }>['reason'] {
  if (!(error instanceof ApiClientError)) return 'error';
  if (error.status === 401 || error.status === 403) return 'signed-out';
  if (error.status === 404 || error.status === 501) return 'not-built';
  if (error.status >= 500) return 'server';
  return 'error';
}

/**
 * 목적지 후보를 찾는다.
 *
 * 🔴 **좌표가 없는 장소는 버린다.** 택시 카드는 결국 「여기로 가 주세요」인데, 좌표도 주소도
 * 없는 줄을 고르게 두면 기사에게 보여줄 것이 없는 카드가 나온다. 고를 수 없는 것을 목록에
 * 두지 않는 편이, 골랐다가 빈 카드를 보는 것보다 낫다.
 *
 * 🔴 결과 0개는 **실패가 아니다.** 정말로 그런 이름의 장소가 없을 수 있다. 화면이 「없어요」
 * 라고 말하면 되고 그것은 사실이다 — 여기서 blocked 로 바꾸면 없는 고장을 만들어 낸다.
 */
export async function searchTaxiDestinations(raw: string, signal?: AbortSignal): Promise<TaxiDestinationOutcome> {
  const query = normalizeDestinationQuery(raw);
  if (query.length < MIN_DESTINATION_QUERY_LENGTH) return { state: 'idle' };
  try {
    const found = await searchPlacesByName(query, signal);
    const items = found.filter((item) => item.placeId && item.nameKo && (item.address || Number.isFinite(item.lat)));
    if (!items.length) return { state: 'empty' };
    return { state: 'ready', items };
  } catch (error) {
    return { state: 'blocked', reason: blockedReason(error) };
  }
}

/**
 * 목록의 둘째 줄에 적을 말.
 *
 * 🔴 주소가 없으면 **아무것도 안 적는다.** 「정보 없음」 같은 자리표시를 남기면 줄만 차지하고
 * 아무것도 알려주지 않는다 — 택시 카드 화면이 영문 주소에 대해 이미 같은 규칙을 쓴다.
 */
export function destinationSubtitle(item: PlaceSearchItem): string | null {
  const address = item.address?.trim();
  return address ? address : null;
}
