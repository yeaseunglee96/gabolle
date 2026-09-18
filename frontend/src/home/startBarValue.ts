// 홈 시작 바가 다루는 값 — 화면이 아니라 여기서 만든다 (S15P21E201-1233).
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p0.
//
// 🔴 날짜 계산은 눈으로 검산이 안 된다. 「1박 2일」이 이틀인지 사흘인지, 월이 바뀔 때
//    어떻게 되는지는 시험이 붙들어야 한다.

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

const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'];
const WEEKDAY_EN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** `2026-09-20` 로 만든다. `toISOString()` 은 UTC 라 한국에서 하루 밀린다. */
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

export function formatDateShort(key: string, ko: boolean): string {
  const date = parseDateKey(key);
  if (!date) return '';
  const weekday = ko ? WEEKDAY_KO[date.getDay()] : WEEKDAY_EN[date.getDay()];
  return `${date.getMonth() + 1}.${date.getDate()}(${weekday})`;
}

/**
 * 시작 바에 한 줄로 보여 줄 요약 — 「부산역 · 9.20(토) – 9.21(일) · 1박 · 성인 2」.
 *
 * 🔴 **채워진 것만 적는다.** 안 고른 칸을 「미정」 같은 말로 채우면, 사람은 그것을
 * 「내가 골랐는데 반영이 안 됐다」로 읽는다. 아무것도 없으면 빈 문자열이고
 * 화면이 그때만 안내 문구를 쓴다.
 */
export function summarizeStartBar(value: StartBarValue, ko: boolean): string {
  // 🔴 인원만 있는 요약은 만들지 않는다 (2026-09-18, 폰 화면을 띄워서 찾았다).
  //
  //    인원에는 **기본값(성인 2)이 들어 있다.** 그래서 아무것도 안 고른 사람에게도
  //    요약이 「성인 2」로 나왔고, 알약에 안내 문구 대신 그것이 찍혔다 —
  //    **고른 적 없는 값이 고른 것처럼 보였다.**
  //
  //    사람이 실제로 고른 것은 출발지와 날짜다. 둘 다 없으면 **요약이 없는 것**이고,
  //    그때는 화면이 「여행 계획 시작해 보세요」를 쓴다.
  if (!value.origin.trim() && !value.startDate) return '';

  const parts: string[] = [];
  if (value.origin.trim()) parts.push(value.origin.trim());

  if (value.startDate) {
    const range = value.endDate && value.endDate !== value.startDate
      ? `${formatDateShort(value.startDate, ko)} – ${formatDateShort(value.endDate, ko)}`
      : formatDateShort(value.startDate, ko);
    parts.push(range);
    const nights = nightCount(value.startDate, value.endDate || value.startDate);
    parts.push(nights > 0 ? (ko ? `${nights}박` : `${nights} nights`) : ko ? '당일치기' : 'Day trip');
  }

  const people: string[] = [];
  if (value.adults > 0) people.push(ko ? `성인 ${value.adults}` : `${value.adults} adults`);
  if (value.children > 0) people.push(ko ? `어린이 ${value.children}` : `${value.children} children`);
  if (people.length) parts.push(people.join(' · '));

  return parts.join(' · ');
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

/**
 * 「바로 시작」 프리셋. 누르면 바가 채워진다.
 *
 * 🔴 오늘 날짜를 인자로 받는다 — 안에서 `new Date()` 를 부르면 시험이 날짜에 따라
 * 붙었다 떨어졌다 한다.
 */
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
