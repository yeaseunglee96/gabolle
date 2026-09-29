// 홈 시작 바가 다루는 값 — 화면이 아니라 여기서 만든다.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p0.

import { stopNameForLanguage } from '@/discovery/romanize';
import { txf } from '@/i18n/format';
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
import { lodgingMissing } from '@/plan/lodgingRequired';
import type { PlaceSnapshot } from '@/plan/origins';

/**
 * 고른 출발지·숙소의 영어 이름 — S15P21E201-1795(고지혁 QA). **화면에만 쓴다.**
 *
 * 🔴 어느 한국어 이름의 것인지(name)를 같이 든다. origin·lodging 은 여러 곳에서 바뀌는데, 영어 이름만
 *    따로 들면 한국어 이름이 바뀐 뒤에도 옛 영어 이름이 남아 **다른 곳의 이름**을 보일 수 있다.
 *    한국어 이름이 같을 때만 쓰므로 그런 값은 저절로 버려진다(englishNameOf).
 * 🔴 서버로 가는 이름은 언제나 origin·lodging(한국어)이다 — 이 칸은 서버로 안 간다.
 */
export type PlaceEnglishName = { name: string; nameEn: string };

export type StartBarValue = {
  origin: string;
  originLat: number | null;
  originLng: number | null;
  /** 숙소 표시 이름. '' 는 미정 — 시안 design_handoff_home_lodging. */
  lodging: string;
  lodgingLat: number | null;
  lodgingLng: number | null;
  /**
   * 고른 숙소를 서버가 장소로 찾거나 만들 수 있게 든다 — S15P21E201-1536. 좌표만으로는 서버가 받을 칸이
   * 없어 버려졌다. 검색 결과가 아닌 것(미정)은 null.
   */
  lodgingPlace: PlaceSnapshot | null;
  /** 출발지의 영어 이름 — 화면용(PlaceEnglishName). 없으면 null. 예전에 저장된 값에는 칸이 없다. */
  originEnglish?: PlaceEnglishName | null;
  /** 숙소의 영어 이름 — 화면용(PlaceEnglishName). 없으면 null. */
  lodgingEnglish?: PlaceEnglishName | null;
  startDate: string;
  endDate: string;
  adults: number;
  children: number;
};

/** 고른 후보의 영어 이름을 한국어 이름과 짝지어 든다. 영어 이름이 없으면 null — 지어내지 않는다. */
export function placeEnglishOf(candidate: { name: string; nameEn?: string | null }): PlaceEnglishName | null {
  const nameEn = candidate.nameEn?.trim();
  return nameEn ? { name: candidate.name, nameEn } : null;
}

/** 지금 이름(name)의 영어 이름. 짝이 다른 이름의 것이면 null — 위 PlaceEnglishName 의 🔴 첫째. */
export function englishNameOf(name: string, english: PlaceEnglishName | null | undefined): string | null {
  return english && english.name === name ? english.nameEn : null;
}

/**
 * 출발지·숙소를 화면에 적는 이름. 언어를 주면 다른 화면과 같은 규칙(stopNameForLanguage)이다 —
 * 한국어 화면은 그대로, 그 밖은 「영어 (한글)」, 영어 이름이 없으면 「한글 (로마자)」.
 * 언어를 안 주면 예전처럼 한국어 이름 그대로다.
 */
export function startBarPlaceName(name: string, english: PlaceEnglishName | null | undefined, language?: LanguageCode): string {
  const trimmed = name.trim();
  if (!language || !trimmed) return trimmed;
  return stopNameForLanguage(trimmed, englishNameOf(name, english), language);
}

/**
 * 한 줄 요약(summarizeStartBar)에 쓰는 짧은 이름 — 한국어가 아닌 화면이면 영어 이름만, 없으면 한국어 이름 그대로.
 * 🔴 「Busan Station (부산역) · Haeundae (해운대)」로 쓰면 폰 폭(375)의 알약이 이름만으로 차서 날짜가 잘렸다(2026-09-27 실측).
 *    알약은 한 줄이라 읽는 법·괄호를 붙이지 않는다. 한글을 같이 보이는 것은 칸·칩(startBarPlaceName)이 한다.
 */
