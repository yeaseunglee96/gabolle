// 3D 도시 화면으로 가는 문.
//
// 3D 화면은 **웹 페이지 한 장**이다 (S15P21E201-649). 앱 안에 3D 엔진을 넣지 않는다 —
// 그러면 개발용 앱을 따로 빌드해야 하고 지금 도는 웹 빌드 검사가 깨진다.
// 대신 iOS·안드로이드에서는 **앱 안 브라우저 탭**(앱 위에 덮여 열리고 닫으면 앱으로
// 돌아오는 브라우저)으로 그 페이지를 띄우고, 웹에서는 새 탭으로 연다.
//
// 🔴 새 부품을 하나도 안 깐다. `expo-web-browser` 는 소셜 로그인이 이미 쓰고 있다.
import { Platform } from 'react-native';
import * as WebBrowser from 'expo-web-browser';

import type { MapStop } from './types';

// 3D 화면이 실제로 올라가 있는 자리. 앱·웹과 **같은 호스트**라 웹에서는 같은 출처다.
//
// 🔴 비밀값이 아니라 배포 주소라서 여기 적는다 — frontend/Jenkinsfile 이
//    OAUTH_CALLBACK_BASE_URL 을 같은 이유로 파일에 그대로 적어 둔 것과 같은 결이다.
//    자리가 바뀌면 고칠 곳은 이 한 줄이다.
export const CITY3D_BASE_URL = 'https://j15e201.p.ssafy.io/city3d/';

/**
 * 3D 화면이 이 좌표들을 보게 하는 주소를 만든다.
 *
 * `?course=경도,위도;경도,위도;…` 는 3D 화면의 주소 손잡이다 (S15P21E201-734).
 * 손잡이를 안 주면 3D 화면은 자기 기본 화면(광안대교 시점)을 띄운다.
 *
 * 🔴 소수점 6자리에서 자른다. 위도 1e-6 은 부산에서 약 11 cm 라 그 아래는 의미가 없고,
 *    주소만 길어진다.
 */
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
