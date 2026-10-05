// 여행 돈 화면의 실패 문구가 다른 언어에서 뭉뚱그린 문장으로 떨어지던 것 — S15P21E201-1968.
//
// 🔴 src/trip/expenses.ts 는 화면 밖 모듈이라 실패 이유를 한국어 문장으로 올리고, 화면은
//    localizeMessage 로 그린다. 그 문장이 MESSAGE_EN 에 없으면 ko 밖 화면에서는
//    「요청을 처리하지 못했어요」 한 줄로 바뀌어, 무엇이 안 됐는지 알 수 없었다.
import type { LanguageCode } from '@/i18n/languages';
import { localizeMessage } from '@/i18n/messages';
import { pickLanguage } from '@/i18n/pick';

const txFor = (language: LanguageCode) => (ko: string, en: string) => pickLanguage(language, { ko, en });

const MONEY_FAILURES = ['여행 경비를 불러오지 못했어요.', '지출을 추가하지 못했어요.', '지우지 못했어요.', '예산을 저장하지 못했어요.'];
const GENERIC = ['We could not complete that request.', '요청을 처리하지 못했어요.'];

describe('여행 돈 실패 문구', () => {
  it.each(MONEY_FAILURES)('🔴 「%s」 — 다섯 언어 모두 제 뜻으로 나온다', (message) => {
    const seen = new Set<string>();
    for (const language of ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as LanguageCode[]) {
      const shown = localizeMessage(txFor(language), message);
      expect(GENERIC).not.toContain(shown);
      if (language !== 'ko') expect(shown).not.toMatch(/[가-힣]/);
      seen.add(shown);
    }
    expect(seen.size).toBe(5);
  });

  it('한국어 화면은 원문 그대로', () => {
    for (const message of MONEY_FAILURES) expect(localizeMessage(txFor('ko'), message)).toBe(message);
  });
});
