// 여행 티켓(TRIP PASS)에 찍히는 값 — 화면이 아니라 여기서 만든다 (S15P21E201-1233).
//
// 🔴 이 파일에 화면 코드가 없는 이유. 티켓은 "실제 일정"을 보여주는 것이 전부이고,
//    그 값이 맞는지는 눈으로 못 본다 — 그럴듯한 숫자가 찍히면 사람은 그냥 믿는다.
//    그래서 계산만 따로 떼어 시험이 붙들게 한다.
//
// 🔴 **모르는 것은 칸 자체를 안 만든다.** 0 이나 '-' 로 채우지 않는다.
//    「이동 0분」과 「이동 시간을 모른다」는 다른 말인데, 화면에 0 이 찍히면 사람은
//    앞의 뜻으로 읽는다. 이 저장소가 여러 번 겪은 고장이라 처음부터 막는다.

import type { ItineraryDto } from '@/plan/itinerary';

export type TripPassField = { key: string; value: string };

export type TripPassData = {
  /** 영수증 오른쪽 위 코드. 같은 일정이면 언제나 같은 값이 나온다. */
  code: string;
  /** 출발 칸의 큰 글자. 출발지가 없으면 빈 문자열이고 화면이 그 칸을 접는다. */
  fromLabel: string;
  /** 도착 칸의 큰 글자. 이 서비스는 부산 고정이다. */
  toLabel: string;
  /** 첫 일정 시각. 모르면 null. */
  startTime: string | null;
  /** 마지막 일정 시각. 모르면 null. */
  endTime: string | null;
  /** 「9.20(토) – 9.21(일)」. 날짜가 없으면 null. */
  dateRange: string | null;
  /** 이동 수단 배지. 모르면 null. */
  mode: string | null;
  /** 여행자 이름. 로그인 전이면 null. */
  owner: string | null;
  /** 아래 6칸 그리드. 값을 모르는 칸은 애초에 안 들어간다. */
  fields: TripPassField[];
  /** QR 이 담을 주소. 일정 id 가 없으면 null 이고 화면이 QR 을 안 그린다. */
  url: string | null;
  /** 바코드 아래 한 줄. */
  validText: string;
};

const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'];
const WEEKDAY_EN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const MODE_LABEL: Record<string, { ko: string; en: string }> = {
  TRANSIT: { ko: '대중교통', en: 'Transit' },
  CAR: { ko: '자동차', en: 'Car' },
  WALK: { ko: '도보', en: 'Walk' },
  TAXI: { ko: '택시', en: 'Taxi' },
};

/** `2026-09-20` · `2026-09-20T10:30:00` 둘 다 받는다. 못 읽으면 null 이다. */
function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const normalized = value.length === 10 ? `${value}T00:00:00` : value;
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDay(date: Date, ko: boolean): string {
  const weekday = ko ? WEEKDAY_KO[date.getDay()] : WEEKDAY_EN[date.getDay()];
  return `${date.getMonth() + 1}.${date.getDate()}(${weekday})`;
}

