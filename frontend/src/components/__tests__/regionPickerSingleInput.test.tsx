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

  // 🔴 찾은 것이 «없을» 때 — S15P21E201-1804.
  //
  // 검색이 실패했을 때는 안내가 있었는데, 서버가 멀쩡히 「그런 곳 없음」이라고 답하면
  // 목록도 안내도 아무것도 안 떴다. 사용자 눈에는 고를 것이 없으니 「이 지역은 안 되는구나」
  // 로 읽힌다 — 실제로는 적은 그대로 저장된다.
  it('0건이면 「적은 그대로 저장된다」고 알린다', async () => {
    mockSearch.mockResolvedValue({ state: 'success', items: [] });
    render(<Harness />, { wrapper: Providers });
    // 아직 안 쳤을 때는 말하지 않는다 — 열자마자 「없어요」는 거짓이다.
    expect(screen.queryByText(/찾는 곳이 없어요/)).toBeNull();

    fireEvent.changeText(screen.getByLabelText('장소나 지역 검색'), '수영구');
    await act(async () => { jest.advanceTimersByTime(350); });
    expect(await screen.findByText(/찾는 곳이 없어요/)).toBeTruthy();
    // 적은 글자는 그대로 지역이다.
    expect(state.region).toBe('수영구');
  });

  it('고른 뒤에는 0건 안내가 남지 않는다', async () => {
    mockSearch.mockResolvedValue({ state: 'success', items: [{ name: '광안리해수욕장', address: '부산 수영구', placeId: 'p-2' }] });
    render(<Harness />, { wrapper: Providers });
    fireEvent.changeText(screen.getByLabelText('장소나 지역 검색'), '광안리');
    await act(async () => { jest.advanceTimersByTime(350); });
    fireEvent.press(await screen.findByLabelText('광안리해수욕장 고르기'));
    expect(screen.queryByText(/찾는 곳이 없어요/)).toBeNull();
  });
});
