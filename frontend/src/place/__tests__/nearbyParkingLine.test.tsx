// 장소 상세의 «근처 주차장» 한 줄 (S15P21E201-2052).
// 🔴 지키는 것: ① 있으면 덤이다 — 불러오는 중·실패·0곳이면 아무것도 안 그린다 ② 요금을 모르는 곳을 «가장 싼 곳»이나 «0원»으로 그리지 않는다
//    ③ 이 장소의 좌표를 줄이지 않고(exact) 1시간 기준으로 묻는다 ④ 누르면 근처 편의시설 화면의 주차장 탭이 이 장소 기준·1시간으로 열린다.
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

import { NearbyParkingLine } from '../NearbyParkingLine';
import { getNearbyFacilities, type FacilityItem, type NearbyFacilities } from '@/field/nearbyFacilities';

const mockPush = jest.fn();
let mockLang: 'ko' | 'en' = 'ko';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string, en: string) => (mockLang === 'ko' ? ko : en), language: mockLang }) }));
jest.mock('@/field/nearbyFacilities', () => ({ ...jest.requireActual('@/field/nearbyFacilities'), getNearbyFacilities: jest.fn() }));

const getMock = getNearbyFacilities as jest.Mock;

const lot = (id: string, distanceM: number, estimatedWon: number | null, free = false): FacilityItem => ({
  id, name: id, address: null, lat: 35.1, lng: 129.1, distanceM, kind: 'PARKING',
  detail: { operator: null, free, spaces: null, fee: null, estimatedWon, note: null, openNow: null, todayHours: null, closesAt: null, allDay: false },
});

const answer = (items: FacilityItem[], total = items.length): NearbyFacilities => ({ kind: 'PARKING', radiusM: 700, stayMinutes: 60, total, limit: 20, items });

const PLACE = { lat: 35.1587, lng: 129.1604, placeName: '해운대해수욕장', accessToken: 'tok' };

beforeEach(() => {
  jest.clearAllMocks();
  mockLang = 'ko';
  getMock.mockResolvedValue(answer([lot('싼', 450, 1200), lot('비싼', 120, 3000)]));
});

describe('그리는 것 — 가장 싼 곳', () => {
  it('요금을 아는 곳 중 가장 싼 곳의 «1시간 요금 · 거리» 한 줄과 이름표', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());
    expect(view.getByText('근처 주차장')).toBeTruthy();
    expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('1시간 1,200원 · 450m');
  });

  it('무료 주차장이 있으면 «무료 · 거리»', async () => {
    getMock.mockResolvedValue(answer([lot('유료', 50, 1000), lot('무료', 120, 0, true)]));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('무료 · 120m'));
  });

  it('🔴 요금을 아는 곳이 하나도 없으면 «몇 곳 · 가장 가까운 곳» — 0원도 무료도 아니다', async () => {
    getMock.mockResolvedValue(answer([lot('가깝다', 90, null), lot('멀다', 600, null), lot('더 멀다', 650, null)], 3));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('3곳 · 가장 가까운 곳 90m'));
    expect(view.queryByText(/무료|0원/)).toBeNull();
  });

  it('🔴 요금을 모르는 가까운 곳이 있어도, 요금을 아는 곳이 «가장 싼 곳»이다', async () => {
    getMock.mockResolvedValue(answer([lot('가까운데 모름', 20, null), lot('아는 곳', 380, 2500)]));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('1시간 2,500원 · 380m'));
  });

  it('1km 가 넘는 거리는 km 로', async () => {
    getMock.mockResolvedValue(answer([lot('먼', 1250, 900)]));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('1시간 900원 · 1.3km'));
  });

  it('접근성 라벨에 이름표와 값이 함께 들어 있다', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());
    expect(view.getByTestId('place-nearby-parking').props.accessibilityLabel).toBe('근처 주차장, 1시간 1,200원 · 450m');
    expect(view.getByTestId('place-nearby-parking').props.accessibilityRole).toBe('link');
  });
});

