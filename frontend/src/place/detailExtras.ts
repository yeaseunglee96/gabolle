// 장소 상세의 «추가 정보» 줄 — S15P21E201-1888.
//
// 백엔드 MR !1930 이 place_feature(장소마다 붙는 표식 한 줄 — featureType·value·evidenceStatus)에
// 새 유형을 싣는다: 대표 메뉴·외국어 메뉴판·편의시설·입장료·가까운 명소·좋은 때.
// 이 파일은 그 값을 «화면에 쓸 글자»로만 바꾼다. 화면(PlaceDetailExtras.tsx)은 그리기만 한다.
//
// 🔴 규칙 셋
//   1. 값이 없거나 모양을 못 읽으면 null — 줄 자체를 안 그린다. 「정보 없음」 줄을 늘어놓지 않는다.
//   2. 추정값(evidenceStatus ESTIMATED)에는 앱 관례대로 「(추정)」을 붙인다(places.ts 와 같다).
//   3. 🔴 재료에서 알레르기·「비건 가능」을 끌어내지 않는다. DB 가 추정 알레르기 행을 일부러 막는다 —
//      재료는 글자 그대로만 보여준다. 알레르기 경고 카드는 [id].tsx 의 기존 것이 그대로 맡는다.
//
// 영업시간·체크인아웃·경사는 이미 [id].tsx 가 places.ts 로 그린다 — 여기서 다시 그리지 않는다(같은 정보 두 번 금지).
// 걷기 난이도만 새로: 자연·산책(NATURE_WALK) 장소에서 SLOPE_PERCENT 의 score 로 판정한다.
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';
import type { EvidenceStatus, PlaceFeature } from '@/discovery/places';
import { MODERATE_SLOPE_PERCENT, STEEP_SLOPE_PERCENT } from '@/map/slopeGrades';

type Tx = (ko: string, en: string) => string;
type Obj = Record<string, unknown>;

function find(features: readonly PlaceFeature[] | undefined, type: string): { value: Obj; status: EvidenceStatus } | null {
  const feature = features?.find((f) => f.featureType === type);
  if (!feature) return null;
  const value = (feature as { value?: unknown }).value;
  const status = ((feature as { evidenceStatus?: EvidenceStatus }).evidenceStatus ?? 'UNKNOWN');
  if (status === 'UNKNOWN' || status === 'NOT_COLLECTED') return null;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return { value: value as Obj, status };
}

function estimated(text: string, status: EvidenceStatus, tx: Tx): string {
  return status === 'ESTIMATED' ? txf(tx, '%s (추정)', '%s (est.)', text) : text;
}

const str = (v: unknown): string | null => (typeof v === 'string' && v.trim() ? v.trim() : null);

// ─── 대표 메뉴(MENU_ITEMS) ───────────────────────────────────────────────

export type MenuLine = { name: string; subName: string | null; price: string | null; ingredients: string | null; signature: boolean };

/** 원화 표시 — 한국어는 「12,000원」, 그 밖은 「₩12,000」. */
export function formatWon(won: number, language: LanguageCode): string {
  const n = Math.round(won).toLocaleString('en-US');
  return language === 'ko' ? `${n}원` : `₩${n}`;
}

/**
 * 한국어 화면: 한국어 이름 + 한국어 재료. 그 밖: 영어 이름(없으면 한국어) + 한국어 이름을 작게(가게에서 가리킬 수 있게) + 영어 재료.
 * 영어 재료가 없으면 재료 줄을 안 그린다 — 한국어 재료를 영어 화면에 내면 읽을 수 없다.
 */
export function menuLines(features: readonly PlaceFeature[] | undefined, language: LanguageCode, tx: Tx): MenuLine[] {
  const slot = find(features, 'MENU_ITEMS');
  if (!slot || !Array.isArray(slot.value.items)) return [];
  const ko = language === 'ko';
  const lines: MenuLine[] = [];
  for (const raw of slot.value.items as unknown[]) {
    if (!raw || typeof raw !== 'object') continue;
    const item = raw as Obj;
    const nameKo = str(item.nameKo);
    const nameEn = str(item.nameEn);
    const name = ko ? nameKo ?? nameEn : nameEn ?? nameKo;
    if (!name) continue;
    const subName = !ko && nameEn && nameKo ? nameKo : null;
    const ingredientText = ko ? str(item.ingredientsKo) : str(item.ingredientsEn);
    const price = typeof item.priceWon === 'number' && Number.isFinite(item.priceWon) && item.priceWon > 0 ? formatWon(item.priceWon, language) : null;
    lines.push({
      name,
      subName,
      price,
      ingredients: ingredientText ? txf(tx, '재료: %s', 'Ingredients: %s', ingredientText) : null,
      signature: item.signature === true,
    });
  }
  // 대표(signature) 메뉴를 앞으로 — 넷만 먼저 보이므로 그 넷이 대표여야 한다. 같은 무리 안의 순서는 서버 순서.
  return [...lines.filter((l) => l.signature), ...lines.filter((l) => !l.signature)];
}

export const MENU_PREVIEW_COUNT = 4;

