import { txf } from '@/i18n/format';
import { apiRequest } from '@/api/client';
import type { LocalNames } from '@/discovery/localNames';

// openingHours·priceLevel — place_feature 의 OPENING_HOURS·PRICE_LEVEL 표식. 행이 있으면
// 상태와 무관하게 실리고 행이 없으면 키 자체가 빠져서 optional 이다. value 의 JSON 구조가
// 미확정이라 화면은 「있다/없다」와 VERIFIED 여부만 보고 값은 문자열로 펼친다.
export type PlaceFeature = { featureType: string; [key: string]: unknown };

// NOT_COLLECTED 는 응답 계층이 만들어 붙이는 값 — 「아직 수집 대상 밖」이라 UNKNOWN(「확인했으나
// 결과 없음」)과 다음 행동이 다르다.
export type EvidenceStatus = 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN' | 'NOT_COLLECTED';
export type FeatureSlot = { value: unknown; evidenceStatus: EvidenceStatus };

export type Place = {
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  /** 일본어·중국어 이름 — 관광공사가 번역해 둔 곳만(S15P21E201-1859). 없으면 칸째 빠져 온다. */
  localNames?: LocalNames;
  category: string;
  address: string;
  addressEn?: string;
  lat: number;
  lng: number;
  features: PlaceFeature[];
  photoUrl?: string;
  photoSource?: string;
  // 사진 피사체 구분용. 값이 없으면 칸 자체가 안 온다.
  photoSubject?: PhotoSubject | null;
  photoLicense?: PhotoLicense | null;
  // 장소 상세의 사진 여러 장(S15P21E201-1839). 순서대로 오고 [0] 이 대표 사진(= photoUrl)이다.
  // 한 장도 없으면 칸 자체가 안 온다 — 그때는 위 photoUrl·photoSource 로 지금처럼 그린다(placePhotos).
  photos?: PlacePhoto[];
  openingHours?: FeatureSlot;
  priceLevel?: FeatureSlot;
};

// 값이 없는 이유 둘의 구분.
export function missingValueLabel(slot: FeatureSlot, tx: (ko: string, en: string) => string): string {
  return slot.evidenceStatus === 'NOT_COLLECTED'
    ? tx('아직 확인하지 않았어요', 'Not checked yet')
    : tx('알아봤지만 확인 못 했어요', 'Looked, but could not confirm');
}

// 사진의 피사체가 그 장소가 아닐 수 있음.
export type PhotoSubject = 'SELF' | 'VENUE';

/**
 * 사진의 라이선스 — 위키미디어 커먼즈 사진(CC BY·CC BY-SA 등)에만 온다(S15P21E201-1610, 백엔드 S15P21E201-1606).
 * 🔴 그 사진들은 출처와 함께 «라이선스 이름과 링크»를 보여야 쓸 수 있다. url 은 퍼블릭 도메인이면 null.
 *    관광공사 공공누리 사진에는 이 칸이 없다 — 그 표기는 photoSource 가 진다.
 */
export type PhotoLicense = { name: string; url?: string | null; filePage?: string | null };

/** 장소 사진 한 장 — 출처(source)는 사진마다 다르다(「출처 : 부산관광아카이브」·「Google 지도 · 사진 …」). license 는 없을 수 있다. */
export type PlacePhoto = { url: string; source?: string | null; license?: PhotoLicense | null };

/**
 * 장소 상세에 그릴 사진 목록 — photos 가 있으면 그것, 없으면 옛 칸(photoUrl·photoSource·photoLicense) 한 장(S15P21E201-1839).
 * 🔴 서버가 photos 를 아직 안 보내는 판과도 맞아야 한다 — 그래서 옛 칸으로 물러서는 길을 지운다면 여기서 지운다.
 *    주소가 빈 사진은 뺀다 — 넘겨 보다 빈 장이 나온다.
 */
export function placePhotos(place: Pick<Place, 'photoUrl' | 'photoSource' | 'photoLicense' | 'photos'>): PlacePhoto[] {
  const list = (place.photos ?? []).filter((photo) => !!photo?.url);
  if (list.length > 0) return list;
  return place.photoUrl ? [{ url: place.photoUrl, source: place.photoSource ?? null, license: place.photoLicense ?? null }] : [];
}

