// 일정에서 장소를 뺄 때 이유 한 번 누르기 — S15P21E201-1695 (조율 세션 결정 · 07 계약).
//
// 🔴 이 시험이 지키는 것:
//    1. 이유 다섯을 누르면 그 이유(서버가 검사하는 코드)로 곧바로 뺀다. 「이유 없이 제외」는 건너뛰기 — null.
//    2. 빼기 요청 본문의 operationalReason 에 실린다. 건너뛰면 칸이 없다(서버에서 null).
//    3. 두 화면(폰 여행 · 예전 일정)이 고른 이유를 그대로 넘긴다.
import { fireEvent, render, screen } from '@testing-library/react-native';

import { apiRequest } from '@/api/client';
import { EXCLUDE_REASONS, ExcludeConfirmModal } from '@/components/ExcludeConfirmModal';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { removeItineraryItem } from '@/plan/itinerary';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn(async () => ({ jobId: 'job-1' })) }));

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8') as string;

jest.setTimeout(20000);

describe('1. 빼기 확인 창의 이유', () => {
  const mount = (onConfirm: jest.Mock) => render(
    <OnboardingPreferencesProvider>
      <ExcludeConfirmModal visible placeTitle="해운대 시장" busy={false} onCancel={jest.fn()} onConfirm={onConfirm} />
    </OnboardingPreferencesProvider>,
  );

  it('🔴 서버와 맞춘 코드 다섯 — 이름을 바꾸면 서버가 400 을 준다', () => {
    expect(EXCLUDE_REASONS.map((reason) => [reason.ko, reason.code])).toEqual([
      ['가 봤어요', 'ALREADY_VISITED'],
      ['취향 아님', 'NOT_INTERESTED'],
      ['멀어요', 'TOO_FAR'],
      ['문 닫음', 'CLOSED'],
      ['그냥', 'OTHER'],
    ]);
  });

  it('🔴 이유를 누르면 그 이유로 곧바로 뺀다(한 번 누르기)', async () => {
    const onConfirm = jest.fn();
    mount(onConfirm);
    fireEvent.press(await screen.findByText('가 봤어요', {}, { timeout: 10000 }));
    expect(onConfirm).toHaveBeenCalledWith('ALREADY_VISITED');
  });

  it('🔴 「이유 없이 제외」는 건너뛰기 — null', async () => {
    const onConfirm = jest.fn();
    mount(onConfirm);
    fireEvent.press(await screen.findByText('이유 없이 제외', {}, { timeout: 10000 }));
    expect(onConfirm).toHaveBeenCalledWith(null);
  });
});

describe('2. 빼기 요청 본문', () => {
  beforeEach(() => { (apiRequest as jest.Mock).mockClear(); });

  it('🔴 고른 이유가 operationalReason 에 실린다', async () => {
    await removeItineraryItem({ itineraryId: 'it-1', itemId: 'i1', baseVersion: 3, operationalReason: 'TOO_FAR', accessToken: 'tok' });
    const [path, options] = (apiRequest as jest.Mock).mock.calls[0];
    expect(path).toBe('/api/v1/itineraries/it-1/items/i1/remove');
    expect(options.body).toEqual({ baseVersion: 3, operationalReason: 'TOO_FAR' });
  });

  it('건너뛰면 칸이 없다 — 보낼 때 JSON 에서 빠져 서버에서 null', async () => {
    await removeItineraryItem({ itineraryId: 'it-1', itemId: 'i1', baseVersion: 3, operationalReason: undefined, accessToken: 'tok' });
    const [, options] = (apiRequest as jest.Mock).mock.calls[0];
    expect(JSON.parse(JSON.stringify(options.body))).toEqual({ baseVersion: 3 });
  });
});

describe('3. 두 화면이 고른 이유를 넘긴다', () => {
  it('🔴 폰 여행 화면', () => {
    const mobile = source('src/trip/page/TripPageMobile.tsx');
    expect(mobile).toContain('if (target) void exclude(target, reason);');
    expect(mobile).toContain('operationalReason: reason ?? undefined');
  });

  it('🔴 예전 일정 화면', () => {
    const classic = source('app/trips/[id]/itinerary.tsx');
    expect(classic).toContain('if (target) void excludeItem(target, reason);');
    expect(classic).toContain('operationalReason: reason ?? undefined');
  });
});
