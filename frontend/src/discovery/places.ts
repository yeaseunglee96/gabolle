import { apiRequest } from '@/api/client';

// jaehyeon 님 계약(2026-09-07, S15P21E201-476 · 다국어는 -430): GET /api/v1/places/{placeId}.
// nameKo·category·address·lat·lng·features·itineraryInclusion 은 이미 온다. addressEn·photoUrl·
// photoSource 는 값이 없으면 칸 자체가 안 온다(택시 카드의 addressEn 과 같은 규칙).
//
// openingHours·priceLevel(S15P21E201-478 착수 시 PlaceDetailResponse.java 원본으로 재확인):
// place_feature 의 OPENING_HOURS·PRICE_LEVEL 표식을 옮긴 자리다. 그 표식에 행이 있으면
// (VERIFIED·ESTIMATED·UNKNOWN 무엇이든) 실리고, 행이 아예 없으면(NOT_COLLECTED) 키 자체가
// 빠진다 — 그래서 optional 이다. value 의 JSON 구조는 데이터 담당이 아직 못 정했다고
// 문서에 적혀 있어(PlaceFeatureView.java) 화면은 "있다/없다"·VERIFIED 여부만 보고 값
// 자체는 그대로 문자열로 펼친다(숫자로 지레짐작해 서식을 걸지 않는다 — 시작 화면 흰
// 화면 사고, 상세설계서 v2 P-02).
//
// provenance·itineraryInclusion 의 정확한 칸 구조는 아직 화면에서 쓸 데가 없어 타입에 안
// 넣는다 — 모르는 것을 아는 척하지 않는다(CONTRIBUTING.md 0절). 쓸 데가 생기면 그때
// PlaceDetailResponse.java 로 다시 확인한다.
//
// photoUrl·photoSource 는 계약에는 있지만(2026-09-08 jaehyeon 님 확인) 아직 채우는 경로가
// 없어 늘 비어 있다 — 값이 없으면 NON_NULL 규칙 때문에 키 자체가 안 온다. 데이터 적재는
// S15P21E201-146(jaehyeon 님 담당, 미착수)이 끝나야 온다. 그래서 여기서는 선택 필드로만
// 받고, 화면에서는 없으면 지금처럼 자리표시만 보여준다.
export type PlaceFeature = { featureType: string; [key: string]: unknown };

// NOT_COLLECTED 는 DB 에 없는 값이고 응답 계층이 만들어 붙인다(백엔드 PlaceFeatureView) —
// "아직 수집 대상에 안 들어갔다"는 뜻이라 UNKNOWN("가서 봤는데 못 정했다")과 다음 행동이 다르다.
export type EvidenceStatus = 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN' | 'NOT_COLLECTED';
export type FeatureSlot = { value: unknown; evidenceStatus: EvidenceStatus };

export type Place = {
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  category: string;
  address: string;
  addressEn?: string;
  lat: number;
  lng: number;
  features: PlaceFeature[];
  photoUrl?: string;
  photoSource?: string;
  openingHours?: FeatureSlot;
  priceLevel?: FeatureSlot;
};

// S15P21E201-1015 — 값이 없는 이유가 둘인데 한 문구로 뭉개고 있었다.
//
// 🔴 「아직 안 알아봤다」(NOT_COLLECTED)와 「알아봤는데 못 정했다」(UNKNOWN)는 다음에 할 일이
// 서로 다르다. 앞은 수집 대상에 안 들어간 것이라 파이프라인을 손대야 하고, 뒤는 가서 봐야
// 한다. 사용자에게도 다르다 — 「안 알아봤다」를 「없다」처럼 읽히게 두면, 알레르기에서 사람이
// 다칠 수 있었던 것과 같은 종류의 거짓말이 된다(S15P21E201-996).
// 🔴 S15P21E201-1015-copy — 앞머리의 "?" 를 뗐다. "계단" 같은 항목 이름 바로 뒤에 이 문장이
// 붙으면 "계단 ? 아직 안 알아봤어요" 처럼 물음표가 문장 중간에 끼어 든 것처럼 보인다는
// 사용자 리포트가 있었다 — 실제로 물어보는 문장이 아닌데 물음표로 시작해서 생긴 일이다.
export function missingValueLabel(slot: FeatureSlot, tx: (ko: string, en: string) => string): string {
  return slot.evidenceStatus === 'NOT_COLLECTED'
    ? tx('아직 확인하지 않았어요', 'Not checked yet')
    : tx('알아봤지만 확인 못 했어요', 'Looked, but could not confirm');
}

