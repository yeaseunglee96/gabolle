// 머리말 날씨는 `[ 하늘 지금 N° ]` 이다 — S15P21E201-1981.
//
// 🔴 이 시험이 지키는 것: 머리말이 「부산 지금」이라고 쓰면서 그날 예보의 최고기온을 보여 줬다(10/3 탭 점검:
//    로그아웃 23° / 로그인 19°). 서버가 지금 기온(currentTemperature)을 주면 그 기온과 그 시각 칸의 하늘을,
//    못 주면 `[ 하늘 오늘 최고 N° ]` 를 보인다. 최고기온에 「지금」을 붙이지 않는다. 밤(19~05시) 맑음은 달이다.
import { render, screen } from '@testing-library/react-native';

import { apiRequest } from '@/api/client';
import { HeaderWeatherText } from '@/home/HomeBlocks';
import { HOME_WEATHER_REFETCH_MS, HOME_WEATHER_STALE_MS } from '@/home/useHomeData';
import { headerWeather, loadWeatherForecast, slotAt, type HourlyForecastDto } from '@/trip/weather';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
const request = jest.mocked(apiRequest);

const todayKey = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const forecast = { date: '2026-10-05', minTemperature: 15, maxTemperature: 23, precipitationProbability: 0, skyCondition: 'CLOUDY' as const };
const slot = (time: string, skyCondition: HourlyForecastDto['skyCondition'], precipitationType: HourlyForecastDto['precipitationType'] = 'NONE'): HourlyForecastDto =>
  ({ time, temperature: 19, skyCondition, precipitationProbability: 0, precipitationType });
const hourly = [slot('13:00', 'PARTLY_CLOUDY'), slot('14:00', 'CLEAR'), slot('22:00', 'CLEAR')];

beforeEach(() => request.mockReset());

describe('지금 기온 받기', () => {
  it('서버가 주는 currentTemperature·currentTime 을 그대로 담는다', async () => {
    request.mockResolvedValue({ forecast: { ...forecast, date: todayKey() }, hourly: [], currentTemperature: 19.4, currentTime: '21:00' });
    await expect(loadWeatherForecast(todayKey(), 'token')).resolves.toMatchObject({ state: 'success', current: { temperature: 19.4, time: '21:00' } });
  });

  it('옛 서버(칸 없음)·null 이면 지금 기온은 null — 최고기온으로 채우지 않는다', async () => {
    request.mockResolvedValue({ forecast: { ...forecast, date: todayKey() }, hourly: [] });
    await expect(loadWeatherForecast(todayKey(), 'token')).resolves.toMatchObject({ state: 'success', current: null });
    request.mockResolvedValue({ forecast: { ...forecast, date: todayKey() }, hourly: [], currentTemperature: null, currentTime: null });
    await expect(loadWeatherForecast(todayKey(), 'token')).resolves.toMatchObject({ state: 'success', current: null });
  });
});

describe('머리말 날씨 고르기', () => {
  it('지금 기온이면 그 기온, 하늘은 하루 요약이 아니라 currentTime 칸의 하늘', () => {
    expect(headerWeather({ ...forecast, hourly, current: { temperature: 19.4, time: '14:00' } }, '14:10')).toEqual({ value: 19.4, kind: 'now', icon: '☀', sky: 'CLEAR' });
  });
  it('같은 시각 칸이 없으면 가장 가까운 칸의 하늘', () => {
    expect(slotAt(hourly, '13:20')?.time).toBe('13:00');
    expect(headerWeather({ ...forecast, hourly, current: { temperature: 18, time: '13:20' } }, '13:20')?.icon).toBe('⛅');
  });
  it('🔴 밤(19~05시) 맑음은 달 — 지금 칸도, 하루 요약도', () => {
    expect(headerWeather({ ...forecast, hourly, current: { temperature: 17, time: '22:00' } }, '22:05')?.icon).toBe('🌙');
    expect(headerWeather({ ...forecast, skyCondition: 'CLEAR', hourly: [], current: null }, '23:00')?.icon).toBe('🌙');
  });
  it('그 칸에 비가 오면 비 그림', () => {
    expect(headerWeather({ ...forecast, hourly: [slot('14:00', 'CLOUDY', 'RAIN')], current: { temperature: 16, time: '14:00' } }, '14:00')?.icon).toBe('🌧');
  });
  it('지금 기온이 없으면 오늘 최고(하루 요약 하늘), 그것도 없으면 오늘 최저, 다 없으면 null', () => {
    expect(headerWeather({ ...forecast, hourly, current: null }, '10:00')).toEqual({ value: 23, kind: 'high', icon: '☁', sky: 'CLOUDY' });
    expect(headerWeather({ ...forecast, maxTemperature: null, current: null }, '10:00')).toMatchObject({ value: 15, kind: 'low' });
    expect(headerWeather({ ...forecast, maxTemperature: null, minTemperature: null, current: null }, '10:00')).toBeNull();
  });
});

describe('머리말 그림 `[ 하늘 지금 N° ]`', () => {
  const at = (h: number) => { const d = new Date(); d.setHours(h, 0, 0, 0); return d; };
  it('지금 기온이면 「지금」', () => {
    render(<HeaderWeatherText forecast={{ ...forecast, hourly, current: { temperature: 19.4, time: '14:00' } }} now={at(14)} />);
    expect(screen.getByText('☀', { includeHiddenElements: true })).toBeTruthy();
    expect(screen.getByText('지금')).toBeTruthy();
    // 읽어 주는 말도 하루 요약(흐림)이 아니라 지금 칸의 하늘(맑음)이다.
    expect(screen.getByLabelText('맑음 · 지금 19°')).toBeTruthy();
    expect(screen.getByText('19°')).toBeTruthy();
  });
  it('🔴 최고기온이면 「지금」이라고 쓰지 않는다 — 「오늘 최고」', () => {
    render(<HeaderWeatherText forecast={{ ...forecast, hourly, current: null }} now={at(14)} />);
    expect(screen.queryByText('지금')).toBeNull();
    expect(screen.queryByText(/부산 지금/)).toBeNull();
    expect(screen.getByText('오늘 최고')).toBeTruthy();
    expect(screen.getByText('23°')).toBeTruthy();
  });
  it('좁으면 하늘과 기온만', () => {
    render(<HeaderWeatherText forecast={{ ...forecast, hourly, current: { temperature: 19.4, time: '14:00' } }} now={at(14)} compact />);
    expect(screen.queryByText('지금')).toBeNull();
    expect(screen.getByText('19°')).toBeTruthy();
  });
});

describe('🔴 지금 기온은 낡는다', () => {
  it('캐시는 10분 지나면 낡은 것, 15분마다 다시 받는다', () => {
    expect(HOME_WEATHER_STALE_MS).toBe(10 * 60 * 1000);
    expect(HOME_WEATHER_REFETCH_MS).toBe(15 * 60 * 1000);
  });
});
