// 푸시 알림의 앱 쪽 — S15P21E201-1429 (서버 계약은 1391, 재현 확정).
//
//   PUT    /api/v1/me/push-tokens            { token, platform: 'ios' | 'android' }   로그인 + 권한 허용일 때
//   DELETE /api/v1/me/push-tokens/{token}                                              로그아웃 때
//
// 서버가 실제로 보내는 코드는 아직 없다(2026-09-21). 그래도 앱이 먼저 토큰을 올려 두면, 보내는 쪽이
// 생기는 순간 알림이 온다. 웹은 아무것도 안 한다 — Expo 푸시는 기기 앱에만 있다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { apiRequest, ApiClientError } from '@/api/client';
import { color } from '@/design/tokens';

const STORAGE_KEY = 'gabolle:push-token';
const CHANNEL_ID = 'trip';

/** 앱이 앞에 떠 있을 때도 알림을 보여 준다 — 안 정하면 iOS 는 앞에 있을 때 조용히 삼킨다. */
export function installNotificationHandler() {
  if (Platform.OS === 'web') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
  });
}

async function ensureChannel() {
  if (Platform.OS !== 'android') return;
  // 안드로이드 8+ 는 채널이 없으면 알림이 안 뜬다. 사용자가 설정에서 보는 이름이라 「여행 알림」.
  await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
    name: '여행 알림',
    importance: Notifications.AndroidImportance.HIGH,
    lightColor: color.action.primary,
    vibrationPattern: [0, 200],
  });
}

function projectId(): string | undefined {
  return Constants.expoConfig?.extra?.eas?.projectId as string | undefined;
}

/**
 * 로그인 뒤 한 번. 권한이 허용돼 있어야 토큰이 나온다 — 여기서 묻지 않는다(첫 실행 00d 와 설정 「알림」이 묻는다).
 * 실패는 조용히 — 알림은 덤이지 로그인의 조건이 아니다.
 */
export async function registerPushToken(accessToken: string): Promise<'registered' | 'skipped'> {
  if (Platform.OS === 'web') return 'skipped';
  try {
    const permission = await Notifications.getPermissionsAsync();
    if (permission.status !== 'granted') return 'skipped';
    await ensureChannel();
    const id = projectId();
    const { data: token } = await Notifications.getExpoPushTokenAsync(id ? { projectId: id } : undefined);
    if (!token) return 'skipped';
    await apiRequest('/api/v1/me/push-tokens', { method: 'PUT', accessToken, body: { token, platform: Platform.OS === 'ios' ? 'ios' : 'android' } });
    await AsyncStorage.setItem(STORAGE_KEY, token);
    return 'registered';
  } catch {
    return 'skipped';
  }
}

/** 로그아웃 때 — 이 기기로 남의 알림이 오지 않게. 서버가 이미 모르는 토큰(404)이면 그것도 끝난 것이다. */
export async function unregisterPushToken(accessToken: string | null): Promise<void> {
  if (Platform.OS === 'web') return;
  let token: string | null = null;
  try { token = await AsyncStorage.getItem(STORAGE_KEY); } catch { token = null; }
  if (!token) return;
  try {
    if (accessToken) await apiRequest(`/api/v1/me/push-tokens/${encodeURIComponent(token)}`, { method: 'DELETE', accessToken });
  } catch (error) {
    if (!(error instanceof ApiClientError && error.status === 404)) { /* 다음 로그인 때 다시 등록되니 여기서 막지 않는다 */ }
  } finally {
    try { await AsyncStorage.removeItem(STORAGE_KEY); } catch { /* */ }
  }
}

/** 알림 본문의 data.href — 서버가 넣어 주는 앱 안 주소(예: /trips/123/itinerary). 없거나 이상하면 null. */
export function hrefFromNotification(response: { notification: { request: { content: { data?: Record<string, unknown> | null } } } } | null | undefined): string | null {
  const href = response?.notification?.request?.content?.data?.href;
  return typeof href === 'string' && href.startsWith('/') && !href.startsWith('//') ? href : null;
}

/** 알림을 눌러 앱이 열리면 그 주소로. 반환값으로 구독을 푼다. */
export function attachNotificationNavigation(navigate: (href: string) => void): () => void {
  if (Platform.OS === 'web') return () => {};
  // 앱이 꺼진 채 알림으로 켜졌을 때 — 마지막 응답이 남아 있다.
  void Notifications.getLastNotificationResponseAsync().then((response) => { const href = hrefFromNotification(response); if (href) navigate(href); }).catch(() => {});
  const sub = Notifications.addNotificationResponseReceivedListener((response) => { const href = hrefFromNotification(response); if (href) navigate(href); });
  return () => sub.remove();
}