// 「KOGL Type 1」은 줄바꿈 안 되는 공백으로 붙인다 — 카드 띠가 두 줄로 꺾일 때 번호 「1」만 둘째 줄에 떨어졌다.
const koglEn = (type: string) => `KOGL\u00A0Type\u00A0${type}`;
// 운영 자료의 출처 글자 다섯 가지(2026-09-26 읽기만 해서 확인)가 드는 두 모양.
const PLAIN_SOURCE = /^한국관광공사 공공누리 제(\d)유형$/;
const GALLERY_SOURCE = /^한국관광공사 관광사진갤러리 공공누리 제(\d)유형 · 촬영 (.+)$/;
// 장소 사진 여러 장(S15P21E201-1839)의 출처는 이름표를 스스로 달고 온다 — 「출처 : 부산관광아카이브」·「Google 지도 · 사진 …」.
// 🔴 거기에 「사진: 」을 또 붙이면 「사진: 출처 : …」가 된다. 그래서 이 두 모양은 받은 글자만 쓴다.
const SOURCE_LABELLED_KO = /^출처\s*:\s*(.+)$/;
const SELF_LABELLED_SOURCE = /^(출처\s*:|Google\s)/;
// 「Google 지도 · 사진 박대규」 — 🔴 영어·일본어·중국어 화면에 이 한국어가 그대로 떴다(5개 언어 점검 2026-09-29).
const GOOGLE_SOURCE = /^Google 지도 · 사진 (.+)$/;

/** 이름표를 스스로 달고 오는 출처인가 — 그러면 앞에 「사진: 」을 또 붙이지 않는다(카드 띠가 「Photo: Google 지도 · 사진 …」이었다). */
export function isSelfLabelledSource(source: string): boolean {
  return SELF_LABELLED_SOURCE.test(source);
}

/**
 * 사진 출처의 영어 — 공공누리 출처 표시 의무는 번역해도 지켜진다(S15P21E201-1705, 조율 세션 결정 A).
 * 🔴 위 두 모양만 바꾸고, 그 밖의 글자는 그대로 둔다.
 *    촬영자 이름은 로마자로 바꾸지 않는다 — 본인이 쓰는 철자가 따로 있을 수 있어, 지어내면 틀린 이름이 된다. 이름표만 영어.
 */
export function photoSourceEnglish(source: string): string {
  const plain = source.match(PLAIN_SOURCE);
  if (plain) return `Korea Tourism Organization · ${koglEn(plain[1])}`;
  const gallery = source.match(GALLERY_SOURCE);
  if (gallery) return `Korea Tourism Organization Photo Gallery · ${koglEn(gallery[1])} · Photographer: ${gallery[2]}`;
  const labelled = source.match(SOURCE_LABELLED_KO);
  if (labelled) return `Source: ${labelled[1]}`;
  const google = source.match(GOOGLE_SOURCE);
  if (google) return `Google Maps · Photo: ${google[1]}`;
  return source;
}

/** 사진 출처를 화면 언어로 — 한국어판은 받은 글자 그대로, 그 밖은 photoSourceEnglish. */
export function photoSourceText(source: string, tx: (ko: string, en: string) => string): string {
  // 🔴 이름표(「출처 :」「Google 지도 · 사진」)는 번역표를 거친다 — 전에는 일본어·중국어에도 영어 「Source:」가 떴다(S15P21E201-1868).
  //    한국어판은 받은 글자를 한 글자도 안 바꾼다(띄어쓰기까지) — 틀로 만든 글자가 한국어 틀과 같으면 원문을 돌려준다.
  const labelled = source.match(SOURCE_LABELLED_KO);
  if (labelled) {
    const text = txf(tx, '출처 : %s', 'Source: %s', labelled[1]);
    return text === `출처 : ${labelled[1]}` ? source : text;
  }
  const google = source.match(GOOGLE_SOURCE);
  if (google) {
    const text = txf(tx, 'Google 지도 · 사진 %s', 'Google Maps · Photo: %s', google[1]);
    return text === `Google 지도 · 사진 ${google[1]}` ? source : text;
  }
  return tx(source, photoSourceEnglish(source));
}

