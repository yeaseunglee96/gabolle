// 웹에서 공유 창을 닫거나 공유가 없는 브라우저일 때 실패로 보이지 않는가(S15P21E201-1958).
import { Platform, Share } from 'react-native';
import { shareLink } from './shareLink';

// CI 의 Node 는 navigator 가 없다(로컬 Node 24 는 있다) — 시험용으로 하나 둔다.
if (!(globalThis as { navigator?: unknown }).navigator) Object.defineProperty(globalThis, 'navigator', { value: {}, configurable: true, writable: true });
const nav = globalThis.navigator as unknown as Record<string, unknown>;
const original = { share: nav.share, clipboard: nav.clipboard };
const realOS = Platform.OS;

function setOS(os: string) { Object.defineProperty(Platform, 'OS', { value: os, configurable: true }); }

afterEach(() => {
  setOS(realOS);
  nav.share = original.share;
  Object.defineProperty(nav, 'clipboard', { value: original.clipboard, configurable: true });
  jest.restoreAllMocks();
});

const content = { title: 't', message: 'm https://x/s/1', url: 'https://x/s/1' };

describe('웹', () => {
  beforeEach(() => setOS('web'));

  it('사용자가 공유 창을 닫으면(AbortError) 실패가 아니라 dismissed', async () => {
    nav.share = jest.fn().mockRejectedValue(Object.assign(new Error('Share canceled'), { name: 'AbortError' }));
    await expect(shareLink(content)).resolves.toBe('dismissed');
  });

  it('navigator.share 가 없으면 링크를 복사하고 copied', async () => {
    nav.share = undefined;
    const writeText = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(nav, 'clipboard', { value: { writeText }, configurable: true });
    await expect(shareLink(content)).resolves.toBe('copied');
    expect(writeText).toHaveBeenCalledWith('https://x/s/1');
  });

  it('공유가 다른 이유로 거절되면 복사로 물러선다', async () => {
    nav.share = jest.fn().mockRejectedValue(Object.assign(new Error('no'), { name: 'NotAllowedError' }));
    const writeText = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(nav, 'clipboard', { value: { writeText }, configurable: true });
    await expect(shareLink(content)).resolves.toBe('copied');
  });

  it('공유도 복사도 안 되면 failed — 던지지 않는다', async () => {
    nav.share = undefined;
    Object.defineProperty(nav, 'clipboard', { value: undefined, configurable: true });
    await expect(shareLink(content)).resolves.toBe('failed');
  });

  it('공유가 되면 shared', async () => {
    nav.share = jest.fn().mockResolvedValue(undefined);
    await expect(shareLink(content)).resolves.toBe('shared');
  });
});

describe('앱(동작은 예전 그대로)', () => {
  beforeEach(() => setOS('android'));

  it('OS 공유 시트를 부르고 닫으면 dismissed', async () => {
    jest.spyOn(Share, 'share').mockResolvedValue({ action: Share.dismissedAction });
    await expect(shareLink(content)).resolves.toBe('dismissed');
    expect(Share.share).toHaveBeenCalledWith({ title: 't', message: 'm https://x/s/1', url: 'https://x/s/1' });
  });

  it('OS 공유가 던지면 그대로 던진다', async () => {
    jest.spyOn(Share, 'share').mockRejectedValue(new Error('boom'));
    await expect(shareLink(content)).rejects.toThrow('boom');
  });
});