// S15P21E201-1021 — 사진이 그 장소를 찍은 것이 아닐 수 있다.
//
// 관광사진갤러리에서 받은 축제 사진 35건을 사진 자체의 제목과 대조해 보니, 그 축제를 찍은
// 것은 1건이고 나머지는 **그 축제가 열리는 곳**을 찍은 것이었다(광안리 드론쇼 → 광안리
// 해수욕장 사진). 그냥 띄우면 「이 축제가 이렇게 생겼구나」로 읽힌다 — 알레르기에서 고친
// 것과 같은 종류다(S15P21E201-996). 다른 것을 같게 그리는 것.
//
// 🔴 출처 문구 하나에 싣지 않고 칸을 갈랐다. 「누가 준 사진인가」(photoSource)와 「무엇을 찍은
// 사진인가」(photoSubject)는 다른 질문이라, 한 칸에 넣으면 둘 중 하나는 반드시 거짓말이 된다.
// 그리고 한국어 문장에 섞어 두면 코드가 못 읽어서, 나중에 「행사장 사진은 빼자」 같은 것을
// 아예 못 한다. evidenceStatus 와 같은 방식이다 — 뜻이 갈리면 값을 갈라 둔다.
//
// SELF 는 축제만이 아니라 식당·해수욕장에도 뜻이 통해야 해서 고른 이름이다(EVENT 는 안 통한다).
export type PhotoSubject = 'SELF' | 'VENUE';

/**
 * 사진에 붙일 말 둘. 값이 없으면 null 이고, null 인 것은 화면에 줄을 만들지 않는다.
 *
 * 🔴 SELF 에는 배지를 안 단다. 그 장소를 찍은 사진인 것은 **기대한 대로**라 말할 것이 없고,
 * 예외에만 표를 다는 편이 예외를 눈에 띄게 한다. 칸 자체가 안 왔을 때도 마찬가지다 —
 * 모르는 것을 아는 척하지 않는다.
 */
export function photoLabels(
  photo: { photoSource?: string | null; photoSubject?: PhotoSubject | null },
  tx: (ko: string, en: string) => string,
): { badge: string | null; credit: string | null } {
  return {
    badge: photo.photoSubject === 'VENUE' ? tx('행사장 사진', 'Venue photo') : null,
    credit: photo.photoSource ? tx(`사진 제공: ${photo.photoSource}`, `Photo: ${photo.photoSource}`) : null,
  };
}

// 값이 있어도 VERIFIED·ESTIMATED·UNKNOWN 셋 다 화면에는 보여준다(정보 없음과 다르다) —
// UNKNOWN 은 "확인은 했는데 결과가 없다"는 뜻이라 그 자체가 정보다. 다만 추정값은
// "추정"이라고 붙여 확정값과 헷갈리지 않게 한다.
// 🔴 S15P21E201-478-opening-hours-raw-json — 문자열·숫자가 아닌 값을 JSON.stringify로
// 물러섰더니, 영업시간(openingHours)이 { raw: "매일 10:00-22:00" } 모양으로 오는 자리에서
// 화면에 {"raw":"매일 10:00-22:00"} 이 글자 그대로 찍혔다 — 사용자 리포트. raw 문자열 칸이
// 있으면 그것을 꺼내 쓰고, 정말 모르는 모양이면 JSON 대신 "확인했지만 형식을 읽지 못했어요"로
// 물러선다 — 속을 못 읽어도 사람이 읽을 문장이어야 한다(JSON 텍스트는 문장이 아니다).
function extractDisplayText(value: unknown, tx: (ko: string, en: string) => string): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (value && typeof value === 'object') {
    const hours = formatOpeningHoursValue(value, tx);
    if (hours) return hours;
    if (typeof (value as { raw?: unknown }).raw === 'string') return (value as { raw: string }).raw;
  }
  return tx('확인했지만 형식을 읽지 못했어요', "We checked, but couldn't read the format");
}

