// 추천이 실패했을 때 «어느 조건이» 후보를 다 걷어냈는지 사람 말로 옮긴다 — S15P21E201-1514.
//
// 서버는 실패 응답에 `failure.blockedBy[]` 를 싣는다(back/dev !1465, S15P21E201-1468 의 ㄷ).
// 막은 후보가 많은 것부터 온다. 앱이 이것을 안 읽어서, 서버는 「식단이 막았고 확인을
// 못 해서다」까지 아는데 화면은 「알레르기 · 식단 · 이동」 셋을 한꺼번에 나열했다 —
// 사용자는 셋 중 무엇을 고쳐야 하는지 여전히 몰랐다.
//
// 🔴 **「확인 못 함(UNVERIFIED)」과 「위반(VIOLATED)」을 같은 말로 쓰지 않는다.**
//    지금 나오는 것은 대부분 앞쪽이다 — 운영 자료에 식단·접근성 표식이 아직 드물어서,
//    서버는 «안 맞는 곳을 골라낸» 것이 아니라 «맞는지 알 수 없어» 뺀 것이다. 뒤쪽 말로
//    적으면 사용자는 「부산에 내가 먹을 것이 없다」로 읽는다.
//    그래서 모르는 reason 이 오면 **「확인 못 함」 쪽으로** 적는다. 「없다」고 잘못 말하는
//    쪽이 더 큰 거짓이다.
//
// 🔴 **사전에 없는 것은 지어내지 않는다** (warningLabels.ts 와 같은 선, S15P21E201-1171).
//    - 모르는 constraintType → 그 줄을 통째로 숨긴다
//    - 모르는 constraintKey → 원문 코드를 안 내보내고 갈래 이름(「식단」)으로 대신 적는다
//    - 적을 것이 하나도 없으면 null — 부르는 쪽이 예전 문구로 떨어진다
//
// 🔴 문장에 값을 끼워 넣지 않는다. 번역표는 한국어 원문 자체를 열쇠로 쓰므로 값이 끼면
//    일본어·중국어에서 영원히 못 찾는다(pick.ts 머리말). 그래서 문장은 갈래·이유마다 고정하고,
//    막은 값들은 따로 번역해 뒤에 붙인다.
import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage, type LocalizedText } from '@/i18n/pick';

/** 서버 `RecommendationJobResponse.BlockedBy` 그대로. 칸이 비어 올 수 있다고 본다. */
export type BlockedBy = {
  constraintType: string | null;
  constraintKey: string | null;
  reason: string | null;
  code: string | null;
  blockedCandidates: number;
};

type ConstraintType = 'ALLERGY' | 'DIET' | 'MOBILITY';

const TYPE_LABEL: Record<ConstraintType, LocalizedText> = {
  ALLERGY: { ko: '알레르기', en: 'Allergy' },
  DIET: { ko: '식단', en: 'Diet' },
  MOBILITY: { ko: '이동 조건', en: 'Getting around' },
};

/**
 * 앱이 실제로 보내는 값만 적는다 — 식단은 travelConditions 의 DIETS,
 * 이동은 tripApi 의 mobility(). 알레르기는 -1497 부터 안 보내므로 값 이름을 두지 않는다
 * (오더라도 갈래 이름 「알레르기」로 적힌다).
 */
const KEY_LABEL: Record<string, LocalizedText> = {
  VEGETARIAN: { ko: '채식', en: 'Vegetarian' },
  VEGAN: { ko: '비건', en: 'Vegan' },
  HALAL: { ko: '할랄', en: 'Halal' },
  GLUTEN_FREE: { ko: '글루텐 프리', en: 'Gluten-free' },
  PESCATARIAN: { ko: '페스코', en: 'Pescatarian' },
  WHEELCHAIR: { ko: '휠체어', en: 'Wheelchair' },
  STROLLER: { ko: '유아차', en: 'Stroller' },
  HEAVY_LUGGAGE: { ko: '큰 짐', en: 'Large luggage' },
  STAIRS_AVOIDANCE: { ko: '계단 피하기', en: 'Avoid stairs' },
  MAX_WALKING_METERS: { ko: '걷는 거리', en: 'Walking distance' },
};

