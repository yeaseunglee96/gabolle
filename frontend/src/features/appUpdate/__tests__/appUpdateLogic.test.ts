import { compareVersions, decideUpdatePrompt, shouldCheckNow, shouldShowWhatsNew, CHECK_INTERVAL_MS, WHATS_NEW } from '../appUpdateLogic';
import { LANGUAGE_CODES } from '@/i18n/languages';

const release = { latestVersion: '1.0.3', minSupportedVersion: '1.0.0', storeUrl: 'https://store' };

describe('compareVersions', () => {
  it('마디를 숫자로 견준다 — 1.0.10 은 1.0.9 보다 높다', () => {
    expect(compareVersions('1.0.10', '1.0.9')).toBeGreaterThan(0);
    expect(compareVersions('1.0.2', '1.0.3')).toBeLessThan(0);
    expect(compareVersions('1.0', '1.0.0')).toBe(0);
    expect(compareVersions('2.0.0', '1.9.9')).toBeGreaterThan(0);
  });
});

describe('decideUpdatePrompt', () => {
  it('최신보다 낮으면 권한다', () => {
    expect(decideUpdatePrompt('1.0.2', release, null)).toEqual({ kind: 'soft', latestVersion: '1.0.3', storeUrl: 'https://store' });
  });
  it('그 판을 「나중에」로 닫았으면 다시 권하지 않는다 — 다음 판은 다시 권한다', () => {
    expect(decideUpdatePrompt('1.0.2', release, '1.0.3').kind).toBe('none');
    expect(decideUpdatePrompt('1.0.2', { ...release, latestVersion: '1.0.4' }, '1.0.3').kind).toBe('soft');
  });
  it('최신이거나 더 높으면 아무것도 안 한다', () => {
    expect(decideUpdatePrompt('1.0.3', release, null).kind).toBe('none');
    expect(decideUpdatePrompt('1.0.4', release, null).kind).toBe('none');
  });
  it('🔴 최소 지원 미만이면 막는다 — 「나중에」 기록과 무관하다', () => {
    expect(decideUpdatePrompt('1.0.1', { ...release, minSupportedVersion: '1.0.2' }, '1.0.3').kind).toBe('force');
  });
  it('🔴 값을 모르면 아무것도 안 한다 — 네트워크 실패·이상한 서버 값', () => {
    expect(decideUpdatePrompt('1.0.2', null, null).kind).toBe('none');
    expect(decideUpdatePrompt(null, release, null).kind).toBe('none');
    expect(decideUpdatePrompt('1.0.2', { ...release, latestVersion: 'abc' }, null).kind).toBe('none');
    expect(decideUpdatePrompt('1.0.2', { ...release, storeUrl: '' }, null).kind).toBe('none');
  });
});

describe('shouldCheckNow', () => {
  const now = 1_800_000_000_000;
  it('하루에 한 번', () => {
    expect(shouldCheckNow(null, now)).toBe(true);
    expect(shouldCheckNow(now - 1000, now)).toBe(false);
    expect(shouldCheckNow(now - CHECK_INTERVAL_MS, now)).toBe(true);
    expect(shouldCheckNow(now + 5000, now)).toBe(true);
  });
});

describe('shouldShowWhatsNew', () => {
  it('🔴 처음 설치한 사람에게는 안 보인다', () => {
    expect(shouldShowWhatsNew('1.0.3', null, false)).toBe(false);
  });
  it('판 기록이 없는 옛 앱에서 올라온 사람(이미 앱에 들어온 적 있음)에게 보인다', () => {
    expect(shouldShowWhatsNew('1.0.3', null, true)).toBe(true);
  });
  it('낮은 판을 봤으면 보이고, 같은 판을 이미 봤으면 안 보인다', () => {
    expect(shouldShowWhatsNew('1.0.3', '1.0.2', false)).toBe(true);
    expect(shouldShowWhatsNew('1.0.3', '1.0.3', true)).toBe(false);
  });
  it('새로워진 점 목록이 없는 판이면 안 보인다', () => {
    expect(shouldShowWhatsNew('1.0.9', '1.0.3', true)).toBe(false);
  });
  it('1.0.3 목록은 여섯 언어가 모두 채워져 있다', () => {
    for (const item of WHATS_NEW['1.0.3']) {
      for (const lang of LANGUAGE_CODES) expect(item[lang].trim()).not.toBe('');
    }
  });
});
