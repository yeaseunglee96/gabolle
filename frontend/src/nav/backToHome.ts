// 안드로이드 뒤로 가기 — 갈 곳이 없으면 앱을 닫지 말고 홈으로 (S15P21E201-1752).
//
// 🔴 탭 이동은 router.replace 다(TabBar.tsx — 탭바를 화면마다 그리는 Stack 하나라서).
//    그래서 피드·여행 만들기·내 여행·마이페이지에는 뒤에 쌓인 화면이 없고, 뒤로 가기가 곧
//    앱 종료였다. Play 35 실기기에서 「여행 만들기」에서 뒤로 가기 → 앱이 닫혔다.
//    안드로이드 관례는 「홈이 아닌 탭 → 홈, 홈에서 한 번 더 → 닫기」다.
//
//    알림으로 연 상세 화면처럼 스택이 비어 있는 다른 화면도 같은 규칙을 탄다 — 닫히는 것보다 홈이 낫다.
//    첫 화면(`/`, 아직 앱에 들어오기 전)과 홈에서는 지금처럼 닫힌다.
import { useEffect, useRef } from 'react';
import { BackHandler, Platform } from 'react-native';
import { usePathname, useRouter } from 'expo-router';

const EXIT_PATHS = new Set(['/', '/home']);

/** 뒤로 가기를 홈으로 돌릴지. true 면 이 손짓은 우리가 먹는다. */
export function shouldGoHomeOnBack(pathname: string, canGoBack: boolean): boolean {
  if (canGoBack) return false;
  return !EXIT_PATHS.has(pathname);
}

/** 앱 뼈대에 한 번 붙인다. 먼저 붙으므로 화면·시트가 붙인 뒤로 가기가 언제나 먼저 받는다. */
export function AndroidBackToHome() {
  const router = useRouter();
  const pathname = usePathname();
  // 🔴 경로는 ref 로 읽고 등록은 한 번만 한다. 경로가 바뀔 때마다 다시 붙이면 이 처리기가 가장
  //    최근 것이 되어, 화면이 먼저 붙여 둔 뒤로 가기(시트 닫기 등)보다 앞서 받는다.
  const pathRef = useRef(pathname);
  pathRef.current = pathname;
  const routerRef = useRef(router);
  routerRef.current = router;
  useEffect(() => {
    if (Platform.OS !== 'android') return undefined;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!shouldGoHomeOnBack(pathRef.current, routerRef.current.canGoBack())) return false;
      routerRef.current.replace('/home');
      return true;
    });
    return () => sub.remove();
  }, []);
  return null;
}