/** 갈래 × 이유 → 첫 문장. 값을 끼우지 않은 고정 문장이라 번역표에서 찾힌다. */
const HEADLINE: Record<ConstraintType, { UNVERIFIED: LocalizedText; VIOLATED: LocalizedText }> = {
  ALLERGY: {
    UNVERIFIED: {
      ko: '알레르기 조건에 맞는지 확인된 곳이 없어 일정을 만들지 못했어요.',
      en: "No place could be confirmed to fit your allergy needs, so we couldn't build the itinerary.",
    },
    VIOLATED: {
      ko: '알레르기 조건을 지키는 곳을 찾지 못해 일정을 만들지 못했어요.',
      en: "We couldn't find places that meet your allergy needs, so we couldn't build the itinerary.",
    },
  },
  DIET: {
    UNVERIFIED: {
      ko: '식단 조건에 맞는지 확인된 곳이 없어 일정을 만들지 못했어요.',
      en: "No place could be confirmed to fit your diet, so we couldn't build the itinerary.",
    },
    VIOLATED: {
      ko: '식단 조건을 지키는 곳을 찾지 못해 일정을 만들지 못했어요.',
      en: "We couldn't find places that meet your diet, so we couldn't build the itinerary.",
    },
  },
  MOBILITY: {
    UNVERIFIED: {
      ko: '이동 조건에 맞는지 확인된 곳이 없어 일정을 만들지 못했어요.',
      en: "No place could be confirmed to fit your getting-around needs, so we couldn't build the itinerary.",
    },
    VIOLATED: {
      ko: '이동 조건을 지키는 곳을 찾지 못해 일정을 만들지 못했어요.',
      en: "We couldn't find places that meet your getting-around needs, so we couldn't build the itinerary.",
    },
  },
};

/**
 * 「확인 못 함」일 때만 첫 문장 아래 붙는 줄. 🔴 첫 문장에 이어 붙이지 않는다 — 폰 폭에서
 * 「…못했어요. 안」 / 「맞는다는 뜻이…」로 끊겨 「안」이 줄 끝에 홀로 남았다(2026-09-23 실측).
 */
const UNVERIFIED_NOTE: LocalizedText = {
  ko: '안 맞는다는 뜻이 아니라, 아직 확인한 자료가 없어요.',
  en: "That doesn't mean none fit — we just don't have the data yet.",
};

const BLOCKED_PREFIX: LocalizedText = { ko: '걸린 조건:', en: 'Blocked by:' };
// 🔴 「조건을 빼고 다시 만들어 주세요」 같은 할 일 줄은 두지 않는다. 화면이 바로 아래에
//    「조건을 조금 넓혀서 다시 해 볼까요?」와 「조건 다시 확인하기」 단추를 이미 그린다 —
//    같은 말을 세 번 하게 된다(2026-09-23 실측).

/** 막은 값을 몇 개까지 적나. 서버가 많이 막은 것부터 주므로 앞의 것이 중요한 것이다. */
const MAX_LABELS = 4;

function isConstraintType(value: string | null): value is ConstraintType {
  return value === 'ALLERGY' || value === 'DIET' || value === 'MOBILITY';
}

/**
 * `blockedBy` 를 화면 문구로. 적을 것이 없으면 null.
 *
 * 모양 —
 * <pre>
 *   식단 조건에 맞는지 확인된 곳이 없어 일정을 만들지 못했어요.
 *   안 맞는다는 뜻이 아니라, 아직 확인한 자료가 없어요.      ← 「확인 못 함」일 때만
 *   걸린 조건: 비건 · 할랄
 * </pre>
 */
export function describeBlockedBy(blockedBy: readonly BlockedBy[] | null | undefined): string | null {
  const known = (blockedBy ?? []).filter((item): item is BlockedBy & { constraintType: ConstraintType } =>
    Boolean(item) && isConstraintType(item.constraintType));
  if (known.length === 0) return null;

  const language = getCurrentLanguage();
  const say = (text: LocalizedText) => pickLanguage(language, text);

  // 첫 문장은 가장 많이 막은 조건(서버가 앞에 준 것)의 갈래와 이유로 정한다.
  const top = known[0];
  const reason = top.reason === 'VIOLATED' ? 'VIOLATED' : 'UNVERIFIED';
  const headline = say(HEADLINE[top.constraintType][reason]);

  // 막은 값 — 사전에 있으면 값 이름, 없으면 갈래 이름. 같은 말은 한 번만.
  const labels: string[] = [];
  for (const item of known) {
    const label = say((item.constraintKey && KEY_LABEL[item.constraintKey]) || TYPE_LABEL[item.constraintType]);
    if (!labels.includes(label)) labels.push(label);
    if (labels.length === MAX_LABELS) break;
  }

  const lines = [headline];
  if (reason === 'UNVERIFIED') lines.push(say(UNVERIFIED_NOTE));
  lines.push(`${say(BLOCKED_PREFIX)} ${labels.join(' · ')}`);
  return lines.join('\n');
}

/** 서버가 준 값을 모양만 확인해 옮긴다. 모르는 모양이면 그 줄을 버린다. */
export function readBlockedBy(raw: unknown): BlockedBy[] {
  if (!Array.isArray(raw)) return [];
  const text = (value: unknown) => (typeof value === 'string' && value ? value : null);
  return raw
    .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
    .map((item) => ({
      constraintType: text(item.constraintType),
      constraintKey: text(item.constraintKey),
      reason: text(item.reason),
      code: text(item.code),
      blockedCandidates: typeof item.blockedCandidates === 'number' ? item.blockedCandidates : 0,
    }));
}
