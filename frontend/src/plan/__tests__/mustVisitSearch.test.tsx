// 꼭 가고 싶은 장소 — 검색 겸 입력창 (시안 ①, 인계 §7①).
//
// 🔴 이 시험이 지키는 것은 「찾아지는가」가 아니라 **서버를 몇 번 부르는가**이다.
//    글자마다 부르면 한 낱말에 열 번 나가고, 한 글자로 부르면 온 부산이 돌아온다.
//    둘 다 화면에서는 멀쩡해 보인다 — 조금 느릴 뿐이다. 그래서 눈으로는 절대 못 잡는다.
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

import { MustVisitSearch, MUST_VISIT_MAX } from '@/plan/MustVisitSearch';

jest.mock('@/discovery/places', () => ({ searchPlacesByName: jest.fn() }));
const places = jest.requireMock('@/discovery/places') as { searchPlacesByName: jest.Mock };

const tx = (ko: string) => ko;

function place(id: string, name: string) {
  return { placeId: id, nameKo: name, nameEn: name, category: '해변', address: `부산 어딘가 ${id}`, lat: 35, lng: 129 };
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.useFakeTimers();
  places.searchPlacesByName.mockResolvedValue([place('p1', '광안리해수욕장')]);
});
afterEach(() => { jest.useRealTimers(); });

function open(picked: Parameters<typeof MustVisitSearch>[0]['picked'] = [], onChange = jest.fn()) {
  const view = render(<MustVisitSearch picked={picked} onChange={onChange} tx={tx} ko />);
  return { view, onChange, input: view.getByLabelText('장소 검색') };
}

describe('꼭 가고 싶은 장소 검색', () => {
  it('🔴 한 글자로는 서버를 안 부른다 — 부르면 온 부산이 돌아온다', () => {
    const { input } = open();

    fireEvent.changeText(input, '광');
    act(() => { jest.advanceTimersByTime(1000); });

    expect(places.searchPlacesByName).not.toHaveBeenCalled();
  });

  it('🔴 글자를 이어 치는 동안은 안 부른다 — 멈춘 뒤 한 번만 부른다', () => {
    const { input } = open();

    fireEvent.changeText(input, '광안');
    act(() => { jest.advanceTimersByTime(100); });
    fireEvent.changeText(input, '광안리');
    act(() => { jest.advanceTimersByTime(100); });
    fireEvent.changeText(input, '광안리해');
    expect(places.searchPlacesByName).not.toHaveBeenCalled();

    act(() => { jest.advanceTimersByTime(300); });
    expect(places.searchPlacesByName).toHaveBeenCalledTimes(1);
    expect(places.searchPlacesByName.mock.calls[0][0]).toBe('광안리해');
  });

  it('찾은 것을 누르면 담긴다', async () => {
    const { input, onChange, view } = open();

    fireEvent.changeText(input, '광안리');
    act(() => { jest.advanceTimersByTime(300); });
    await waitFor(() => view.getByText('광안리해수욕장'));
    fireEvent.press(view.getByText('광안리해수욕장'));

    expect(onChange).toHaveBeenCalledWith([
      { placeId: 'p1', nameKo: '광안리해수욕장', nameEn: '광안리해수욕장', lat: 35, lng: 129 },
    ]);
  });

  it('🔴 이미 담은 곳은 다시 못 담는다 — 같은 곳이 두 번 들어가면 일정이 그 자리를 두 번 비운다', async () => {
    const already = [{ placeId: 'p1', nameKo: '광안리해수욕장', nameEn: '광안리해수욕장', lat: 35, lng: 129 }];
    const { input, onChange, view } = open(already);

    fireEvent.changeText(input, '광안리');
    act(() => { jest.advanceTimersByTime(300); });
    await waitFor(() => view.getByText('✓ 담김'));
    fireEvent.press(view.getByText('✓ 담김'));

    expect(onChange).not.toHaveBeenCalled();
  });

  it(`🔴 ${MUST_VISIT_MAX}곳을 채우면 더 못 담는다 — 넘으면 추천이 「이 여행」이 아니라 「이 목록」이 된다`, () => {
    const full = Array.from({ length: MUST_VISIT_MAX }, (_, i) => ({
      placeId: `f${i}`, nameKo: `곳${i}`, nameEn: null, lat: 35, lng: 129,
    }));
    const { input } = open(full);

    expect(input.props.editable).toBe(false);
  });

  it('담은 것을 뺄 수 있다', () => {
    const picked = [{ placeId: 'p1', nameKo: '광안리해수욕장', nameEn: null, lat: 35, lng: 129 }];
    const { onChange, view } = open(picked);

    fireEvent.press(view.getByLabelText('광안리해수욕장 빼기'));

    expect(onChange).toHaveBeenCalledWith([]);
  });
});
