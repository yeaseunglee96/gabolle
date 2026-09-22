// 문항 화면의 자리(몇 번째 질문이 열려 있나 · 무엇을 건너뛰었나)를 기기에 남긴다 — S15P21E201-1376.
//
// 🔴 답(초안)은 PlanProvider 가 기기에 남기는데 «자리»는 useState 라 화면을 벗어나면 0 으로
//    돌아갔다. 날짜를 정하러 홈에 갔다 오거나, 마지막에 로그인하고 돌아오면 열 문항을 다 답한
//    사람이 1번을 다시 봤다. 답은 있는데 자리를 잃는 것은, 사람 눈에는 답을 잃은 것이다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { INITIAL_QUESTION_STATE, PLAN_QUESTIONS, type QuestionKey, type QuestionState } from './planQuestions';

const STORAGE_KEY = 'gabolle:plan-questions-state';
const KEYS = new Set<string>(PLAN_QUESTIONS.map((item) => item.key));

/** 저장된 자리를 읽는다. 없거나 깨졌으면 처음 자리. 문항 수를 벗어난 값은 잘라 낸다. */
export async function loadQuestionState(): Promise<QuestionState> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (!raw) return INITIAL_QUESTION_STATE;
    const parsed = JSON.parse(raw) as Partial<QuestionState>;
    const open = typeof parsed.open === 'number' && Number.isFinite(parsed.open) ? Math.max(0, Math.min(PLAN_QUESTIONS.length - 1, Math.floor(parsed.open))) : 0;
    const skipped: Partial<Record<QuestionKey, boolean>> = {};
    for (const [key, value] of Object.entries(parsed.skipped ?? {})) if (KEYS.has(key) && value === true) skipped[key as QuestionKey] = true;
    return { open, skipped, editing: null };
  } catch {
    return INITIAL_QUESTION_STATE;
  }
}

export async function saveQuestionState(state: QuestionState): Promise<void> {
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ open: state.open, skipped: state.skipped }));
  } catch {
    // 기기 저장이 안 되면 자리만 못 잇는다 — 답은 PlanProvider 가 따로 남긴다.
  }
}

/** 일정을 만들어 보낸 뒤 — 다음 여행은 1번부터. */
export async function clearQuestionState(): Promise<void> {
  try {
    await AsyncStorage.removeItem(STORAGE_KEY);
  } catch {
    // 위와 같다.
  }
}
