import { Image, Pressable, StyleSheet, View } from 'react-native';
import { usePathname, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { TopNavWeather } from '@/home/HomeBlocks';
import { useHomeWeather } from '@/home/useHomeData';
import { useLayout } from '@/layout/useLayout';
import { useI18n } from '@/i18n';
import { LANGUAGE_CODES, LANGUAGE_OPTIONS } from '@/i18n/languages';

const bellIcon = require('../../assets/icons/home/bell.png');
import { type LanguageCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { txf } from '@/i18n/format';

// 국기는 유니코드 그림문자(🇰🇷)가 아니라 실제 이미지를 쓴다 — 윈도우 브라우저는 국가
// 그림문자를 정책적으로 지원하지 않아 KR·US 같은 두 글자로 떨어진다
// app/index.tsx 의 같은 매핑 참고). require 는 번들러가 정적으로 읽어야 해서 값이 동적으로
// 도는 languages.ts 에는 안 두고 쓰는 자리마다 이렇게 둔다.
const FLAG_IMAGES: Record<LanguageCode, ReturnType<typeof require>> = {
  ko: require('../../assets/flags/kr.png'),
  en: require('../../assets/flags/us.png'),
  ja: require('../../assets/flags/jp.png'),
  'zh-Hans': require('../../assets/flags/cn.png'),
  'zh-Hant': require('../../assets/flags/tw.png'),
};

// 넓은 화면의 단 하나의 상단 바다 에서 모양을, -970 에서 범위를, -994 에서
// 붙이는 자리를, -1103 에서 2단 구조를 정했다).

const LINKS = [
  // 홈은 주소가 둘이다 — 랜딩이 `/` 이고 폰 홈이 `/home` 이다. 둘 다 홈으로 친다.
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

// 바를 안 그리는 주소. 지금은 하나뿐이고, 늘리기 전에 한 번 더 생각한다 — 이 목록이
// 길어지는 순간 "있는 화면과 없는 화면" 이 다시 갈린다(-994 가 고친 것이 그것이다).
function isChromeless(pathname: string) {
  return pathname.startsWith('/oauth/');
}

/** 폰 TabBar 의 활성 표식과 같은 모양 — 5×5 점. */
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
  // 날씨는 홈에서만 보인다. 훅은 조건 없이 불러야 해서(React 규칙) 여기서 부르고,
  // 홈이 아니면 꺼 둔다 — 그러면 요청 자체가 안 나간다.
  const weather = useHomeWeather(pathname === '/' || pathname === '/home');
  // 좁은 화면은 아래 탭 바가 같은 일을 한다. 둘 다 그리면 화면이 위아래로 잘린다.
  if (kind !== 'tablet' || isChromeless(pathname)) return null;

  const planActive = isPlanRoute(pathname);

  // 언어는 다섯 칸이라 누른 쪽 언어로 정한다. 버튼 하나를 눌러 뒤집는 방식이면
  // 다섯 중 어느 쪽으로 갈지 계산해야 하는데, 칸이 각 언어 하나씩이면 그럴 필요가 없다
  // 화면에 보이는 것과 하는 일이 그대로 맞는다.
  const pickLanguage = (code: LanguageCode) => setPreferences(code, mobility);

  // 위쪽 안전 영역은 이 컴포넌트가 직접 두른다. 전에는 랜딩 파일이 두르고 있었는데, 이제
  // 바가 모든 화면에 뜨므로 그 처리도 같이 따라다녀야 한다 — 안 그러면 노치 있는 기기를
  // 가로로 눕혔을 때 바가 노치 밑에 깔린다. 배경색을 안전 영역까지 칠해야 틈이 안 뜬다.
  return <SafeAreaView edges={['top']} style={styles.safeArea}>
    {/* ── 1층 · 유틸 바 (높이 36) — 언어와 계정. 이동이 아니다. ───────────────── */}
    <View style={styles.utilBar}>
      {LANGUAGE_CODES.map((code, index) => {
        const current = language === code;
        const option = LANGUAGE_OPTIONS.find((item) => item.code === code)!;
        return (
          <View key={code} style={styles.utilItem}>
            {index > 0 ? <View style={styles.utilFlagDivider} /> : null}
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ selected: current }}
              accessibilityLabel={txf(tx, '언어를 %s로 변경', 'Change language to %s', option.endonym)}
              onPress={() => pickLanguage(code)}
              style={[styles.utilFlagTouch, current && styles.utilFlagTouchSelected]}
            >
              <Image source={FLAG_IMAGES[code]} resizeMode="contain" style={styles.utilFlagImage} />
            </Pressable>
          </View>
        );
      })}
      <View style={styles.utilDivider} />
      {/* 알림 종 — 폰 홈과 같은 자리(오른쪽 위). 넓은 화면에는 없어서 알림에 갈 길이 없었다(2026-09-21 지적, S15P21E201-1390). */}
      <Pressable accessibilityRole="link" accessibilityLabel={tx('알림 확인', 'Check notifications')} onPress={() => router.push('/notifications')} style={styles.utilBell}>
        <Image source={bellIcon} resizeMode="contain" style={styles.utilBellIcon} />
      </Pressable>
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
            <Pressable key={item.key} accessibilityRole="link" accessibilityState={{ selected: active }} onPress={() => router.push(item.path)} style={styles.capsuleItem}>
              <Text weight={active ? 'bold' : 'medium'} color={active ? color.brand.navy : color.text.body} style={styles.noUnderline}>{tx(item.labelKo, item.labelEn)}</Text>
              {active ? <ActiveMarker /> : null}
            </Pressable>
          );
        })}
      </View>

      {/* 날씨는 CTA «왼쪽»에 붙는다. 날씨가 없으면 TopNavWeather 가 아무것도 안 그려서
          자리가 저절로 접힌다 — 빈 칸을 남겨 두지 않는다. */}
      <View style={styles.navRight}>
        <TopNavWeather forecast={weather} />
        <Pressable accessibilityRole="link" accessibilityState={{ selected: planActive }} onPress={() => router.push('/plan')} style={({ pressed }) => [styles.cta, pressed && styles.ctaPressed]}>
          {/* 한 줄로 묶는다. 옆에 날씨가 서면서 좁아져 「여행 / 만들기」로 접혔다. */}
          <Text weight="bold" color={color.action.outline} numberOfLines={1} style={styles.noUnderline}>{tx('여행 만들기', 'Plan a trip')}</Text>
          {planActive ? <ActiveMarker /> : null}
        </Pressable>
      </View>
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
  utilTouch: { height: UTIL_HEIGHT, justifyContent: 'center', paddingHorizontal: spacing[1] },
  utilBell: { height: UTIL_HEIGHT, width: 32, alignItems: 'center', justifyContent: 'center' },
  utilBellIcon: { width: 18, height: 18 },
  utilName: { maxWidth: 160 },
  utilDivider: { width: 1, height: 14, backgroundColor: color.surface.field },

  // 국기 다섯 칸. 안 고른 것은 옅게 둬서 고른 언어가 눈에 띈다.
  utilFlagDivider: { width: 1, height: 14, backgroundColor: color.surface.field, marginRight: spacing[2] },
  utilFlagTouch: { height: UTIL_HEIGHT, width: 30, alignItems: 'center', justifyContent: 'center', opacity: 0.55 },
  utilFlagTouchSelected: { opacity: 1 },
  utilFlagImage: { width: 20, height: 14, borderRadius: 2 },

  // 2층
  nav: { height: NAV_HEIGHT, paddingHorizontal: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: color.brand.ivory, borderBottomWidth: 1, borderBottomColor: color.surface.border },
  logo: { width: 154, height: 28 },

  // 가운데 칸들 — 알약 바탕도 흰 카드도 없다. 「지금 여기」는 검은 글자와 밑의 점이 말한다.
  // 바탕으로 말하면 「고른 것」이 되고, 이 배색에서 고른 것은 색이 아니라 굵기와 점으로 뜬다.
  capsule: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], padding: spacing[1] },
  capsuleItem: { height: CTA_HEIGHT, paddingHorizontal: spacing[4] + spacing[1], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },

  // 날씨와 CTA 를 한 덩어리로 묶는다. 2층이 space-between 이라 이 덩어리가 오른쪽 끝을 잡는다.
  navRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[4] },

  cta: { height: CTA_HEIGHT, paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1, borderColor: color.action.outline, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' },
  ctaPressed: { opacity: 0.88 },

  // 절대배치라 alignSelf 는 믿지 않는다 — 폭(5)의 절반을 왼쪽으로 당겨 가운데를 맞춘다.
  marker: { position: 'absolute', bottom: CTA_MARKER_DROP, left: '50%', marginLeft: -2.5, width: 5, height: 5, borderRadius: radius.full, backgroundColor: color.state.dot },

  // 웹에서 Pressable 안의 글자에 기본 밑줄이 붙는 경우가 있어 명시적으로 끈다.
  noUnderline: { textDecorationLine: 'none' },
});
