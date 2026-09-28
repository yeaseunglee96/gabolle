// 조건 한 페이지의 질문 순서와 「답한 것으로 보는 조건」.
// 시안: docs/design_handoff_plan_flow/PlanFlow.dc.html 의 p1.

import type { PlanDraft } from '@/plan/PlanProvider';
import { timeToMinutes } from '@/plan/tripBasics';

export type QuestionKey =
  | 'areas' | 'budget' | 'move' | 'cats' | 'pace'
  | 'aids' | 'must';

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

/**
 * 하루 시간대가 쓸 수 있는 값인가. 문제가 없으면 null.
 *
 * 🔴 **화면과 통과 조건이 이 함수 하나를 같이 쓴다.** 「넘어가도 되나」와 「무엇이
 *    틀렸다고 적나」가 서로 다른 판정을 쓰면, 넘어가지는 않는데 이유는 안 뜨는 화면이 된다.
 */
export function dayWindowIssue(draft: Pick<PlanDraft, 'dayStartTime' | 'dayEndTime'>): 'FORMAT' | 'ORDER' | null {
  const start = timeToMinutes(draft.dayStartTime);
  const end = timeToMinutes(draft.dayEndTime);
  if (Number.isNaN(start) || Number.isNaN(end)) return 'FORMAT';
  // 끝이 시작보다 이르거나 같으면 하루가 안 된다. 서버는 이것을 안 막는다(1453).
  if (end <= start) return 'ORDER';
  return null;
}

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
    // 숙박비는 뺀 값이다 — 서버의 예산 상한도 메뉴 값(식비·카페·입장료)만 센다(S15P21E201-1591).
    hintKo: '한 사람이 아니라 이번 여행 전체 예산이에요. 숙박비는 빼고 식비·카페·입장료 기준이에요.',
    hintEn: 'For the whole trip, not per person — food, cafes, and admissions only, not lodging.',
    skippable: false,
    answered: (draft) => typeof draft.budgetKrw === 'number' && draft.budgetKrw > 0,
  },
  {
    key: 'move', ko: '하루 여행 시간 · 이동수단', en: 'Daily hours and transport',
    hintKo: '몇 시부터 몇 시까지 다닐지, 무엇으로 이동할지 알려 주세요.',
    hintEn: 'When you want to be out, and how you will get around.',
    skippable: false,
    // 🔴 여기가 `Boolean(draft.transport)` 뿐이었다 — **이동수단만 보고 시각은 안 봤다.**
    //    그래서 「0800」처럼 못 읽는 값을 넣고도 다음으로 넘어갔고, 서버는 그것을 시간
    //    범위가 아니라 프리셋 이름으로 오해해 **조용히 버렸다**(S15P21E201-1452·1453).
    answered: (draft) => Boolean(draft.transport) && dayWindowIssue(draft) === null,
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

// 🔴 «장»(page) 묶음은 걷어냈다 — S15P21E201-1425. 시안이 한 화면에 질문 하나씩
//    보이는 스테퍼로 돌아갔다(필수 3 + 선택 4). 예전 3-장 모델(-1377)은 문항을 열 개까지
//    한 장에 모으려던 것인데, 문항이 일곱으로 줄면서 장으로 묶을 이유가 사라졌다.
//    진행은 위 settledCount / nextOpenIndex 가 질문 단위로 그대로 잰다.
