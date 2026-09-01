// 17 현장 말하기·택시 카드 — Figma 17_현장 말하기·택시 카드 실측 그대로.
//
// 번역·음성 API 업체가 아직 안 정해졌다(Jira S15P21E201-77). 그래서 "말하기" 탭의
// 재생 버튼은 실제 TTS 를 부르지 않고 눌린 상태만 로컬로 바꾼다 — UI 와 목업 데이터만 있다.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';

type Tab = 'speak' | 'taxi';
type PlaySpeed = 'normal' | 'slow' | null;

const MAP_APPS = ['카카오맵', 'Google', 'Apple 지도'];

export default function Speak() {
  const [tab, setTab] = useState<Tab>('speak');
  const [playing, setPlaying] = useState<PlaySpeed>(null);
  const [copied, setCopied] = useState(false);

  function play(speed: PlaySpeed) {
    // TODO: 실제 TTS 재생 미착수(업체 미정) — 버튼 상태만 바꾼다.
    setPlaying(speed);
  }

  function copyAddress() {
    // TODO: 클립보드 복사는 별도 패키지가 필요하다(새 의존성 추가 금지 방침이라 보류) — 확인 표시만 한다.
    setCopied(true);
  }

  return (
    <Screen scroll>
      <Text variant="eyebrow" weight="bold">
        여행 중 · 흰여울문화마을
      </Text>
      <Text variant="display" weight="bold" style={styles.title}>
        현장에서 바로 쓰기
      </Text>

      <View style={styles.segment}>
        <Pressable
          style={[styles.segmentItem, tab === 'speak' && styles.segmentItemActive]}
          onPress={() => setTab('speak')}
        >
          <Text variant="caption" weight="bold" color={tab === 'speak' ? color.text.accent : color.text.body}>
            🔊 말하기
          </Text>
        </Pressable>
        <Pressable
          style={[styles.segmentItem, tab === 'taxi' && styles.segmentItemActive]}
          onPress={() => setTab('taxi')}
        >
          <Text variant="caption" weight="bold" color={tab === 'taxi' ? color.text.accent : color.text.body}>
            🚕 택시 카드
          </Text>
        </Pressable>
      </View>

      {tab === 'speak' ? (
        <View style={styles.phraseCard}>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            사진 촬영 요청
          </Text>
          <Text variant="display" weight="bold" style={styles.phraseKorean}>
            사진 한 장{'\n'}부탁드려도 될까요?
          </Text>
          <Text variant="caption" style={styles.phraseRoman}>
            sajin han jang butakdeuryeodo doelkkayo?
          </Text>
          <View style={styles.playRow}>
            <Pressable style={styles.playButton} onPress={() => play('normal')}>
              <Text variant="body" weight="bold" color={color.text.onAction}>
                {playing === 'normal' ? '▶ 재생 중' : '▶  일반 속도'}
              </Text>
            </Pressable>
            <Pressable style={styles.slowButton} onPress={() => play('slow')}>
              <Text variant="body" weight="bold" color={color.action.field}>
                {playing === 'slow' ? '½× 재생 중' : '½×  천천히'}
              </Text>
            </Pressable>
          </View>
        </View>
      ) : (
        <View style={styles.taxiCard}>
          <Text variant="body" weight="bold">
            택시 기사님께 보여주세요
          </Text>
          <Text variant="title" weight="bold" color={color.action.field} style={styles.taxiPlace}>
            흰여울문화마을 안내센터
          </Text>
          <View style={styles.taxiAddressRow}>
            <Text variant="body" weight="medium" style={styles.taxiAddress}>
              부산 영도구 영선동4가 605-3
            </Text>
            <Pressable onPress={copyAddress}>
              <Text variant="caption" weight="bold" color={color.text.accent}>
                {copied ? '복사됨 ✓' : '주소 복사'}
              </Text>
            </Pressable>
          </View>
          <Text variant="caption" style={styles.taxiNotice}>
            ※ 하차 후 경사 없는 우회 진입로 안내
          </Text>
        </View>
      )}

      <Text variant="body" weight="bold" style={styles.mapAppsTitle}>
        길찾기 앱으로 열기
      </Text>
      <View style={styles.mapAppsRow}>
        {MAP_APPS.map((app) => (
          // TODO: 지도 앱 딥링크 URL 스킴 미정 — 지금은 눌러도 이동하지 않는다.
          <View key={app} style={styles.mapAppButton}>
            <Text variant="body" weight="bold" color={color.action.field}>
              {app}
            </Text>
          </View>
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
});
