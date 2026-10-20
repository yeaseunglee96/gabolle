/**
 * 근처 편의시설 — 주차장 · 약국 · 공중화장실 · 지하철 물품보관함 (S15P21E201-2052).
 *
 * 서버 계약: back/dev `GET /api/v1/facilities/nearby` (같은 티켓의 BE MR, backend/docs/PLACE-DATA-LOAD.md 12절).
 * 로그인(손님 세션 포함)이 필요하고, 좌표와 종류만으로 답이 정해진다.
 *
 * 🔴 `null` 은 «모른다»다. 서버는 칸을 빼지 않고 null 로 내보낸다 — 화면이 «없다»와 «모른다»를 가려야 한다.
 *    예: `estimatedWon: 0` 은 무료이고 `estimatedWon: null` 은 요금 정보 없음이다. 둘을 바꿔 그리면 요금을 모르는 주차장이
 *    «무료»로 보인다.
 */
import { apiRequest } from '@/api/client';
import { coarseCoordinate } from '@/personalization/locationConsent';

// 🔴 약국은 탭에서 뺐다 — 「근처 병원·약국」 한 곳에만 둔다(S15P21E201-2066). 그 자리에 환전소. 타입에는 PHARMACY 를 남긴다(서버는 아직 답한다).
export type FacilityKind = 'PARKING' | 'PHARMACY' | 'TOILET' | 'LOCKER' | 'EXCHANGE';
export const FACILITY_KINDS: readonly FacilityKind[] = ['PARKING', 'EXCHANGE', 'TOILET', 'LOCKER'];

export type LatLng = { latitude: number; longitude: number };

/** 오늘의 시간대. `close` 는 24:00 이상이면 다음 날 시각으로 접힌 글이다(25:00 → 01:00). */
export type Hours = { open: string; close: string };

export type ParkingFee = {
  baseMinutes: number | null;
  baseWon: number | null;
  addMinutes: number | null;
  addWon: number | null;
  dayMaxWon: number | null;
  freeMinutes: number | null;
};

export type ParkingDetail = {
  operator: string | null;
  free: boolean;
  spaces: number | null;
  /** 요금표. null 이면 요금을 모른다. 칸 하나가 null 이면 그 칸만 모른다(추가요금을 모르면 기본 시간을 넘는 요금은 모른다) */
  fee: ParkingFee | null;
  /** 요청한 체류 시간의 예상요금(원). 무료면 0, **모르면 null** */
  estimatedWon: number | null;
  note: string | null;
  openNow: boolean | null;
  todayHours: Hours | null;
  closesAt: string | null;
  allDay: boolean;
};

export type PharmacyDetail = {
  tel: string | null;
  directions: string | null;
  note: string | null;
  /** 늘 true — 운영시간은 약국이 신고한 정보라 가기 전에 전화로 확인해야 한다 */
  hoursAreReported: boolean;
  openNow: boolean | null;
  todayHours: Hours | null;
  closesAt: string | null;
  allDay: boolean;
  opensSunday: boolean;
  opensHoliday: boolean;
  lateNight: boolean;
};

/** 환전소 — 카카오 로컬이 준 값뿐이다. 영업시간·환율은 없다(S15P21E201-2066). */
export type ExchangeDetail = {
  tel: string | null;
  /** 카카오맵 장소 주소 — 영업시간·후기는 거기 있다 */
  placeUrl: string | null;
};

export type ToiletDetail = {
  /** OPEN(개방) · PUBLIC(공중) · SIMPLE(간이) · MOBILE(이동) */
  type: string | null;
  openHours: string | null;
  alwaysOpen: boolean;
  accessible: boolean;
  diaper: boolean;
  /** WOMEN · MEN · BOTH. 없거나 모르면 null */
  diaperPlace: string | null;
  emergencyBell: boolean;
  cctv: boolean;
  tel: string | null;
};

