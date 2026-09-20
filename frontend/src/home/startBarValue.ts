// 홈 시작 바가 다루는 값 — 화면이 아니라 여기서 만든다.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p0.

import { txf } from '@/i18n/format';

export type StartBarValue = {
  origin: string;
  originLat: number | null;
  originLng: number | null;
  startDate: string;
  endDate: string;
  adults: number;
  children: number;
};

export const EMPTY_START_BAR: StartBarValue = {
  origin: '',
  originLat: null,
  originLng: null,
  startDate: '',
  endDate: '',
  adults: 2,
  children: 0,
};

/** 시작 바에서 열 수 있는 칸. */
export type StartBarSection = 'origin' | 'dates' | 'people' | null;

/**
 * 🔴 주소의 `?edit=` 를 열 칸으로 바꾼다 — S15P21E201-1350.
 *
 * <p>주소에서 온 값은 무엇이든 올 수 있다(사용자가 손으로 고칠 수도 있고, 배열로도 온다).
 * 모르는 값이면 <b>아무 칸도 안 연다</b> — 예전처럼 접힌 바가 뜰 뿐이라 나빠지지 않는다.
 */
export function startBarEditSection(raw: string | string[] | undefined): StartBarSection {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value === 'origin' || value === 'dates' || value === 'people' ? value : null;
}

/**
 * 🔴 이미 답한 것을 시작 바에 다시 채운다 — S15P21E201-1350.
 *
 * <p>열 문항 화면에서 「날짜 정하기」를 누르면 홈으로 온다. 그때 시작 바가 빈 채로
 * 뜨면 사람은 「내가 넣은 것이 날아갔나」로 읽고 처음부터 다시 넣는다. 그래서
 * 가지고 있는 값을 그대로 옮겨 넣어 둔다.
 *
 * <p>칸 이름이 양쪽이 같아 그대로 옮기면 되지만, 그 «그대로» 가 진짜인지를 사람이
 * 눈으로 확인하기 어렵다. 그래서 함수로 뺀다 — 여기서 한 칸이라도 빠지면 시험이 잡는다.
 */
export function startBarFromDraft(draft: StartBarValue): StartBarValue {
  return {
    origin: draft.origin,
    originLat: draft.originLat,
    originLng: draft.originLng,
    startDate: draft.startDate,
    endDate: draft.endDate,
    // 인원은 0 이 될 수 없다. 빈 초안이면 시작 바의 기본값을 쓴다.
    adults: draft.adults > 0 ? draft.adults : EMPTY_START_BAR.adults,
    children: draft.children > 0 ? draft.children : 0,
  };
}

const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'];
const WEEKDAY_EN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function toDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function parseDateKey(key: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
  const date = new Date(`${key}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function addDays(key: string, days: number): string {
  const date = parseDateKey(key);
  if (!date) return key;
  date.setDate(date.getDate() + days);
  return toDateKey(date);
}

/** 두 날짜가 며칠짜리인가 — 같은 날이면 1일이다(당일치기). */
export function dayCount(startKey: string, endKey: string): number {
  const start = parseDateKey(startKey);
  const end = parseDateKey(endKey);
  if (!start || !end) return 0;
  const diff = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
  return diff > 0 ? diff : 0;
}

/** 「1박 2일」의 밤 수. 당일치기는 0박이다. */
export function nightCount(startKey: string, endKey: string): number {
  const days = dayCount(startKey, endKey);
  return days > 0 ? days - 1 : 0;
}

export type StartBarTx = (ko: string, en: string) => string;

/** 「9.20(토)」 — 요일은 고른 언어로(번역표에 요일 일곱 개가 있다). */
export function formatDateShort(key: string, tx: StartBarTx): string {
  const date = parseDateKey(key);
  if (!date) return '';
  const weekday = tx(WEEKDAY_KO[date.getDay()], WEEKDAY_EN[date.getDay()]);
  return `${date.getMonth() + 1}.${date.getDate()}(${weekday})`;
}

/** 시작 바에 한 줄로 보여 줄 요약 — 「부산역 · 9.20(토) – 9.21(일) · 1박 · 성인 2」. */
export function summarizeStartBar(value: StartBarValue, tx: StartBarTx): string {
  // 인원에는 기본값(성인 2)이 들어 있다. 그래서 아무것도 안 고른 사람에게도
  // 요약이 「성인 2」로 나왔고, 알약에 안내 문구 대신 그것이 찍혔다
  // 고른 적 없는 값이 고른 것처럼 보였다.
  if (!value.origin.trim() && !value.startDate) return '';

  const parts: string[] = [];
  if (value.origin.trim()) parts.push(value.origin.trim());

  if (value.startDate) {
    const range = value.endDate && value.endDate !== value.startDate
      ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(value.endDate, tx)}`
      : formatDateShort(value.startDate, tx);
    parts.push(range);
    const nights = nightCount(value.startDate, value.endDate || value.startDate);
    parts.push(nights > 0 ? tx(`${nights}박`, `${nights} nights`) : tx('당일치기', 'Day trip'));
  }

  const people: string[] = [];
  if (value.adults > 0) people.push(tx(`성인 ${value.adults}`, `${value.adults} adults`));
  if (value.children > 0) people.push(tx(`어린이 ${value.children}`, `${value.children} children`));
  if (people.length) parts.push(people.join(' · '));

  return parts.join(' · ');
}

