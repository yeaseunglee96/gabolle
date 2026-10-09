// 직접 짜기(S15P21E201-2018) · 참고 명소(S15P21E201-2019).
//
// 🔴 이 시험이 지키는 것:
//    1. 참고 명소는 갈래 여섯(바다·문화·전망·시장·자연·축제)이고, 고정 명소는 모두 운영 장소 번호(지어낸 것 아님)·한 줄 소개를 갖는다.
//    2. 직접 짜기는 여행을 만들고 «빈 일정»을 붙인다 — 추천 작업(recommendation-jobs)을 부르지 않는다.
//       같은 조건으로 AI 추천 여행을 만든 적이 있어도 그 여행을 돌려받지 않게 멱등 키가 다르다.
//    3. 참고 명소 카드의 「넣기」 한 번이 그 장소를 넣는다. 이미 그날에 있는 곳은 「넣음」이라 다시 못 누른다.
//    4. 확인 표에 「직접 짜기」 입구가 있고, 장소 더하기 창에도 참고 명소 탭이 있다.
import { fireEvent, render, screen } from '@testing-library/react-native';

declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8') as string;

const mockApiRequest = jest.fn();
jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: (...args: unknown[]) => mockApiRequest(...args) };
});
jest.mock('@/plan/placePhotos', () => ({
  loadPlacePhoto: jest.fn(async () => ({ photoUrl: null, photoSource: null, category: 'SEA_BEACH', nameEn: null })),
  categoryGlyph: () => '📍',
}));
jest.mock('@/discovery/festivals', () => ({
  getFestivals: jest.fn(async () => []),
  festivalDisplayTitle: (f: { nameKo: string }) => f.nameKo,
}));

import { REFERENCE_THEMES, referenceSightsOf } from '@/plan/referenceSights';
import { createTripForSelfPlan, stableIdempotencyKey, toCreateTripPayload } from '@/api/tripApi';
import { ReferenceSightsList as ReferenceSights } from '@/plan/ReferenceSightsList';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

jest.setTimeout(20000);

describe('1. 참고 명소 목록', () => {
  it('갈래는 여섯이고 축제는 여행 날짜로 받아 온다', () => {
    expect(REFERENCE_THEMES.map((theme) => theme.key)).toEqual(['sea', 'culture', 'view', 'market', 'nature', 'festival']);
    expect(referenceSightsOf('festival')).toEqual([]);
  });

  it('🔴 고정 명소는 운영 장소 번호(UUID)·한 줄 소개를 갖고, 겹치지 않는다', () => {
    const all = REFERENCE_THEMES.filter((theme) => theme.key !== 'festival').flatMap((theme) => referenceSightsOf(theme.key));
    expect(all.length).toBeGreaterThanOrEqual(15);
    for (const sight of all) {
      expect(sight.placeId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
      expect(sight.lineKo.length).toBeGreaterThan(4);
      expect(sight.lineEn.length).toBeGreaterThan(4);
    }
    expect(new Set(all.map((sight) => sight.placeId)).size).toBe(all.length);
  });
});

describe('2. 직접 짜기 — 여행 + 빈 일정', () => {
  beforeEach(() => mockApiRequest.mockReset());

  it('🔴 여행을 만들고 빈 일정을 붙인다 — 추천 작업은 부르지 않는다', async () => {
    mockApiRequest
      .mockResolvedValueOnce({ tripId: 'trip-1' })
      .mockResolvedValueOnce({ itineraryId: 'it-1' });
    const draft = { ...EMPTY_PLAN, startDate: '2026-10-20', endDate: '2026-10-21', travelers: 2, adults: 2, attemptId: 'a1' };
    await expect(createTripForSelfPlan(draft, null)).resolves.toEqual({ tripId: 'trip-1', itineraryId: 'it-1' });
    expect(mockApiRequest).toHaveBeenCalledTimes(2);
    expect(mockApiRequest.mock.calls[0][0]).toBe('/api/v1/trips');
    expect(mockApiRequest.mock.calls[1][0]).toBe('/api/v1/trips/trip-1/itineraries/blank');
    expect(mockApiRequest.mock.calls[1][1]).toMatchObject({ method: 'POST' });
    const key = mockApiRequest.mock.calls[0][1].headers['Idempotency-Key'];
    expect(key).not.toBe(stableIdempotencyKey(toCreateTripPayload(draft), draft.attemptId));
  });
});

describe('3. 참고 명소 카드', () => {
  const mount = (onAdd = jest.fn(), added = new Set<string>()) => render(
    <OnboardingPreferencesProvider>
      <ReferenceSights startDate="2026-10-20" endDate="2026-10-21" addedPlaceIds={added} busyPlaceId={null} onAdd={onAdd} />
    </OnboardingPreferencesProvider>,
  );

  it('🔴 「넣기」 한 번에 그 장소를 넣는다', async () => {
    const onAdd = jest.fn();
    mount(onAdd);
    const first = referenceSightsOf('sea')[0];
    fireEvent.press(await screen.findByTestId(`reference-add-${first.placeId}`, {}, { timeout: 10000 }));
    expect(onAdd).toHaveBeenCalledWith(first.placeId);
  });

  it('갈래를 바꾸면 그 갈래 명소가 나온다', async () => {
    mount();
    fireEvent.press(await screen.findByTestId('reference-theme-market', {}, { timeout: 10000 }));
    expect(await screen.findByTestId(`reference-add-${referenceSightsOf('market')[0].placeId}`)).toBeTruthy();
  });

  it('이미 그날에 있는 곳은 「넣음」이고 다시 못 누른다', async () => {
    const onAdd = jest.fn();
    const first = referenceSightsOf('sea')[0];
    mount(onAdd, new Set([first.placeId]));
    fireEvent.press(await screen.findByTestId(`reference-add-${first.placeId}`, {}, { timeout: 10000 }));
    expect(onAdd).not.toHaveBeenCalled();
  });
});

describe('4. 입구', () => {
  it('확인 표에 「직접 짜기」가 있고 직접 짜기 화면으로 간다', () => {
    const plan = source('app/(plan)/questions.tsx');
    expect(plan).toContain('createTripForSelfPlan');
    expect(plan).toContain('self-plan');
  });

  it('장소 더하기 창에 참고 명소 탭이 있다', () => {
    const sheet = source('src/components/AddPlaceSheet.tsx');
    expect(sheet).toContain("['sights', tx('참고 명소'");
    expect(sheet).toContain('<ReferenceSightsList');
  });
});