// ─── 정보 줄 ─────────────────────────────────────────────────────────────

export type ExtraRow = { key: string; label: string; value: string; caption?: string };

export function foreignMenuRow(features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  const slot = find(features, 'FOREIGN_MENU');
  if (!slot || typeof slot.value.available !== 'boolean') return null;
  const text = slot.value.available ? tx('있어요', 'Available') : tx('없어요', 'Not available');
  return { key: 'foreign-menu', label: tx('외국어 메뉴판', 'Foreign-language menu'), value: estimated(text, slot.status, tx) };
}

// 이름표는 글자 그대로 tx 에 넣는다 — 변수로 넘기면 번역 검사가 못 보고 일본어·중국어 화면에서 영어로 떨어진다.
function amenityLabels(tx: Tx): readonly (readonly [key: string, label: string])[] {
  return [
    ['wifi', tx('와이파이', 'Wi-Fi')],
    ['parking', tx('주차', 'Parking')],
    ['restroom', tx('화장실', 'Restroom')],
    ['reservation', tx('예약', 'Reservations')],
  ];
}

/** 참/거짓이 확인된 것만 — null(모름)은 그 항목을 빼고, 다 빠지면 줄이 없다. homepage 는 주소 글자라 따로 줄을 둔다. */
export function amenitiesRow(features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  const slot = find(features, 'AMENITIES');
  if (!slot) return null;
  const parts: string[] = [];
  for (const [key, label] of amenityLabels(tx)) {
    const v = slot.value[key];
    if (v === true) parts.push(txf(tx, '%s 있음', '%s: yes', label));
    else if (v === false) parts.push(txf(tx, '%s 없음', '%s: no', label));
  }
  if (!parts.length) return null;
  return { key: 'amenities', label: tx('편의시설', 'Amenities'), value: estimated(parts.join(' · '), slot.status, tx) };
}

export function homepageUrl(features: readonly PlaceFeature[] | undefined): string | null {
  const slot = find(features, 'AMENITIES');
  const url = slot ? str(slot.value.homepage) : null;
  return url && /^https?:\/\//i.test(url) ? url : null;
}

export function admissionFeeRow(features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  const slot = find(features, 'ADMISSION_FEE');
  const raw = slot ? str(slot.value.raw) : null;
  if (!slot || !raw) return null;
  return { key: 'admission-fee', label: tx('입장료', 'Admission'), value: estimated(raw, slot.status, tx) };
}

/** 「용두산공원 · 216m」 — 1km 이상은 「1.2km」. */
export function formatDistance(meters: number): string {
  return meters >= 1000 ? `${(Math.round(meters / 100) / 10).toFixed(1)}km` : `${Math.round(meters)}m`;
}

export function nearbyLandmarkRow(features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  const slot = find(features, 'NEARBY_LANDMARK');
  const name = slot ? str(slot.value.name) : null;
  if (!slot || !name) return null;
  const d = slot.value.distanceM;
  const text = typeof d === 'number' && Number.isFinite(d) && d >= 0 ? `${name} · ${formatDistance(d)}` : name;
  return { key: 'nearby-landmark', label: tx('가까운 명소', 'Nearby landmark'), value: estimated(text, slot.status, tx) };
}

export type BestTime = 'day' | 'night' | 'any';

/**
 * 현지인 설문 표(day·night·any) 가운데 가장 많은 쪽. 낮과 밤이 같거나, 「언제든」이 가장 많거나 공동 1등이면 「언제든」.
 * 표가 하나도 없으면 null.
 */
export function pickBestTime(counts: { day?: unknown; night?: unknown; any?: unknown }): { pick: BestTime; total: number } | null {
  const n = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0);
  const day = n(counts.day);
  const night = n(counts.night);
  const any = n(counts.any);
  const total = day + night + any;
  if (total === 0) return null;
  const max = Math.max(day, night, any);
  if (any === max || day === night) return { pick: 'any', total };
  return { pick: day > night ? 'day' : 'night', total };
}

export function bestTimeRow(features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  const slot = find(features, 'BEST_TIME');
  if (!slot) return null;
  const best = pickBestTime(slot.value);
  if (!best) return null;
  const word = best.pick === 'day' ? tx('낮', 'Daytime') : best.pick === 'night' ? tx('밤', 'Night') : tx('언제든', 'Any time');
  return {
    key: 'best-time',
    label: tx('좋은 때', 'Best time'),
    value: estimated(word, slot.status, tx),
    caption: txf(tx, '현지인 설문 %s명', 'Local survey · %s people', String(best.total)),
  };
}

export type WalkDifficulty = 'easy' | 'moderate' | 'hard';

