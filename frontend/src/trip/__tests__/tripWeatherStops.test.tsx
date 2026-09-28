// 날씨 창의 「들를 때 날씨」·일정 시각 테두리·폰 줄 첫 일정 시각부터 — S15P21E201-1586.
//
// 🔴 이 시험이 지키는 것은 **정차 시각의 칸이 서버에 없을 때 옆 칸으로 메우지 않는가**다.
//    오늘 날짜는 발표 이후 시각만 온다(2026-09-24 운영 실측: 21~23시 셋). 15시에 들르는 곳에
//    21시 날씨를 붙이면 그럴듯한 가짜가 된다 — 「—」로 둔다.
import type { ReactNode } from 'react';
import { ScrollView } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { firstStopScrollX, hourForStop, TripWeatherPanel, weatherStops } from '@/trip/TripWeatherPanel';
import type { HourlyForecastDto } from '@/trip/weather';

const mockApiRequest = jest.fn();
jest.mock('@/api/client', () => ({
  ...jest.requireActual('@/api/client'),
  apiRequest: (...args: unknown[]) => mockApiRequest(...args),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
let mockDesktop = false;
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ desktop: mockDesktop, kind: mockDesktop ? 'tablet' : 'phone' }) }));

const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;
// 첫 시험은 부품을 처음 불러오는 값(수 초)을 치른다 — 기본 1초 대기로는 모자란다.
const WAIT = { timeout: 5000 };
// 🔴 기다림(WAIT 5초)보다 시험 제한 시간이 길어야 기다림이 먹힌다. jest 기본 제한 시간도 5초라, 부하가 걸리면 기다림이
//    끝나기 전에 시험이 먼저 끝났다(「Exceeded timeout of 5000 ms」 — S15P21E201-1665). 파일 전체에 넉넉히 준다.
jest.setTimeout(20000);

const hour = (hh: number, extra: Partial<HourlyForecastDto> = {}): HourlyForecastDto => ({
  time: `${String(hh).padStart(2, '0')}:00`, temperature: 21, skyCondition: 'CLEAR', precipitationProbability: 0, precipitationType: 'NONE', ...extra,
});
const fullDay = Array.from({ length: 24 }, (_, hh) => hour(hh));
const forecast = { date: '2026-09-25', minTemperature: 20, maxTemperature: 27, precipitationProbability: 30, skyCondition: 'CLEAR' };
const respond = (hourly: HourlyForecastDto[]) => mockApiRequest.mockResolvedValue({ nx: 98, ny: 76, cached: false, forecast, hourly });
const item = (clock: string, title: string) => ({ startsAt: `2026-09-25T${clock}:00`, title });

beforeEach(() => { mockApiRequest.mockReset(); mockDesktop = false; });

// 🔴 이 시험은 날짜를 2026-09-25 로 박았다 — 시계도 그날에 멈춘다(S15P21E201-1641). 날씨는 오늘부터 사흘 안만 부르므로
//    시계를 안 멈추면 다음 날부터 「지난 날짜」라 부르지 않고 이 시험이 실패한다. 한국·UTC(CI 러너) 어느 시각대로 돌려도
//    같은 날이 되게 한낮에 멈춘다. 멈추는 것은 날짜뿐이다 — 기다리기(setTimeout 등)는 진짜 시계 그대로 둔다.
const FROZEN_NOW = new Date('2026-09-25T12:00:00+09:00');
beforeAll(() => {
  jest.useFakeTimers({ now: FROZEN_NOW, doNotFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'setImmediate', 'clearImmediate', 'queueMicrotask', 'nextTick', 'requestAnimationFrame', 'cancelAnimationFrame', 'requestIdleCallback', 'cancelIdleCallback', 'hrtime', 'performance'] });
});
afterAll(() => { jest.useRealTimers(); });

