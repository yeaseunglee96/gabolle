// 17 현장 말하기·택시 카드 — Figma 17_현장 말하기·택시 카드 실측 그대로.
//
// 번역 업체 계약과 무관하게 기기 TTS·클립보드·지도 링크로 완결할 수 있는 현장 기능은
// Expo 네이티브 API로 실제 동작시킨다.
import { useRef, useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';
import * as Speech from 'expo-speech';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { useI18n } from '@/i18n';

type Tab = 'speak' | 'taxi';
type PlaySpeed = 'normal' | 'slow' | null;

const MAP_APPS = [
  { id: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', url: `https://map.kakao.com/link/search/${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'google', labelKo: 'Google', labelEn: 'Google', url: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'apple', labelKo: 'Apple 지도', labelEn: 'Apple Maps', url: `https://maps.apple.com/?q=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
] as const;
// 🔴 이 한국어 문장·주소는 번역 대상이 아니다 — 실제로 한국어로 말하거나 택시 기사에게
// 보여줄 한국어 주소라, 영어로 바뀌면 현장에서 그대로 쓸모가 없어진다.
const KOREAN_PHRASE = '사진 한 장 부탁드려도 될까요?';
const TAXI_ADDRESS = '부산 영도구 영선동4가 605-3';
const speakerIcon = require('../../assets/icons/common/speaker.png');
const taxiIcon = require('../../assets/icons/common/taxi.png');

export default function Speak() {
  const { tx } = useI18n();
  const [tab, setTab] = useState<Tab>('speak');
  const [playing, setPlaying] = useState<PlaySpeed>(null);
  const [copied, setCopied] = useState(false);
  // play()를 빠르게 다시 누르면 Speech.stop()이 취소한 "이전" 재생의 onDone/onError가
  // 나중에 도착해서 방금 시작한 재생의 상태를 null로 덮어쓴다 — 눌러도 반응이 없어
  // 보이는 원인이었다. 매 호출마다 토큰을 새로 발급해 자기 차례가 아니면 무시한다.
  const playTokenRef = useRef(0);

  function play(speed: PlaySpeed) {
    Speech.stop();
    const token = ++playTokenRef.current;
    setPlaying(speed);
    const finish = () => { if (playTokenRef.current === token) setPlaying(null); };
    Speech.speak(KOREAN_PHRASE, { language: 'ko-KR', rate: speed === 'slow' ? 0.65 : 0.95, onDone: finish, onStopped: finish, onError: finish });
  }

  async function copyAddress() {
    await Clipboard.setStringAsync(TAXI_ADDRESS);
    setCopied(true);
  }

  return (
    <Screen scroll>
      <Text variant="eyebrow" weight="bold">
        {tx('여행 중 · 흰여울문화마을', 'Traveling · Huinnyeoul Culture Village')}
      </Text>
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
        <View style={styles.phraseCard}>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            {tx('사진 촬영 요청', 'Requesting a photo')}
          </Text>
          <Text variant="display" weight="bold" style={styles.phraseKorean}>
            사진 한 장{'\n'}부탁드려도 될까요?
          </Text>
          <Text variant="caption" style={styles.phraseRoman}>
            sajin han jang butakdeuryeodo doelkkayo?
          </Text>
          <View style={styles.playRow}>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('한국어 문장 일반 속도로 듣기', 'Listen to the Korean phrase at normal speed')} style={styles.playButton} onPress={() => play('normal')}>
              <Text variant="body" weight="bold" color={color.text.onAction}>
                {playing === 'normal' ? tx('▶ 재생 중', '▶ Playing') : tx('▶  일반 속도', '▶  Normal speed')}
              </Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('한국어 문장 천천히 듣기', 'Listen to the Korean phrase slowly')} style={styles.slowButton} onPress={() => play('slow')}>
              <Text variant="body" weight="bold" color={color.action.field}>
                {playing === 'slow' ? tx('½× 재생 중', '½× Playing') : tx('½×  천천히', '½×  Slowly')}
              </Text>
            </Pressable>
          </View>
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
  phraseCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.soft,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  phraseKorean: {
    marginTop: spacing[1],
  },
  phraseRoman: {
    color: color.text.body,
  },
  playRow: {
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[2],
  },
  playButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: color.action.field,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
  },
  slowButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
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
