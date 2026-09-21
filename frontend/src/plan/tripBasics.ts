import type { PlanDraft } from './PlanProvider';

export type TripBasicsErrors = Partial<Record<'startDate' | 'endDate' | 'adults' | 'children' | 'travelers' | 'budgetKrw' | 'dayStartTime' | 'dayEndTime' | 'transport' | 'origin', string>>;

function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
function dateDays(from: string, to: string) { return (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000; }
/**
 * 「HH:MM」을 분으로. 형식이 아니거나 24시·60분을 넘으면 NaN.
 *
 * 🔴 **밖으로 연 이유** — 이 판정이 두 곳에 필요하다. 질문이 다음으로 넘어가도 되는지와,
 *    화면이 무엇이 틀렸는지 적는 것. 두 벌로 만들면 한쪽만 고쳐져서 조용히 어긋난다
 *    (S15P21E201-1452 가 정확히 그렇게 났다 — 검사 함수는 있는데 아무도 안 불렀다).
 */
export function timeToMinutes(value: string) { const match = /^(\d{2}):(\d{2})$/.exec(value); if (!match) return Number.NaN; const hour = Number(match[1]); const minute = Number(match[2]); return hour <= 23 && minute <= 59 ? hour * 60 + minute : Number.NaN; }
export function localToday(now = new Date()) { const offset = now.getTimezoneOffset() * 60000; return new Date(now.getTime() - offset).toISOString().slice(0, 10); }

/** 예산 한 칸의 크기. 검증도 10,000원 단위를 요구하므로 같은 값을 쓴다. */
export const BUDGET_UNIT_KRW = 10000;

export function formatBudgetKo(amountKrw: number) {
  return amountKrw % BUDGET_UNIT_KRW === 0 ? `${(amountKrw / BUDGET_UNIT_KRW).toLocaleString('ko-KR')}만 원` : `${amountKrw.toLocaleString('ko-KR')}원`;
}
/** 영어에는 "만" 에 해당하는 자리가 없어서 원 단위 금액을 그대로 쓴다. */
export function formatBudgetEn(amountKrw: number) {
  return `₩${amountKrw.toLocaleString('en-US')}`;
}

export function validateTripBasics(draft: PlanDraft, today = localToday()): TripBasicsErrors {
  const errors: TripBasicsErrors = {};
  if (!validDate(draft.startDate)) errors.startDate = '시작일을 YYYY-MM-DD 형식으로 입력해 주세요.';
  else if (draft.startDate < today) errors.startDate = '오늘보다 이전 날짜는 선택할 수 없어요.';
  if (!validDate(draft.endDate)) errors.endDate = '종료일을 YYYY-MM-DD 형식으로 입력해 주세요.';
  if (!errors.startDate && !errors.endDate) {
    const nights = dateDays(draft.startDate, draft.endDate);
    if (nights < 0) errors.endDate = '종료일은 시작일보다 빠를 수 없어요.';
    else if (nights > 7) errors.endDate = '여행은 최대 7박까지 설정할 수 있어요.';
  }
  if (!Number.isInteger(draft.adults) || draft.adults < 0) errors.adults = '성인 인원은 0명 이상이어야 해요.';
  if (!Number.isInteger(draft.children) || draft.children < 0) errors.children = '아동 인원은 0명 이상이어야 해요.';
  if (draft.adults + draft.children < 1) errors.travelers = '성인과 아동을 합해 최소 1명이 필요해요.';
  if (!draft.origin.trim()) errors.origin = '출발지를 입력해 주세요.';
  else if (draft.originLat === null || draft.originLng === null) errors.origin = '목록에서 출발지를 선택해 좌표를 확인해 주세요.';
  if (draft.budgetKrw === null || !Number.isInteger(draft.budgetKrw) || draft.budgetKrw < 10000) errors.budgetKrw = '총예산은 최소 10,000원이어야 해요.';
  else if (draft.budgetKrw % 10000 !== 0) errors.budgetKrw = '총예산은 10,000원 단위로 입력해 주세요.';
  const start = timeToMinutes(draft.dayStartTime); const end = timeToMinutes(draft.dayEndTime);
  if (Number.isNaN(start)) errors.dayStartTime = '시작 시각을 HH:MM 형식으로 입력해 주세요.';
  if (Number.isNaN(end)) errors.dayEndTime = '종료 시각을 HH:MM 형식으로 입력해 주세요.';
  if (!errors.dayStartTime && !errors.dayEndTime && end <= start) errors.dayEndTime = '종료 시각은 시작 시각보다 늦어야 해요.';
  if (draft.transport !== 'CAR' && draft.transport !== 'TRANSIT' && draft.transport !== 'WALK') errors.transport = '이동수단을 선택해 주세요.';
  return errors;
}

export function toTripBasicsPayload(draft: PlanDraft) {
  const travelModes = draft.transport === 'TRANSIT' ? ['BUS', 'SUBWAY'] : draft.transport === 'CAR' ? ['PRIVATE_CAR'] : ['WALK'];
  return { startDate: draft.startDate, finishDate: draft.endDate, budgetKrw: draft.budgetKrw, partySize: draft.adults + draft.children, timeWindow: `${draft.dayStartTime}-${draft.dayEndTime}`, timezone: 'Asia/Seoul', travelModes };
}