export function startBarPlaceShortName(name: string, english: PlaceEnglishName | null | undefined, language?: LanguageCode): string {
  const trimmed = name.trim();
  if (!language || resolveTextLanguage(language) === 'ko') return trimmed;
  return englishNameOf(name, english) ?? trimmed;
}

export const EMPTY_START_BAR: StartBarValue = {
  origin: '',
  originLat: null,
  originLng: null,
  lodging: '',
  lodgingLat: null,
  lodgingLng: null,
  lodgingPlace: null,
  originEnglish: null,
  lodgingEnglish: null,
  startDate: '',
  endDate: '',
  adults: 2,
  children: 0,
};

/** 시작 바에서 열 수 있는 칸. */
export type StartBarSection = 'origin' | 'lodging' | 'dates' | 'people' | null;

/**
 * 🔴 주소의 `?edit=` 를 열 칸으로 바꾼다 — S15P21E201-1350.
 *
 * <p>주소에서 온 값은 무엇이든 올 수 있다(사용자가 손으로 고칠 수도 있고, 배열로도 온다).
 * 모르는 값이면 <b>아무 칸도 안 연다</b> — 예전처럼 접힌 바가 뜰 뿐이라 나빠지지 않는다.
 */
export function startBarEditSection(raw: string | string[] | undefined): StartBarSection {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value === 'origin' || value === 'lodging' || value === 'dates' || value === 'people' ? value : null;
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
    lodging: draft.lodging,
    lodgingLat: draft.lodgingLat,
    lodgingLng: draft.lodgingLng,
    // 예전에 저장된 초안에는 이 칸이 없다 — 없으면 null.
    lodgingPlace: draft.lodgingPlace ?? null,
    // 화면용 영어 이름(S15P21E201-1795). 예전 초안에는 칸이 없다 — 없는 채로 옮긴다(읽는 쪽이 없음으로 본다).
    originEnglish: draft.originEnglish,
    lodgingEnglish: draft.lodgingEnglish,
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

/**
 * 시작 바에 한 줄로 보여 줄 요약 — 「부산역 · 9.20(토) – 9.21(일) · 1박 · 성인 2」.
 * language 를 주면 장소 이름을 그 언어로 짧게 적는다(startBarPlaceShortName, S15P21E201-1795).
 */
export function summarizeStartBar(value: StartBarValue, tx: StartBarTx, language?: LanguageCode): string {
  // 인원에는 기본값(성인 2)이 들어 있다. 그래서 아무것도 안 고른 사람에게도
  // 요약이 「성인 2」로 나왔고, 알약에 안내 문구 대신 그것이 찍혔다
  // 고른 적 없는 값이 고른 것처럼 보였다.
  if (!value.origin.trim() && !value.startDate) return '';

  const parts: string[] = [];
  if (value.origin.trim()) parts.push(startBarPlaceShortName(value.origin, value.originEnglish, language));
  if (value.lodging.trim()) parts.push(startBarPlaceShortName(value.lodging, value.lodgingEnglish, language));

  if (value.startDate) {
    const range = value.endDate && value.endDate !== value.startDate
      ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(value.endDate, tx)}`
      : formatDateShort(value.startDate, tx);
    parts.push(range);
    const nights = nightCount(value.startDate, value.endDate || value.startDate);
    parts.push(nights > 0 ? tx(`${nights}박`, `${nights} ${nights === 1 ? 'night' : 'nights'}`) : tx('당일치기', 'Day trip'));
  }

  const people: string[] = [];
  if (value.adults > 0) people.push(tx(`성인 ${value.adults}`, `${value.adults} adults`));
  if (value.children > 0) people.push(tx(`어린이 ${value.children}`, `${value.children} children`));
  if (people.length) parts.push(people.join(' · '));

  return parts.join(' · ');
}

/**
 * 「홈에서 받은 정보」를 칩 세 개로 쪼갠다 — 시안 p1.
 * language 를 주면 장소 이름을 그 언어의 규칙으로 적는다 — 영어 화면에 「From 부산역」이 나오던 것(S15P21E201-1795).
 */
export function startBarChips(value: StartBarValue, tx: StartBarTx, language?: LanguageCode): string[] {
  if (!value.origin.trim() && !value.startDate) return [];
  const chips: string[] = [];
  if (value.origin.trim()) chips.push(txf(tx, '%s 출발', 'From %s', startBarPlaceName(value.origin, value.originEnglish, language)));
  if (value.lodging.trim()) chips.push(txf(tx, '%s 숙박', 'Staying in %s', startBarPlaceName(value.lodging, value.lodgingEnglish, language)));
  if (value.startDate) {
    const range = value.endDate && value.endDate !== value.startDate
      ? `${formatDateShort(value.startDate, tx)} – ${formatDateShort(value.endDate, tx)}`
      : formatDateShort(value.startDate, tx);
    const nights = nightCount(value.startDate, value.endDate || value.startDate);
    const stay = nights > 0 ? tx(`${nights}박`, `${nights} ${nights === 1 ? 'night' : 'nights'}`) : tx('당일치기', 'Day trip');
    chips.push(`${range} · ${stay}`);
  }
  const people: string[] = [];
  if (value.adults > 0) people.push(tx(`성인 ${value.adults}`, `${value.adults} adults`));
  if (value.children > 0) people.push(tx(`어린이 ${value.children}`, `${value.children} children`));
  if (people.length) chips.push(people.join(' · '));
  return chips;
}

/**
 * 여행 만들기로 넘길 때의 귀환일 — 시작일만 있으면 시작일(당일치기).
 *
 * 🔴 시작 바는 시작일만 있어도 요약·칩에 「당일치기」라고 적고 「일정 물어보기」를 켠다. 그런데 넘길 때
 *    귀환일을 빈 채로 넘겨, 여행 만들기 화면이 날짜 카드를 다시 열고 「귀환일까지 골라 주세요」로 잠갔다
 *    (S15P21E201-1729). 보여 준 것과 넘기는 것을 같게 한다.
 */
export function startBarEndDate(value: StartBarValue): string {
  return value.endDate || value.startDate;
}

/** 「일정 물어보기」를 누를 수 있나 — 출발지와 날짜가 있어야 한다. */
export function canAskForPlan(value: StartBarValue): boolean {
  // 🔴 출발지는 안 묻는다 — S15P21E201-1376. 서버(CreateTripRequest)는 originLat·originLng 를 선택으로
  //    받고, 열 문항 화면도 출발지 없이 만들게 한다. 여기서만 요구하니 「일정 물어보기」가 이유 없이
  //    잠겼다(2026-09-21 실기). 출발지가 있으면 첫 이동 시간이 붙고, 없으면 그 줄만 없다.
  // 🔴 1박 이상이면 숙소가 있어야 한다 — S15P21E201-1584. 당일치기는 숙소 없이 된다.
  return Boolean(value.startDate) && value.adults + value.children > 0 && !lodgingMissing(value);
}

/** 왜 못 누르나 — 잠긴 단추 대신 이 말을 단추에 쓴다. 누를 수 있으면 null. */
export function askForPlanBlocker(value: StartBarValue, tx: StartBarTx): string | null {
  if (!value.startDate) return tx('날짜를 골라 주세요', 'Pick your dates');
  if (value.adults + value.children <= 0) return tx('인원을 정해 주세요', 'Set the party size');
  if (lodgingMissing(value)) return tx('숙소를 골라 주세요', 'Pick where you will stay');
  return null;
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
    apply: () => ({ origin: '부산역', originEnglish: { name: '부산역', nameEn: 'Busan Station' }, originLat: 35.1152, originLng: 129.0403 }),
  },
];