/** 출처 글자에 라이선스 이름이 이미 들어 있나 — 그러면 「 · 이름」을 또 붙이지 않는다(S15P21E201-1868, 「CC BY-SA 4.0 · CC BY-SA 4.0」). */
export function sourceMentionsLicense(source: string, licenseName: string): boolean {
  return source.includes(licenseName.trim());
}

/**
 * 좁은 자리(카드 사진 띠·코스 표지)의 짧은 출처 — 기관과 이용 조건(공공누리 유형)만(S15P21E201-1705, 사용자 결정).
 * 🔴 폰 두 칸 카드의 띠는 두 줄까지(글자 폭 147px)다. 긴 이름은 영어 세 줄, 갤러리는 한국어도 세 줄이라 이용 조건이 잘렸다.
 *    그래서 한국어는 「한국관광공사 공공누리 제1유형」(갤러리의 「관광사진갤러리」「· 촬영 ○○」를 뺀다), 영어는 「KTO · KOGL Type 1」.
 *    다섯 모양 밖의 글자는 그대로. 보이는 글자만 줄이고, 화면 낭독에는 긴 것(photoSourceText)을 붙인다.
 */
export function photoSourceShortText(source: string, tx: (ko: string, en: string) => string): string {
  const match = source.match(PLAIN_SOURCE) ?? source.match(GALLERY_SOURCE);
  if (!match) return isSelfLabelledSource(source) ? photoSourceText(source, tx) : source;
  return tx(`한국관광공사 공공누리 제${match[1]}유형`, `KTO · ${koglEn(match[1])}`);
}

/** 사진 설명 두 줄 — null 이면 화면에 줄을 안 만든다 */
export function photoLabels(
  photo: { photoSource?: string | null; photoSubject?: PhotoSubject | null; photoLicense?: PhotoLicense | null },
  tx: (ko: string, en: string) => string,
): { badge: string | null; credit: string | null; licenseUrl: string | null } {
  // 라이선스 이름은 고유명사라 번역하지 않는다(「CC BY-SA 3.0」). 링크는 파일 페이지가 먼저 — 작성자·라이선스가 거기 다 있다.
  const license = photo.photoLicense?.name ? photo.photoLicense : null;
  return {
    badge: photo.photoSubject === 'VENUE' ? tx('행사장 사진', 'Venue photo') : null,
    credit: photo.photoSource
      ? (SELF_LABELLED_SOURCE.test(photo.photoSource) ? photoSourceText(photo.photoSource, tx) : txf(tx, '사진: %s', 'Photo: %s', photoSourceText(photo.photoSource, tx))) + (license && !sourceMentionsLicense(photo.photoSource, license.name) ? ` · ${license.name}` : '')
      : null,
    licenseUrl: license ? license.filePage || license.url || null : null,
  };
}

// 표식 상태 셋 다 화면에 보여준다 — UNKNOWN 은 「확인했으나 결과 없음」이라 그 자체가 정보다.
// 추정값에는 「추정」을 붙인다. 모르는 모양은 JSON.stringify 대신 사람이 읽을 문장으로 물러선다
// — 화면에 {"raw":"매일 10:00-22:00"} 이 글자 그대로 찍힌 적이 있다.
/**
 * 가격대(PRICE_LEVEL) 등급 — 서버 값 {"band":"MID","raw":"mid"} 에서 band 로 고른다(S15P21E201-1680, 조율 세션 결정).
 * 🔴 raw 는 조사원이 쓴 영어 낱말이다 — 그대로 써서 장소 상세에 「low」·「mid」, 축제 입장료 자리에 「high」가 떴다.
 */
const PRICE_BANDS: Readonly<Record<string, readonly [ko: string, en: string]>> = {
  LOW: ['저렴한 편', 'Inexpensive'],
  MID: ['보통', 'Moderate'],
  MID_HIGH: ['조금 비싼 편', 'A bit pricey'],
  HIGH: ['비싼 편', 'Expensive'],
};

