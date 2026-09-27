import type { PlanDraft } from './PlanProvider';
import { localToday } from './tripBasics';
import { AREA_OPTIONS, CATEGORY_OPTIONS } from './planOptions';

/**
 * 비서(챗봇)가 대화에서 뽑아 주소에 실어 보낸 일수·인원을 여행 초안에 채운다 —
 * S15P21E201-1274.
 *
 * 서버(`GeminiAssistantAdapter.withPrefill`)는 `/plan?days=2&people=4` 처럼 보낸다.
 * 그런데 이 값을 읽는 화면이 여태 하나도 없었다. 옛 `basics.tsx` 도 안 읽었으니
 * 1233(조건 한 페이지 개편)이 깨뜨린 것이 아니라 처음부터 안 이어져 있던 자리다.
 */

/** 서버가 보내는 범위. 벗어나면 서버가 그 파라미터만 빼지만, 앱도 스스로 막는다. */
const MAX_PEOPLE = 20;

/**
 * 🔴 앱이 받아들이는 여행 길이는 **7박까지**다 (`validateTripBasics` 의
 * "여행은 최대 7박까지 설정할 수 있어요"). 서버는 `days` 를 30 까지 허용하므로
 * 둘이 안 맞는다. 8일을 넘는 요청은 **날짜를 아예 안 채운다** — 30일이라고 말한
 * 사람에게 8일짜리를 슬쩍 내주면, 고쳐진 줄 모르고 그대로 만들기 때문이다.
 */
const MAX_NIGHTS = 7;
const MAX_DAYS = MAX_NIGHTS + 1;

type Param = string | string[] | undefined;
/** areas·categories·start 는 S15P21E201-1826 에서 더했다 — 서버(-1825)가 `/plan?people=2&areas=GWANGALLI&categories=FOOD` 로 보낸다. */
export type AssistantPrefillParams = { days?: Param; people?: Param; areas?: Param; categories?: Param; start?: Param };

const AREA_CODES = AREA_OPTIONS.map(([code]) => code);
const CATEGORY_CODES = CATEGORY_OPTIONS.map(([code]) => code);

/** 쉼표로 이은 코드 목록 가운데 앱이 아는 것만, 중복 없이. */
function codes(value: Param, allowed: readonly string[]): string[] {
  const raw = first(value);
  if (!raw) return [];
  const picked = raw.split(',').map((part) => part.trim().toUpperCase()).filter((code) => allowed.includes(code));
  return [...new Set(picked)];
}

function isoDate(value: Param): string | null {
  const raw = first(value)?.trim();
  return raw && /^20\d{2}-\d{2}-\d{2}$/.test(raw) && !Number.isNaN(Date.parse(`${raw}T00:00:00Z`)) ? raw : null;
}

/** expo-router 는 같은 이름이 두 번 오면 배열로 준다. 첫 값만 본다. */
function first(value: Param): string | undefined {
  if (Array.isArray(value)) return value[0];
  return value;
}

function positiveInt(value: Param): number | null {
  const raw = first(value);
  if (raw === undefined || !/^\d+$/.test(raw.trim())) return null;
  const parsed = Number(raw.trim());
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function addDays(isoDate: string, count: number): string {
  return new Date(Date.parse(`${isoDate}T00:00:00Z`) + count * 86400000).toISOString().slice(0, 10);
}

/**
 * 채울 값만 담은 조각을 낸다. 채울 것이 없으면 빈 객체다.
 *
 * 🔴 **사용자가 말하지 않은 것은 건드리지 않는다.** 주소에 없는 칸은 그대로 둔다.
 *    반대로 주소에 실린 것은 방금 사용자가 말한 것이므로 예전 초안보다 앞선다 — S15P21E201-1826.
 *    「광안리 맛집 2명」을 말했는데 지난번 초안의 1명·당일치기가 남아 있었다(갤럭시 S10 실기).
 *
 * 날짜만 예외가 하나 있다 — 이미 **앞으로의** 날짜를 골라 둔 사람이 일수만 말하면(「2박3일」)
 * 그 출발일에서 센다. 지난 날짜·오늘 날짜는 쓸 수 없으니 내일부터 다시 잡는다.
 */
export function assistantPrefillPatch(
  params: AssistantPrefillParams,
  draft: PlanDraft,
  today: string = localToday(),
): Partial<PlanDraft> {
  const patch: Partial<PlanDraft> = {};

  const days = positiveInt(params.days);
  const given = isoDate(params.start);
  const start = given && given > today ? given : null;
  if (start || (days !== null && days <= MAX_DAYS)) {
    const kept = draft.startDate && draft.startDate > today ? draft.startDate : null;
    // 날짜를 말하지 않은 사람에게 오늘을 잡아 주면 이미 지난 시간이 섞인다. 내일부터 센다.
    const from = start ?? kept ?? addDays(today, 1);
    const length = days !== null && days <= MAX_DAYS ? days : null;
    if (length !== null) {
      patch.startDate = from;
      patch.endDate = addDays(from, length - 1);
    } else if (start) {
      // 출발일만 왔다 — 전에 고른 길이를 지키되, 끝이 출발보다 앞서면 당일치기로 둔다.
      const prevLength = draft.startDate && draft.endDate && draft.endDate >= draft.startDate
        ? Math.round((Date.parse(draft.endDate) - Date.parse(draft.startDate)) / 86400000) + 1 : 1;
      patch.startDate = start;
      patch.endDate = addDays(start, Math.min(prevLength, MAX_DAYS) - 1);
    }
  }

  const people = positiveInt(params.people);
  if (people !== null && people <= MAX_PEOPLE) {
    // 인원을 나누는 방식은 assistant/intent.ts 가 이미 쓰던 것과 같게 둔다 — 전원 성인.
    patch.travelers = people;
    patch.adults = people;
    patch.children = 0;
  }

  const areas = codes(params.areas, AREA_CODES);
  if (areas.length) patch.travelAreas = areas;

  const categories = codes(params.categories, CATEGORY_CODES);
  if (categories.length) {
    patch.preferences = categories;
    patch.preferenceAnswerStatus = { ...draft.preferenceAnswerStatus, category: 'SELECTED' };
  }

  return patch;
}
