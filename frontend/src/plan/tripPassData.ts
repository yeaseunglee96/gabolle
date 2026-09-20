// 여행 티켓(TRIP PASS)에 찍히는 값 — 화면이 아니라 여기서 만든다.

import type { ItineraryDto } from '@/plan/itinerary';
import { pickLanguage } from '@/i18n';
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';

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

function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const normalized = value.length === 10 ? `${value}T00:00:00` : value;
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}

type Tx = (ko: string, en: string) => string;

function formatDay(date: Date, tx: Tx): string {
  const weekday = tx(WEEKDAY_KO[date.getDay()], WEEKDAY_EN[date.getDay()]);
  return `${date.getMonth() + 1}.${date.getDate()}(${weekday})`;
}

function formatTime(value: string | null | undefined): string | null {
  const date = parseDate(value);
  // 날짜만 있는 값(길이 10)에서 시각을 만들지 않는다. 00:00 이 출발 시각인 척한다.
  if (!date || !value || value.length === 10) return null;
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/** 일정 id 에서 사람이 읽을 코드를 만든다 — `GB-7F3A9C`. */
export function tripPassCode(itineraryId: string | null | undefined): string {
  if (!itineraryId) return '';
  const compact = itineraryId.replace(/[^0-9a-zA-Z]/g, '').toUpperCase();
  return compact ? `GB-${compact.slice(0, 6)}` : '';
}

/** 티켓의 QR 이 담는 주소 — 그 일정을 여는 화면이다. */
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

function formatWalking(meters: number | null | undefined, tx: Tx): string | null {
  if (typeof meters !== 'number' || !Number.isFinite(meters) || meters <= 0) return null;
  if (meters < 1000) return `${Math.round(meters)}m`;
  const km = (meters / 1000).toFixed(1);
  return tx(`${km}km`, `${km} km`);
}

function formatCost(krw: number | null | undefined, tx: Tx): string | null {
  if (typeof krw !== 'number' || !Number.isFinite(krw) || krw <= 0) return null;
  const man = Math.round(krw / 1000) / 10;
  return tx(`${man}만원`, `₩${krw.toLocaleString()}`);
}

export type TripPassInput = {
  itinerary: ItineraryDto | null;
  /** 배포 주소. QR 이 담을 주소를 만드는 데 쓴다. */
  baseUrl?: string | null;
  origin: string | null;
  startDate: string | null;
  endDate: string | null;
  transport: string | null;
  ownerName: string | null;
  language: LanguageCode;
};

export function buildTripPass(input: TripPassInput): TripPassData {
  const tx: Tx = (ko, en) => pickLanguage(input.language, { ko, en });
  const days = input.itinerary?.days ?? [];
  const allItems = days.flatMap((day) => day.items);

  // 실제 일정이 있으면 그 날짜가 이긴다. 초안의 날짜는 「요청한 날짜」이고
  // 일정의 날짜는 「실제로 만들어진 날짜」다 — 둘이 다를 수 있고, 티켓은 후자를 말해야 한다.
  const firstDate = parseDate(days[0]?.date ?? input.startDate);
  const lastDate = parseDate(days.at(-1)?.date ?? input.endDate);

  const dateRange = firstDate
    ? lastDate && lastDate.getTime() !== firstDate.getTime()
      ? `${formatDay(firstDate, tx)} – ${formatDay(lastDate, tx)}`
      : formatDay(firstDate, tx)
    : null;

  const fields: TripPassField[] = [];
  const visitCount = allItems.length;
  if (visitCount > 0) fields.push({ key: tx('방문지', 'Stops'), value: tx(`${visitCount}곳`, String(visitCount)) });
  if (days.length > 0) fields.push({ key: tx('일정', 'Days'), value: tx(`${days.length}일`, `${days.length}d`) });

  const walking = formatWalking(input.itinerary?.totalWalkingMeters, tx);
  if (walking) fields.push({ key: tx('걷는 거리', 'Walking'), value: walking });

  const cost = formatCost(input.itinerary?.totalEstimatedCostKrw, tx);
  if (cost) fields.push({ key: tx('예상 비용', 'Est. cost'), value: cost });

  // 🔴 -1338 — 인원은 **일정이 말할 때만** 적는다.
  //
  // 예전에는 기기에 남은 초안(`draft.travelers`)에서 읽었다. 그 값은 기본이 **1** 이라
  // 초안이 비면 「1명」이라고 **단언**했고, 성인 2명으로 만든 여행도 그렇게 나왔다 —
  // 새로고침 한 번, 다른 기기면 전부 1명이었다(배포된 화면에서 실측).
  //
  // 🔴 초안으로 되돌아가지 않는다. 그 값은 「모른다」와 「혼자다」가 구분이 안 된다.
  //    옛 서버에 붙은 앱에서는 이 칸이 **아예 안 나온다** — 틀린 숫자보다 낫다.
  //    날짜가 이미 같은 규칙을 쓴다(위 「실제 일정이 있으면 그 날짜가 이긴다」).
  const partySize = input.itinerary?.partySize;
  if (typeof partySize === 'number' && Number.isFinite(partySize) && partySize > 0) {
    fields.push({ key: tx('인원', 'Travelers'), value: tx(`${partySize}명`, String(partySize)) });
  }

  const firstStopName = allItems[0]?.title?.trim();
  if (firstStopName) fields.push({ key: tx('첫 일정', 'First stop'), value: firstStopName });

  const modeKey = (input.transport ?? '').toUpperCase();
  const modeLabel = MODE_LABEL[modeKey];

  return {
    code: tripPassCode(input.itinerary?.id),
    fromLabel: shortenOrigin(input.origin),
    toLabel: tx('부산', 'BUSAN'),
    startTime: formatTime(allItems[0]?.startsAt),
    endTime: formatTime(allItems.at(-1)?.startsAt),
    dateRange,
    mode: modeLabel ? tx(modeLabel.ko, modeLabel.en) : null,
    owner: input.ownerName?.trim() || null,
    fields: fields.slice(0, 6),
    url: tripPassUrl(input.itinerary?.id, input.baseUrl),
    validText: dateRange
      ? txf(tx, '이 승차권은 %s 여행에만 쓸 수 있어요', 'Valid for %s', dateRange)
      : tx('가볼래 여행 승차권', 'GABOLLE trip pass'),
  };
}

/** 여행표 오른쪽 칸의 한 줄. 시안 TripPassCard 의 `details`. */
export type TripPassDetail = { key: string; value: string };

/** 시안 p4 의 여행표 오른쪽 칸 — 출발지 · 첫 일정 · 마지막 일정 · 이동 합계 · 예산. */
export function buildTripPassDetails(input: TripPassInput): TripPassDetail[] {
  const tx: Tx = (ko, en) => pickLanguage(input.language, { ko, en });
  const allItems = (input.itinerary?.days ?? []).flatMap((day) => day.items);
  const rows: TripPassDetail[] = [];

  const origin = input.origin?.trim();
  if (origin) rows.push({ key: tx('출발지', 'From'), value: origin });

  const stopLabel = (item: (typeof allItems)[number] | undefined) => {
    if (!item) return null;
    const time = formatTime(item.startsAt);
    const title = item.title?.trim();
    if (!title) return time;
    return time ? `${time} · ${title}` : title;
  };
  const first = stopLabel(allItems[0]);
  if (first) rows.push({ key: tx('첫 일정', 'First stop'), value: first });
  const last = allItems.length > 1 ? stopLabel(allItems[allItems.length - 1]) : null;
  if (last) rows.push({ key: tx('마지막 일정', 'Last stop'), value: last });

  // 값이 있는 구간만 더하고, 몇 구간을 셌는지 같이 적는다.
  const legs = allItems.filter((item) => typeof item.travelDurationMin === 'number' && item.travelDurationMin !== null);
  if (legs.length) {
    const minutes = legs.reduce((sum, item) => sum + (item.travelDurationMin ?? 0), 0);
    rows.push({
      key: tx('이동 합계', 'Travel total'),
      value: tx(`${minutes}분 (${legs.length}구간)`, `${minutes} min (${legs.length} legs)`),
    });
  }

  const cost = formatCost(input.itinerary?.totalEstimatedCostKrw, tx);
  if (cost) rows.push({ key: tx('예상 비용', 'Est. cost'), value: cost });

  return rows;
}
