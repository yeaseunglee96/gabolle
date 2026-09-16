// 추천 목록에서 고른 저장·제외를 여행별로 기기에 남긴다 — S15P21E201-975.
//
// 그전에는 화면 상태만 바꾸고 아무 데도 안 적었다. 버튼을 누르면 "저장됨" 으로 바뀌지만
// 화면을 나갔다 들어오면 그대로 "저장" 이었다. 사용자는 이것을 "버튼이 잘 안 들린다" 고
// 적었다 — 눌리기는 하는데 남지 않는 것이 실제 증상이었다.
//
// 🔴 서버에 저장하지 않는다. 추천 후보의 저장·제외를 받는 API 가 아직 없고, 이 화면의
//    표시를 되살리는 데는 기기 저장으로 충분하다. 같은 판단을 CollectionProvider 가
//    먼저 했다(그 파일 머리말 참고). API 가 생기면 이 모듈의 두 함수만 바꾸면 된다.
//
// 🔴 여행마다 따로 적는다. 한 곳에 몰아 적으면 다른 여행에서 제외한 장소가 이번 여행에서도
//    제외된 것처럼 보인다 — 저장·제외는 그 여행의 후보에 대한 판단이지 장소 자체에 대한
//    판단이 아니다.
import AsyncStorage from '@react-native-async-storage/async-storage';

/** 되돌린 것(idle)은 안 적는다 — 아무 판단도 아닌 상태다. */
export type RecommendationAction = 'saved' | 'excluded';

export type RecommendationActions = Record<string, RecommendationAction>;

const PREFIX = '@gabolle/recommendation-actions:';

function keyOf(tripId: string) {
  return `${PREFIX}${tripId}`;
}

/**
 * 저장된 판단을 읽는다. 없거나 모양이 깨졌으면 빈 것으로 본다 — 이 값 때문에 추천 화면이
 * 안 열리는 일이 있으면 안 된다.
 */
export async function loadRecommendationActions(tripId: string): Promise<RecommendationActions> {
  if (!tripId) return {};
  try {
    const raw = await AsyncStorage.getItem(keyOf(tripId));
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const next: RecommendationActions = {};
    for (const [placeId, action] of Object.entries(parsed as Record<string, unknown>)) {
      if (action === 'saved' || action === 'excluded') next[placeId] = action;
    }
    return next;
  } catch {
    return {};
  }
}

/**
 * 판단 하나를 적는다. {@code action} 이 없으면 그 장소의 판단을 지운다(되돌리기).
 *
 * <p>읽고 고쳐 쓴다 — 화면이 들고 있는 값을 통째로 넘기게 하면, 아직 다 못 읽은 상태에서
 * 누른 한 번이 나머지를 전부 지운다.
 */
export async function saveRecommendationAction(
  tripId: string,
  placeId: string,
  action: RecommendationAction | null,
): Promise<void> {
  if (!tripId || !placeId) return;
  try {
    const current = await loadRecommendationActions(tripId);
    if (action) current[placeId] = action;
    else delete current[placeId];
    await AsyncStorage.setItem(keyOf(tripId), JSON.stringify(current));
  } catch {
    // 기기 저장이 막혀도 화면은 계속 돈다. 표시가 안 남을 뿐이다.
  }
}