// 요일 순서는 월→일. 서버가 주는 byDay 는 사전이라 순서가 없다 — 여기서 정한다.
const WEEK: { key: string; ko: string; en: string }[] = [
  { key: 'mon', ko: '월', en: 'Mon' },
  { key: 'tue', ko: '화', en: 'Tue' },
  { key: 'wed', ko: '수', en: 'Wed' },
  { key: 'thu', ko: '목', en: 'Thu' },
  { key: 'fri', ko: '금', en: 'Fri' },
  { key: 'sat', ko: '토', en: 'Sat' },
  { key: 'sun', ko: '일', en: 'Sun' },
];

function rangesText(ranges: unknown): string | null {
  if (!Array.isArray(ranges) || ranges.length === 0) return null;
  const parts = ranges
    .map((r) => (Array.isArray(r) && typeof r[0] === 'string' && typeof r[1] === 'string' ? `${r[0]}~${r[1]}` : null))
    .filter((r): r is string => r !== null);
  return parts.length ? parts.join(', ') : null;
}

/**
 * 영업시간 값을 사람이 읽는 문장으로 바꾼다 — S15P21E201-1202.
 *
 * 🔴 서버는 「읽었다」고 말하는데 화면은 「못 읽었다」고 말하고 있었다.
 *    값이 `{ raw: {...}, byDay: {...}, status: "PARSED" }` 로 오는데,
 *    예전 코드는 `raw` 가 **문자열**일 때만 꺼냈다. 여기서는 객체라 그 분기를 못 타고
 *    물러섬 문장으로 떨어졌다. 읽을 수 있는 정보가 두 겹으로 있는데 사용자는 못 봤다.
 *
 * 읽을 것이 없으면 null 을 돌려 부르는 쪽이 지금처럼 물러서게 한다 — 여기서
 * 지어내지 않는다.
 */
export function formatOpeningHoursValue(value: unknown, tx: (ko: string, en: string) => string): string | null {
  if (!value || typeof value !== 'object') return null;
  const byDay = (value as { byDay?: unknown }).byDay;
  if (byDay && typeof byDay === 'object') {
    const rows = WEEK.map((day) => ({ day, text: rangesText((byDay as Record<string, unknown>)[day.key]) }));
    const known = rows.filter((row) => row.text !== null);
    if (known.length) {
      // 일곱 요일이 전부 같으면 「매일」 한 줄로 묶는다 — 같은 줄을 일곱 번 쓰지 않는다.
      const sameEveryDay = known.length === WEEK.length && known.every((row) => row.text === known[0].text);
      if (sameEveryDay) return tx(`매일 ${known[0].text}`, `Daily ${known[0].text}`);
      return known.map((row) => tx(`${row.day.ko} ${row.text}`, `${row.day.en} ${row.text}`)).join(' · ');
    }
  }
  const raw = (value as { raw?: unknown }).raw;
  if (typeof raw === 'string') return raw;
  if (raw && typeof raw === 'object' && typeof (raw as { hoursValue?: unknown }).hoursValue === 'string') {
    return (raw as { hoursValue: string }).hoursValue;
  }
  return null;
}

