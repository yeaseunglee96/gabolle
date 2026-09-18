// 계정에 기억되는 취향 다섯 — 온보딩 ③ 과 마이페이지 「여행 취향」이 함께 쓴다 (S15P21E201-960).
//
// 🔴 값의 모양을 이 파일 한 곳에만 둔다. 화면 둘이 각자 만들면 한쪽만 고쳐지고, 그 종류의
// 어긋남은 오류를 내지 않는다 — 사용자는 분명히 골랐는데 추천에 반영되지 않고 로그에도
// 아무것도 안 남는다. 이 저장소가 실제로 겪은 일이다(S15P21E201-635·-915).
//
// 🔴 값은 여행 만들기 취향 화면(app/(plan)/taste.tsx → src/api/tripApi.ts)이 보내는 것과
// 글자 그대로 같아야 한다. 같은 답이 두 경로로 들어와 한 칸에 쌓이기 때문이다.
//
//   눈금 셋   맨 정수 1~5        예: 3
//   음식      맨 배열            예: ["MILMYEON","SEAFOOD"]
//   경사      맨 낱말            예: "AVOID"
//
// 서버가 그 모양을 잘못 짐작해 취향이 통째로 0점이 됐던 적이 있다 — 까닭과, 눈금을 0~1 로
// 맞춘 이유는 백엔드 PreferenceJson 클래스 주석에 있다.
import { apiRequest } from '@/api/client';
import { FOODS } from '@/plan/foodConflicts';

export type TasteKey = 'locality' | 'quiet' | 'tourist' | 'foods' | 'slope';

export type SlopeAnswer = 'AVOID' | 'ALLOW';

/** 답한 것만 담는다. 🔴 키가 없는 것과 값이 비어 있는 것은 다른 뜻이다 — 앞엣것만 "답 안 함" 이다. */
export type TasteAnswers = {
  locality?: number;
  quiet?: number;
  tourist?: number;
  foods?: string[];
  slope?: SlopeAnswer;
};

export type TasteValue = number | string[] | SlopeAnswer;

/**
 * 화면의 이름 → 서버가 쓰는 어휘.
 *
 * 🔴 대문자 어휘로 보낸다. 서버는 앱의 camelCase(`locality`)도 받아 주지만 **돌려줄 때는
 * 언제나 대문자**라, camelCase 로 보내면 보낼 때와 받을 때 표가 두 벌 필요해진다.
 */
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
//
// 문장은 「보통 어투」다 — 계정에 기억되는 답이라 "이번 여행" 이 아니라 평소를 묻는다.
// 세 질문(spendProfile.ts)의 SPEND_HEADER.USER 와 같은 결이다.

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

/**
 * 지금 답이 무엇인지 한 줄로. 답이 없으면 `null`.
 *
 * 눈금은 숫자만 보여 주면 4 가 어느 쪽인지 알 수 없어서 끝 라벨을 붙인다 — 「4 / 5 · 현지인 공간」.
 */
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
 * 서버가 준 값 하나를 화면이 쓰는 값으로. 🔴 모르는 모양이면 **그 칸만 버린다** — 화면을
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

/**
 * `GET /api/v1/me/preferences/taste`
 *
 * 🔴 한 번도 저장한 적 없으면 404 가 아니라 **빈 목록으로 200** 이 온다 — "아직 취향이 없다"
 * 는 오류가 아니라 정상 상태이고, 404 로 답하면 첫 실행인 사람에게 빨간 화면이 뜬다.
 * 세 질문 경로가 같은 까닭으로 `status:UNKNOWN` 을 낸다.
 */
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

/** 바꿀 것만 담는다. `null` 은 **지우기**, 키를 안 넣으면 **그대로 둔다**. */
export type TasteChanges = Partial<Record<TasteKey, TasteValue | null>>;

/**
 * `PUT /api/v1/me/preferences/taste`
 *
 * 🔴 **보낸 차원만 바뀐다.** 안 보낸 차원은 서버에 그대로 남는다. 그래서 마이페이지에서 한
 * 줄만 고칠 때 나머지 넷을 같이 보낼 필요가 없다.
 *
 * 🔴 지우기는 `null` 로 **명시해서** 보낸다(서버에는 `UNKNOWN` 으로 간다). "지웠다" 와 "안
 * 보냈다" 를 구분할 방법이 이것뿐이고, 그 둘은 사용자에게 정반대다.
 */
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
