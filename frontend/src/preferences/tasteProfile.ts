// 계정에 기억되는 취향 다섯 — 온보딩 ③ 과 마이페이지 「여행 취향」이 함께 쓴다.
import { apiRequest } from '@/api/client';
import { FOODS } from '@/plan/foodConflicts';

export type TasteKey = 'locality' | 'quiet' | 'tourist' | 'foods' | 'slope';

export type SlopeAnswer = 'AVOID' | 'ALLOW';

/** 답한 것만 담는다. 키가 없는 것과 값이 비어 있는 것은 다른 뜻이다 — 앞엣것만 "답 안 함" 이다. */
export type TasteAnswers = {
  locality?: number;
  quiet?: number;
  tourist?: number;
  foods?: string[];
  slope?: SlopeAnswer;
};

export type TasteValue = number | string[] | SlopeAnswer;

/** 화면의 이름 → 서버가 쓰는 어휘. */
const DIMENSION: Record<TasteKey, string> = {
  locality: 'LOCALITY',
  quiet: 'QUIETNESS',
  tourist: 'TOURIST_PREFERENCE',
  foods: 'FOOD_PREFERENCE',
  slope: 'SLOPE_PREFERENCE',
};

const KEY_BY_DIMENSION: Record<string, TasteKey | undefined> = Object.fromEntries(
  Object.entries(DIMENSION).map(([key, dimension]) => [dimension, key as TasteKey]),
);

/** 화면에 나오는 순서. 온보딩 단계 번호가 이 순서다. */
export const TASTE_KEYS: TasteKey[] = ['locality', 'quiet', 'tourist', 'foods', 'slope'];

// ── 문항 ────────────────────────────────────────────────────────────────────

export type Bilingual = { ko: string; en: string };

export type TasteQuestion =
  | { key: 'locality' | 'quiet' | 'tourist'; kind: 'scale'; title: Bilingual; low: Bilingual; high: Bilingual; skip: Bilingual }
  | { key: 'foods'; kind: 'multi'; title: Bilingual; skip: Bilingual }
  | { key: 'slope'; kind: 'choice'; title: Bilingual; skip: Bilingual; options: { value: SlopeAnswer; label: Bilingual; desc?: Bilingual }[] };

const NOT_SURE: Bilingual = { ko: '잘 모르겠어요', en: 'Not sure' };

export const TASTE_QUESTIONS: TasteQuestion[] = [
  {
    key: 'locality', kind: 'scale', skip: NOT_SURE,
    title: { ko: '보통 대표 명소와 현지인 공간 중 어느 쪽에 더 끌리세요?', en: 'Do you usually lean toward landmarks or places locals go?' },
    low: { ko: '대표 명소', en: 'Landmarks' },
    high: { ko: '현지인 공간', en: 'Where locals go' },
  },
  {
    key: 'quiet', kind: 'scale', skip: NOT_SURE,
    title: { ko: '보통 조용한 곳을 얼마나 찾으세요?', en: 'How much do you usually seek out quiet places?' },
    low: { ko: '상관없음', en: 'No preference' },
    high: { ko: '매우 선호', en: 'Strongly prefer' },
  },
  {
    key: 'tourist', kind: 'scale', skip: NOT_SURE,
    title: { ko: '보통 숨은 곳과 대표 관광지 중 어느 쪽이세요?', en: 'Hidden gems or famous spots — which is usually you?' },
    low: { ko: '숨은 곳', en: 'Hidden gems' },
    high: { ko: '대표 관광지', en: 'Famous spots' },
  },
  {
    key: 'foods', kind: 'multi',
    title: { ko: '부산에서 보통 어떤 음식을 찾으세요? 여러 개 골라도 돼요.', en: 'What do you usually look for in Busan? Pick as many as you like.' },
    skip: { ko: '지금은 안 고를게요', en: 'Skip for now' },
  },
  {
    key: 'slope', kind: 'choice', skip: NOT_SURE,
    title: { ko: '가파른 경사는 보통 피하고 싶으세요?', en: 'Do you usually want to avoid steep slopes?' },
    options: [
      {
        value: 'AVOID',
        label: { ko: '예, 경사는 피하고 싶어요', en: 'Yes, keep slopes out' },
        desc: { ko: '일정과 동선에서 가파른 길을 빼요', en: 'We leave steep paths out of your route' },
      },
      { value: 'ALLOW', label: { ko: '아니요, 상관없어요', en: 'No, it does not matter' } },
    ],
  },
];

