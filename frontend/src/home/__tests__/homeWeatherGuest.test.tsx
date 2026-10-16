// 게스트(가입 안 한 사람)에게도 홈 날씨를 불러온다 — S15P21E201-2060 (2026-10-16 웹 점검: 「게스트 홈에 날씨 없음」).
//
// 🔴 전에는 코드 주석이 「날씨도 스토리와 같다 — 익명으로는 401 이다」라 로그인한 사람에게만 불렀다. 그런데 서버는 익명 출입증에도 날씨를
//    준다(WeatherController: 익명은 미리 받아 둔 예보까지만 — 호출 한도를 태울 길이 없다). 그래서 넓은 화면 상단 바(TopNav)의 날씨 칸이
//    게스트에게는 늘 비어 있었다.
//
// 🔴 지키는 것
//   ① 게스트도 로그인 복원이 끝나면 한 번 부른다(접속 표 없이). 복원 중에는 안 부른다 — 로그인한 사람이 손님 몫으로 한 번 더 부르게 되니까.
//   ② 서버가 «아직 준비 안 됨»(404)이면 칩만 빠진다 — 화면이 깨지지 않는다.
//   ③ 로그인한 사람은 전과 같다(표를 들고 부른다). 손님으로 받아 둔 답이 로그인 뒤에 남지 않게 열쇠가 갈린다.
//   ④ enabled=false(홈이 아닌 화면의 상단 바)면 안 부른다.
import type { ReactNode } from 'react';
import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { loadWeatherForecast } from '@/trip/weather';

let mockAuth: { accessToken: string | null; ready: boolean } = { accessToken: null, ready: true };
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));
jest.mock('@/trip/weather', () => ({ ...jest.requireActual('@/trip/weather'), loadWeatherForecast: jest.fn() }));

import { useHomeWeather } from '@/home/useHomeData';

const mockedLoad = loadWeatherForecast as jest.Mock;

const forecast = { date: '2026-10-16', minTemperature: 17, maxTemperature: 25, precipitationProbability: 20, skyCondition: 'CLEAR' as const };
const success = { state: 'success', forecast, hourly: [], current: { temperature: 19.4, time: '09:00' } };

const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>{children}</QueryClientProvider>
);

beforeEach(() => {
  mockedLoad.mockReset();
  mockedLoad.mockResolvedValue(success);
  mockAuth = { accessToken: null, ready: true };
});

describe('게스트 홈 날씨', () => {
  it('🔴 게스트도 로그인 복원이 끝나면 날씨를 받는다 — 접속 표 없이 한 번', async () => {
    const { result } = renderHook(() => useHomeWeather(true), { wrapper });

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockedLoad).toHaveBeenCalledTimes(1);
    expect(mockedLoad.mock.calls[0][1]).toBeNull();
    expect(result.current).toMatchObject({ maxTemperature: 25, skyCondition: 'CLEAR', current: { temperature: 19.4 } });
  });

  it('🔴 복원 중(ready 전)에는 안 부르고, 끝나면 그때 한 번만 부른다', async () => {
    mockAuth = { accessToken: null, ready: false };
    const { result, rerender } = renderHook(() => useHomeWeather(true), { wrapper });
    await Promise.resolve();
    expect(mockedLoad).not.toHaveBeenCalled();
    expect(result.current).toBeNull();

    mockAuth = { accessToken: null, ready: true };
    rerender({});

    await waitFor(() => expect(mockedLoad).toHaveBeenCalledTimes(1));
  });

  it('🔴 서버가 «아직 준비 안 됨»(404)이면 칩만 빠진다 — null 이지 오류가 아니다', async () => {
    mockedLoad.mockResolvedValue({ state: 'unavailable', message: '지금은 이 정보를 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.' });
    const { result } = renderHook(() => useHomeWeather(true), { wrapper });

    await waitFor(() => expect(mockedLoad).toHaveBeenCalledTimes(1));
    expect(result.current).toBeNull();
  });
});

describe('로그인한 사람 · 꺼 둔 자리', () => {
  it('로그인한 사람은 전과 같다 — 표를 들고 부른다', async () => {
    mockAuth = { accessToken: 'member-token', ready: true };
    const { result } = renderHook(() => useHomeWeather(true), { wrapper });

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockedLoad).toHaveBeenCalledTimes(1);
    expect(mockedLoad.mock.calls[0][1]).toBe('member-token');
  });

  it('🔴 손님으로 받은 답이 로그인한 뒤에 남지 않는다 — 열쇠가 갈려 로그인한 몫으로 다시 받는다', async () => {
    mockedLoad.mockResolvedValueOnce({ state: 'unavailable', message: 'x' });
    const { result, rerender } = renderHook(() => useHomeWeather(true), { wrapper });
    await waitFor(() => expect(mockedLoad).toHaveBeenCalledTimes(1));
    expect(result.current).toBeNull();

    mockAuth = { accessToken: 'member-token', ready: true };
    rerender({});

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockedLoad).toHaveBeenCalledTimes(2);
    expect(mockedLoad.mock.calls[1][1]).toBe('member-token');
  });

  it('enabled=false(홈이 아닌 화면의 상단 바)면 아무도 안 부른다', async () => {
    const { result } = renderHook(() => useHomeWeather(false), { wrapper });
    await Promise.resolve();

    expect(mockedLoad).not.toHaveBeenCalled();
    expect(result.current).toBeNull();
  });
});
