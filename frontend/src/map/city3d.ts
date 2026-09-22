// 3D 도시 화면으로 가는 문.
import { Platform } from 'react-native';
import * as WebBrowser from 'expo-web-browser';

import type { MapStop } from './types';

// 3D 화면이 실제로 올라가 있는 자리. 앱·웹과 같은 호스트라 웹에서는 같은 출처다.
export const CITY3D_BASE_URL = 'https://j15e201.p.ssafy.io/city3d/';

/** 3D 화면이 이 좌표들을 보게 하는 주소를 만든다. */
export function city3dUrlForStops(stops: MapStop[]): string {
  const course = stops
    .map((stop) => `${stop.longitude.toFixed(6)},${stop.latitude.toFixed(6)}`)
    .join(';');
  if (!course) return CITY3D_BASE_URL;
  return `${CITY3D_BASE_URL}?course=${encodeURIComponent(course)}`;
}

/** 3D 화면 한 장을 연다. 앱은 앱 안 브라우저 탭, 웹은 새 탭. */
export async function openCity3D(url: string): Promise<void> {
  if (Platform.OS === 'web') {
    // noopener 를 주면 새 탭이 이 탭을 조종하지 못한다.
    window.open(url, '_blank', 'noopener,noreferrer');
    return;
  }
  await WebBrowser.openBrowserAsync(url);
}
