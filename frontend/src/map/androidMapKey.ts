// 안드로이드 지도 키가 이 빌드에 들어갔는가 — S15P21E201-1140.
//
// 🔴 **이 판정만 따로 떼어 놓는다.** 판정 자체는 값 하나를 보는 간단한 일인데, 그게
//    `RouteMap.native.tsx` 안에 있으면 시험을 돌릴 수 없다 — 그 파일은 `react-native-maps`
//    를 불러오고, 그건 시험 환경에서 안 도는 네이티브 꾸러미다.
//
// 🔴 이 판정이 틀리면 **둘 다 나쁘다.** 키가 있는데 없다고 하면 멀쩡한 지도 대신 안내문이
//    뜨고, 키가 없는데 있다고 하면 회색 네모가 뜬다. 회색 네모는 사용자에게 "앱이 고장났다"
//    로 읽힌다. 그래서 잰다.
import { Platform } from 'react-native';
import Constants from 'expo-constants';

type AndroidConfig = { config?: { googleMaps?: { apiKey?: unknown } } };

/**
 * 설정에서 안드로이드 지도 키를 읽는다. 없으면 null.
 *
 * 🔴 공백만 있는 값은 **없는 것으로 친다.** 환경 변수를 비워 두면 빈 문자열이 아니라
 * 공백 한 칸이 들어가는 일이 흔하고, 그 값으로는 지도가 안 그려진다.
 */
export function readAndroidMapKey(android: unknown): string | null {
  const key = (android as AndroidConfig | undefined)?.config?.googleMaps?.apiKey;
  if (typeof key !== 'string') return null;
  const trimmed = key.trim();
  return trimmed ? trimmed : null;
}

/**
 * 지도 대신 안내문을 띄워야 하는가.
 *
 * 🔴 **iOS 는 언제나 false 다** — 애플 지도를 쓰므로 키가 필요 없다. 여기서 플랫폼을 안
 * 가르면 아이폰에서도 「키가 없어요」가 떠서, 잘 되는 지도를 안 보여주게 된다.
 * 웹은 이 파일을 안 쓴다(웹은 카카오 지도를 쓰는 `RouteMap.tsx` 가 맡는다).
 */
export function shouldShowMissingKeyNotice(platform: string, android: unknown): boolean {
  if (platform !== 'android') return false;
  return readAndroidMapKey(android) === null;
}

/** 지금 이 기기·이 빌드 기준의 판정. */
export function androidMapKeyMissing(): boolean {
  return shouldShowMissingKeyNotice(Platform.OS, Constants.expoConfig?.android);
}