/** 마이페이지 목록의 짧은 이름. 문항 제목은 길어서 한 줄에 안 맞는다. */
export const TASTE_LABELS: Record<TasteKey, Bilingual> = {
  locality: { ko: '로컬성', en: 'Local feel' },
  quiet: { ko: '조용한 곳 선호', en: 'Quiet places' },
  tourist: { ko: '관광지 선호', en: 'Tourist spots' },
  foods: { ko: '음식 취향', en: 'Food' },
  slope: { ko: '가파른 경사 피하기', en: 'Avoiding slopes' },
};

/** 지금 답이 무엇인지 한 줄로. 답이 없으면 `null`. */
export function describeTasteAnswer(key: TasteKey, answers: TasteAnswers): Bilingual | null {
  const question = TASTE_QUESTIONS.find((item) => item.key === key);
  if (!question) return null;

  if (question.kind === 'scale') {
    const value = answers[question.key];
    if (value === undefined) return null;
    const side = value <= 2 ? question.low : value >= 4 ? question.high : { ko: '중간', en: 'In between' };
    return { ko: `${value} / 5 · ${side.ko}`, en: `${value} / 5 · ${side.en}` };
  }

  if (question.kind === 'multi') {
    const codes = answers.foods;
    if (!codes || codes.length === 0) return null;
    const picked = codes.map((code) => FOODS.find((food) => food[0] === code)).filter((food) => food !== undefined);
    if (picked.length === 0) return null;
    return { ko: picked.map((food) => food[1]).join(' · '), en: picked.map((food) => food[2]).join(' · ') };
  }

  const slope = answers.slope;
  if (slope === undefined) return null;
  return slope === 'AVOID'
    ? { ko: '예 · 경사를 피해요', en: 'Yes · slopes left out' }
    : { ko: '아니요 · 상관없어요', en: 'No · does not matter' };
}

export function countTasteAnswers(answers: TasteAnswers): number {
  return TASTE_KEYS.filter((key) => answers[key] !== undefined).length;
}

// ── 서버와 주고받기 ─────────────────────────────────────────────────────────

type TasteAnswerDto = { dimension: string; status: string; value: string | null };

const FOOD_CODES = new Set<string>(FOODS.map((food) => food[0]));

/**
 * 서버가 준 값 하나를 화면이 쓰는 값으로. 모르는 모양이면 그 칸만 버린다 — 화면을
 * 죽이지 않는다. 한 칸이 이상하다고 나머지 넷까지 못 보여 주면 고칠 방법이 사라진다.
 */
function parseValue(key: TasteKey, raw: string): TasteValue | undefined {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (key === 'foods') {
    if (!Array.isArray(parsed)) return undefined;
    const codes = parsed.filter((code): code is string => typeof code === 'string' && FOOD_CODES.has(code));
    return codes.length > 0 ? codes : undefined;
  }
  if (key === 'slope') {
    return parsed === 'AVOID' || parsed === 'ALLOW' ? parsed : undefined;
  }
  return typeof parsed === 'number' && Number.isInteger(parsed) && parsed >= 1 && parsed <= 5 ? parsed : undefined;
}

/** `GET /api/v1/me/preferences/taste` */
export async function getTasteProfile(accessToken: string | null): Promise<TasteAnswers> {
  const dto = await apiRequest<{ answers: TasteAnswerDto[] }>('/api/v1/me/preferences/taste', { accessToken });
  const answers: TasteAnswers = {};
  for (const item of dto.answers ?? []) {
    if (item.status !== 'SELECTED' || item.value === null) continue;
    const key = KEY_BY_DIMENSION[item.dimension];
    if (!key) continue;
    const value = parseValue(key, item.value);
    if (value !== undefined) Object.assign(answers, { [key]: value });
  }
  return answers;
}

/** 바꿀 것만 담는다. `null` 은 지우기, 키를 안 넣으면 그대로 둔다. */
export type TasteChanges = Partial<Record<TasteKey, TasteValue | null>>;

/** `PUT /api/v1/me/preferences/taste` */
export function putTasteProfile(changes: TasteChanges, accessToken: string | null) {
  const answers = Object.entries(changes).map(([key, value]) => ({
    dimension: DIMENSION[key as TasteKey],
    value: value === null || value === undefined ? null : JSON.stringify(value),
    answerStatus: value === null || value === undefined ? 'UNKNOWN' : 'SELECTED',
  }));
  return apiRequest<{ answers: TasteAnswerDto[] }>('/api/v1/me/preferences/taste', {
    method: 'PUT',
    accessToken,
    body: { answers },
  });
}
