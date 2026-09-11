import { Linking, Platform } from 'react-native';

export type MapDestination = { name: string; latitude?: number | null; longitude?: number | null };
export type MapProviderKey = 'kakao' | 'google' | 'apple';

type MapProvider = { key: MapProviderKey; labelKo: string; labelEn: string; appUrl: (d: MapDestination) => string; webUrl: (d: MapDestination) => string };

const hasCoords = (d: MapDestination): d is MapDestination & { latitude: number; longitude: number } => d.latitude != null && d.longitude != null;

const PROVIDERS: MapProvider[] = [
  {
    key: 'kakao',
    labelKo: '카카오맵',
    labelEn: 'KakaoMap',
    appUrl: (d) => hasCoords(d) ? `kakaomap://look?p=${d.latitude},${d.longitude}` : `kakaomap://search?q=${encodeURIComponent(d.name)}`,
    webUrl: (d) => hasCoords(d) ? `https://map.kakao.com/link/map/${encodeURIComponent(d.name)},${d.latitude},${d.longitude}` : `https://map.kakao.com/link/search/${encodeURIComponent(d.name)}`,
  },
  {
    key: 'google',
    labelKo: '구글맵',
    labelEn: 'Google Maps',
    appUrl: (d) => hasCoords(d) ? `comgooglemaps://?q=${d.latitude},${d.longitude}&center=${d.latitude},${d.longitude}` : `comgooglemaps://?q=${encodeURIComponent(d.name)}`,
    webUrl: (d) => hasCoords(d) ? `https://www.google.com/maps/search/?api=1&query=${d.latitude},${d.longitude}` : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(d.name)}`,
  },
  {
    key: 'apple',
    labelKo: '애플맵',
    labelEn: 'Apple Maps',
    appUrl: (d) => hasCoords(d) ? `maps://?ll=${d.latitude},${d.longitude}&q=${encodeURIComponent(d.name)}` : `maps://?q=${encodeURIComponent(d.name)}`,
    webUrl: (d) => hasCoords(d) ? `https://maps.apple.com/?ll=${d.latitude},${d.longitude}&q=${encodeURIComponent(d.name)}` : `https://maps.apple.com/?q=${encodeURIComponent(d.name)}`,
  },
];

// 웹은 "설치된 앱" 개념이 없어 카카오맵·구글맵 웹 링크만 안내한다 (애플맵은 앱에서만 노출 — 티켓 조건).
export const WEB_PROVIDERS = PROVIDERS.filter((provider) => provider.key !== 'apple');

export type AvailableMapProvider = { key: MapProviderKey; labelKo: string; labelEn: string; open: () => Promise<void> };

export async function listAvailableMapApps(destination: MapDestination): Promise<AvailableMapProvider[]> {
  if (Platform.OS === 'web') {
    return WEB_PROVIDERS.map((provider) => ({ key: provider.key, labelKo: provider.labelKo, labelEn: provider.labelEn, open: () => Linking.openURL(provider.webUrl(destination)) }));
  }
  const candidates = Platform.OS === 'ios' ? PROVIDERS : PROVIDERS.filter((provider) => provider.key !== 'apple');
  const checked = await Promise.all(candidates.map(async (provider) => {
    const installed = await Linking.canOpenURL(provider.appUrl(destination)).catch(() => false);
    return { provider, installed };
  }));
  const installed = checked.filter((entry) => entry.installed).map((entry) => entry.provider);
  // 설치된 지도 앱이 하나도 없으면 웹 지도로 대신 연다 (완료 기준).
  const fallback = installed.length ? installed : [PROVIDERS[0]];
  const usedWebFallback = !installed.length;
  return fallback.map((provider) => ({
    key: provider.key,
    labelKo: provider.labelKo,
    labelEn: provider.labelEn,
    open: () => Linking.openURL(usedWebFallback ? provider.webUrl(destination) : provider.appUrl(destination)),
  }));
}
