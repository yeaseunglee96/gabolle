import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  clearFirstRunMarks, markOnboardingFinished, markScreenGuideUsed, requestAllGuidesAgain, requestScreenGuideAgain, screenGuidesSeen, takeHomeCoach, takeScreenGuide,
} from '../firstRun';

// 화면별 첫 안내(UI 캔버스 ㉔, S15P21E201-1885)의 규칙 — 한 번 · 해 본 사람은 건너뜀 · 도움말에서 다시.
describe('화면별 첫 안내', () => {
  beforeEach(async () => { await AsyncStorage.clear(); });

  const onboarded = async () => { await markOnboardingFinished(); await takeHomeCoach(); };

  it('앱 소개를 거친 사람에게 화면마다 딱 한 번', async () => {
    await onboarded();
    expect(await takeScreenGuide('taxi')).toBe(true);
    expect(await takeScreenGuide('taxi')).toBe(false);
    // 다른 화면의 안내는 따로 센다
    expect(await takeScreenGuide('assistant')).toBe(true);
  });

  it('앱 소개를 안 거친 기존 회원에게는 저절로 안 뜬다', async () => {
    expect(await takeScreenGuide('taxi')).toBe(false);
  });

  it('🔴 이미 해 본 사람(택시 카드를 연 사람)에게는 안 뜬다', async () => {
    await onboarded();
    await markScreenGuideUsed('taxi');
    expect(await takeScreenGuide('taxi')).toBe(false);
  });

  it('도움말의 「다시 보기」는 앱 소개를 안 거친 사람에게도 한 번 더 띄운다', async () => {
    await requestScreenGuideAgain('taxi');
    expect((await screenGuidesSeen()).taxi).toBe(false);
    expect(await takeScreenGuide('taxi')).toBe(true);
    expect(await takeScreenGuide('taxi')).toBe(false);
    expect((await screenGuidesSeen()).taxi).toBe(true);
  });

  it('「전부 다시 켜기」는 홈 코치와 화면별 안내를 함께 켠다', async () => {
    await onboarded();
    await takeScreenGuide('taxi');
    await takeScreenGuide('assistant');
    await requestAllGuidesAgain();
    expect(await takeHomeCoach()).toBe(true);
    expect(await takeScreenGuide('taxi')).toBe(true);
    expect(await takeScreenGuide('assistant')).toBe(true);
  });

  it('로그아웃하면 화면별 표시도 비운다 — 다음 계정이 앞사람의 「봤음」을 물려받지 않게', async () => {
    await onboarded();
    await takeScreenGuide('taxi');
    await clearFirstRunMarks();
    await onboarded();
    expect(await takeScreenGuide('taxi')).toBe(true);
  });
});
