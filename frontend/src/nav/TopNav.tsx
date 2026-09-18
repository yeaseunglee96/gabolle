import { Pressable, StyleSheet, View } from 'react-native';
import { usePathname, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';

// 넓은 화면의 **단 하나의 상단 바**다 (S15P21E201-968 에서 모양을, -970 에서 범위를, -994 에서
// 붙이는 자리를, -1103 에서 2단 구조를 정했다).
//
// 🔴 이 파일은 앱 뼈대(app/_layout.tsx)가 **딱 한 번** 붙인다. 화면이나 하위 레이아웃에서
// 따로 붙이지 않는다 — 붙이면 두 벌이 그려진다.
//
// 그 규칙이 생긴 이유가 있다. 전에는 붙일 자리를 화면마다 손으로 정했고(랜딩 · 여행 만들기
// 둘 · 마이페이지, 네 군데), 화면 파일은 68개였다. 즉 **50개 넘는 화면에 상단 바가 없었다** —
// 피드도 여행 상세도 알림도 남의 프로필도. 새 화면을 만들 때마다 "여기도 붙여야 하나" 를
// 사람이 기억해야 하는 구조라 **안 붙는 쪽이 기본값**이 됐다. -970 에서 마이페이지 하나를
// 손으로 더 붙여 막았지만 원인은 그대로였고, 같은 일이 곧 다시 났다.
//
// 그래서 파일도 src/plan/ 에서 여기로 옮겼다. `plan/` 안에 있는 한 다음 사람은 이것을
// 「여행 만들기 전용」으로 읽는다 — 실제로 그렇게 읽혀서 나머지 화면에 안 붙었다.
//
// 🔴 그 전에는 로그인 상태를 안 보고 "로그인" 버튼을 늘 그리고 있었다. 로그인한 사람이 여행
// 만들기로 들어가면 세션이 풀린 것처럼 보였다(S15P21E201-763). 세션이 끊긴 적은 없다.
//
// `ready` 를 함께 보는 이유: 앱이 뜰 때 저장된 세션을 되살리는 동안에는 accessToken 이 잠시
// null 이다. 그때 버튼을 그리면 로그인한 사람에게 "로그인" 이 한 번 번쩍인다.
//
// ── 2026-09-16 (S15P21E201-1103) 2단으로 바꿨다 ────────────────────────────────
// 전에는 56px 한 줄에 이동 링크 · CTA · 언어 · 계정이 **같은 간격으로** 붙어 있어서,
// 「어디로 갈까」와 「나는 누구인가」가 구분되지 않았다. 시안(docs/design_handoff_topnav)대로
// 층을 나눈다 — 1층은 계정·언어(유틸), 2층은 이동. 합쳐서 96.

const LINKS = [
  // 🔴 홈은 주소가 둘이다 — 랜딩이 `/` 이고 폰 홈이 `/home` 이다. 둘 다 홈으로 친다.
  { key: 'home', labelKo: '홈', labelEn: 'Home', path: '/', extra: ['/home'] },
  { key: 'feed', labelKo: '피드', labelEn: 'Feed', path: '/feed', extra: [] },
  { key: 'trips', labelKo: '내 여행', labelEn: 'My trips', path: '/trips', extra: [] },
] as const;

// 여행 만들기 흐름의 주소 둘. `(plan)` 은 괄호 묶음이라 주소에 안 나타나서 `/basics` 처럼
// 보이고, `/plan/basic` 쪽은 그것을 그대로 내보내는 한 줄짜리 별칭 파일들이다.
const PLAN_PATHS = ['/basics', '/taste', '/constraints', '/confirm', '/generating'];

function isPlanRoute(pathname: string) {
  return pathname.startsWith('/plan') || PLAN_PATHS.includes(pathname);
}

// 🔴 바를 안 그리는 주소. 지금은 하나뿐이고, 늘리기 전에 한 번 더 생각한다 — 이 목록이
// 길어지는 순간 "있는 화면과 없는 화면" 이 다시 갈린다(-994 가 고친 것이 그것이다).
//
// 소셜 로그인에서 되돌아오는 주소는 토큰을 받아 다른 화면으로 넘기기만 하는 자리라 보통
// 1초 안에 사라진다. 여기에 바를 그리면 켜졌다 꺼지며 한 번 번쩍인다.
function isChromeless(pathname: string) {
  return pathname.startsWith('/oauth/');
}

/** 폰 TabBar 의 활성 표식과 같은 모양 — 18×3 오렌지 바.
 *
 *  🔴 이제 **CTA 에만** 쓴다. 캡슐 안의 항목은 **흰 카드 자체가 활성 표식**이라
 *  마커까지 붙이면 같은 말을 두 번 하는 셈이다(시안 2층). */
function ActiveMarker() {
  return <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.marker} />;
}

