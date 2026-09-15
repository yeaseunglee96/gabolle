import { Pressable, StyleSheet, View } from 'react-native';
import { usePathname, useRouter } from 'expo-router';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';

// 🔴 이 내비는 여행 만들기 흐름 다섯 화면(basics · taste · constraints · confirm · generating)에
// (plan)/_layout 이 붙인다. 로그인 상태를 안 보고 "로그인" 버튼을 늘 그리고 있었고, 그래서
// 로그인한 사람이 여행 만들기로 들어가면 세션이 풀린 것처럼 보였다(S15P21E201-763).
// 세션이 실제로 끊긴 적은 없다 — 이 화면들은 API 를 부르지 않는다.
//
// `ready` 를 함께 보는 이유: 앱이 뜰 때 저장된 세션을 되살리는 동안에는 accessToken 이
// 잠시 null 이다. 그때 버튼을 그리면 로그인한 사람에게 "로그인" 이 한 번 번쩍인다.
//
// 🔴 활성 항목을 **라우트로 정한다** (S15P21E201-968). 전에는 `item.path === '/plan/basic'`
// 이라고 박혀 있어서, 어느 화면에 있든 「여행 만들기」가 현재 위치처럼 칠해졌다 — 상단 바가
// 자기 위치를 거짓으로 알려주고 있었다.

const LINKS = [
  { key: 'home', labelKo: '홈', labelEn: 'Home', path: '/home' },
  { key: 'feed', labelKo: '피드', labelEn: 'Feed', path: '/feed' },
  { key: 'trips', labelKo: '내 여행', labelEn: 'My trips', path: '/trips' },
  { key: 'me', labelKo: '마이페이지', labelEn: 'Profile', path: '/me' },
] as const;

// 여행 만들기 흐름의 주소 둘. `(plan)` 은 괄호 묶음이라 주소에 안 나타나서 `/basics` 처럼
// 보이고, `/plan/basic` 쪽은 그것을 그대로 내보내는 한 줄짜리 별칭 파일들이다.
const PLAN_PATHS = ['/basics', '/taste', '/constraints', '/confirm', '/generating'];

function isPlanRoute(pathname: string) {
  return pathname.startsWith('/plan') || PLAN_PATHS.includes(pathname);
}

/**
 * 폰 TabBar 의 활성 표식과 같은 모양 — 18×3 오렌지 바. 여기서는 바 **바닥**에 붙인다.
 *
 * 「여행 만들기」는 36px 버튼이라 56px 바 안에서 위아래로 10px 씩 뜬다. 그 버튼 안에
 * 붙일 때는 10px 을 내려야 다른 링크의 표식과 같은 높이에 선다.
 */
function ActiveMarker({ onCta = false }: { onCta?: boolean }) {
  return <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.marker, onCta && styles.markerOnCta]} />;
}

export function PlanWebNav() {
  const { kind } = useLayout();
  const router = useRouter();
  const pathname = usePathname();
  const { tx } = useI18n();
  const { accessToken, ready } = useAuth();
  const signedOut = ready && !accessToken;
  if (kind !== 'tablet') return null;

  const planActive = isPlanRoute(pathname);

  return <View style={styles.nav}>
    <BrandLogoLink href="/" imageStyle={styles.logo} />
    <View style={styles.links}>
      {LINKS.map((item) => {
        const active = !planActive && (pathname === item.path || pathname.startsWith(`${item.path}/`));
        return (
          <Pressable
            key={item.path}
            accessibilityRole="link"
            accessibilityState={{ selected: active }}
            onPress={() => router.push(item.path)}
            style={styles.link}
          >
            <Text
              weight={active ? 'bold' : 'medium'}
              color={active ? color.brand.navy : color.text.body}
              style={styles.linkLabel}
            >
              {tx(item.labelKo, item.labelEn)}
            </Text>
            {active ? <ActiveMarker /> : null}
          </Pressable>
        );
      })}

      <Pressable accessibilityRole="link" accessibilityState={{ selected: planActive }} onPress={() => router.push('/plan/basic')} style={({ pressed }) => [styles.cta, pressed && styles.ctaPressed]}>
        <Text weight="bold" color={color.text.onAction} style={styles.linkLabel}>{tx('여행 만들기', 'Plan a trip')}</Text>
        {planActive ? <ActiveMarker onCta /> : null}
      </Pressable>

      {signedOut ? (
        <Pressable accessibilityRole="link" onPress={() => router.push('/sign-in')} style={styles.login}>
          <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로그인', 'Sign in')}</Text>
        </Pressable>
      ) : null}
    </View>
  </View>;
}

const BAR_HEIGHT = 56;

const styles = StyleSheet.create({
  nav: {
    height: BAR_HEIGHT,
    paddingHorizontal: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: color.brand.ivory,
    borderBottomWidth: 1,
    borderBottomColor: color.surface.border,
  },
  logo: { width: 120, height: 28 },
  links: { flexDirection: 'row', alignItems: 'stretch', height: BAR_HEIGHT, gap: 28 },
  // 링크 높이를 바 전체로 잡아야 표식이 바 바닥에 붙는다.
  link: { height: BAR_HEIGHT, justifyContent: 'center' },
  // 웹에서 Pressable 안의 글자에 기본 밑줄이 붙는 경우가 있어 명시적으로 끈다.
  linkLabel: { textDecorationLine: 'none' },
  // 절대배치라 alignSelf 는 믿지 않는다 — 폭(18)의 절반을 왼쪽으로 당겨 가운데를 맞춘다.
  marker: { position: 'absolute', bottom: 0, left: '50%', marginLeft: -9, width: 18, height: 3, borderRadius: 2, backgroundColor: color.brand.orange },
  markerOnCta: { bottom: -10 },
  cta: {
    alignSelf: 'center',
    height: 36,
    paddingHorizontal: spacing[4],
    marginLeft: spacing[2],
    borderRadius: radius.md,
    backgroundColor: color.brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctaPressed: { opacity: 0.88 },
  login: { alignSelf: 'center', minHeight: 36, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
});
