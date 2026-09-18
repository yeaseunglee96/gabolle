// 로그인을 마치고 앱 안으로 들어갈 때 **쌓인 화면을 치우고** 간다 (S15P21E201-1199).
//
// 🔴 이 파일이 생긴 이유. `router.replace` 는 **맨 위 한 칸만** 바꾼다. 그래서 로그인
//    화면 위에 홈을 얹어도 **로그인 화면이 아래에 그대로 남고**, 홈에서 뒤로 가기를
//    누르면 그것이 다시 보인다.
//
//    앞서 두 번은 「로그인 화면이 스스로 비키게」 고쳤다(그리는 순간 → 포커스가
//    돌아오는 순간). 둘 다 **보인 다음에 치우는** 방법이라, 사람 눈에는 로그인
//    화면이 한 번 번쩍인다. 이번에는 **애초에 안 남게** 한다.
//
// 🔴 `dismissAll` 은 쌓인 것을 첫 칸까지 걷어낸다. 그다음 `replace` 로 그 한 칸을
//    목적지로 바꾸면 **뒤에 아무것도 없는 상태**가 된다 — 홈에서 뒤로 가기를 누르면
//    앱을 빠져나가는 원래 일을 한다.
//
// 🔴 걷어낼 것이 없을 때 `dismissAll` 을 부르면 안 된다. 그 경우를 `canDismiss` 가
//    말해 준다 — 안 물어보고 부르면 이동 자체가 죽는 판이 있다.
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
    // 🔴 치우기가 실패해도 **가기는 간다.** 여기서 던지면 로그인은 됐는데 화면이
    //    로그인 화면에 멈춰 서고, 사용자에게는 「로그인이 안 됐다」로 보인다.
  }
  router.replace(destination);
}
