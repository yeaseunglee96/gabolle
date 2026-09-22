import { useCallback } from 'react';
import { useFocusEffect, useRouter, type Href } from 'expo-router';

/**
 * 조건이 참이면 다른 화면으로 보낸다 — 단, <b>이 화면을 보고 있을 때만</b>.
 * .
 */
export function useFocusedRedirect(shouldRedirect: boolean, href: Href) {
  const router = useRouter();
  useFocusEffect(
    useCallback(() => {
      if (shouldRedirect) router.replace(href);
    }, [shouldRedirect, href, router]),
  );
}
