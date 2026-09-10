// 17 현장 말하기·택시 카드 — Figma 17_현장 말하기·택시 카드 실측 그대로.
//
// "말하기" 탭은 더 이상 문장 하나만 보여주지 않는다 — 장소 카드 모달(PlacePhraseModal)과
// 같은 PlacePhraseBrowser 를 써서 관광지·식당카페·택시·숙소 문장을 전부 보여준다.
// 홈·챗봇·현장 도구 어디서 들어와도 같은 경험이 되도록 맞춘 것(구조 정리, UX 통합).
// 번역 업체 계약과 무관하게 기기 TTS·클립보드·지도 링크로 완결할 수 있는 택시 카드는
// 그대로 Expo 네이티브 API로 동작시킨다.
import { useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import * as Clipboard from 'expo-clipboard';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { PlacePhraseBrowser } from '@/components/PlacePhraseBrowser';
import { useI18n } from '@/i18n';

type Tab = 'speak' | 'taxi';

const MAP_APPS = [
  { id: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', url: `https://map.kakao.com/link/search/${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'google', labelKo: 'Google', labelEn: 'Google', url: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'apple', labelKo: 'Apple 지도', labelEn: 'Apple Maps', url: `https://maps.apple.com/?q=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
] as const;
// 🔴 이 한국어 주소는 번역 대상이 아니다 — 실제로 택시 기사에게 보여줄 한국어 주소라,
// 영어로 바뀌면 현장에서 그대로 쓸모가 없어진다.
const TAXI_ADDRESS = '부산 영도구 영선동4가 605-3';
const taxiIcon = require('../../assets/icons/common/taxi.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

export default function Speak() {
  const { tx } = useI18n();
  const { tab: initialTab } = useLocalSearchParams<{ tab?: string }>();
  const [tab, setTab] = useState<Tab>(initialTab === 'taxi' ? 'taxi' : 'speak');
  const [copied, setCopied] = useState(false);

  async function copyAddress() {
    await Clipboard.setStringAsync(TAXI_ADDRESS);
    setCopied(true);
  }

  return (
    <Screen scroll>
      <Eyebrow>
        {tx('여행 중 · 흰여울문화마을', 'Traveling · Huinnyeoul Culture Village')}
      </Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        {tx('현장에서 바로 쓰기', 'Use it right now')}
      </Text>

      <View style={styles.segment}>
        <Pressable
          style={[styles.segmentItem, tab === 'speak' && styles.segmentItemActive]}
          onPress={() => setTab('speak')}
        >
          <View style={styles.segmentLabel}>
            <Image source={speakerIcon} resizeMode="contain" style={styles.segmentIcon} />
            <Text variant="caption" weight="bold" color={tab === 'speak' ? color.text.accent : color.text.body}>
              {tx('말하기', 'Speak')}
            </Text>
          </View>
        </Pressable>
        <Pressable
          style={[styles.segmentItem, tab === 'taxi' && styles.segmentItemActive]}
          onPress={() => setTab('taxi')}
        >
          <View style={styles.segmentLabel}>
            <Image source={taxiIcon} resizeMode="contain" style={styles.segmentIcon} />
            <Text variant="caption" weight="bold" color={tab === 'taxi' ? color.text.accent : color.text.body}>
              {tx('택시 카드', 'Taxi card')}
            </Text>
          </View>
        </Pressable>
      </View>

      {tab === 'speak' ? (
        <View style={styles.speakSection}>
          <PlacePhraseBrowser onOpenTaxiCard={() => setTab('taxi')} />
        </View>
      ) : (
        <View style={styles.taxiCard}>
          <Text variant="body" weight="bold">
            {tx('택시 기사님께 보여주세요', 'Show this to the taxi driver')}
          </Text>
          <Text variant="title" weight="bold" color={color.action.field} style={styles.taxiPlace}>
            {tx('흰여울문화마을 안내센터', 'Huinnyeoul Culture Village Info Center')}
          </Text>
          <View style={styles.taxiAddressRow}>
            <Text variant="body" weight="medium" style={styles.taxiAddress}>
              {TAXI_ADDRESS}
            </Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('주소 복사', 'Copy address')} onPress={() => void copyAddress()}>
              <Text variant="caption" weight="bold" color={color.text.accent}>
                {copied ? tx('복사됨 ✓', 'Copied ✓') : tx('주소 복사', 'Copy address')}
              </Text>
            </Pressable>
          </View>
          <Text variant="caption" style={styles.taxiNotice}>
            {tx('※ 하차 후 경사 없는 우회 진입로 안내', '※ After getting off, use the step-free detour entrance')}
          </Text>
        </View>
      )}

      {tab === 'taxi' ? (
        <>
          <Text variant="body" weight="bold" style={styles.mapAppsTitle}>
            {tx('길찾기 앱으로 열기', 'Open in a map app')}
          </Text>
          <View style={styles.mapAppsRow}>
            {MAP_APPS.map((app) => (
              <Pressable accessibilityRole="link" accessibilityLabel={tx(`${app.labelKo}에서 목적지 열기`, `Open destination in ${app.labelEn}`)} key={app.id} onPress={() => void Linking.openURL(app.url)} style={({ pressed }) => [styles.mapAppButton, pressed && styles.pressed]}>
                <Text variant="body" weight="bold" color={color.action.field}>
                  {tx(app.labelKo, app.labelEn)}
                </Text>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  segment: {
    flexDirection: 'row',
    backgroundColor: color.surface.soft,
    borderRadius: radius.lg,
    padding: spacing[1],
    gap: spacing[1],
  },
  segmentItem: {
    flex: 1,
    alignItems: 'center',
    borderRadius: radius.md,
    paddingVertical: spacing[2],
  },
  segmentItemActive: {
    backgroundColor: color.surface.card,
  },
  segmentLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  segmentIcon: {
    width: 14,
    height: 14,
  },
  speakSection: {
    marginTop: spacing[4],
  },
  taxiCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[1],
  },
  taxiPlace: {
    marginTop: spacing[1],
  },
  taxiAddressRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[1],
  },
  taxiAddress: {
    flex: 1,
  },
  taxiNotice: {
    marginTop: spacing[2],
    color: color.text.body,
  },
  mapAppsTitle: {
    marginTop: spacing[6],
    marginBottom: spacing[3],
  },
  mapAppsRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  mapAppButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
  },
  pressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
});
