// 방침 알림은 «가장 새로 동의한 판»으로 판단한다 (S15P21E201-1769).
import { policyVersionToNotify } from '@/legal/PolicyUpdateNotice';

const row = (policyVersion: string) => ({ consentType: 'PRIVACY_POLICY', status: 'GRANTED', policyVersion });

it('🔴 옛 판 줄이 먼저 와도 새 판에 이미 동의했으면 알리지 않는다', () => {
  expect(policyVersionToNotify({ currentPolicyVersion: '2026-09', consents: [row('2026-01'), row('2026-09')] }, null)).toBeNull();
});

it('옛 판에만 동의했으면 새 판을 알린다', () => {
  expect(policyVersionToNotify({ currentPolicyVersion: '2026-09', consents: [row('2026-01')] }, null)).toBe('2026-09');
});

it('기기에 이미 본 판이 있으면 알리지 않는다', () => {
  expect(policyVersionToNotify({ currentPolicyVersion: '2026-09', consents: [row('2026-01')] }, '2026-09')).toBeNull();
});