export function TopNav() {
  const { kind } = useLayout();
  const router = useRouter();
  const pathname = usePathname();
  const { tx, language } = useI18n();
  const { accessToken, ready, user } = useAuth();
  const { mobility, setPreferences } = useOnboardingPreferences();
  const signedOut = ready && !accessToken;
  // 좁은 화면은 아래 탭 바가 같은 일을 한다. 둘 다 그리면 화면이 위아래로 잘린다.
  if (kind !== 'tablet' || isChromeless(pathname)) return null;

  const planActive = isPlanRoute(pathname);

  // 🔴 언어는 이제 「KO | EN」 두 칸이라 **누른 쪽 언어로 정한다.** 전에는 버튼 하나를
  // 눌러 뒤집는 방식이라 반대쪽 값을 계산했는데, 두 칸을 놓고 그렇게 하면 이미 켜진
  // 언어를 눌렀을 때 반대로 바뀐다 — 화면에 보이는 것과 하는 일이 어긋난다.
  const pickLanguage = (code: LanguageCode) => setPreferences(code, mobility);

  // 🔴 위쪽 안전 영역은 이 컴포넌트가 직접 두른다. 전에는 랜딩 파일이 두르고 있었는데, 이제
  // 바가 모든 화면에 뜨므로 그 처리도 같이 따라다녀야 한다 — 안 그러면 노치 있는 기기를
  // 가로로 눕혔을 때 바가 노치 밑에 깔린다. 배경색을 안전 영역까지 칠해야 틈이 안 뜬다.
  //
  // 🔴 안전 영역의 배경은 **1층 색(soft)** 이다. 2층 색(ivory)으로 칠하면 노치 아래에
  // 다른 색 띠가 한 줄 생긴다.
  return <SafeAreaView edges={['top']} style={styles.safeArea}>
    {/* ── 1층 · 유틸 바 (높이 36) — 언어와 계정. 이동이 아니다. ───────────────── */}
    <View style={styles.utilBar}>
      {(['ko', 'en'] as const).map((code, index) => {
        const current = language === code;
        return (
          <View key={code} style={styles.utilItem}>
            {index > 0 ? <Text variant="util" color={color.surface.field} style={styles.utilSeparator}>|</Text> : null}
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ selected: current }}
              accessibilityLabel={tx(`언어를 ${code === 'ko' ? '한국어' : 'English'}로 변경`, `Change language to ${code === 'ko' ? 'Korean' : 'English'}`)}
              onPress={() => pickLanguage(code)}
              style={styles.utilTouch}
            >
              <Text variant="util" weight={current ? 'bold' : 'medium'} color={current ? color.text.muted : color.text.muted} style={styles.noUnderline}>{code.toUpperCase()}</Text>
            </Pressable>
          </View>
        );
      })}
      <View style={styles.utilDivider} />
      {signedOut ? (
        <>
          <Pressable accessibilityRole="link" onPress={() => router.push('/sign-in')} style={styles.utilTouch}>
            <Text variant="util" weight="bold" style={styles.noUnderline}>{tx('로그인', 'Sign in')}</Text>
          </Pressable>
          <Pressable accessibilityRole="link" onPress={() => router.push('/sign-up')} style={styles.utilTouch}>
            <Text variant="util" weight="bold" color={color.brand.navy} style={styles.noUnderline}>{tx('회원가입', 'Sign up')}</Text>
          </Pressable>
        </>
      ) : user ? (
        // 로그인하면 「회원가입」 자리에 이름이 선다. 여기가 마이페이지로 가는 길이라
        // 아래 캡슐에는 「마이페이지」를 넣지 않는다.
        <Pressable accessibilityRole="link" onPress={() => router.push('/me')} style={[styles.utilTouch, styles.utilName]}>
          <Text variant="util" weight="bold" color={color.brand.navy} numberOfLines={1} style={styles.noUnderline}>{user.displayName}</Text>
        </Pressable>
      ) : null}
    </View>

    {/* ── 2층 · 주 내비 (높이 60) — 로고 · 캡슐 · CTA ──────────────────────────── */}
    <View style={styles.nav}>
      <BrandLogoLink href="/" imageStyle={styles.logo} />

      <View style={styles.capsule}>
        {LINKS.map((item) => {
          const paths = [item.path, ...item.extra];
          const active = !planActive && paths.some((path) => pathname === path || (path !== '/' && pathname.startsWith(`${path}/`)));
          return (
            <Pressable key={item.key} accessibilityRole="link" accessibilityState={{ selected: active }} onPress={() => router.push(item.path)} style={[styles.capsuleItem, active && styles.capsuleItemActive]}>
              <Text weight={active ? 'bold' : 'medium'} color={active ? color.brand.navy : color.text.body} style={styles.noUnderline}>{tx(item.labelKo, item.labelEn)}</Text>
            </Pressable>
          );
        })}
      </View>

      <Pressable accessibilityRole="link" accessibilityState={{ selected: planActive }} onPress={() => router.push('/plan')} style={({ pressed }) => [styles.cta, pressed && styles.ctaPressed]}>
        <Text weight="bold" color={color.text.onAction} style={styles.noUnderline}>{tx('여행 만들기', 'Plan a trip')}</Text>
        {planActive ? <ActiveMarker /> : null}
      </Pressable>
    </View>
  </SafeAreaView>;
}

