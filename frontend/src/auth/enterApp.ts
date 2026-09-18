// 로그인을 마치고 앱 안으로 들어갈 때 쌓인 화면을 치우고 간다.
import type { Href } from 'expo-router';

export type StackRouter = {
  replace: (href: Href) => void;
  canDismiss?: () => boolean;
  dismissAll?: () => void;
};

export function enterApp(router: StackRouter, destination: Href) {
  try {
    if (router.canDismiss?.()) router.dismissAll?.();
  } catch {
    // 치우기가 실패해도 가기는 간다. 여기서 던지면 로그인은 됐는데 화면이
    // 로그인 화면에 멈춰 서고, 사용자에게는 「로그인이 안 됐다」로 보인다.
  }
  router.replace(destination);
}