function formatTime(value: string | null | undefined): string | null {
  const date = parseDate(value);
  // 🔴 날짜만 있는 값(길이 10)에서 시각을 만들지 않는다. 00:00 이 출발 시각인 척한다.
  if (!date || !value || value.length === 10) return null;
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/**
 * 일정 id 에서 사람이 읽을 코드를 만든다 — `GB-7F3A9C`.
 *
 * 🔴 무작위로 만들지 않는다. 같은 일정을 다시 열었을 때 코드가 바뀌면, 사람은 그것을
 * 「다른 여행」으로 읽는다. id 가 없으면 코드도 없다(빈 문자열) — 지어내지 않는다.
 */
export function tripPassCode(itineraryId: string | null | undefined): string {
  if (!itineraryId) return '';
  const compact = itineraryId.replace(/[^0-9a-zA-Z]/g, '').toUpperCase();
  return compact ? `GB-${compact.slice(0, 6)}` : '';
}

/**
 * 티켓의 QR 이 담는 주소 — 그 일정을 여는 화면이다.
 *
 * 🔴 `/trips/{id}/itinerary` 의 `{id}` 는 **일정 id 다. 여행 id 가 아니다.**
 * 이 저장소가 그 둘을 헷갈려 「이 여행을 찾을 수 없어요」를 낸 적이 있다(S15P21E201-1178).
 *
 * 🔴 **일정 id 가 없으면 주소도 없다(null).** 찍어도 안 열리는 QR 을 그리느니 안 그린다 —
 * 안 열리는 QR 은 「고장난 앱」으로 읽힌다.
 */
export function tripPassUrl(itineraryId: string | null | undefined, baseUrl: string | null | undefined): string | null {
  const id = (itineraryId ?? '').trim();
  const base = (baseUrl ?? '').trim().replace(/\/+$/, '');
  if (!id || !base) return null;
  return `${base}/trips/${encodeURIComponent(id)}/itinerary`;
}

/** 출발지 이름을 티켓의 큰 글자 칸에 맞게 줄인다. 자르면 뜻이 사라지므로 8자까지만 둔다. */
export function shortenOrigin(origin: string | null | undefined): string {
  const trimmed = (origin ?? '').trim();
  if (!trimmed) return '';
  return trimmed.length <= 8 ? trimmed : `${trimmed.slice(0, 7)}…`;
}

function formatWalking(meters: number | null | undefined, ko: boolean): string | null {
  if (typeof meters !== 'number' || !Number.isFinite(meters) || meters <= 0) return null;
  if (meters < 1000) return `${Math.round(meters)}m`;
  const km = (meters / 1000).toFixed(1);
  return ko ? `${km}km` : `${km} km`;
}

function formatCost(krw: number | null | undefined, ko: boolean): string | null {
  if (typeof krw !== 'number' || !Number.isFinite(krw) || krw <= 0) return null;
  const man = Math.round(krw / 1000) / 10;
  return ko ? `${man}만원` : `₩${krw.toLocaleString()}`;
}

export type TripPassInput = {
  itinerary: ItineraryDto | null;
  /** 배포 주소. QR 이 담을 주소를 만드는 데 쓴다. */
  baseUrl?: string | null;
  origin: string | null;
  startDate: string | null;
  endDate: string | null;
  transport: string | null;
  travelers: number | null;
  ownerName: string | null;
  language: 'ko' | 'en';
};

export function buildTripPass(input: TripPassInput): TripPassData {
  const ko = input.language === 'ko';
  const days = input.itinerary?.days ?? [];
  const allItems = days.flatMap((day) => day.items);

  // 🔴 실제 일정이 있으면 그 날짜가 이긴다. 초안의 날짜는 「요청한 날짜」이고
  //    일정의 날짜는 「실제로 만들어진 날짜」다 — 둘이 다를 수 있고, 티켓은 후자를 말해야 한다.
  const firstDate = parseDate(days[0]?.date ?? input.startDate);
  const lastDate = parseDate(days.at(-1)?.date ?? input.endDate);

  const dateRange = firstDate
    ? lastDate && lastDate.getTime() !== firstDate.getTime()
      ? `${formatDay(firstDate, ko)} – ${formatDay(lastDate, ko)}`
      : formatDay(firstDate, ko)
    : null;

  const fields: TripPassField[] = [];
  const visitCount = allItems.length;
  if (visitCount > 0) fields.push({ key: ko ? '방문지' : 'Stops', value: ko ? `${visitCount}곳` : String(visitCount) });
  if (days.length > 0) fields.push({ key: ko ? '일정' : 'Days', value: ko ? `${days.length}일` : `${days.length}d` });

  const walking = formatWalking(input.itinerary?.totalWalkingMeters, ko);
  if (walking) fields.push({ key: ko ? '걷는 거리' : 'Walking', value: walking });

  const cost = formatCost(input.itinerary?.totalEstimatedCostKrw, ko);
  if (cost) fields.push({ key: ko ? '예상 비용' : 'Est. cost', value: cost });

  if (typeof input.travelers === 'number' && input.travelers > 0) {
    fields.push({ key: ko ? '인원' : 'Travelers', value: ko ? `${input.travelers}명` : String(input.travelers) });
  }

  const firstStopName = allItems[0]?.title?.trim();
  if (firstStopName) fields.push({ key: ko ? '첫 일정' : 'First stop', value: firstStopName });

  const modeKey = (input.transport ?? '').toUpperCase();
  const modeLabel = MODE_LABEL[modeKey];

  return {
    code: tripPassCode(input.itinerary?.id),
    fromLabel: shortenOrigin(input.origin),
    toLabel: ko ? '부산' : 'BUSAN',
    startTime: formatTime(allItems[0]?.startsAt),
    endTime: formatTime(allItems.at(-1)?.startsAt),
    dateRange,
    mode: modeLabel ? (ko ? modeLabel.ko : modeLabel.en) : null,
    owner: input.ownerName?.trim() || null,
    fields: fields.slice(0, 6),
    url: tripPassUrl(input.itinerary?.id, input.baseUrl),
    validText: dateRange
      ? ko
        ? `이 승차권은 ${dateRange} 여행에만 쓸 수 있어요`
        : `Valid for ${dateRange}`
      : ko
        ? '가볼래 여행 승차권'
        : 'GABOLLE trip pass',
  };
}