const UTIL_HEIGHT = 36;
const NAV_HEIGHT = 60;
const CTA_HEIGHT = 40;
// CTA(40)가 바(60) 안에서 위아래로 10 씩 뜬다. 마커를 바 바닥에 세우려면 그만큼 내린다.
const CTA_MARKER_DROP = -(NAV_HEIGHT - CTA_HEIGHT) / 2;

const styles = StyleSheet.create({
  safeArea: { backgroundColor: color.surface.soft },

  // 1층 — 오른쪽으로 몰고, 항목 하나하나가 바 높이만큼 눌린다.
  utilBar: { height: UTIL_HEIGHT, paddingHorizontal: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: spacing[3], backgroundColor: color.surface.soft },
  utilItem: { flexDirection: 'row', alignItems: 'center' },
  utilSeparator: { paddingHorizontal: spacing[1] },
  utilTouch: { height: UTIL_HEIGHT, justifyContent: 'center', paddingHorizontal: spacing[1] },
  utilName: { maxWidth: 160 },
  utilDivider: { width: 1, height: 14, backgroundColor: color.surface.field },

  // 2층
  nav: { height: NAV_HEIGHT, paddingHorizontal: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: color.surface.border },
  logo: { width: 120, height: 28 },

  // 가운데 캡슐 — 흰 카드가 「지금 여기」를 말한다.
  capsule: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  capsuleItem: { height: CTA_HEIGHT, paddingHorizontal: spacing[4] + spacing[1], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  capsuleItemActive: {
    backgroundColor: color.surface.card,
    // 웹은 boxShadow, 안드로이드는 elevation 이 그림자를 그린다. 둘 다 둔다.
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 2, shadowOffset: { width: 0, height: 1 }, elevation: 1,
  },

  cta: { height: CTA_HEIGHT, paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  ctaPressed: { opacity: 0.88 },

  // 절대배치라 alignSelf 는 믿지 않는다 — 폭(18)의 절반을 왼쪽으로 당겨 가운데를 맞춘다.
  marker: { position: 'absolute', bottom: CTA_MARKER_DROP, left: '50%', marginLeft: -9, width: 18, height: 3, borderRadius: 2, backgroundColor: color.brand.orange },

  // 웹에서 Pressable 안의 글자에 기본 밑줄이 붙는 경우가 있어 명시적으로 끈다.
  noUnderline: { textDecorationLine: 'none' },
});
