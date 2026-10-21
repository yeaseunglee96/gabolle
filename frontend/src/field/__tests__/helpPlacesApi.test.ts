// 가까운 도움 서버 조회 — S15P21E201-1894.
import { apiRequest } from '@/api/client';

import { getNearbyHelp, HELP_TIMEOUT_MS } from '../helpPlacesApi';
import { openLine } from '../../../app/nearby-help';

jest.mock('@/api/client', () => ({ apiRequest: jest.fn() }));
// 화면 파일을 불러오기만 한다 — 화면이 쓰는 무거운 부품은 비운다
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('expo-location', () => ({}));

const mocked = apiRequest as jest.MockedFunction<typeof apiRequest>;
const ko = (text: string) => text;

describe('가까운 도움 서버 조회', () => {
  beforeEach(() => mocked.mockReset());

  it('🔴 좌표는 약 100m 단위로 줄여서 보낸다 — 위치 동의의 약속', async () => {
    mocked.mockResolvedValue({ kind: 'PHARMACY', places: [], source: '', basedOn: '' });
    await getNearbyHelp('pharmacy', { latitude: 35.153249, longitude: 129.118612 }, 't');
    const [path, options] = mocked.mock.calls[0];
    expect(path).toContain('kind=PHARMACY');
    expect(path).not.toContain('35.153249');
    expect(path).not.toContain('129.118612');
    expect(options).toMatchObject({ timeoutMs: HELP_TIMEOUT_MS });
  });

  it('🔴 못 받으면 null — 화면이 앱 자료로 물러선다(급할 때 빈 화면을 안 보인다)', async () => {
    mocked.mockRejectedValue(new Error('offline'));
    expect(await getNearbyHelp('hospital', { latitude: 35.15, longitude: 129.11 }, null)).toBeNull();
    mocked.mockResolvedValue({ unexpected: true });
    expect(await getNearbyHelp('hospital', { latitude: 35.15, longitude: 129.11 }, null)).toBeNull();
  });

  it('🔴 다시 묻기가 길어져도 6초면 포기한다 — 503 재시도로 「찾고 있어요…」에 멈추던 것', async () => {
    jest.useFakeTimers();
    try {
      mocked.mockReturnValue(new Promise(() => {}));
      const pending = getNearbyHelp('pharmacy', { latitude: 35.15, longitude: 129.11 }, null);
      jest.advanceTimersByTime(HELP_TIMEOUT_MS);
      await expect(pending).resolves.toBeNull();
    } finally {
      jest.useRealTimers();
    }
  });
});

describe('진료 상태 한 줄', () => {
  it('지금 진료 중이면 닫는 시각까지, 시간 밖이면 오늘 시간, 쉬는 날이면 휴진, 모르면 안 적는다', () => {
    expect(openLine({ openNow: true, todayOpen: '09:00', todayClose: '18:30' }, ko)).toEqual({ text: '지금 진료 중 · 18:30까지', open: true });
    expect(openLine({ openNow: false, todayOpen: '09:00', todayClose: '18:30' }, ko)).toEqual({ text: '지금은 진료 시간이 아니에요 · 오늘 09:00–18:30', open: false });
    expect(openLine({ openNow: false, todayOpen: null, todayClose: null }, ko)).toEqual({ text: '오늘 휴진', open: false });
    expect(openLine({ openNow: null, todayOpen: null, todayClose: null }, ko)).toBeNull();
  });
});