/**
 * 평균 경사 % → 걷기 난이도. 5% 미만 쉬움 · 8.33% 까지 보통 · 그 위 힘듦.
 *
 * 🔴 문턱은 바로 위 「경사」 줄(places.ts 의 slopeWord)과 지도 경사 색(slopeGrades.ts)이 쓰는 값 그대로다 — 새로 정하지 않고
 *    같은 상수를 가져다 쓴다(S15P21E201-1901). 8.33% 는 휠체어 경사로 기준(1:12)이고 서버의 접근성 판정도 같은 값이다.
 *    전에는 5 / 10 을 따로 적어서 같은 화면에서 엇갈렸다 — 운영 금정산 상세가 「경사: 가파라요 · 8.8%」 바로 아래
 *    「걷기 난이도: 보통」이라고 적었다. 5 는 «미만»이 쉬움이다(지도는 5.0 이 노랑이다).
 */
export function walkDifficulty(slopePercent: number): WalkDifficulty {
  if (slopePercent < MODERATE_SLOPE_PERCENT) return 'easy';
  if (slopePercent <= STEEP_SLOPE_PERCENT) return 'moderate';
  return 'hard';
}

/**
 * 자연·산책(NATURE_WALK) 장소만. 경사 % 는 바로 아래 「경사」 줄이 이미 보여주므로 여기서 되풀이하지 않는다(같은 정보 두 번 금지).
 */
export function walkDifficultyRow(category: string | undefined, features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow | null {
  if (category !== 'NATURE_WALK') return null;
  const slot = find(features, 'SLOPE_PERCENT');
  const score = slot?.value.score;
  if (!slot || typeof score !== 'number' || !Number.isFinite(score)) return null;
  const level = walkDifficulty(score);
  const word = level === 'easy' ? tx('쉬움', 'Easy') : level === 'moderate' ? tx('보통', 'Moderate') : tx('힘듦', 'Hard');
  return { key: 'walk-difficulty', label: tx('걷기 난이도', 'Walking difficulty'), value: estimated(word, slot.status, tx) };
}

/** 정보 카드에 들어갈 줄 — 순서가 곧 화면 순서. */
export function extraRows(category: string | undefined, features: readonly PlaceFeature[] | undefined, tx: Tx): ExtraRow[] {
  return [
    walkDifficultyRow(category, features, tx),
    admissionFeeRow(features, tx),
    bestTimeRow(features, tx),
    nearbyLandmarkRow(features, tx),
    foreignMenuRow(features, tx),
    amenitiesRow(features, tx),
  ].filter((row): row is ExtraRow => row !== null);
}

// ─── 공인 표식(RECOGNITION) — S15P21E201-1892 ─────────────────────────────
//
// 백엔드 S15P21E201-1891 의 값: {"badges":[{"kind","since","menu","source"}]}.
//   MODEL_RESTAURANT  구·군이 지정한 모범음식점(부산광역시_구군 모범음식점 현황)
//   TAXI_DRIVER_PICK  택시기사 추천 식당(부산광역시 택슐랭 선정 식당 2025)
// 공공기관이 정한 공식 지정이라 추정이 아니다 — 「(추정)」을 붙이지 않는다.
// 모르는 kind 는 이름을 못 붙이므로 건너뛴다(서버 적재기도 받지 않는다).

export type BadgeKind = 'MODEL_RESTAURANT' | 'TAXI_DRIVER_PICK';

export type RecognitionBadge = { kind: BadgeKind; label: string; menu: string | null; source: string };

/**
 * 출처 글. 한국어 화면은 서버가 준 원문 그대로, 그 밖의 언어는 종류별 번역 — 한국어 원문을 외국어 화면에 내면 읽을 수 없다.
 * 이름표는 글자 그대로 tx 에 넣는다(번역 검사가 보도록).
 */
function badgeText(kind: BadgeKind, tx: Tx): { label: string; source: string } {
  return kind === 'MODEL_RESTAURANT'
    ? { label: tx('모범음식점', 'Model Restaurant'), source: tx('부산광역시 모범음식점 현황', 'Busan Model Restaurant list') }
    : { label: tx('택시기사 추천', 'Taxi drivers’ pick'), source: tx('부산광역시 택슐랭 선정 식당(2025)', 'Busan “Taxlin” restaurant picks (2025)') };
}

export function recognitionBadges(features: readonly PlaceFeature[] | undefined, language: LanguageCode, tx: Tx): RecognitionBadge[] {
  const slot = find(features, 'RECOGNITION');
  if (!slot || !Array.isArray(slot.value.badges)) return [];
  const out: RecognitionBadge[] = [];
  const seen = new Set<BadgeKind>();
  for (const raw of slot.value.badges as unknown[]) {
    if (!raw || typeof raw !== 'object') continue;
    const badge = raw as Obj;
    const kind = badge.kind;
    if (kind !== 'MODEL_RESTAURANT' && kind !== 'TAXI_DRIVER_PICK') continue;
    if (seen.has(kind)) continue;
    seen.add(kind);
    const text = badgeText(kind, tx);
    const menu = str(badge.menu);
    out.push({
      kind,
      label: text.label,
      // 메뉴 이름은 한국어 그대로 — 가게에서 가리켜 주문할 수 있게.
      menu: menu ? txf(tx, '추천 메뉴: %s', 'Recommended dish: %s', menu) : null,
      source: language === 'ko' ? str(badge.source) ?? text.source : text.source,
    });
  }
  return out;
}
