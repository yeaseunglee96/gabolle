// 웹 맨 아래 안내 — 앱 받기 · 메뉴 묶음 · 자료 출처 (S15P21E201-1930, UI 캔버스 ㉖).
// 전에는 웹 홈이 「내 여행」 카드에서 그냥 끝나서 앱을 받을 곳도, 약관·출처를 찾을 곳도 없었다(사용자 의견 2026-10-02).
//
// 🔴 로고 그림은 여기 안 둔다 — 위쪽 메뉴에 이미 있다. 한 화면에 로고 둘이면 「두 번 그렸다」로 읽힌다(S15P21E201-1547).
// 🔴 안드로이드도 이제 링크다 — Google Play 프로덕션 심사를 통과했다(S15P21E201-2037). 전에는 「Google Play 심사 중」
//    표시만 두었다(없는 곳으로 가는 단추는 고장으로 보여서). QR 도 App Store·Google Play 둘을 나란히 둔다.
// 🔴 긴 문장은 문장·쉼표 단위로 줄을 나눈다(\n) — 상자가 아무 데서나 꺾으면 「…여행이 / 그대로」처럼 읽힌다(사용자 지적 2026-10-02).
import { Image, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useRouter, type Href } from 'expo-router';

import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS } from '@/i18n/languages';
import { useLayout } from '@/layout/useLayout';

/** 가볼래 iOS 앱 — 앱스토어 심사를 통과한 주소(포스터·팸플릿 QR 과 같다) */
export const APP_STORE_URL = 'https://apps.apple.com/app/id6811252919';
/** 가볼래 안드로이드 앱 — app.json 의 android.package 와 같아야 한다(시험이 맞춰 본다) */
export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=com.gabolle.app';
const qrAppStore = require('../../assets/brand/qr-appstore.png');
// qr-appstore.png 와 같은 방식 — qrcode-generator(오류 정정 M, 여백 없이 흰 틀이 둘러 준다)로 만든 그림
const qrPlayStore = require('../../assets/brand/qr-googleplay.png');
const brandLogo = require('../../assets/brand/gabolle-logo-hd.png');

/** href 는 앱 안 화면, mail 은 메일 앱으로. 「문의하기」는 2026-10-17 에 뺐다 — 문의는 「도움말·문의」 화면 안에 있다(사용자 지시). */
type FooterLink = { ko: string; en: string; href?: Href; mail?: string; emphasis?: boolean };
type FooterGroup = { ko: string; en: string; links: FooterLink[] };

export const FOOTER_GROUPS: FooterGroup[] = [
  { ko: '여행', en: 'Travel', links: [
    { ko: '여행 만들기', en: 'Plan a trip', href: '/plan' },
    { ko: '피드', en: 'Feed', href: '/feed' },
    { ko: '로컬 탐색', en: 'Explore locally', href: '/explore' },
  ] },
  { ko: '도움', en: 'Help', links: [
    { ko: '긴급 도움', en: 'Emergency help', href: '/emergency' },
    { ko: '근처 병원·약국', en: 'Nearby hospitals & pharmacies', href: '/nearby-help' },
    { ko: '도움말·문의', en: 'Help & support', href: '/help' },
  ] },
  { ko: '약관·고지', en: 'Policies & notices', links: [
    { ko: '이용약관', en: 'Terms of service', href: '/legal/terms' },
    { ko: '개인정보 처리방침', en: 'Privacy policy', href: '/legal/privacy', emphasis: true },
    { ko: '자료 출처', en: 'Data sources', href: '/legal/data-sources' },
  ] },
];