export type LockerDetail = {
  station: string;
  stationEn: string | null;
  line: string | null;
  location: string | null;
  small: number;
  medium: number;
  large: number;
  xlarge: number;
  feeText: string | null;
  operator: string | null;
};

type ItemBase = { id: string; name: string; address: string | null; lat: number; lng: number; distanceM: number };

export type FacilityItem =
  | (ItemBase & { kind: 'PARKING'; detail: ParkingDetail })
  | (ItemBase & { kind: 'PHARMACY'; detail: PharmacyDetail })
  | (ItemBase & { kind: 'EXCHANGE'; detail: ExchangeDetail })
  | (ItemBase & { kind: 'TOILET'; detail: ToiletDetail })
  | (ItemBase & { kind: 'LOCKER'; detail: LockerDetail });

export type NearbyFacilities = {
  kind: FacilityKind;
  radiusM: number;
  /** 주차장만 — 예상요금에 쓴 체류 시간(분) */
  stayMinutes: number | null;
  /** 반경 안에서 조건에 맞는 곳의 수 — `items` 는 가까운(또는 싼) 순으로 잘린 것이다 */
  total: number;
  limit: number;
  items: FacilityItem[];
};

export type NearbyOptions = {
  radiusM?: number;
  limit?: number;
  /** 주차장 예상요금용 체류 시간(분) */
  stayMinutes?: number;
  /** 화장실만 — 장애인용만 */
  accessible?: boolean;
  /** 화장실만 — 기저귀 교환대가 있는 곳만 */
  diaper?: boolean;
  /**
   * 사람이 아니라 «장소»의 자리를 기준으로 찾을 때 — 좌표를 줄이지 않고 그대로 보낸다(S15P21E201-2052, 장소 상세의 «근처 주차장»).
   * 약 100m 로 줄이는 것은 «내 위치»를 가리려는 약속인데, 장소의 자리는 누구나 보는 값이다. 게다가 줄이면 «그 장소에서 몇 m»가
   * 최대 80m 남짓 틀어진다.
   */
  exact?: boolean;
  signal?: AbortSignal;
};

export const DEFAULT_RADIUS_M = 700;
export const WIDE_RADIUS_M = 2000;
export const DEFAULT_LIMIT = 20;

/** 위치를 못 쓸 때 기준으로 삼는 «부산 중심» — 서면역. 거리 숫자는 이 점에서 잰 것이라 화면이 그렇게 밝힌다. */
export const SEOMYEON: LatLng = { latitude: 35.1583, longitude: 129.0596 };

/** 걸음 속도 — 직선거리를 분당 80m 로 나눈다(티켓 결정). */
export const WALK_METERS_PER_MINUTE = 80;

/** 주차장 「몇 시간 머물러요?」 칩. */
export const STAY_HOURS_CHOICES: readonly number[] = [1, 2, 3, 4];
export const DEFAULT_STAY_HOURS = 2;

/** 직선거리로 어림한 걷는 시간(분). 1분보다 짧아도 1분이다 — 「0분」은 사람이 쓰는 말이 아니다. */
export function walkMinutesFor(distanceM: number): number {
  return Math.max(1, Math.round(distanceM / WALK_METERS_PER_MINUTE));
}