describe('정차 시각 → 시간별 칸', () => {
  it('일정 항목에서 시각과 이름을 읽는다 — 시각이 없는 항목은 뺀다', () => {
    expect(weatherStops([item('09:46', '카페오뜨'), { startsAt: '2026-09-25', title: '시각 없음' }])).toEqual([{ time: '09:46', name: '카페오뜨' }]);
    expect(weatherStops(undefined)).toEqual([]);
  });

  it('가장 가까운 정시의 칸이다 — 09:46 → 10시, 09:29 → 9시', () => {
    expect(hourForStop('09:46', fullDay)?.time).toBe('10:00');
    expect(hourForStop('09:29', fullDay)?.time).toBe('09:00');
    expect(hourForStop('09:30', fullDay)?.time).toBe('10:00');
  });

  it('🔴 그 칸이 서버에 없으면 null — 옆 칸으로 대신하지 않는다', () => {
    const tonight = [hour(21), hour(22), hour(23)];
    expect(hourForStop('15:00', tonight)).toBeNull();
    // 23:40 은 「24시」 — 없는 칸이다. 23시로 내려 붙이지 않는다.
    expect(hourForStop('23:40', fullDay)).toBeNull();
  });

  it('폰 줄은 일정이 있는 가장 이른 칸까지 밀어 둔다 — 칸 폭 60 + 사이 6', () => {
    expect(firstStopScrollX(fullDay, [{ time: '08:10', name: 'a' }, { time: '14:00', name: 'b' }])).toBe(8 * 66);
    // 일정 순서가 시각 순서와 달라도 「가장 이른 칸」이다.
    expect(firstStopScrollX(fullDay, [{ time: '14:00', name: 'b' }, { time: '08:10', name: 'a' }])).toBe(8 * 66);
  });

  it('일정 칸이 없으면 맨 앞부터(0)', () => {
    expect(firstStopScrollX(fullDay, [])).toBe(0);
    expect(firstStopScrollX([hour(21), hour(22)], [{ time: '09:00', name: 'a' }])).toBe(0);
  });
});

describe('날씨 창 — 들를 때 날씨·테두리', () => {
  it('일정 있는 시각 칸에 정차 번호를 붙이고, 정차지마다 그 칸의 날씨를 적는다', async () => {
    respond(fullDay.map((cell) => (cell.time === '14:00' ? { ...cell, temperature: 26, skyCondition: 'PARTLY_CLOUDY' as const, precipitationProbability: 10 } : cell)));
    render(<TripWeatherPanel date="2026-09-25" items={[item('09:46', '카페오뜨'), item('14:08', '무슈뱅상')]} />, { wrapper: Providers });

    expect(await screen.findByText('10시 · 1', {}, WAIT)).toBeTruthy();
    expect(screen.getByText('14시 · 2')).toBeTruthy();
    expect(screen.getByText('9시')).toBeTruthy();
    expect(screen.getByText('들를 때 날씨')).toBeTruthy();
    expect(screen.getByText('카페오뜨')).toBeTruthy();
    expect(screen.getByText('☀ 21° · 비 0%')).toBeTruthy();
    expect(screen.getByText('⛅ 26° · 비 10%')).toBeTruthy();
  });

  it('🔴 정차 시각의 칸이 없으면 그 줄은 「—」이고 테두리도 없다', async () => {
    respond([hour(21), hour(22), hour(23)]);
    render(<TripWeatherPanel date="2026-09-25" items={[item('15:00', '해운대')]} />, { wrapper: Providers });

    expect(await screen.findByText('해운대', {}, WAIT)).toBeTruthy();
    expect(screen.getByLabelText('15:00, 해운대, 예보 없음')).toBeTruthy();
    expect(screen.queryByText(/ · 1$/)).toBeNull();
  });

  it('일정을 안 넘기면 시간별 줄만 — 「들를 때 날씨」는 없다', async () => {
    respond(fullDay);
    render(<TripWeatherPanel date="2026-09-25" />, { wrapper: Providers });

    expect(await screen.findByText('1시간별 예보', {}, WAIT)).toBeTruthy();
    expect(screen.queryByText('들를 때 날씨')).toBeNull();
  });

  it('폰 줄은 처음 그려질 때 첫 일정 칸으로 한 번 밀린다', async () => {
    respond(fullDay);
    const scrollTo = jest.fn();
    jest.spyOn(ScrollView.prototype, 'scrollTo').mockImplementation(scrollTo);
    render(<TripWeatherPanel date="2026-09-25" items={[item('08:10', '아침')]} />, { wrapper: Providers });

    await screen.findByText('8시 · 1', {}, WAIT);
    const row = screen.UNSAFE_getByType(ScrollView);
    fireEvent(row, 'contentSizeChange', 1600, 120);
    fireEvent(row, 'contentSizeChange', 1600, 120);
    // 한 번만 민다 — 사용자가 앞으로 밀어 새벽을 본 뒤 다시 끌려가지 않게.
    expect(scrollTo).toHaveBeenCalledTimes(1);
    expect(scrollTo).toHaveBeenCalledWith({ x: 8 * 66, animated: false });
  });
});