export function formatFeatureSlot(slot: FeatureSlot | undefined, tx: (ko: string, en: string) => string): string | null {
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const text = extractDisplayText(slot.value, tx);
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

// 로컬점수(LOCALITY_SCORE) 표식이 붙어 있는지만 확인한다. 표식 안의 값 칸 이름은 아직 몰라서
// 숫자를 꺼내 보여주지 않는다 — 있는지 없는지만 배지로 표시한다.
export function hasLocalityScore(place: Place) {
  return place.features.some((feature) => feature.featureType === 'LOCALITY_SCORE');
}

// 장소 종류(category) 값의 정확한 목록을 아직 못 받았다(field/placePhrases.ts의
// defaultTabForCategory와 같은 사정). 그래서 정확히 아는 척하지 않고, 식당·카페로 흔히
// 쓰이는 키워드가 들어 있으면 식음료 장소로 본다.
function isFoodPlace(category: string) {
  return /FOOD|RESTAURANT|CAFE|맛집|카페|식당/i.test(category);
}

// 안전 정보(알레르기·식단) — S15P21E201-478·-141. 대조표(user_place_code_map,
// S15P21E201-545 마이그레이션)의 짝: 알레르기 = ALLERGEN_TAG, 식단 = DIETARY_SUPPORT_TAG.
//
// 🔴 2026-09-15 정정 (S15P21E201-996). 아래 "행이 없으면 아무 표식도 안 남는다"는 낡았다 —
// 서버가 수집 안 된 종류에도 evidenceStatus:"NOT_COLLECTED" 행을 만들어 붙이도록 바뀌었다.
// 그래서 행의 존재만 세던 옛 판정은 뒤집혔다: 아무도 조사하지 않은 장소가 "확인됨"이 됐고,
// 화면은 "등록된 유발 성분이 없습니다"라고 말했다. 사람이 다칠 수 있는 정보다.
// 이제 행의 존재가 아니라 evidenceStatus 를 본다 — VERIFIED 만 확인으로 친다.
// ESTIMATED 는 원천이 확인해 준 값이 아니라 안전 표시에는 못 쓴다.
function hasVerifiedFeature(place: Place, featureType: string) {
  return place.features.some(
    (feature) => feature.featureType === featureType && (feature as { evidenceStatus?: EvidenceStatus }).evidenceStatus === 'VERIFIED',
  );
}

export function needsFoodSafetyCheck(place: Place) {
  if (!isFoodPlace(place.category)) return false;
  return !hasVerifiedFeature(place, 'ALLERGEN_TAG') || !hasVerifiedFeature(place, 'DIETARY_SUPPORT_TAG');
}

// S15P21E201-325: "확인 못 함"과 "확인했고 문제 없음"을 화면에서 다르게 보여줘야 한다 —
// 안 그러면 needsFoodSafetyCheck 가 false 인 자리에 아무것도 안 뜨고, 그 빈 자리를
// 사용자는 "안전하다고 확인됨"과 구분 못 한다. 식음료 장소가 아니면 이 표시 자체가 의미 없다.
export function hasFoodSafetyConfirmed(place: Place) {
  if (!isFoodPlace(place.category)) return false;
  return hasVerifiedFeature(place, 'ALLERGEN_TAG') && hasVerifiedFeature(place, 'DIETARY_SUPPORT_TAG');
}

function findFeature(place: Place, featureType: string): PlaceFeature | undefined {
  return place.features.find((feature) => feature.featureType === featureType);
}

function toFeatureSlot(feature: PlaceFeature | undefined): FeatureSlot | undefined {
  if (!feature) return undefined;
  return { value: (feature as { value?: unknown }).value, evidenceStatus: (feature as { evidenceStatus?: EvidenceStatus }).evidenceStatus ?? 'UNKNOWN' };
}

// 혼밥 안심(SOLO_FRIENDLY, S15P21E201-141·265) — 참거짓형(FLAG) 피처라 값이 boolean 이다.
// formatFeatureSlot 은 문자열·숫자만 그대로 보여주므로(그 밖은 JSON.stringify) boolean 은
// "true"/"false" 로 나가 사용자에게 뜻이 안 통한다 — 그래서 이 필드만 라벨을 따로 붙인다.
export function formatSoloFriendly(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'SOLO_FRIENDLY'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const label = slot.value === true ? tx('혼밥하기 좋아요', 'Good for solo dining') : tx('혼밥은 어려울 수 있어요', 'May not suit solo diners');
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${label} (추정)`, `${label} (est.)`) : label;
}

// 브레이크타임·라스트오더(BREAK_TIME·LAST_ORDER_TIME, S15P21E201-141·265) — 값 모양이 아직
// 마이그레이션 단계에서 확정되지 않았다(예상되는 모양만 문서에 있음). 그래서 예상 모양과
// 맞으면 보기 좋게 합치고, 안 맞으면 formatFeatureSlot 과 같은 안전한 문자열화로 물러선다 —
// 모르는 모양을 아는 척 파싱하지 않는다.
export function formatBreakTime(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'BREAK_TIME'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { start?: unknown; end?: unknown };
  const text = typeof value?.start === 'string' && typeof value?.end === 'string' ? `${value.start}–${value.end}` : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

export function formatLastOrderTime(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'LAST_ORDER_TIME'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { time?: unknown };
  const text = typeof value?.time === 'string' ? value.time : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

// 계단 유무(STAIRS_PRESENT, S15P21E201-540) — 참거짓형(FLAG) 피처, formatSoloFriendly와 같은
// 이유로 boolean 라벨을 따로 붙인다. "정보 없음 = 계단 없음"으로 읽지 않는다(마이그레이션
// 주석: "정보 없음은 UNKNOWN") — 모르면 모른다고 말한다.
export function formatStairsPresent(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'STAIRS_PRESENT'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const label = slot.value === true ? tx('계단 있음', 'Has stairs') : tx('계단 없음', 'No stairs');
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${label} (추정)`, `${label} (est.)`) : label;
}

