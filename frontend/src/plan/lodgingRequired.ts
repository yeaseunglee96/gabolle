// 1박 이상 여행은 숙소를 골라야 일정을 만든다 — S15P21E201-1584 (사용자 결정 2026-09-24). 당일치기는 예외.
//
// 숙소를 모르면 서버는 둘째 날 아침도 처음 출발지(역)에서 나선다고 보고, 첫날 밤엔 돌아갈 곳이 없다.
// 모르는 곳을 지어내지 않는 대신 앱의 흐름에서 숙소를 반드시 고르게 한다. 서버도 같은 규칙으로 막는다
// (TRIP_VALIDATION_FAILED · accommodation) — 앱이 먼저 막아서 단추에서 이유를 말한다.
import { lodgingAreaCodeOf, type PlaceSnapshot } from './origins';

type LodgingFields = {
  startDate: string;
  endDate: string;
  lodgingPlace: PlaceSnapshot | null;
  lodgingLat: number | null;
  lodgingLng: number | null;
  /** 질문 화면의 초안에만 있다 — 홈 시작 바 값에는 없다. */
  accommodationPlace?: { placeId: string } | null;
};

/** 1박 이상인가 — 오는 날이 가는 날보다 뒤. 날짜가 비면 아직 모른다(false). */
export function isOvernight(startDate: string, endDate: string): boolean {
  return Boolean(startDate && endDate && endDate > startDate);
}

/**
 * 서버가 숙소로 받는 것이 있나. 🔴 여행 만들기 요청(`toCreateTripPayload`)이 싣는 세 칸 — 숙소 장소 번호 ·
 * 검색한 호텔 · 숙소 동네 — 과 같은 답이어야 한다. 앱은 통과시켰는데 서버가 막거나, 그 반대가 되면 안 된다.
 * 둘이 같은지는 시험이 지킨다.
 */
export function hasLodging(value: Omit<LodgingFields, 'startDate' | 'endDate'>): boolean {
  return Boolean(value.accommodationPlace?.placeId || value.lodgingPlace || lodgingAreaCodeOf(value.lodgingLat, value.lodgingLng));
}

/** 숙소를 골라야 하는데 안 골랐나. */
export function lodgingMissing(value: LodgingFields): boolean {
  return isOvernight(value.startDate, value.endDate) && !hasLodging(value);
}
