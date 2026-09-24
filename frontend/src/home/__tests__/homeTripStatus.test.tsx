// 홈 「내 여행」 카드의 상태 글자 — 여행 목록 카드와 같은 날짜 기준 함수(S15P21E201-1595).
//
// 🔴 이 시험이 지키는 것:
//    ① 오늘이 기간 안인 여행은 서버 status 가 아직 READY 여도 「진행 중」이다 — 전에는 「준비 완료」가 붙었다.
//    ② 홈 두 곳(넓은 화면 카드 · 폰 홈)이 서버 status 로 글자를 따로 만들지 않는다 — 두 벌이 되면 한쪽만 고쳐진다.
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react-native';

import { MyTripCard } from '@/home/HomeBlocks';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { TripSummaryDto } from '@/trip/trips';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다(tripCoverField.test.ts 와 같다).
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));

const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const key = (offset: number) => { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const trip = (startDate: string, endDate: string, status: TripSummaryDto['status']): TripSummaryDto => ({
  tripId: 't1', title: '광안리 여행', startDate, endDate, dayCount: 2, partySize: 2, status, role: 'OWNER', createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
  coverImageUrl: null, firstStopNameKo: null, firstStopNameEn: null,
});

describe('홈 「내 여행」 카드 상태 글자', () => {
  it('🔴 오늘이 기간 안이면 서버가 아직 READY 여도 「진행 중」', () => {
    render(<MyTripCard trip={trip(key(0), key(1), 'READY')} signedIn loaded />, { wrapper: Providers });
    expect(screen.getByText('진행 중')).toBeTruthy();
    expect(screen.queryByText('준비 완료')).toBeNull();
  });

  it('아직 안 떠난 여행은 「예정」 — 여행 목록 카드와 같은 말', () => {
    render(<MyTripCard trip={trip(key(5), key(6), 'READY')} signedIn loaded />, { wrapper: Providers });
    expect(screen.getByText('예정')).toBeTruthy();
  });

  it('🔴 홈 두 곳이 서버 status 로 글자를 따로 만들지 않는다 — 목록과 같은 함수를 부른다', () => {
    for (const file of ['src/home/HomeBlocks.tsx', 'app/(tabs)/home.tsx']) {
      const source = readFileSync(join(__dirname, '..', '..', '..', file), 'utf8') as string;
      expect(source).toContain('tripStatusLabel(effectiveTripStatus(');
      expect(source).not.toContain("tx('준비 완료'");
    }
  });
});
