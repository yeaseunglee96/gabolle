// 푸시 토큰 등록·해제 — 서버 계약(1391)을 앱이 지키는가. S15P21E201-1429.
import { Platform } from 'react-native';

const mockApiRequest = jest.fn((..._args: unknown[]) => Promise.resolve(undefined));
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: (...args: unknown[]) => mockApiRequest(...args) }));
let mockPermission = 'granted';
const mockGetToken = jest.fn((..._args: unknown[]) => Promise.resolve({ data: 'ExponentPushToken[abc]' }));
jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(async () => ({ status: mockPermission })),
  getExpoPushTokenAsync: (...args: unknown[]) => mockGetToken(...args),
  setNotificationChannelAsync: jest.fn(async () => {}),
  setNotificationHandler: jest.fn(),
  getLastNotificationResponseAsync: jest.fn(async () => null),
  addNotificationResponseReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  AndroidImportance: { HIGH: 4 },
}));
jest.mock('expo-constants', () => ({ __esModule: true, default: { expoConfig: { extra: { eas: { projectId: 'proj-1' } } } } }));
jest.mock('@react-native-async-storage/async-storage', () => { let v: string | null = null; return { getItem: jest.fn(async () => v), setItem: jest.fn(async (_: string, x: string) => { v = x; }), removeItem: jest.fn(async () => { v = null; }) }; });

import { hrefFromNotification, registerPushToken, unregisterPushToken } from '@/notifications/pushToken';

const originalOs = Platform.OS;
beforeEach(() => { mockApiRequest.mockClear(); mockGetToken.mockClear(); mockPermission = 'granted'; Object.defineProperty(Platform, 'OS', { value: 'android', configurable: true }); });
afterAll(() => Object.defineProperty(Platform, 'OS', { value: originalOs, configurable: true }));

describe('등록', () => {
  it('권한이 있으면 EAS projectId 로 토큰을 받아 PUT /me/push-tokens { token, platform }', async () => {
    expect(await registerPushToken('tok')).toBe('registered');
    expect(mockGetToken).toHaveBeenCalledWith({ projectId: 'proj-1' });
    expect(mockApiRequest).toHaveBeenCalledWith('/api/v1/me/push-tokens', expect.objectContaining({ method: 'PUT', body: { token: 'ExponentPushToken[abc]', platform: 'android' } }));
  });

  it('권한이 없으면 묻지 않고 건너뛴다 — 묻는 자리는 첫 실행과 설정 「알림」이다', async () => {
    mockPermission = 'denied';
    expect(await registerPushToken('tok')).toBe('skipped');
    expect(mockGetToken).not.toHaveBeenCalled();
    expect(mockApiRequest).not.toHaveBeenCalled();
  });

  it('웹은 아무것도 안 한다', async () => {
    Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
    expect(await registerPushToken('tok')).toBe('skipped');
    expect(mockApiRequest).not.toHaveBeenCalled();
  });

  it('서버가 실패해도 던지지 않는다 — 알림은 로그인의 조건이 아니다', async () => {
    mockApiRequest.mockRejectedValueOnce(new Error('boom'));
    await expect(registerPushToken('tok')).resolves.toBe('skipped');
  });
});

describe('해제', () => {
  it('등록해 둔 토큰을 DELETE /me/push-tokens/{token} 하고 기기에서 지운다', async () => {
    await registerPushToken('tok');
    mockApiRequest.mockClear();
    await unregisterPushToken('tok');
    expect(mockApiRequest).toHaveBeenCalledWith('/api/v1/me/push-tokens/ExponentPushToken%5Babc%5D', expect.objectContaining({ method: 'DELETE' }));
    mockApiRequest.mockClear();
    await unregisterPushToken('tok');
    expect(mockApiRequest).not.toHaveBeenCalled();
  });
});

describe('알림 눌러 열기', () => {
  it('data.href 가 앱 안 주소일 때만 쓴다', () => {
    const wrap = (data: Record<string, unknown> | null) => ({ notification: { request: { content: { data } } } });
    expect(hrefFromNotification(wrap({ href: '/trips/t1/itinerary' }))).toBe('/trips/t1/itinerary');
    expect(hrefFromNotification(wrap({ href: 'https://evil.example' }))).toBeNull();
    expect(hrefFromNotification(wrap({ href: '//evil.example' }))).toBeNull();
    expect(hrefFromNotification(wrap(null))).toBeNull();
    expect(hrefFromNotification(null)).toBeNull();
  });
});
