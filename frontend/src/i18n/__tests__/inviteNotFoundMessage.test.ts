// 영어 화면에서 없는 초대 링크를 열면 서버 원문 「초대를 찾을 수 없습니다.」가 그대로 나왔다 — S15P21E201-1774.
// 🔴 운영 웹(2026-09-27) /invite/{토큰}·/story-invite/{토큰}. 화면은 localizeMessage 를 거치는데 표에 이 문장이 없었다.
import { localizeMessage } from '@/i18n/messages';
import { pickLanguage } from '@/i18n/pick';

const en = (ko: string, english: string) => pickLanguage('en', { ko, en: english });
const ko = (k: string) => k;

describe('초대를 찾지 못했을 때의 서버 문장', () => {
  it('🔴 영어 화면에서 한국어로 남지 않는다', () => {
    expect(localizeMessage(en, '초대를 찾을 수 없습니다.')).toBe('We could not find this invite.');
  });
  it('한국어 화면은 원문 그대로', () => {
    expect(localizeMessage(ko, '초대를 찾을 수 없습니다.')).toBe('초대를 찾을 수 없습니다.');
  });
});

it('🔴 일본어 화면도 번역표에서 찾는다', () => {
  const ja = (k: string, english: string) => pickLanguage('ja', { ko: k, en: english });
  expect(localizeMessage(ja, '초대를 찾을 수 없습니다.')).toBe('招待が見つかりません。');
});