/** 칸 값을 한 줄로. 안 보여야 하는 값(모르는 가격 등급)이면 null. */
function extractDisplayText(value: unknown, tx: (ko: string, en: string) => string): string | null {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (value && typeof value === 'object') {
    // 🔴 가격대를 영업시간보다 먼저 본다 — 영업시간 함수는 raw 글자를 그대로 돌려줘서, 뒤에 두면 「low」가 거기서 샌다.
    //    모르는 등급이면 안 보인다 — raw 로 물러서면 영어 낱말이 그대로 나간다.
    if ('band' in value) {
      const band = PRICE_BANDS[String((value as { band: unknown }).band)];
      return band ? tx(band[0], band[1]) : null;
    }
    const hours = formatOpeningHoursValue(value, tx);
    if (hours) return hours;
    if (typeof (value as { raw?: unknown }).raw === 'string') return (value as { raw: string }).raw;
  }
  return tx('확인했지만 형식을 읽지 못했어요', "We checked, but couldn't read the format");
}

// 요일 순서 월→일 — 서버의 byDay 는 사전이라 순서가 없다.
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

type DayRow = { day: typeof WEEK[number]; text: string | null };

/** 월→일 순서에서 시간이 같은 이웃 요일끼리 묶는다. 쉬거나 모르는 날(null)은 묶음을 끊는다. */
function dayRuns(rows: DayRow[]): { days: typeof WEEK; text: string }[] {
  const runs: { days: typeof WEEK; text: string }[] = [];
  for (const row of rows) {
    if (row.text === null) continue;
    const last = runs[runs.length - 1];
    const prev = rows[rows.indexOf(row) - 1];
    if (last && prev && prev.text === row.text && last.days[last.days.length - 1] === prev.day) last.days.push(row.day);
    else runs.push({ days: [row.day], text: row.text });
  }
  return runs;
}

/** 셋 이상 이어지면 「월–토」, 둘이면 「토·일」, 하나면 그 요일. */
function runLabel(days: typeof WEEK, tx: (ko: string, en: string) => string): string {
  const name = (day: typeof WEEK[number]) => tx(day.ko, day.en);
  if (days.length >= 3) return `${name(days[0])}–${name(days[days.length - 1])}`;
  return days.map(name).join('·');
}

// 쉬는 날은 요일 이름을 다 쓴다 — 「일 휴무」는 중국어에서 한 글자 「일」이 「天」(날짜 단위)으로 옮겨진다.
const FULL_DAY: Record<string, [ko: string, en: string]> = {
  mon: ['월요일', 'Monday'], tue: ['화요일', 'Tuesday'], wed: ['수요일', 'Wednesday'], thu: ['목요일', 'Thursday'],
  fri: ['금요일', 'Friday'], sat: ['토요일', 'Saturday'], sun: ['일요일', 'Sunday'],
};

/** 서버가 준 closedDays(예: ["sun"])를 「일요일 휴무」로. 없거나 못 읽으면 null. */
function closedDaysText(value: object, tx: (ko: string, en: string) => string): string | null {
  const closedDays = (value as { closedDays?: unknown }).closedDays;
  if (!Array.isArray(closedDays)) return null;
  const names = WEEK.filter((day) => closedDays.includes(day.key)).map((day) => tx(FULL_DAY[day.key][0], FULL_DAY[day.key][1]));
  return names.length ? txf(tx, '%s 휴무', 'Closed %s', names.join('·')) : null;
}

