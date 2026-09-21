// 마이페이지가 쓰는 숫자 셋 — 기록·팔로워·팔로잉과 「취향 몇 개 답했나」.
//
// 🔴 -1331 — 원래 `MyPageShell` 안에 있었다. 그 껍데기(왼쪽 기둥 + 본문)는 마이페이지
//    하위 주소 일곱이 쓰던 것인데, 그 일곱을 없애면서 껍데기도 사라졌다. 숫자만 남긴다.
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { countAnswered, loadAccountPreferences, PREFERENCES_KEY } from '@/preferences/accountPreferences';
import { getUserProfile } from '@/social/stories';

/** 받아오지 못한 값은 `null` 로 둔다 — 지어내지 않는다. 화면이 그 자리를 접거나 「—」를 그린다. */
export function useMyPageCounts() {
  const { accessToken, user } = useAuth();
  const preferencesQuery = useQuery({
    // 🔴 취향 화면과 **같은 열쇠**를 쓴다. 따로 읽으면 한쪽만 새로 읽혀 메뉴의 숫자와
    //    화면의 목록이 어긋난다.
    queryKey: PREFERENCES_KEY,
    enabled: Boolean(accessToken),
    queryFn: () => loadAccountPreferences(accessToken),
  });
  const profileQuery = useQuery({
    queryKey: ['user-profile', user?.userId],
    enabled: Boolean(accessToken && user?.userId),
    queryFn: () => getUserProfile(user!.userId, accessToken),
  });
  const profile = profileQuery.data?.state === 'success' ? profileQuery.data.profile : null;
  return {
    answeredPreferences: preferencesQuery.data ? countAnswered(preferencesQuery.data) : null,
    storyCount: profile?.storyCount ?? null,
    followerCount: profile?.followerCount ?? null,
    followingCount: profile?.followingCount ?? null,
  };
}
