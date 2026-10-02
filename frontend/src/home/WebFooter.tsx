// 웹 맨 아래 안내 — 앱 받기 · 메뉴 묶음 · 자료 출처 (S15P21E201-1930, UI 캔버스 ㉖).
// 전에는 웹 홈이 「내 여행」 카드에서 그냥 끝나서 앱을 받을 곳도, 약관·출처를 찾을 곳도 없었다(사용자 의견 2026-10-02).
//
// 🔴 로고 그림은 여기 안 둔다 — 위쪽 메뉴에 이미 있다. 한 화면에 로고 둘이면 「두 번 그렸다」로 읽힌다(S15P21E201-1547).
// 🔴 안드로이드 링크는 넣지 않는다 — 아직 스토어에 없다. 없는 곳으로 가는 단추는 고장으로 보인다.
// 🔴 긴 문장은 문장·쉼표 단위로 줄을 나눈다(\n) — 상자가 아무 데서나 꺾으면 「…여행이 / 그대로」처럼 읽힌다(사용자 지적 2026-10-02).
import { Image, Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter, type Href } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

/** 가볼래 iOS 앱 — 앱스토어 심사를 통과한 주소(포스터·팸플릿 QR 과 같다) */
export const APP_STORE_URL = 'https://apps.apple.com/app/id6811252919';
const qrAppStore = require('../../assets/brand/qr-appstore.png');

type FooterLink = { ko: string; en: string; href: Href };
type FooterGroup = { ko: string; en: string; links: FooterLink[] };

export const FOOTER_GROUPS: FooterGroup[] = [
  { ko: '여행', en: 'Travel', links: [
    { ko: '여행 만들기', en: 'Plan a trip', href: '/plan' },
    { ko: '피드', en: 'Feed', href: '/feed' },
    { ko: '로컬 탐색', en: 'Explore locally', href: '/explore' },
    { ko: '부산 축제', en: 'Busan festivals', href: '/festivals' },
  ] },
  { ko: '도움', en: 'Help', links: [
    { ko: '긴급 도움 · 119 112 1330', en: 'Emergency · 119 112 1330', href: '/emergency' },
    { ko: '가까운 병원·약국', en: 'Nearby hospitals & pharmacies', href: '/nearby-help' },
    { ko: '처음 쓰는 분께', en: 'Getting started', href: '/help' },
  ] },
  { ko: '약관', en: 'Policies', links: [
    { ko: '이용약관', en: 'Terms of service', href: '/legal/terms' },
    { ko: '개인정보 처리방침', en: 'Privacy policy', href: '/legal/privacy' },
    { ko: '자료 출처', en: 'Data sources', href: '/legal/data-sources' },
    { ko: '계정 삭제', en: 'Delete account', href: '/legal/account-deletion' },
  ] },
];

