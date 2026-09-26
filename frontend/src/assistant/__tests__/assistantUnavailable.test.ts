// 서버 AI 에 못 닿았을 때 무엇을 보이나 (S15P21E201-1749).
//
// 🔴 실기기에서 「자갈치 해산물 추천」에 「…를 도와드릴 수 있어요」만 돌아왔다.
//    서버는 30초를 붙잡다 실패했고, 앱은 로컬 해석기의 기본 안내를 답인 척 보였다.
import { assistantUnavailable, understandAssistantMessage } from '@/assistant/intent';

describe('서버 AI 에 못 닿았을 때', () => {
  it('🔴 못 알아들은 질문에는 기본 안내가 아니라 실패를 알린다', () => {
    const help = understandAssistantMessage('Recommend a seafood place near Jagalchi');
    expect(help.kind).toBe('help');
    const action = assistantUnavailable('Recommend a seafood place near Jagalchi');
    expect(action.kind).toBe('help');
    expect(action.reply).not.toBe(help.reply);
    expect(action.reply).toMatch(/try again|다시/);
  });

  it('알아들은 질문(현장 문장)은 로컬 답을 그대로 쓴다 — 오프라인에서도 쓸모 있다', () => {
    const action = assistantUnavailable('얼마예요 한국어로 뭐라고 해');
    expect(action).toEqual(understandAssistantMessage('얼마예요 한국어로 뭐라고 해'));
    expect(action.kind).toBe('phrase');
  });
});