describe('🔴 있으면 덤 — 못 그릴 때는 아무것도 안 그린다', () => {
  it('불러오는 동안에는 자리를 비워 두지도 «불러오는 중»을 걸지도 않는다', async () => {
    let finish: (value: NearbyFacilities) => void = () => undefined;
    getMock.mockImplementation(() => new Promise<NearbyFacilities>((resolve) => { finish = resolve; }));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(view.toJSON()).toBeNull();
    await act(async () => { finish(answer([lot('싼', 100, 500)])); });
    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());
  });

  it('0곳이면 줄이 통째로 없다', async () => {
    getMock.mockResolvedValue(answer([]));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    await act(async () => { await Promise.resolve(); });
    expect(view.toJSON()).toBeNull();
  });

  it('서버가 실패해도 줄이 없을 뿐 터지지 않는다', async () => {
    getMock.mockRejectedValue(new Error('boom'));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    await act(async () => { await Promise.resolve(); });
    expect(view.toJSON()).toBeNull();
  });

  it.each([[Number.NaN, 129.1], [35.1, Number.NaN], [0, 0], [91, 129.1], [35.1, 181]])('좌표가 쓸 수 없으면(%s, %s) 서버에 묻지도 않는다', async (lat, lng) => {
    const view = render(<NearbyParkingLine {...PLACE} lat={lat} lng={lng} />);

    await act(async () => { await Promise.resolve(); });
    expect(getMock).not.toHaveBeenCalled();
    expect(view.toJSON()).toBeNull();
  });
});

describe('서버에 묻는 방법', () => {
  it('🔴 이 장소의 좌표를 줄이지 않고(exact), 700m · 1시간 기준으로, 로그인 토큰과 함께', async () => {
    render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(getMock).toHaveBeenCalledWith('PARKING', { latitude: 35.1587, longitude: 129.1604 }, 'tok', expect.objectContaining({ radiusM: 700, stayMinutes: 60, exact: true }));
  });

  it('로그인하지 않은 사람도 묻는다(토큰 없이)', async () => {
    render(<NearbyParkingLine {...PLACE} accessToken={null} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledWith('PARKING', expect.anything(), null, expect.anything()));
  });

  it('화면을 떠나면 진행 중인 요청을 취소한다', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    const signal = (getMock.mock.calls[0][3] as { signal: AbortSignal }).signal;
    expect(signal.aborted).toBe(false);

    view.unmount();

    expect(signal.aborted).toBe(true);
  });

  it('🔴 장소가 바뀌면 앞 장소의 줄을 지우고 새로 묻는다 — 새 답이 오기 전에 앞 장소의 요금이 보이지 않는다', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());
    let finish: (value: NearbyFacilities) => void = () => undefined;
    getMock.mockImplementation(() => new Promise<NearbyFacilities>((resolve) => { finish = resolve; }));

    view.rerender(<NearbyParkingLine {...PLACE} lat={35.1532} lng={129.1186} placeName="광안리해수욕장" />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));
    expect(view.queryByTestId('place-nearby-parking') === null).toBe(true);
    await act(async () => { finish(answer([lot('새', 70, 800)])); });
    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('1시간 800원 · 70m'));
  });

  it('로그인 토큰만 새로 바뀌면 다시 묻되 줄이 깜빡이지 않는다', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());

    view.rerender(<NearbyParkingLine {...PLACE} accessToken="tok2" />);

    expect(view.getByTestId('place-nearby-parking')).toBeTruthy();
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));
    expect(view.getByTestId('place-nearby-parking')).toBeTruthy();
  });
});

describe('누르면', () => {
  it('🔴 근처 편의시설 화면의 주차장 탭이 이 장소 기준으로 열린다 — 1시간 칩으로, 장소 이름을 달고', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(view.getByTestId('place-nearby-parking')).toBeTruthy());

    fireEvent.press(view.getByTestId('place-nearby-parking'));

    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/field/nearby',
      params: { kind: 'PARKING', lat: '35.1587', lng: '129.1604', name: '해운대해수욕장', stay: '1' },
    });
  });
});

describe('영어', () => {
  beforeEach(() => { mockLang = 'en'; });

  it('«₩1,200 for 1 hour · 450m»', async () => {
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('₩1,200 for 1 hour · 450m'));
    expect(view.getByText('Nearby parking')).toBeTruthy();
  });

  it('«Free · 120m»', async () => {
    getMock.mockResolvedValue(answer([lot('무료', 120, 0, true)]));
    const view = render(<NearbyParkingLine {...PLACE} />);

    await waitFor(() => expect(view.getByTestId('place-nearby-parking-value').props.children).toBe('Free · 120m'));
  });

  it('곳 수는 단수·복수를 가린다 — «1 lot», «3 lots»', async () => {
    getMock.mockResolvedValue(answer([lot('하나', 90, null)], 1));
    const one = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(one.getByTestId('place-nearby-parking-value').props.children).toBe('1 lot · nearest 90m'));
    one.unmount();

    getMock.mockResolvedValue(answer([lot('a', 90, null), lot('b', 100, null), lot('c', 110, null)], 3));
    const many = render(<NearbyParkingLine {...PLACE} />);
    await waitFor(() => expect(many.getByTestId('place-nearby-parking-value').props.children).toBe('3 lots · nearest 90m'));
  });
});
