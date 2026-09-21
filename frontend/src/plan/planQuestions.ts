// 조건 한 페이지의 질문 순서와 「답한 것으로 보는 조건」.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p1.

import type { PlanDraft } from '@/plan/PlanProvider';

export type QuestionKey =
  | 'areas' | 'budget' | 'move' | 'cats' | 'pace'
  | 'moods' | 'scales' | 'foods' | 'aids' | 'must';

export type PlanQuestion = {
  key: QuestionKey;
  ko: string;
  en: string;
  hintKo: string;
  hintEn: string;
  /** 건너뛸 수 있나. 앞의 셋은 일정을 만드는 데 꼭 필요해서 못 건너뛴다. */
  skippable: boolean;
  /** 이 질문에 답한 것으로 볼 조건. */
  answered: (draft: PlanDraft) => boolean;
};

export const PLAN_QUESTIONS: PlanQuestion[] = [
  {
    key: 'areas', ko: '여행 범위', en: 'Where in Busan',
    hintKo: '가고 싶은 지역을 골라 주세요. 여러 곳도 괜찮아요.',
    hintEn: 'Pick the areas you want to visit. More than one is fine.',
    skippable: false,
    answered: (draft) => draft.travelAreas.length > 0,
  },
  {
    key: 'budget', ko: '총예산', en: 'Total budget',
    hintKo: '한 사람이 아니라 이번 여행 전체 예산이에요.',
    hintEn: 'For the whole trip, not per person.',
    skippable: false,
    answered: (draft) => typeof draft.budgetKrw === 'number' && draft.budgetKrw > 0,
  },
  {
    key: 'move', ko: '하루 여행 시간 · 이동수단', en: 'Daily hours and transport',
    hintKo: '몇 시부터 몇 시까지 다닐지, 무엇으로 이동할지 알려 주세요.',
    hintEn: 'When you want to be out, and how you will get around.',
    skippable: false,
    answered: (draft) => Boolean(draft.transport),
  },
  {
    key: 'cats', ko: '여행 카테고리', en: 'Trip categories',
    hintKo: '최대 세 가지까지 고를 수 있어요.',
    hintEn: 'Up to three.',
    skippable: true,
    answered: (draft) => draft.preferences.length > 0 && draft.preferences.length <= 3,
  },
  {
    key: 'pace', ko: '여행 기분', en: 'Trip pace',
    hintKo: '하루에 몇 곳을 도는 게 좋은지로 정해요.',
    hintEn: 'How many places a day feels right.',
    skippable: true,
    answered: (draft) => Boolean(draft.paceLevel),
  },
  {
    key: 'moods', ko: '좋아하는 분위기', en: 'Preferred mood',
    hintKo: '여러 개 골라도 괜찮아요.',
    hintEn: 'Pick as many as you like.',
    skippable: true,
    answered: (draft) => draft.atmospheres.length > 0,
  },
  {
    key: 'scales', ko: '로컬성 · 조용함 · 관광지', en: 'Local, quiet, touristy',
    hintKo: '셋 중 하나만 답해도 돼요.',
    hintEn: 'Answering just one is fine.',
    skippable: true,
    answered: (draft) => draft.localityLevel !== null || draft.quietLevel !== null || draft.touristLevel !== null,
  },
  {
    key: 'foods', ko: '음식 취향', en: 'Food preferences',
    hintKo: '못 먹는 것은 앞에서 받은 조건으로 이미 걸러져요.',
    hintEn: 'Anything you cannot eat is already filtered out.',
    skippable: true,
    answered: (draft) => draft.foods.length > 0,
  },
  {
    key: 'aids', ko: '이번 여행 이동 보조 · 짐', en: 'Mobility aids and luggage',
    hintKo: '여행마다 달라서 계정이 아니라 이 여행에만 저장해요.',
    hintEn: 'Saved for this trip only — it changes trip to trip.',
    skippable: true,
    answered: (draft) => draft.wheelchair !== null || draft.stroller !== null || draft.luggage !== null,
  },
  {
    key: 'must', ko: '꼭 가고 싶은 장소', en: 'Must-visit places',
    hintKo: '없으면 건너뛰어도 돼요.',
    hintEn: 'Skip if there is none.',
    skippable: true,
    // 이 질문은 「없음」도 답이다. 그래서 언제나 답한 것으로 본다
    // 빈 채로 「다음」을 눌러야만 넘어갈 수 있으면 아무도 못 끝낸다.
    answered: () => true,
  },
];

export type QuestionState = {
  /** 지금 열려 있는 질문의 자리(0부터). */
  open: number;
  /** 사람이 「건너뛰기」를 누른 질문들. */
  skipped: Partial<Record<QuestionKey, boolean>>;
  /** 「수정」으로 다시 펼친 질문. 없으면 null. */
  editing: QuestionKey | null;
};

export const INITIAL_QUESTION_STATE: QuestionState = { open: 0, skipped: {}, editing: null };

/** 이 질문이 지나간 것인가 — 답했거나 건너뛰었으면 지나간 것이다. */
export function isSettled(question: PlanQuestion, draft: PlanDraft, state: QuestionState): boolean {
  return Boolean(state.skipped[question.key]) || question.answered(draft);
}

/** 「다음」을 누를 수 있나. */
export function canAdvance(question: PlanQuestion, draft: PlanDraft): boolean {
  return question.answered(draft);
}

/** 지나간 질문 수 — 진행 막대가 쓴다. */
export function settledCount(_draft: PlanDraft, state: QuestionState): number {
  return Math.min(state.open, PLAN_QUESTIONS.length);
}

/** 남은 질문 수. */
export function remainingCount(draft: PlanDraft, state: QuestionState): number {
  return PLAN_QUESTIONS.length - settledCount(draft, state);
}

/** 전부 지나갔나 — 그때만 마지막 「이 조건으로 일정 만들기」가 나온다. */
export function allSettled(draft: PlanDraft, state: QuestionState): boolean {
  // 끝까지 가 본 사람에게만 마지막 카드를 보인다. 기본값만으로 「다 됐어요」가
  // 뜨면, 사람은 답하지도 않은 조건으로 일정이 만들어지는 줄 모른다.
  if (state.open < PLAN_QUESTIONS.length) return false;
  return PLAN_QUESTIONS.every((question) =>
    question.skippable ? isSettled(question, draft, state) : question.answered(draft));
}

/** 다음에 열 질문의 자리. 더 없으면 목록 길이를 준다(= 전부 끝). */
export function nextOpenIndex(_draft: PlanDraft, state: QuestionState): number {
  return Math.min(state.open + 1, PLAN_QUESTIONS.length);
}
