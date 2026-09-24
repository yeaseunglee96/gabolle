// 글쓰기 「지역」은 입력칸 하나 — S15P21E201-1593.
//
// 🔴 전에는 검색칸과 「지역 직접 쓰기」칸이 따로 있어서 「지역」을 누르면 칸이 두 개 떴다.
//    이제 적는 글자가 곧 지역이고, 같은 글자로 검색한다. 고르면 장소가 연결되고, 고른 뒤 고쳐 쓰면 끊긴다.
import { useState, type ReactNode } from 'react';
import { TextInput } from 'react-native';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { RegionPicker } from '@/components/RegionPicker';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockSearch = jest.fn();
jest.mock('@/social/regionSearch', () => ({
  ...jest.requireActual('@/social/regionSearch'),
  searchRegions: (...args: unknown[]) => mockSearch(...args),
}));

const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
const state = { region: '', placeId: undefined as string | undefined };

function Harness() {
  const [region, setRegion] = useState('');
  const [placeId, setPlaceId] = useState<string | undefined>(undefined);
  state.region = region;
  state.placeId = placeId;
  return <RegionPicker region={region} onChangeRegion={setRegion} placeId={placeId} onChangePlaceId={setPlaceId} accessToken={null} />;
}

beforeEach(() => {
  jest.useFakeTimers();
  mockSearch.mockReset();
  mockSearch.mockResolvedValue({ state: 'success', items: [{ name: '해운대해수욕장', address: '부산 해운대구', placeId: 'p-1' }] });
});
afterEach(() => { jest.useRealTimers(); });

describe('지역 입력칸', () => {
  it('🔴 입력칸은 하나다', () => {
    render(<Harness />, { wrapper: Providers });
    expect(screen.UNSAFE_getAllByType(TextInput)).toHaveLength(1);
  });

  it('적는 글자가 곧 지역이고, 같은 글자로 검색한다', async () => {
    render(<Harness />, { wrapper: Providers });
    fireEvent.changeText(screen.getByLabelText('장소나 지역 검색'), '해운대');
    expect(state.region).toBe('해운대');
    await act(async () => { jest.advanceTimersByTime(350); });
    expect(mockSearch).toHaveBeenCalledWith('해운대', null, expect.anything());
  });

  it('결과를 고르면 장소가 연결되고, 고른 뒤 고쳐 쓰면 연결이 끊긴다', async () => {
    render(<Harness />, { wrapper: Providers });
    fireEvent.changeText(screen.getByLabelText('장소나 지역 검색'), '해운대');
    await act(async () => { jest.advanceTimersByTime(350); });
    fireEvent.press(await screen.findByLabelText('해운대해수욕장 고르기'));
    expect(state.placeId).toBe('p-1');

    fireEvent.changeText(screen.getByLabelText('장소나 지역 검색'), '광안리');
    expect(state.region).toBe('광안리');
    expect(state.placeId).toBeUndefined();
  });
});