export function WebFooter() {
  const { tx } = useI18n();
  const router = useRouter();
  // 좁은 데스크톱(폴드 펼침 가로 795 등)은 소개를 한 줄 전체로, 메뉴 셋을 그 아래 한 줄에 — 네 칸을 한 줄에 넣으면
  //  「약관」 하나만 아랫줄로 떨어졌다(캡처 2026-10-02).
  const { width } = useLayout();
  const narrow = width < 1100;
  // 폰 폭 — 띠를 세로로 쌓는다(글 → 단추 → QR 둘). 옆으로 두면 글 칸이 손가락 폭으로 줄었다.
  const phone = width < 640;
  const storeButtons = <>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('App Store에서 가볼래 받기', 'Get GABOLLE on the App Store')} onPress={() => { void Linking.openURL(APP_STORE_URL).catch(() => {}); }} style={({ pressed }) => [styles.storeButton, !phone && styles.storeButtonStacked, pressed && styles.pressed]}>
      <Text variant="micro" color={color.text.muted}>iOS</Text>
      <Text weight="bold">{tx('App Store에서 받기', 'Get it on the App Store')}</Text>
    </Pressable>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('Google Play에서 가볼래 받기', 'Get GABOLLE on Google Play')} onPress={() => { void Linking.openURL(PLAY_STORE_URL).catch(() => {}); }} style={({ pressed }) => [styles.storeButton, !phone && styles.storeButtonStacked, pressed && styles.pressed]}>
      <Text variant="micro" color={color.text.muted}>Android</Text>
      <Text weight="bold">{tx('Google Play에서 받기', 'Get it on Google Play')}</Text>
    </Pressable>
  </>;
  return (
    <View style={styles.footer}>
      {/* 🔴 두 단 — 왼쪽은 글, 오른쪽은 받는 곳(단추 둘 + QR). 전에는 단추·QR 이 글 밑 왼쪽에 몰려 판 오른쪽 절반이 비었다
          (사용자 지적 2026-10-02 「QR 코드랑 다운로드 버튼이 왼쪽에 붙어 있다」). QR 은 폰 카메라로 찍는 것이라
          크게 둔다 — 데스크톱 사람이 실제로 쓰는 것은 이쪽이다. 좁으면(세로 탭) 단추를 글 아래로 내리고 QR 만 오른쪽에. */}
      {/* 🔴 앱 안(탭의 넓은 화면 판)에서는 숨긴다 — 이미 앱을 쓰는 사람에게 앱을 받으라고 하고, 안드로이드 앱 안에서
          「Google Play 심사 중」 표시가 보였다(S15P21E201-1964). 메뉴·약관 칸은 앱에서도 쓸모가 있어 남긴다. */}
      {Platform.OS === 'web' ? (
      <View style={[styles.appBand, narrow && styles.appBandNarrow, phone && styles.appBandPhone]}>
        {narrow ? null : <GabolleMascot state="idle" still style={styles.mascot} />}
        <View style={[styles.appMain, narrow && !phone && styles.appMainNarrow]}>
          <View style={styles.appCopy}>
            <Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 중에는 앱이 더 편해요', 'The app is handier on the road')}</Text>
            <Text color={color.text.onDarkMuted}>{tx('버스·지하철에서 내릴 곳까지 남은 정류장을 세어 주고, 다가오면 진동으로 알려 줘요.\n웹에서 만든 여행이 그대로 이어져요.', 'It counts the stops left on the bus or subway and buzzes as yours gets close.\nTrips you made on the web carry right over.')}</Text>
          </View>
          {phone ? <View style={[styles.actions, styles.actionsPhone]}>{storeButtons}</View> : null}
        </View>
        <View style={[styles.getRow, phone && styles.qrRowPhone]}>
        {phone ? null : <View style={styles.actionsStacked}>{storeButtons}</View>}
        <View style={[styles.qrRow, phone && styles.qrRowPhone]}>
          <View style={styles.qrBlock}>
            <View style={styles.qrFrame}>
              <Image source={qrAppStore} accessibilityLabel={tx('App Store 내려받기 QR 코드', 'QR code to the App Store')} style={styles.qr} />
            </View>
            <Text variant="caption" weight="medium" color={color.text.onDarkMuted} numberOfLines={1} style={styles.qrLabel}>{'App Store'}</Text>
          </View>
          <View style={styles.qrBlock}>
            <View style={styles.qrFrame}>
              <Image source={qrPlayStore} accessibilityLabel={tx('Google Play 내려받기 QR 코드', 'QR code to Google Play')} style={styles.qr} />
            </View>
            <Text variant="caption" weight="medium" color={color.text.onDarkMuted} numberOfLines={1} style={styles.qrLabel}>{'Google Play'}</Text>
          </View>
        </View>
        </View>
      </View>
      ) : null}

      <View style={styles.columns}>
        <View style={[styles.about, narrow && styles.aboutNarrow]}>
          <Image source={brandLogo} resizeMode="contain" accessibilityLabel="GABOLLE" accessibilityIgnoresInvertColors style={styles.brandLogo} />
          <Text color={color.text.body} style={styles.aboutText}>{tx('언제, 누구와, 어떻게 다닐지만 알려 주면\n부산 일정을 짜 드려요.\n경사·그늘까지 따져 길을 고르고,\n여행 중에는 통역과 긴급 도움을 바로 열 수 있어요.', 'Tell us when, with whom and how you travel,\nand we plan your Busan days.\nRoutes weigh slopes and shade,\nand interpreting and emergency help are one tap away.')}</Text>
          <Text variant="caption" color={color.text.muted}>{LANGUAGE_OPTIONS.map((option) => option.endonym).join(' · ')}</Text>
        </View>
        {FOOTER_GROUPS.map((group) => (
          <View key={group.ko} accessibilityRole="list" accessibilityLabel={tx(group.ko, group.en)} style={[styles.group, narrow && styles.groupNarrow]}>
            <Text variant="caption" weight="bold" color={color.text.heading}>{tx(group.ko, group.en)}</Text>
            {group.links.map((link) => (
              <Pressable key={link.ko} accessibilityRole="link" onPress={() => { if (link.mail) void Linking.openURL(`mailto:${link.mail}`).catch(() => {}); else if (link.href) router.push(link.href); }} style={({ pressed }) => [styles.link, pressed && styles.pressed]}>
                {/* 개인정보 처리방침은 굵게 — 개인정보보호법 가이드라인이 다른 약관과 구별해 눈에 띄게 두라고 한다(㉖ 시안) */}
                <Text color={link.emphasis ? color.text.heading : color.text.body} weight={link.emphasis ? 'bold' : 'regular'}>{tx(link.ko, link.en)}</Text>
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
const QR_FRAME = 112 + spacing[2] * 2;
const FLOATING_ASSISTANT_CLEARANCE = 84 + spacing[8] + spacing[4];

const styles = StyleSheet.create({
  footer: { width: '100%', backgroundColor: color.surface.card, borderTopWidth: 1, borderTopColor: color.surface.border, paddingHorizontal: desktopGutter, paddingTop: desktopGutter, paddingBottom: FLOATING_ASSISTANT_CLEARANCE },
  // 앱 받기 띠 — 짙은 판. 웹 홈에서 유일하게 어두운 면이라 「여기서 앱을 받는다」가 바로 읽힌다.
  appBand: { flexDirection: 'row', alignItems: 'center', gap: spacing[8], paddingVertical: spacing[8], paddingHorizontal: spacing[8] + spacing[2], borderRadius: radius.lg, backgroundColor: color.action.secondary },
  // 좁으면(세로 탭) 동백이를 빼고 안쪽 여백을 줄여 글 폭을 번다 — QR 은 그대로 오른쪽에
  // 🔴 좁으면(폴드 가로 등) 글을 위 한 줄로, 그 아래에 넓은 판과 같은 「단추 둘 + QR 둘」 묶음을 둔다.
  //  단추를 글 밑에 가로로 두면 「Google Play에서 받기」가 두 줄로 꺾였다(캡처 2026-10-10).
  appBandNarrow: { flexDirection: 'column', alignItems: 'flex-start', gap: spacing[6], paddingHorizontal: spacing[8] },
  mascot: { width: 72, height: 72, alignSelf: 'flex-start' },
  appMain: { flex: 1, minWidth: 0, gap: spacing[4] + spacing[1] },
  appMainNarrow: { flexGrow: 0, flexShrink: 0, flexBasis: 'auto', alignSelf: 'stretch' },
  appCopy: { gap: spacing[2] },
  actions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[3] },
  // 넓으면 단추 둘을 세로로 쌓아 QR 옆에 — 같은 폭으로 늘린다
  // 🔴 단추 둘의 높이 합 = QR 판 높이(112 + 위아래 여백 8×2 = 128), 단추 사이 = QR 사이(16). 그래서 단추 위·아래 끝이
  //  QR 판 위·아래 끝과 맞는다(사용자 지적 2026-10-10 「단추와 QR 이 어색하다」). 캡션 몫은 아래로 남는다.
  actionsStacked: { width: 240, gap: spacing[4] },
  storeButtonStacked: { height: (QR_FRAME - spacing[4]) / 2, minHeight: 0 },
  storeButton: { minHeight: 52, justifyContent: 'center', paddingHorizontal: spacing[4] + spacing[1], borderRadius: radius.md, backgroundColor: color.surface.card },
  appBandPhone: { flexDirection: 'column', alignItems: 'stretch', paddingHorizontal: spacing[6] },
  actionsPhone: { flexDirection: 'column', alignItems: 'stretch' },
  // QR 둘을 나란히 — 폰 폭에서는 띠 가운데로
  qrRow: { flexDirection: 'row', gap: spacing[4], alignItems: 'flex-start' },
  // 단추 묶음과 QR 둘을 한 줄로 — 위 끝을 맞춘다
  getRow: { flexDirection: 'row', gap: spacing[4], alignItems: 'flex-start' },
  qrRowPhone: { justifyContent: 'center' },
  qrBlock: { alignItems: 'center', gap: spacing[2], width: 128 },
  qrLabel: { textAlign: 'center' },
  // 흰 여백(QR 의 «조용한 칸»)을 둘러야 어두운 판 위에서도 카메라가 읽는다
  qrFrame: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card },
  qr: { width: 112, height: 112 },
  // 안내 단 — 소개 한 칸을 조금 넓게, 메뉴 셋은 같은 폭
  columns: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[8], marginTop: spacing[8] + spacing[4] },
  about: { flexGrow: 1.4, flexBasis: 320, gap: spacing[3] },
  aboutText: { lineHeight: 24 },
  group: { flexGrow: 1, flexBasis: 160, gap: spacing[1] },
  aboutNarrow: { flexBasis: '100%' },
  groupNarrow: { flexBasis: 0, minWidth: 0 },
  link: { minHeight: 36, justifyContent: 'center', alignSelf: 'flex-start' },
  brandLogo: { width: 132, height: 24, alignSelf: 'flex-start' },
  // 맨 아래 줄 — 안내 단 바로 밑에. 바닥에 붙이면 그 사이가 통째로 빈다(시안 점검 2026-10-02).
  bottomRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: spacing[3], marginTop: spacing[8] + spacing[4], paddingTop: spacing[4], borderTopWidth: 1, borderTopColor: color.surface.border },
  sources: { flexShrink: 1 },
  pressed: { opacity: 0.75 },
});