export function WebFooter() {
  const { tx } = useI18n();
  const router = useRouter();
  // 좁은 데스크톱(폴드 펼침 가로 795 등)은 소개를 한 줄 전체로, 메뉴 셋을 그 아래 한 줄에 — 네 칸을 한 줄에 넣으면
  //  「약관」 하나만 아랫줄로 떨어졌다(캡처 2026-10-02).
  const { width } = useLayout();
  const narrow = width < 1100;
  return (
    <View style={styles.footer}>
      <View style={[styles.appBand, narrow && styles.appBandNarrow]}>
        <GabolleMascot state="idle" still style={styles.mascot} />
        <View style={[styles.appCopy, narrow && styles.appCopyNarrow]}>
          <Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 중에는 앱이 더 편해요', 'The app is handier on the road')}</Text>
          <Text color={color.text.onDarkMuted}>{tx('버스에서 내릴 정류장을 세어 주고, 잠금 화면에 다음 일정이 떠요.\n웹에서 만든 여행이 그대로 이어져요.', 'It counts the stops until yours and shows your next plan on the lock screen.\nTrips you made on the web carry right over.')}</Text>
        </View>
        {/* 좁으면 단추·QR 을 글 아랫줄로 — 한 줄에 다 넣으면 글이 「…잠금 / 화면에」처럼 문장 중간에서 꺾였다 */}
        <View style={[styles.actions, narrow && styles.actionsNarrow]}>
        <Pressable accessibilityRole="link" accessibilityLabel={tx('App Store에서 가볼래 받기', 'Get GABOLLE on the App Store')} onPress={() => { void Linking.openURL(APP_STORE_URL).catch(() => {}); }} style={({ pressed }) => [styles.storeButton, pressed && styles.pressed]}>
          <Text variant="micro" color={color.text.muted}>iPhone</Text>
          <Text weight="bold">{tx('App Store에서 받기', 'Get it on the App Store')}</Text>
        </Pressable>
        <View style={styles.qrBlock}>
          <Image source={qrAppStore} accessibilityLabel={tx('App Store 내려받기 QR 코드', 'QR code to the App Store')} style={styles.qr} />
          <Text variant="micro" color={color.text.onDarkMuted}>{tx('iPhone 카메라로', 'Scan with iPhone')}</Text>
        </View>
        </View>
      </View>

      <View style={styles.columns}>
        <View style={[styles.about, narrow && styles.aboutNarrow]}>
          <Text variant="title" weight="bold">{tx('가볼래', 'GABOLLE')}</Text>
          <Text color={color.text.body} style={styles.aboutText}>{tx('언제, 누구와, 어떻게 다닐지만 알려 주면\n부산 일정을 짜 드려요.\n경사·그늘까지 따져 길을 고르고,\n여행 중에는 통역과 긴급 도움을 바로 열 수 있어요.', 'Tell us when, with whom and how you travel,\nand we plan your Busan days.\nRoutes weigh slopes and shade,\nand interpreting and emergency help are one tap away.')}</Text>
          <Text variant="caption" color={color.text.muted}>한국어 · English · 日本語 · 简体中文 · 繁體中文</Text>
        </View>
        {FOOTER_GROUPS.map((group) => (
          <View key={group.ko} accessibilityRole="list" accessibilityLabel={tx(group.ko, group.en)} style={[styles.group, narrow && styles.groupNarrow]}>
            <Text variant="caption" weight="bold" color={color.text.heading}>{tx(group.ko, group.en)}</Text>
            {group.links.map((link) => (
              <Pressable key={link.ko} accessibilityRole="link" onPress={() => router.push(link.href)} style={({ pressed }) => [styles.link, pressed && styles.pressed]}>
                <Text color={color.text.body}>{tx(link.ko, link.en)}</Text>
              </Pressable>
            ))}
          </View>
        ))}
      </View>

      <View style={styles.bottomRow}>
        <Text variant="caption" color={color.text.muted}>© 2026 GABOLLE</Text>
        <Text variant="caption" color={color.text.muted} style={styles.sources}>{tx('장소·축제: 한국관광공사 · 병원·약국: 건강보험심사평가원 · 지도: © OpenStreetMap 기여자', 'Places & festivals: Korea Tourism Organization · Hospitals & pharmacies: HIRA · Map: © OpenStreetMap contributors')}</Text>
      </View>
    </View>
  );
}

// 🔴 맨 아래 줄이 오른쪽 아래에 떠 있는 「AI에게 물어보기」 단추(높이 84 + 바닥 32) 밑에 깔리지 않게 그만큼 비운다(캡처 2026-10-02).
const FLOATING_ASSISTANT_CLEARANCE = 84 + spacing[8] + spacing[4];

const styles = StyleSheet.create({
  footer: { width: '100%', backgroundColor: color.surface.card, borderTopWidth: 1, borderTopColor: color.surface.border, paddingHorizontal: desktopGutter, paddingTop: desktopGutter, paddingBottom: FLOATING_ASSISTANT_CLEARANCE },
  // 앱 받기 띠 — 짙은 판. 웹 홈에서 유일하게 어두운 면이라 「여기서 앱을 받는다」가 바로 읽힌다.
  appBand: { flexDirection: 'row', alignItems: 'center', gap: spacing[6], paddingVertical: spacing[6], paddingHorizontal: spacing[8], borderRadius: radius.lg, backgroundColor: color.action.secondary },
  mascot: { width: 72, height: 72 },
  appCopy: { flex: 1, minWidth: 0, gap: spacing[2] },
  appBandNarrow: { flexWrap: 'wrap', rowGap: spacing[4] },
  appCopyNarrow: { flexBasis: 420 },
  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[6] },
  // 마스코트(72) + 간격(24) 만큼 들여서 글 왼쪽 끝에 맞춘다
  actionsNarrow: { flexBasis: '100%', paddingLeft: 72 + spacing[6] },
  storeButton: { minHeight: 52, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  qrBlock: { alignItems: 'center', gap: spacing[1] },
  qr: { width: 84, height: 84, borderRadius: radius.sm, backgroundColor: color.surface.card },
  // 안내 단 — 소개 한 칸을 조금 넓게, 메뉴 셋은 같은 폭
  columns: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[8], marginTop: spacing[8] + spacing[4] },
  about: { flexGrow: 1.4, flexBasis: 320, gap: spacing[3] },
  aboutText: { lineHeight: 24 },
  group: { flexGrow: 1, flexBasis: 160, gap: spacing[1] },
  aboutNarrow: { flexBasis: '100%' },
  groupNarrow: { flexBasis: 0, minWidth: 0 },
  link: { minHeight: 36, justifyContent: 'center', alignSelf: 'flex-start' },
  // 맨 아래 줄 — 안내 단 바로 밑에. 바닥에 붙이면 그 사이가 통째로 빈다(시안 점검 2026-10-02).
  bottomRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[8] + spacing[4], paddingTop: spacing[4], borderTopWidth: 1, borderTopColor: color.surface.border },
  sources: { flexShrink: 1 },
  pressed: { opacity: 0.75 },
});
