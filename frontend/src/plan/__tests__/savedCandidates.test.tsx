// 여행 만들기 — 저장한 후보를 「꼭 가고 싶은 곳」으로 고르기(S15P21E201-1970).
//
// 🔴 저장한 후보는 추천에 「꼭 넣기」로 들어가지 않았다 — 여행 만들기 화면이 저장 목록을 아예 안 읽어서,
//    같은 곳을 검색해서 다시 골라야 했다. 고르면 mustVisitPlaces 에 들어가고(그대로 mustVisitPlaceIds 로 간다),
//    자동으로 전부 넣지는 않으며, 하루 3곳 × 여행 일수를 넘지 않는지를 지킨다.
import { act, fireEvent, render, screen } from '@testing-library/react-native';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 't', user: { userId: 'u1' } }) }));
jest.mock('@/discovery/savedPlaces', () => ({
  ...jest.requireActual('@/discovery/savedPlaces'),
  loadSavedPlaceIds: jest.fn(async () => ['p-1', 'p-2', 'haeundae']),
}));
jest.mock('@/discovery/places', () => ({
  ...jest.requireActual('@/discovery/places'),
  getPlace: jest.fn(async (id: string) => ({ placeId: id, nameKo: id === 'p-1' ? '자갈치시장' : '흰여울문화마을', nameEn: null, category: '시장', address: '부산', lat: 35, lng: 129, features: [] })),
}));
import { SavedCandidates } from '@/plan/SavedCandidates';
import { mustVisitLimit } from '@/plan/MustVisitSearch';

const tx = (ko: string) => ko;

describe('꼭 가고 싶은 곳 상한 — 하루 3곳 × 여행 일수', () => {
  it.each([
    ['2026-10-10', '2026-10-10', 3],
    ['2026-10-10', '2026-10-12', 9],
    ['', '', 3],
    ['2026-10-12', '2026-10-10', 3],
  ])('%s ~ %s → %s곳', (start, end, expected) => {
    expect(mustVisitLimit(start, end)).toBe(expected);
  });
});

describe('저장한 후보 줄', () => {
  it('🔴 저장한 후보가 보이고, 누르면 꼭 가고 싶은 곳에 들어간다 — 데모 카드는 DB 장소가 아니라 안 보인다', async () => {
    const onChange = jest.fn();
    render(<SavedCandidates picked={[]} onChange={onChange} max={3} tx={tx} language="ko" />);
    const chip = await screen.findByText('자갈치시장');
    expect(screen.getByText('흰여울문화마을')).toBeTruthy();
    expect(screen.queryByText('해운대 해수욕장')).toBeNull();
    await act(async () => { fireEvent.press(chip); });
    expect(onChange).toHaveBeenCalledWith([{ placeId: 'p-1', nameKo: '자갈치시장', nameEn: null, lat: 35, lng: 129 }]);
  });

  it('이미 고른 후보를 다시 누르면 뺀다', async () => {
    const onChange = jest.fn();
    const picked = [{ placeId: 'p-1', nameKo: '자갈치시장', nameEn: null, lat: 35, lng: 129 }];
    render(<SavedCandidates picked={picked} onChange={onChange} max={3} tx={tx} language="ko" />);
    const on = await screen.findByText('✓ 자갈치시장');
    await act(async () => { fireEvent.press(on); });
    expect(onChange).toHaveBeenCalledWith([]);
  });

  it('🔴 상한을 채우면 더 못 넣고 그 이유를 말한다', async () => {
    const onChange = jest.fn();
    const picked = [{ placeId: 'p-1', nameKo: '자갈치시장', nameEn: null, lat: 35, lng: 129 }];
    render(<SavedCandidates picked={picked} onChange={onChange} max={1} tx={tx} language="ko" />);
    const other = await screen.findByText('흰여울문화마을');
    await act(async () => { fireEvent.press(other); });
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText('이 여행에는 1곳까지 담을 수 있어요. 하나를 빼면 다른 후보를 넣을 수 있어요.')).toBeTruthy();
  });
});
