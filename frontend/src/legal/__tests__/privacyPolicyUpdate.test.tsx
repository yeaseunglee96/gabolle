// 개인정보 처리방침 — 위치정보 쓰임·품질 통계 목적 · 「처리방침이 바뀌었어요」 알림 (S15P21E201-1694).
//
// 🔴 이 시험이 지키는 것:
//    1. 방침에 위치 쓰임 셋(주변 찾기·방문 인증·도착·출발)과 품질 통계 목적이 적혀 있다 — 한국어·영어 둘 다.
//    2. 아직 안 정한 칸(책임자 이름·시행일)을 지어 채우지 않았다. 문의처는 팀이 채웠다(!1646).
//    3. 알림은 서버가 지금 판을 알려 주고, 내 동의 판이 그보다 옛것일 때만 — 한 번. 칸이 없으면(지금 운영) 안 띄운다.
//    4. 「확인」이면 PATCH {PRIVACY_POLICY:true}, 「나중에」면 아무것도 안 보낸다(강제하지 않음).
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';
import { getMyConsents } from '@/auth/authApi';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PRIVACY_SECTIONS } from '@/legal/legalContent';

jest.mock('@/auth/authApi', () => ({ getMyConsents: jest.fn() }));
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn(async () => ({})) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'tok', user: { userId: 'me' } }) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));

import { PolicyUpdateNotice, policyVersionToNotify } from '@/legal/PolicyUpdateNotice';

jest.setTimeout(20000);

const consents = (current: string | null | undefined, mine: string) => ({
  behaviorPersonalizationEnabled: false,
  ...(current === undefined ? {} : { currentPolicyVersion: current }),
  consents: [{ consentType: 'PRIVACY_POLICY', status: 'GRANTED', policyVersion: mine, decidedAt: '2026-09-01T00:00:00Z' }],
});

describe('1·2. 방침 문구', () => {
  const text = (lang: 0 | 1) => PRIVACY_SECTIONS.flatMap((section) => section.paragraphs.map((paragraph) => paragraph[lang])).join('\n');

  it('🔴 위치 쓰임 셋과 좌표를 보내지 않는 쪽이 적혀 있다', () => {
    const ko = text(0);
    expect(ko).toContain('내 주변 찾기');
    expect(ko).toContain('방문 인증');
    expect(ko).toContain('도착·출발 시각만 저장하고, 좌표는 저장하거나 보내지 않습니다');
    expect(ko).toContain('약 100m 단위로 줄여 보냅니다');
    expect(ko).toContain('「위치 사용」에서 언제든 철회');
    // 🔴 보내는 곳과 보내지 않는 곳을 나눈다 — 지도의 내 위치를 「보내는」 쪽에 적었었다(조율 세션이 짚음).
    expect(ko).toContain('① 내 주변 찾기(둘러보기·주변 버스·지금 갈 곳):');
    expect(ko).toContain('② 피드의 가까운 순 정렬과 지도의 내 위치 표시: 이 기기 안에서만 쓰고 보내지 않습니다.');
    expect(text(1)).toContain('used only on this device and never sent');
    expect(text(1)).toContain('coordinates are never stored or sent');
  });

  it('🔴 품질 통계 목적 — 개인을 알아볼 수 없게', () => {
    expect(text(0)).toContain('서비스 품질 개선을 위한 통계에도 사용하며, 이때는 개인을 알아볼 수 없게 처리합니다.');
    expect(text(1)).toContain('statistics to improve service quality');
  });

  // 🔴 문의처는 팀이 채웠다(!1646, S15P21E201-1646). 책임자 이름·방침 버전·시행일도 2026-09-26 사용자
  //    결정으로 채워졌다(S15P21E201-1648) — 예전에는 여기서 그 셋이 「팀 확정 후」로 남았는지를 봤는데,
  //    이제는 정해진 값이 실제로 들어갔는지를 본다. 두 MR 이 같이 머지된 2026-09-26 아침 front/dev 가
  //    빨갰던 사고(S15P21E201-1713)가 다시 나지 않게, 이 화면의 방침 문구 쪽 값은 여기서 계속 잠근다 —
  //    server-driven policyVersionToNotify(위 3·4절)의 「판」과는 다른 것이다(서버 응답으로만 정해진다).
  it('🔴 책임자 이름·방침 버전·시행일·문의처가 확정값 그대로다 — 다시 「팀 확정 후」로 비지 않았다', () => {
    expect(text(0)).toContain('개인정보 보호책임자: 이예승');
    expect(text(0)).toContain('방침 버전 1.0, 시행일 2026년 9월 26일');
    expect(text(0)).toContain('개인정보 문의처는 gabolle.support@gmail.com 입니다');
    expect(text(0)).not.toContain('팀 확정 후');
  });
});

describe('3. 알릴 판', () => {
  it('🔴 서버가 지금 판을 안 알려 주면(지금 운영) 안 띄운다', () => {
    expect(policyVersionToNotify(consents(undefined, '2026-01'), null)).toBeNull();
    expect(policyVersionToNotify(consents(null, '2026-01'), null)).toBeNull();
  });

  it('내 동의 판이 옛것이면 지금 판을 알린다', () => {
    expect(policyVersionToNotify(consents('2026-10', '2026-01'), null)).toBe('2026-10');
  });

  it('같은 판이면 안 알린다 · 이 기기에서 이미 본 판이면 안 알린다', () => {
    expect(policyVersionToNotify(consents('2026-10', '2026-10'), null)).toBeNull();
    expect(policyVersionToNotify(consents('2026-10', '2026-01'), '2026-10')).toBeNull();
  });
});

describe('4. 알림 창', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    (apiRequest as jest.Mock).mockClear();
    (getMyConsents as jest.Mock).mockResolvedValue(consents('2026-10', '2026-01'));
  });
  const mount = () => render(<OnboardingPreferencesProvider><PolicyUpdateNotice /></OnboardingPreferencesProvider>);

  it('🔴 「확인」이면 새 판의 동의로 남긴다', async () => {
    mount();
    fireEvent.press(await screen.findByText('확인', {}, { timeout: 10000 }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith('/api/v1/auth/me/consents', expect.objectContaining({ method: 'PATCH', body: { consents: { PRIVACY_POLICY: true } } })));
  });

  it('🔴 「나중에」면 아무것도 안 보낸다 — 강제하지 않는다. 같은 판은 다시 안 묻는다', async () => {
    mount();
    fireEvent.press(await screen.findByText('나중에', {}, { timeout: 10000 }));
    await waitFor(async () => expect(await AsyncStorage.getItem('gabolle.privacy-policy-notice-seen')).toBe('2026-10'));
    expect(apiRequest).not.toHaveBeenCalled();
  });
});
