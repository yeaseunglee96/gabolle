// 사투리 목소리 — 클립이 있는 문장은 미리 만든 소리를, 없는 문장은 기기 TTS 로. S15P21E201-1422.
import { DIALECT_PHRASES } from '@/discovery/dialectPhrases';

const mockPlay = jest.fn();
const mockRemove = jest.fn();
let mockListener: ((status: { didJustFinish: boolean }) => void) | null = null;
jest.mock('expo-audio', () => ({
  setAudioModeAsync: jest.fn(() => Promise.resolve()),
  createAudioPlayer: jest.fn(() => ({ play: mockPlay, pause: jest.fn(), remove: mockRemove, addListener: (_: string, fn: (s: { didJustFinish: boolean }) => void) => { mockListener = fn; } })),
}));
jest.mock('@react-native-async-storage/async-storage', () => { let v: string | null = null; return { getItem: jest.fn(async () => v), setItem: jest.fn(async (_: string, x: string) => { v = x; }) }; });

import { hasDialectClip, loadDialectVoice, playDialectClip, saveDialectVoice } from '@/field/dialectVoice';

describe('사투리 목소리', () => {
  it('🔴 카드의 모든 문장에 남·여 클립이 있다 — 문장을 늘리면 gen-dialect.cjs 로 소리도 같이 만든다', () => {
    for (const phrase of DIALECT_PHRASES) expect(hasDialectClip(phrase.id)).toBe(true);
  });

  it('없는 문장은 false — 부르는 쪽이 기기 TTS 로 내려간다', async () => {
    expect(hasDialectClip('no-such-phrase')).toBe(false);
    expect(await playDialectClip('no-such-phrase', 'FEMALE', () => {})).toBe(false);
  });

  it('클립이 있으면 틀고, 끝나면 onDone 을 한 번 부른다', async () => {
    const done = jest.fn();
    expect(await playDialectClip('maimura', 'MALE', done)).toBe(true);
    expect(mockPlay).toHaveBeenCalled();
    mockListener?.({ didJustFinish: false });
    expect(done).not.toHaveBeenCalled();
    mockListener?.({ didJustFinish: true });
    mockListener?.({ didJustFinish: true });
    expect(done).toHaveBeenCalledTimes(1);
    expect(mockRemove).toHaveBeenCalled();
  });

  it('고른 목소리는 기기에 남고, 아무것도 없으면 개나리(여)가 기본', async () => {
    expect(await loadDialectVoice()).toBe('FEMALE');
    await saveDialectVoice('MALE');
    expect(await loadDialectVoice()).toBe('MALE');
  });
});
