// 여행 페이지 날씨 창의 「1시간별 예보」 — S15P21E201-1582.
//
// 🔴 이 시험이 지키는 것은 **서버가 준 것만 그리는가**다.
//    서버는 원문에 있는 시각만 보낸다 — 오늘 날짜면 발표 이후 몇 칸뿐이다(2026-09-24 운영 실측: 21~23시 셋).
//    빈 시각을 채우거나, 모르는 값(null)을 0 으로 그리면 「0%」·「0°」라고 단언하는 가짜 데이터가 된다.
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripWeatherPanel } from '@/trip/TripWeatherPanel';
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

const forecast = { date: '2026-09-24', minTemperature: 22, maxTemperature: 23, precipitationProbability: 30, skyCondition: 'CLOUDY' };
const hour = (time: string, extra: Partial<HourlyForecastDto> = {}): HourlyForecastDto => ({
  time, temperature: 22, skyCondition: 'CLEAR', precipitationProbability: 0, precipitationType: 'NONE', ...extra,
});
const respond = (hourly?: HourlyForecastDto[]) => mockApiRequest.mockResolvedValue({ nx: 98, ny: 76, cached: false, forecast, ...(hourly ? { hourly } : {}) });

beforeEach(() => { mockApiRequest.mockReset(); mockDesktop = false; });

// 🔴 이 시험은 날짜를 2026-09-24 로 박았다 — 시계도 그날에 멈춘다(S15P21E201-1641). 날씨는 오늘부터 사흘 안만 부르므로
//    시계를 안 멈추면 그날이 지나 「지난 날짜」라 부르지 않고 이 시험이 실패한다. 한국·UTC(CI 러너) 어느 시각대로 돌려도
//    같은 날이 되게 한낮에 멈춘다. 멈추는 것은 날짜뿐이다 — 기다리기(setTimeout 등)는 진짜 시계 그대로 둔다.
const FROZEN_NOW = new Date('2026-09-24T12:00:00+09:00');
beforeAll(() => {
  jest.useFakeTimers({ now: FROZEN_NOW, doNotFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'setImmediate', 'clearImmediate', 'queueMicrotask', 'nextTick', 'requestAnimationFrame', 'cancelAnimationFrame', 'requestIdleCallback', 'cancelIdleCallback', 'hrtime', 'performance'] });
});
afterAll(() => { jest.useRealTimers(); });

// 첫 시험은 부품을 처음 불러오는 값(수 초)을 치른다 — 기본 1초 대기로는 그 사이에 끝나 버린다(로컬 실측 3.5초).
const WAIT = { timeout: 5000 };
// 🔴 기다림(WAIT 5초)보다 시험 제한 시간이 길어야 기다림이 먹힌다. jest 기본 제한 시간도 5초라, 부하가 걸리면 기다림이
//    끝나기 전에 시험이 먼저 끝났다(「Exceeded timeout of 5000 ms」 — S15P21E201-1665). 파일 전체에 넉넉히 준다.
jest.setTimeout(20000);

describe('날씨 창 — 1시간별 예보', () => {
  it('🔴 서버가 준 시각만 그린다 — 오늘처럼 세 칸만 오면 세 칸이다', async () => {
    respond([hour('21:00'), hour('22:00'), hour('23:00')]);
    render(<TripWeatherPanel date="2026-09-24" />, { wrapper: Providers });

    expect(await screen.findByText('1시간별 예보', {}, WAIT)).toBeTruthy();
    expect(screen.getAllByText(/^\d+시$/).map((node) => node.props.children)).toEqual(['21시', '22시', '23시']);
  });

  it('🔴 모르는 값(null)을 0 으로 그리지 않는다', async () => {
    respond([hour('09:00', { temperature: null, skyCondition: null, precipitationProbability: null, precipitationType: null })]);
    render(<TripWeatherPanel date="2026-09-24" />, { wrapper: Providers });

    expect(await screen.findByText('9시', {}, WAIT)).toBeTruthy();
    expect(screen.queryByText('0°')).toBeNull();
    expect(screen.queryByText('0%')).toBeNull();
    // 기온 · 하늘 · 강수확률 세 자리 모두 「모름」 표시다.
    expect(screen.getAllByText('—')).toHaveLength(3);
  });

  it('비가 오면 하늘보다 비를 그리고, 낭독기에도 그렇게 읽힌다', async () => {
    respond([hour('15:00', { skyCondition: 'CLOUDY', precipitationType: 'RAIN', precipitationProbability: 60, temperature: 20.6 })]);
    render(<TripWeatherPanel date="2026-09-24" />, { wrapper: Providers });

    expect(await screen.findByText('🌧', {}, WAIT)).toBeTruthy();
    expect(screen.getByText('21°')).toBeTruthy();
    expect(screen.getByLabelText('15시, 비, 21°, 강수확률 60%')).toBeTruthy();
  });

  it('옛 서버(시간별 칸이 없다)면 줄 자체를 안 그린다 — 하루 요약은 그대로', async () => {
    respond();
    render(<TripWeatherPanel date="2026-09-24" />, { wrapper: Providers });

    expect(await screen.findByText('강수확률 30%', {}, WAIT)).toBeTruthy();
    expect(screen.queryByText('1시간별 예보')).toBeNull();
  });

  it('넓은 화면(서랍)에서도 같은 칸을 그린다 — 격자로 놓일 뿐 시각은 그대로', async () => {
    mockDesktop = true;
    respond([hour('08:00'), hour('09:00')]);
    render(<TripWeatherPanel date="2026-09-24" />, { wrapper: Providers });

    expect(await screen.findByText('8시', {}, WAIT)).toBeTruthy();
    expect(screen.getByText('9시')).toBeTruthy();
  });
});
