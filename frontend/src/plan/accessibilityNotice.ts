// 휠체어 안내 창에 적을 숫자 — 승차권에 보이는 일정만 센다(S15P21E201-1732).
//
// 🔴 전에는 추천 결과 목록으로 셌다. 그 목록은 코스 A·B·C 세 안의 장소를 합친 것이라, 승차권의 방문지는 8곳인데
//    창은 「24곳 중 23곳」이라고 했다. 서버는 일정 응답의 항목마다 warningCodes 를 준다 — 그것으로 센다.
import { txf } from '@/i18n/format';
import type { ItineraryDto } from './itinerary';

export const ACCESSIBILITY_UNVERIFIED = 'ACCESSIBILITY_UNVERIFIED';

/**
 * 보이는 일정에서 휠체어로 들어갈 수 있는지 확인 안 된 곳 수와 전체 수.
 * 항목 경고 칸이 하나도 없으면(옛 서버) null — 모른다. 그때 0곳이라고 말하면 「확인됐다」로 읽힌다.
 */
export function itineraryAccessibilityCounts(itinerary: { days: Array<{ items: Array<Pick<ItineraryDto['days'][number]['items'][number], 'warningCodes'>> }> }): { unverified: number; total: number } | null {
  const items = itinerary.days.flatMap((day) => day.items);
  if (!items.some((item) => Array.isArray(item.warningCodes))) return null;
  return { unverified: items.filter((item) => item.warningCodes?.includes(ACCESSIBILITY_UNVERIFIED)).length, total: items.length };
}

// ── 창의 첫 문장 — 고른 이동 보조에 맞춘다(S15P21E201-1814) ──────────────────────────────
//
// 🔴 서버는 휠체어·유아차·큰 짐을 가리지 않고 ACCESSIBILITY_UNVERIFIED 코드 «하나»만 보낸다
//    (백엔드 BaselineCandidateScorer.evaluateMobility). 그런데 이 창은 늘 「휠체어로 들어갈 수 있는지」라고 적어서,
//    유아차만·큰 짐만 고른 사람에게도 휠체어 문장이 떴다(QA, 진미리). 무엇을 골랐는지는 이 앱이 안다 — 여기서 고른다.
//    하나만 골랐으면 그것의 말로, 둘 이상이거나 모르면(조건을 이미 지운 뒤) 휠체어를 지어내지 않고 「고르신 이동 조건」으로.

// 🔴 큰 짐(HEAVY_LUGGAGE)은 이 창의 갈래가 아니다. 앱은 큰 짐을 묻고 보내지만, 서버는 큰 짐을 경사로만 가르고
//    「확인 안 됨」(ACCESSIBILITY_UNVERIFIED)을 붙이지 않는다 — 큰 짐을 가리키는 접근성 표식이 원천 자료에 없어서,
//    붙이면 거의 모든 곳이 「확인 안 됨」이 되었다. 그래서 이 창은 휠체어·유아차만 센다.
export type MobilityAid = 'WHEELCHAIR' | 'STROLLER';

/** 이번 여행에서 «예»라고 고른 이동 보조. 안 고름·모름(null)은 넣지 않는다. */
export function selectedMobilityAids(draft: { wheelchair: boolean | null; stroller: boolean | null }): MobilityAid[] {
  const aids: MobilityAid[] = [];
  if (draft.wheelchair === true) aids.push('WHEELCHAIR');
  if (draft.stroller === true) aids.push('STROLLER');
  return aids;
}

type Translate = (ko: string, en: string) => string;
type Template = { withTotal: [string, string]; withoutTotal: [string, string] };

// 영어 withTotal 은 값 순서를 한국어(전체 · 몇)에 맞춰 적었다 — txf 는 앞에서부터 차례로 끼운다.
const HEADLINE: Record<MobilityAid | 'ANY', Template> = {
  WHEELCHAIR: {
    withTotal: ['이번 일정 %s곳 중 %s곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.', 'Of the %s places in this trip, %s have not been checked for wheelchair access yet.'],
    withoutTotal: ['이번 일정의 %s곳은 휠체어로 들어갈 수 있는지 아직 확인되지 않았어요.', '%s places in this trip have not been checked for wheelchair access yet.'],
  },
  STROLLER: {
    withTotal: ['이번 일정 %s곳 중 %s곳은 유아차로 다니기 편한지 아직 확인되지 않았어요.', 'Of the %s places in this trip, %s have not been checked for stroller access yet.'],
    withoutTotal: ['이번 일정의 %s곳은 유아차로 다니기 편한지 아직 확인되지 않았어요.', '%s places in this trip have not been checked for stroller access yet.'],
  },
  ANY: {
    withTotal: ['이번 일정 %s곳 중 %s곳은 고르신 이동 조건으로 다니기 편한지 아직 확인되지 않았어요.', 'Of the %s places in this trip, %s have not been checked for the mobility needs you chose yet.'],
    withoutTotal: ['이번 일정의 %s곳은 고르신 이동 조건으로 다니기 편한지 아직 확인되지 않았어요.', '%s places in this trip have not been checked for the mobility needs you chose yet.'],
  },
};

/** 창의 첫 문장. totalCount 가 0이면(일정을 못 읽었다) 분모를 지어내지 않는다. */
export function accessibilityHeadline(tx: Translate, aids: readonly MobilityAid[], unverifiedCount: number, totalCount: number): string {
  const template = HEADLINE[aids.length === 1 ? aids[0] : 'ANY'];
  return totalCount > 0
    ? txf(tx, template.withTotal[0], template.withTotal[1], totalCount, unverifiedCount)
    : txf(tx, template.withoutTotal[0], template.withoutTotal[1], unverifiedCount);
}
