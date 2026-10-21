// 부산 사투리 카드 — 한글 아래 읽는 법(5개 언어 점검 2026-09-29).
// 🔴 카드 앞면이 한글뿐이라 한글을 못 읽는 사람은 무엇을 누르는지 몰랐다. 말하기 화면처럼 일본어는 가타카나, 그 밖은 로마자.
import { render, screen } from '@testing-library/react-native';

let mockLanguage = 'ja';
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (_ko: string, en: string) => en, language: mockLanguage }) }));
jest.mock('@/field/dialectVoice', () => ({
  loadDialectVoice: () => Promise.resolve('FEMALE'), saveDialectVoice: () => Promise.resolve(),
  hasDialectClip: () => false, playDialectClip: () => Promise.resolve(false), stopDialectClip: () => undefined,
}));
jest.mock('@/field/speakAloud', () => ({ speakAloud: jest.fn(), stopSpeaking: jest.fn() }));

import { DIALECT_PHRASES } from '../dialectPhrases';
import { DialectFlashcards } from '../DialectFlashcards';

describe('사투리 카드 읽는 법', () => {
  it('🔴 모든 표현에 로마자·가타카나가 있다', () => {
    for (const phrase of DIALECT_PHRASES) {
      expect(phrase.pronunciation).toMatch(/^[a-z ]+$/);
      expect(phrase.pronunciationJa).toMatch(/^[゠-ヿ ]+$/);
    }
  });

  it('일본어는 가타카나, 영어는 로마자를 한글 아래에', () => {
    mockLanguage = 'ja';
    const ja = render(<DialectFlashcards />);
    expect(screen.getByText('ムォラカノ')).toBeTruthy();
    ja.unmount();
    mockLanguage = 'en';
    render(<DialectFlashcards />);
    expect(screen.getByText('mworakano')).toBeTruthy();
  });

  it('한국어 사용자에게는 적지 않는다', () => {
    mockLanguage = 'ko';
    render(<DialectFlashcards />);
    expect(screen.queryByText('mworakano')).toBeNull();
  });
});
