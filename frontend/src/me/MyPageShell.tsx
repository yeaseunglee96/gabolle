// 마이페이지 하위 화면 다섯이 함께 쓰는 껍데기.
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { countAnswered, loadAccountPreferences, PREFERENCE_TOTAL, PREFERENCES_KEY } from '@/preferences/accountPreferences';
import { getUserProfile } from '@/social/stories';

export type MyPageTab = 'profile' | 'preferences' | 'posts' | 'saved' | 'identities' | 'blocked' | 'terms';

const TABS: Array<{ key: MyPageTab; path: string; ko: string; en: string }> = [
  { key: 'profile', path: '/me/profile', ko: '프로필', en: 'Profile' },
  { key: 'preferences', path: '/me/preferences', ko: '여행 취향', en: 'Travel preferences' },
  { key: 'posts', path: '/me/posts', ko: '내 기록', en: 'My records' },
  // 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게" —.
  { key: 'saved', path: '/me/saved', ko: '저장한 기록', en: 'Saved records' },
  { key: 'identities', path: '/me/identities', ko: '연결된 소셜 계정', en: 'Connected accounts' },
  // — 알림·차단된 계정처럼 스토어 심사가 보는 기본 기능이 마이페이지에
  // 없었다(사용자 리포트). 알림은 화면이 이미 있다(app/notifications.tsx, 홈 종 아이콘)
  // 여기서는 새로 만들지 않고 같은 화면으로 가는 입구만 하나 더 둔다.
  { key: 'blocked', path: '/me/blocked', ko: '차단된 계정', en: 'Blocked accounts' },
  { key: 'terms', path: '/me/terms', ko: '약관·고지', en: 'Terms & notices' },
];

/** 메뉴 오른쪽 작은 글자. 받아오지 못한 값은 빈 문자열로 둔다 (지어내지 않는다). */
export function useMyPageCounts() {
  const { accessToken, user } = useAuth();
  const preferencesQuery = useQuery({
    // me.tsx · preferences.tsx 와 같은 열쇠를 쓴다. 따로 읽으면 한쪽만 새로 읽혀
    // 메뉴의 숫자와 화면의 목록이 어긋난다.
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

function SideMenu({ tab }: { tab: MyPageTab }) {
  const router = useRouter();
  const { tx } = useI18n();
  const { user, signOut } = useAuth();
  const { answeredPreferences, storyCount } = useMyPageCounts();
  const name = user?.displayName || tx('여행자', 'Traveler');
  const meta: Partial<Record<MyPageTab, string>> = {
    preferences: answeredPreferences === null ? '' : `${answeredPreferences} / ${PREFERENCE_TOTAL}`,
    posts: storyCount === null ? '' : tx(`${storyCount}개`, `${storyCount}`),
  };
  return (
    <View style={styles.aside}>
      <View style={styles.asideUser}>
        <View style={styles.asideAvatar}>
          <Text weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>
        </View>
        <View style={styles.asideUserCopy}>
          <Text weight="bold" numberOfLines={1}>{name}</Text>
          <Text variant="caption" numberOfLines={1}>{user?.email ?? ''}</Text>
        </View>
      </View>
      <Text variant="eyebrow" weight="bold" style={styles.asideEyebrow}>{tx('내 계정', 'Account')}</Text>
      {TABS.map((item) => {
        const active = item.key === tab;
        return (
          <Pressable
            key={item.key}
            accessibilityRole="link"
            accessibilityState={{ selected: active }}
            onPress={() => router.replace(item.path)}
            style={({ pressed }) => [styles.asideItem, active && styles.asideItemActive, pressed && !active && styles.asideItemPressed]}
          >
            <Text weight={active ? 'bold' : 'medium'} color={active ? color.text.onAction : color.text.body}>{tx(item.ko, item.en)}</Text>
            <Text variant="caption" weight="medium" color={active ? color.text.onDarkMuted : color.text.muted}>{meta[item.key] ?? ''}</Text>
          </Pressable>
        );
      })}
      <Pressable accessibilityRole="button" onPress={() => void signOut()} style={({ pressed }) => [styles.asideItem, styles.asideLogout, pressed && styles.asideItemPressed]}>
        <Text weight="medium" color={color.text.body}>{tx('로그아웃', 'Sign out')}</Text>
      </Pressable>
    </View>
  );
}

type MyPageShellProps = {
  tab: MyPageTab;
  title: string;
  description?: string;
  children: ReactNode;
};

export function MyPageShell({ tab, title, description, children }: MyPageShellProps) {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useWindowDimensions();
  const desktop = isAtLeast(width, 'lg');

  const heading = (
    <View style={styles.heading}>
      <Text variant="display" weight="bold">{title}</Text>
      {description ? <Text>{description}</Text> : null}
    </View>
  );

  if (!desktop) {
    return (
      <Screen scroll>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('뒤로 가기', 'Go back')}
          onPress={() => (router.canGoBack() ? router.back() : router.replace('/me'))}
          style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
        >
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="eyebrow" weight="bold">{tx('내 계정', 'Account')}</Text>
        {heading}
        {children}
      </Screen>
    );
  }

  return (
    <Screen scroll wide>
      <View style={styles.deskRow}>
        <SideMenu tab={tab} />
        <View style={styles.deskMain}>
          {heading}
          {children}
        </View>
      </View>
    </Screen>
  );
}

const ASIDE_WIDTH = 240;
const MAIN_WIDTH = 720;

const styles = StyleSheet.create({
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginBottom: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card },
  backPressed: { opacity: 0.7 },
  heading: { gap: spacing[1], marginBottom: spacing[4] },

  deskRow: { flexDirection: 'row', gap: spacing[8] + spacing[4], justifyContent: 'center' },
  deskMain: { flex: 1, maxWidth: MAIN_WIDTH, minWidth: 0 },

  aside: { width: ASIDE_WIDTH, gap: spacing[1] },
  asideUser: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingHorizontal: spacing[3], paddingBottom: spacing[4] },
  asideAvatar: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  asideUserCopy: { flex: 1, minWidth: 0 },
  asideEyebrow: { paddingHorizontal: spacing[3], paddingBottom: spacing[2] },
  asideItem: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.sm },
  asideItemActive: { backgroundColor: color.brand.navy },
  asideItemPressed: { backgroundColor: color.surface.tint },
  asideLogout: { marginTop: spacing[4] },
});