/** 영업시간 값의 문장화 */
export function formatOpeningHoursValue(value: unknown, tx: (ko: string, en: string) => string): string | null {
  if (!value || typeof value !== 'object') return null;
  const byDay = (value as { byDay?: unknown }).byDay;
  if (byDay && typeof byDay === 'object') {
    const rows = WEEK.map((day) => ({ day, text: rangesText((byDay as Record<string, unknown>)[day.key]) }));
    // 🔴 걸러 낸 뒤에는 text 가 반드시 있다. 그것을 «타입으로» 적어 둔다 — 그냥
    //    filter 로는 타입이 안 좁혀져서, 값을 쓰는 자리마다 null 을 다시 달래야 한다.
    const known = rows.filter((row): row is typeof rows[number] & { text: string } => row.text !== null);
    if (known.length) {
      // 일곱 요일이 같으면 「매일」 한 줄로 묶음.
      const sameEveryDay = known.length === WEEK.length && known.every((row) => row.text === known[0].text);
      if (sameEveryDay) return txf(tx, '매일 %s', 'Daily %s', known[0].text);
      // 🔴 이어진 요일의 같은 시간은 한 덩어리로 — 「월 09:00~20:00 · 화 09:00~20:00 · …」가 세 줄을 차지했다.
      //    쉬는 날은 서버가 closedDays 로 따로 준다. 안 적으면 그날도 여는 줄 알고 간다(국제시장 「매주 일요일」).
      const hours = dayRuns(rows).map((run) => `${runLabel(run.days, tx)} ${run.text}`).join(' · ');
      const closed = closedDaysText(value, tx);
      return closed ? `${hours}\n${closed}` : hours;
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
  if (text === null) return null;
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
}

// category 값 목록이 미확정이라 식당·카페 키워드로 식음료 장소를 추정한다.
function isFoodPlace(category: string) {
  return /FOOD|RESTAURANT|CAFE|맛집|카페|식당/i.test(category);
}

// 안전 정보 — 알레르기 = ALLERGEN_TAG, 식단 = DIETARY_SUPPORT_TAG.
function hasVerifiedFeature(place: Place, featureType: string) {
  return place.features.some(
    (feature) => feature.featureType === featureType && (feature as { evidenceStatus?: EvidenceStatus }).evidenceStatus === 'VERIFIED',
  );
}

export function needsFoodSafetyCheck(place: Place) {
  if (!isFoodPlace(place.category)) return false;
  return !hasVerifiedFeature(place, 'ALLERGEN_TAG') || !hasVerifiedFeature(place, 'DIETARY_SUPPORT_TAG');
}

// 「확인 못 함」과 「확인했고 문제 없음」의 구분 — 빈 자리를 사용자는 안전 확인으로 읽는다.
// 식음료 장소가 아니면 이 표시 자체가 무의미.
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

// 혼밥 안심(SOLO_FRIENDLY) — FLAG 피처라 값이 boolean 이다. 공통 서식은 "true"/"false" 로
// 내보내 뜻이 안 통하므로 이 필드만 라벨을 따로 붙인다.
export function formatSoloFriendly(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'SOLO_FRIENDLY'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const label = slot.value === true ? tx('혼밥하기 좋아요', 'Good for solo dining') : tx('혼밥은 어려울 수 있어요', 'May not suit solo diners');
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', label) : label;
}

// 브레이크타임·라스트오더 — 값 모양이 미확정이라 예상 모양과 맞을 때만 합치고, 아니면 안전한
// 문자열화로 물러선다. 모르는 모양을 아는 척 파싱하지 않는다.
export function formatBreakTime(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'BREAK_TIME'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { start?: unknown; end?: unknown };
  const text = typeof value?.start === 'string' && typeof value?.end === 'string' ? `${value.start}–${value.end}` : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
}

export function formatLastOrderTime(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'LAST_ORDER_TIME'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { time?: unknown };
  const text = typeof value?.time === 'string' ? value.time : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
}

// 계단 유무(STAIRS_PRESENT) — FLAG 피처. 「정보 없음 = 계단 없음」으로 읽지 않는다.
export function formatStairsPresent(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'STAIRS_PRESENT'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const label = slot.value === true ? tx('계단 있음', 'Has stairs') : tx('계단 없음', 'No stairs');
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', label) : label;
}

// 경사도(SLOPE_PERCENT) — 점수형 피처. 단위 없는 숫자는 뜻이 안 통하므로 % 를 붙인다.
/** 경사도 표시용 숫자 추출 — 못 꺼내면 null */
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
  // JSON.stringify 금지 자리 — 서버 값이 숫자에서 객체로 바뀌면 화면에 그대로 찍힌다.
  const text = slopeText(slot.value);
  if (text === null) return tx('확인했지만 형식을 읽지 못했어요', "We checked, but couldn't read the format");
  const number = slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
  // 숫자만으로는 판단이 안 선다 — 지도의 경사 색과 같은 기준으로 먼저 말한다(slopeGrades.ts: 5% · 8.33%).
  const percent = Number.parseFloat(text);
  return Number.isFinite(percent) ? `${slopeWord(percent, tx)} · ${number}` : number;
}

/** 지도 경사 색과 같은 문턱 — 5% 미만 초록 · 8.33% 이하 노랑 · 그 위 빨강. */
function slopeWord(percent: number, tx: (ko: string, en: string) => string): string {
  if (percent < 5) return tx('완만해요', 'Gentle');
  if (percent <= 8.33) return tx('조금 가파라요', 'A bit steep');
  return tx('가파라요', 'Steep');
}

// 숙박 체크인·체크아웃(CHECK_IN_OUT) — 숙박에는 OPENING_HOURS 대신 이 표식이 온다. 답하는
// 질문이 달라 한 자리에 안 섞는다. 한쪽만 있을 수 있다.
export function formatCheckInOut(place: Place, tx: (ko: string, en: string) => string): string | null {
  const slot = toFeatureSlot(findFeature(place, 'CHECK_IN_OUT'));
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return missingValueLabel(slot, tx);
  const value = slot.value as { checkIn?: unknown; checkOut?: unknown };
  const checkIn = typeof value?.checkIn === 'string' ? value.checkIn : null;
  const checkOut = typeof value?.checkOut === 'string' ? value.checkOut : null;
  const text = checkIn && checkOut
    ? txf(tx, '체크인 %s · 체크아웃 %s', 'Check-in %s · Check-out %s', checkIn, checkOut)
    : checkIn
      ? txf(tx, '체크인 %s', 'Check-in %s', checkIn)
      : checkOut
        ? txf(tx, '체크아웃 %s', 'Check-out %s', checkOut)
        : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
}

// 장소 이름 한글·영문 병기 — 언어 설정과 무관. 영문 이름은 택시 기사에게 쓸모없고 한글만으로는
// 영어 사용자가 못 읽는다. 영문이 없으면 괄호 없이 한국어만.
export function getPlace(placeId: string, signal?: AbortSignal) {
  return apiRequest<Place>(`/api/v1/places/${encodeURIComponent(placeId)}`, { signal });
}

// 계약 — GET /api/v1/places, query 와 facetType 은 정확히 하나만(둘 다 없거나 둘 다 있으면 400).
// photoUrl·photoSource 는 값이 없으면 칸이 안 와서 optional. 사진을 그리면 출처도 같이 그린다
// — 공공누리 이용 조건.
export type PlaceSearchItem = { placeId: string; nameKo: string; nameEn: string | null; localNames?: LocalNames; category: string; address: string; addressEn?: string; lat: number; lng: number; photoUrl?: string | null; photoSource?: string | null; photoSubject?: PhotoSubject | null; photoLicense?: PhotoLicense | null };

type PlacePageDto = { items: PlaceSearchItemDto[]; limit: number; nextCursor: string | null; hasNext: boolean; rankTruncated: boolean };
export type PlaceSearchItemDto = { placeId: string; nameKo: string; nameEn: string | null; localNames?: LocalNames; category: string; address: string; addressEn?: string; lat: number; lng: number; matchedField: 'NAME_KO' | 'NAME_EN' | null; photoUrl?: string | null; photoSource?: string | null; photoSubject?: PhotoSubject | null; photoLicense?: PhotoLicense | null };

/** 목록 응답 한 건의 화면 모양 변환 */
export function toPlaceSearchItem(dto: PlaceSearchItemDto): PlaceSearchItem {
  const { placeId, nameKo, nameEn, localNames, category, address, addressEn, lat, lng, photoUrl, photoSource, photoSubject, photoLicense } = dto;
  return { placeId, nameKo, nameEn, localNames, category, address, addressEn, lat, lng, photoUrl, photoSource, photoSubject, photoLicense };
}

/** 변환에서 일부러 빼는 칸 — 시험이 이 목록만 예외로 친다 */
export const PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE = ['matchedField'] as const;

export async function searchPlacesByName(query: string, signal?: AbortSignal): Promise<PlaceSearchItem[]> {
  const dto = await apiRequest<PlacePageDto>(`/api/v1/places?query=${encodeURIComponent(query)}&limit=8`, { signal });
  return dto.items.map(toPlaceSearchItem);
}

/** 로컬 갈래별 장소 조회 — 거리 제한 없음 */
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