/** 「1,200」 — 자릿점을 직접 찍는다. 엔진마다 toLocaleString 의 결과가 달라 시험과 화면이 어긋나지 않게. */
export function formatWon(won: number): string {
  return String(Math.round(won)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/**
 * 가까운 시설을 서버에 묻는다. 못 받으면 던진다 — 화면이 «불러오지 못했어요 · 다시 시도»를 그린다.
 *
 * 🔴 좌표는 약 100m 단위로 줄여서 보낸다 — 위치 동의가 «내 주변 찾기는 약 100m 단위로»라고 약속한다(둘러보기·버스와 같다).
 *    단 `options.exact` 일 때(장소의 자리를 기준으로 찾을 때)는 그대로 보낸다 — 소수 다섯째 자리(약 1m)까지만.
 */
export async function getNearbyFacilities(
  kind: FacilityKind,
  at: LatLng,
  accessToken: string | null,
  options: NearbyOptions = {},
): Promise<NearbyFacilities> {
  const point = (value: number) => (options.exact ? Math.round(value * 100000) / 100000 : coarseCoordinate(value));
  const query = new URLSearchParams({
    kind,
    lat: String(point(at.latitude)),
    lng: String(point(at.longitude)),
    radiusM: String(options.radiusM ?? DEFAULT_RADIUS_M),
    limit: String(options.limit ?? DEFAULT_LIMIT),
  });
  if (kind === 'PARKING' && options.stayMinutes !== undefined) query.set('stayMinutes', String(options.stayMinutes));
  if (kind === 'TOILET' && options.accessible) query.set('accessible', 'true');
  if (kind === 'TOILET' && options.diaper) query.set('diaper', 'true');
  const result = await apiRequest<NearbyFacilities>(`/api/v1/facilities/nearby?${query.toString()}`, {
    accessToken,
    signal: options.signal,
  });
  if (!result || !Array.isArray(result.items)) throw new Error('근처 편의시설 응답의 모양이 올바르지 않다');
  return result;
}

// ── 영업 상태 ──────────────────────────────────────────────────────────────

export type OpenStatus =
  | { kind: 'open'; closesAt: string | null; allDay: boolean }
  | { kind: 'closed'; todayHours: Hours | null }
  | { kind: 'unknown' };

/**
 * 지금 여는가를 화면이 그릴 세 가지로 — 열었다 / 닫았다(오늘 시간이 있으면 같이) / 모른다.
 * 🔴 `openNow: null` 은 «닫았다»가 아니다. 모르면 아무 말도 지어내지 않는다.
 */
export function openStatusOf(detail: Pick<ParkingDetail, 'openNow' | 'todayHours' | 'closesAt' | 'allDay'>): OpenStatus {
  if (detail.openNow === true) return { kind: 'open', closesAt: detail.closesAt, allDay: detail.allDay };
  if (detail.openNow === false) return { kind: 'closed', todayHours: detail.todayHours };
  return { kind: 'unknown' };
}

// ── 주차장 요금 ────────────────────────────────────────────────────────────

export type FeeSummary =
  | { kind: 'free' }
  | { kind: 'estimate'; won: number }
  | { kind: 'partial'; baseMinutes: number; baseWon: number }
  | { kind: 'unknown' };

/**
 * 예상요금을 화면이 그릴 네 가지로.
 *
 * 🔴 `estimatedWon: null` 인 유료 주차장을 0원으로 그리지 않는다. 요금표의 일부(기본요금)만 아는 곳은 «기본 30분 300원 · 추가요금은
 * 현장에서»로, 아예 모르는 곳은 «요금 정보 없음»으로 그린다.
 */
export function feeSummaryOf(detail: Pick<ParkingDetail, 'free' | 'estimatedWon' | 'fee'>): FeeSummary {
  if (detail.free) return { kind: 'free' };
  if (detail.estimatedWon === 0) return { kind: 'free' };
  if (detail.estimatedWon !== null && detail.estimatedWon > 0) return { kind: 'estimate', won: detail.estimatedWon };
  const fee = detail.fee;
  if (fee && fee.baseWon !== null && fee.baseMinutes !== null) {
    return { kind: 'partial', baseMinutes: fee.baseMinutes, baseWon: fee.baseWon };
  }
  return { kind: 'unknown' };
}

// ── 장소 상세의 «근처 주차장» 한 줄 ─────────────────────────────────────────

/** 한 줄은 «1시간 기준»이다 — 몇 시간 머물지는 이 화면이 모른다. 누르면 열리는 화면도 1시간으로 열린다(같은 숫자를 보게). */
export const PARKING_LINE_STAY_MINUTES = 60;

export type ParkingLine =
  | { kind: 'fee'; won: number; distanceM: number }
  | { kind: 'free'; distanceM: number }
  /** 근처에 주차장은 있는데 요금을 아는 곳이 하나도 없다 */
  | { kind: 'unknown'; count: number; nearestM: number };

/**
 * 근처 주차장 목록(1시간 기준)을 한 줄로 줄인다 — «요금을 아는 곳 중 가장 싼 곳»과 그 거리. 같은 값이면 가까운 곳.
 * 요금을 아는 곳이 없으면 «몇 곳 · 가장 가까운 곳». 하나도 없으면 null — 부르는 쪽이 줄을 통째로 숨긴다.
 *
 * 🔴 요금을 모르는 곳(`estimatedWon: null`)은 «가장 싼 곳»이 될 수 없다. null 을 0원으로 읽으면 요금도 모르는 주차장이 무료로 보인다.
 *    서버가 이미 싼 순으로 주지만(요금 모름은 맨 뒤) 이 함수는 그 순서에 기대지 않고 직접 고른다.
 */
export function parkingLineOf(data: Pick<NearbyFacilities, 'items' | 'total'>): ParkingLine | null {
  const lots = data.items.filter((item): item is Extract<FacilityItem, { kind: 'PARKING' }> => item.kind === 'PARKING');
  if (lots.length === 0) return null;
  let best: { won: number; distanceM: number } | null = null;
  for (const lot of lots) {
    const fee = feeSummaryOf(lot.detail);
    const won = fee.kind === 'free' ? 0 : fee.kind === 'estimate' ? fee.won : null;
    if (won === null) continue;
    if (best === null || won < best.won || (won === best.won && lot.distanceM < best.distanceM)) best = { won, distanceM: lot.distanceM };
  }
  if (best) return best.won === 0 ? { kind: 'free', distanceM: best.distanceM } : { kind: 'fee', won: best.won, distanceM: best.distanceM };
  return { kind: 'unknown', count: Math.max(data.total, lots.length), nearestM: Math.min(...lots.map((lot) => lot.distanceM)) };
}

// ── 물품보관함 요금 글 ─────────────────────────────────────────────────────

export type LockerSize = 'small' | 'medium' | 'large' | 'xlarge';
export const LOCKER_SIZES: readonly LockerSize[] = ['small', 'medium', 'large', 'xlarge'];

export type LockerFee = {
  /** 「3시간당」의 3. 글에 없으면 null */
  perHours: number | null;
  bySize: Partial<Record<LockerSize, number>>;
};

const SIZE_BY_WORD: Record<string, LockerSize> = { 소: 'small', 중: 'medium', 대: 'large', 특대: 'xlarge' };

/**
 * 요금 글(「특대: 6,000원 / 대:4,000원 / 중: 3,000원 / 소: 2,000원(3시간당)」)에서 크기별 요금을 뽑는다.
 * 읽을 수 있는 크기가 하나도 없으면 null — 부르는 쪽이 원문을 그대로 보여 준다(없는 값을 지어내지 않는다).
 */
export function parseLockerFee(feeText: string | null): LockerFee | null {
  if (!feeText) return null;
  const bySize: Partial<Record<LockerSize, number>> = {};
  for (const match of feeText.matchAll(/(특대|대|중|소)\s*:?\s*([\d,]+)\s*원/g)) {
    const size = SIZE_BY_WORD[match[1]];
    const won = Number(match[2].replace(/,/g, ''));
    if (size && Number.isFinite(won) && won > 0 && bySize[size] === undefined) bySize[size] = won;
  }
  if (Object.keys(bySize).length === 0) return null;
  const hours = /(\d+)\s*시간\s*당/.exec(feeText);
  return { perHours: hours ? Number(hours[1]) : null, bySize };
}