// 경사도(SLOPE_PERCENT, S15P21E201-540) — 점수형 피처, 값은 숫자(%)다. formatFeatureSlot을
// 그대로 쓰면 "3.5"처럼 단위 없는 숫자만 나가 사용자가 뜻을 모른다 — % 를 붙인다.
/** 경사도 값에서 사람에게 보여줄 숫자를 꺼낸다. 몰라서 못 꺼내면 null (S15P21E201-1202). */
function slopeText(value: unknown): string | null {
  if (typeof value === 'number') return `${value}%`;
  if (value && typeof value === 'object') {
    const score = (value as { score?: unknown }).score;
    if (typeof score === 'number') return `${score}%`;
  }
  return null;
}

export function formatSlopePercent(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'SLOPE_PERCENT'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  // 🔴 S15P21E201-1202 — 여기에 `JSON.stringify` 가 있었고, 그것이 화면에
  //    `{"score":2.7,"radiusM":200,…}` 를 글자 그대로 찍었다. **478 에서 이미 한 번
  //    고쳤던 실수다** — 이 파일의 extractDisplayText 주석이 그 교훈을 적어 둔자리고,
  //    이 함수만 그 규칙을 안 거쳐서 서버 값이 숫자에서 객체로 바뀜 날 바로 드러났다.
  //
  //    서버는 {"score": 2.7, "radiusM": 200, "segments": 25, "walkLengthM": 8227} 로 준다.
  //    보여줄 것은 score 하나고, 나머지는 그 점수를 어떻게 재었는지다.
  const text = slopeText(slot.value);
  if (text === null) return tx('확인했지만 형식을 읽지 못했어요', "We checked, but couldn't read the format");
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

// 숙박 체크인·체크아웃(CHECK_IN_OUT, S15P21E201-141·852) — 숙박은 영업시간(OPENING_HOURS)
// 대신 이 표식이 온다(OpeningHoursReader.java 기준). 둘이 답하는 질문이 달라 한 자리에
// 안 섞는다 — "영업시간" 줄은 숙박에서 그냥 안 뜨고(표식 자체가 없으므로), 이 줄이 그
// 자리를 대신한다. checkIn·checkOut 은 하나만 있을 수 있다(원본 자료에 한쪽만 있는 경우).
export function formatCheckInOut(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'CHECK_IN_OUT'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { checkIn?: unknown; checkOut?: unknown };
  const checkIn = typeof value?.checkIn === 'string' ? value.checkIn : null;
  const checkOut = typeof value?.checkOut === 'string' ? value.checkOut : null;
  const text = checkIn && checkOut
    ? tx(`체크인 ${checkIn} · 체크아웃 ${checkOut}`, `Check-in ${checkIn} · Check-out ${checkOut}`)
    : checkIn
      ? tx(`체크인 ${checkIn}`, `Check-in ${checkIn}`)
      : checkOut
        ? tx(`체크아웃 ${checkOut}`, `Check-out ${checkOut}`)
        : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

// 장소 이름 한글·영문 병기(S15P21E201-264) — 언어 설정과 무관하게 "해운대 해수욕장 (Haeundae
// Beach)" 형태로 둘 다 보여준다. 영문 이름이 한국인 택시 기사에게는 쓸모없고, 한글 이름만
// 보여주면 영어 사용자가 못 읽는다. 영문이 없으면 괄호 없이 한국어 원문만 보여준다 — 빈
// 문자열을 보여주지 않는다.
export function getPlace(placeId: string, signal?: AbortSignal) {
  return apiRequest<Place>(`/api/v1/places/${encodeURIComponent(placeId)}`, { signal });
}

// 계약: backend/src/main/java/com/gabolle/backend/place/api/PlaceQueryController.java (S15P21E201-462).
// GET /api/v1/places?query=&limit= — query 와 facetType 은 정확히 하나만 보내야 한다(둘 다
// 없거나 둘 다 있으면 400). 여기서는 이름 검색만 쓰므로 query 만 보낸다.
// photoUrl·photoSource 는 S15P21E201-1125 에서 열렸다(백엔드 MR !992). 값이 없으면 칸이
// 안 오므로 optional 이다. 🔴 사진을 그리면 출처도 같이 그린다 — 공공누리 이용 조건이다.
export type PlaceSearchItem = { placeId: string; nameKo: string; nameEn: string | null; category: string; address: string; addressEn?: string; lat: number; lng: number; photoUrl?: string | null; photoSource?: string | null };

type PlacePageDto = { items: PlaceSearchItemDto[]; limit: number; nextCursor: string | null; hasNext: boolean; rankTruncated: boolean };
export type PlaceSearchItemDto = { placeId: string; nameKo: string; nameEn: string | null; category: string; address: string; addressEn?: string; lat: number; lng: number; matchedField: 'NAME_KO' | 'NAME_EN' | null; photoUrl?: string | null; photoSource?: string | null };

/**
 * 목록 응답 한 건을 화면이 쓰는 모양으로 옮긴다.
 *
 * 🔴 칸을 하나씩 열거한다. 서버가 주는 것 중 화면이 안 쓰는 것(matchedField)을 안 들고
 *    오려는 것인데, 그래서 **칸을 더할 때 여기도 같이 고쳐야 한다.** 타입에만 더하면
 *    타입 검사는 통과하고 값만 조용히 사라진다.
 *
 *    실제로 그럴 뻔했다 — S15P21E201-1195(영문 주소). 그래서 옮기는 일을 여기 한 곳으로
 *    모으고, 옆 시험이 「일부러 뺀 것 말고는 하나도 안 버린다」를 잰다. 다음에 어떤 칸을
 *    더해도 같은 자리에서 걸린다.
 */
export function toPlaceSearchItem(dto: PlaceSearchItemDto): PlaceSearchItem {
  const { placeId, nameKo, nameEn, category, address, addressEn, lat, lng, photoUrl, photoSource } = dto;
  return { placeId, nameKo, nameEn, category, address, addressEn, lat, lng, photoUrl, photoSource };
}

/** 위 함수가 **일부러** 안 들고 오는 칸. 시험이 이 목록만 예외로 친다. */
export const PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE = ['matchedField'] as const;

export async function searchPlacesByName(query: string, signal?: AbortSignal): Promise<PlaceSearchItem[]> {
  const dto = await apiRequest<PlacePageDto>(`/api/v1/places?query=${encodeURIComponent(query)}&limit=8`, { signal });
  return dto.items.map(toPlaceSearchItem);
}

/** 부산 전체에서 특정 로컬 갈래에 해당하는 장소를 찾는다. 거리 제한은 적용하지 않는다. */
export async function getPlacesByFacet(
  facetType: string,
  facetKey: string,
  limit = 20,
  signal?: AbortSignal,
): Promise<PlaceSearchItem[]> {
  const query = new URLSearchParams({ facetType, facetKey, limit: String(limit) });
  const dto = await apiRequest<PlacePageDto>(`/api/v1/places?${query.toString()}`, { signal });
  return dto.items.map(toPlaceSearchItem);
}
