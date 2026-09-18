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

// 경로로 열기 —/ 명세 4절.
export type RouteEnds = { originLat: number; originLng: number; destLat: number; destLng: number; destName: string };

const ROUTE_URLS: Record<MapProviderKey, { app: (r: RouteEnds) => string; web: (r: RouteEnds) => string }> = {
  kakao: {
    app: (r) => `kakaomap://route?sp=${r.originLat},${r.originLng}&ep=${r.destLat},${r.destLng}&by=PUBLICTRANSIT`,
    web: (r) => `https://map.kakao.com/link/to/${encodeURIComponent(r.destName)},${r.destLat},${r.destLng}`,
  },
  google: {
    app: (r) => `comgooglemaps://?saddr=${r.originLat},${r.originLng}&daddr=${r.destLat},${r.destLng}&directionsmode=transit`,
    web: (r) => `https://www.google.com/maps/dir/?api=1&origin=${r.originLat},${r.originLng}&destination=${r.destLat},${r.destLng}&travelmode=transit`,
  },
  apple: {
    app: (r) => `maps://?saddr=${r.originLat},${r.originLng}&daddr=${r.destLat},${r.destLng}&dirflg=r`,
    web: (r) => `https://maps.apple.com/?saddr=${r.originLat},${r.originLng}&daddr=${r.destLat},${r.destLng}&dirflg=r`,
  },
};

export function routeMapUrl(provider: MapProviderKey, route: RouteEnds, target: 'app' | 'web'): string {
  return ROUTE_URLS[provider][target](route);
}

/** 설치 여부 판정은 장소 열기와 같은 규칙을 쓴다 — 여는 주소만 경로용으로 바꾼다. */
export async function listAvailableRouteMapApps(route: RouteEnds): Promise<AvailableMapProvider[]> {
  const apps = await listAvailableMapApps({ name: route.destName, latitude: route.destLat, longitude: route.destLng });
  return apps.map((app) => ({
    ...app,
    // 앱 주소가 안 열리면(설치 판정이 빗나갔거나 형식을 못 받는 판) 웹 주소로 물러선다.
    open: () => Linking.openURL(routeMapUrl(app.key, route, Platform.OS === 'web' ? 'web' : 'app'))
      .catch(() => Linking.openURL(routeMapUrl(app.key, route, 'web'))),
  }));
}

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