/** 「홈에서 받은 정보」를 칩 세 개로 쪼갠다 — 시안 p1 */
export function startBarChips(value: StartBarValue, tx: StartBarTx): string[] {
  if (!value.origin.trim() && !value.startDate) return [];
  const chips: string[] = [];
  if (value.origin.trim()) chips.push(txf(tx, '%s 출발', 'From %s', value.origin.trim()));
  if (value.startDate) {
    const range = value.endDate && value.endDate !== value.startDate
      ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(value.endDate, tx)}`
      : formatDateShort(value.startDate, tx);
    const nights = nightCount(value.startDate, value.endDate || value.startDate);
    const stay = nights > 0 ? tx(`${nights}박`, `${nights} nights`) : tx('당일치기', 'Day trip');
    chips.push(`${range} · ${stay}`);
  }
  const people: string[] = [];
  if (value.adults > 0) people.push(tx(`성인 ${value.adults}`, `${value.adults} adults`));
  if (value.children > 0) people.push(tx(`어린이 ${value.children}`, `${value.children} children`));
  if (people.length) chips.push(people.join(' · '));
  return chips;
}

/** 「일정 물어보기」를 누를 수 있나 — 출발지와 날짜가 있어야 한다. */
export function canAskForPlan(value: StartBarValue): boolean {
  return Boolean(value.origin.trim()) && Boolean(value.startDate) && value.adults + value.children > 0;
}

export type StartBarPreset = { id: string; ko: string; en: string; apply: (today: Date) => Partial<StartBarValue> };

/** 다음 토요일. 오늘이 토요일이면 오늘이다. */
function nextSaturday(today: Date): Date {
  const date = new Date(today.getTime());
  date.setDate(date.getDate() + ((6 - date.getDay() + 7) % 7));
  return date;
}

/** 「바로 시작」 프리셋. 누르면 바가 채워진다. */
export const START_BAR_PRESETS: StartBarPreset[] = [
  {
    id: 'weekend-1n',
    ko: '이번 주말 1박 2일',
    en: 'This weekend, 1 night',
    apply: (today) => {
      const start = toDateKey(nextSaturday(today));
      return { startDate: start, endDate: addDays(start, 1) };
    },
  },
  {
    id: 'two-2n',
    ko: '둘이서 2박 3일',
    en: 'Two travelers, 2 nights',
    apply: (today) => {
      const start = toDateKey(nextSaturday(today));
      return { startDate: start, endDate: addDays(start, 2), adults: 2, children: 0 };
    },
  },
  {
    id: 'kid-day',
    ko: '아이와 당일치기',
    en: 'Day trip with a child',
    apply: (today) => {
      const start = toDateKey(nextSaturday(today));
      return { startDate: start, endDate: start, adults: 2, children: 1 };
    },
  },
  {
    id: 'from-station',
    ko: '부산역 출발',
    en: 'Start at Busan Station',
    apply: () => ({ origin: '부산역', originLat: 35.1152, originLng: 129.0403 }),
  },
];
