import { EMPTY_PLAN, type PlanDraft } from './PlanProvider';
import { localToday } from './tripBasics';

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

export type AssistantPrefillParams = { days?: string | string[]; people?: string | string[] };

/** expo-router 는 같은 이름이 두 번 오면 배열로 준다. 첫 값만 본다. */
function first(value: string | string[] | undefined): string | undefined {
  if (Array.isArray(value)) return value[0];
  return value;
}

function positiveInt(value: string | string[] | undefined): number | null {
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
 * **이미 정해진 것은 덮지 않는다.** 날짜는 비어 있을 때만, 인원은 아직 초깃값
 * (`EMPTY_PLAN` 의 성인 1·아동 0)일 때만 채운다. 돌아온 사람의 초안을 비서 한마디로
 * 갈아엎지 않기 위해서다.
 */
export function assistantPrefillPatch(
  params: AssistantPrefillParams,
  draft: PlanDraft,
  today: string = localToday(),
): Partial<PlanDraft> {
  const patch: Partial<PlanDraft> = {};

  const days = positiveInt(params.days);
  if (days !== null && days <= MAX_DAYS && !draft.startDate && !draft.endDate) {
    // 날짜를 말하지 않은 사람에게 오늘을 잡아 주면 이미 지난 시간이 섞인다. 내일부터 센다.
    const start = addDays(today, 1);
    patch.startDate = start;
    patch.endDate = addDays(start, days - 1);
  }

  const people = positiveInt(params.people);
  const partyUntouched = draft.adults === EMPTY_PLAN.adults && draft.children === EMPTY_PLAN.children;
  if (people !== null && people <= MAX_PEOPLE && partyUntouched) {
    // 인원을 나누는 방식은 assistant/intent.ts 가 이미 쓰던 것과 같게 둔다 — 전원 성인.
    patch.travelers = people;
    patch.adults = people;
    patch.children = 0;
  }

  return patch;
}
