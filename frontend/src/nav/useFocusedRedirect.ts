import { useCallback } from 'react';
import { useFocusEffect, useRouter, type Href } from 'expo-router';

/**
 * 조건이 참이면 다른 화면으로 보낸다 — 단, <b>이 화면을 보고 있을 때만</b>.
 * S15P21E201-1128.
 *
 * <h2>🔴 화면 밖으로 밀려난 화면은 내비게이션을 조종하면 안 된다</h2>
 *
 * expo-router 에서 {@code push} 로 다음 화면에 가면 앞 화면은 <b>사라지지 않고 밑에
 * 남는다.</b> 그래서 앞 화면의 {@code useEffect} 는 그 뒤에도 계속 돈다. 그 안에
 * {@code router.replace} 가 들어 있으면, 사용자가 지금 보고 있는 화면을 <b>엉뚱한
 * 화면이 끌어내린다.</b>
 *
 * <p>실제로 그렇게 됐다. 여행 만들기 마지막 화면(confirm)에 이 가드가 있었다.
 *
 * <pre>
 * useEffect(() => { if (!basicComplete) router.replace('/plan/basic'); }, [basicComplete]);
 * </pre>
 *
 * <p>그리고 일정이 완성되면 생성 화면(generating)이 {@code clear()} 로 입력을 비운다.
 * 비우는 순간 {@code basicComplete} 가 거짓이 되고, <b>밑에 남아 있던 confirm 이</b>
 * 위 줄을 실행해 사용자를 1단계 빈 화면으로 끌어내린다.
 *
 * <p>사용자 눈에는 「일정 만들기를 눌렀더니 처음으로 돌아가고 입력이 다 사라졌다」로
 * 보인다. 서버는 여행도 일정도 정상으로 만들어 놨는데도 그렇다(2026-09-16 안드로이드
 * 실기기에서 여행 201 · 일정 200 을 확인하고도 화면은 1단계였다).
 *
 * <p>{@code useFocusEffect} 는 <b>보고 있는 동안에만</b> 돈다. 조건이 보고 있지 않을 때
 * 바뀌면 아무 일도 일어나지 않고, 다시 그 화면으로 돌아왔을 때 판정한다 — 그게 원래
 * 하려던 일이다.
 */
export function useFocusedRedirect(shouldRedirect: boolean, href: Href) {
  const router = useRouter();
  useFocusEffect(
    useCallback(() => {
      if (shouldRedirect) router.replace(href);
    }, [shouldRedirect, href, router]),
  );
}
