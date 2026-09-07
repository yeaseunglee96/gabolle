// 16 여행 준비·날씨 — Figma 16_여행 준비·날씨 실측 그대로.
//
// 날씨·준비물은 전부 하드코딩 목업이다. 실제 기상청 API 연동 전까지는 이 값 그대로 둔다.
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Speech from 'expo-speech';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { LanguageBadge } from '@/components/LanguageBadge';
import { useI18n } from '@/i18n';
import { DIALECT_PHRASES } from '@/discovery/dialectPhrases';

const PREP_ITEMS = [
  { icon: '☂', name: '접이식 우산', desc: '오후 비 예보' },
  { icon: '👟', name: '미끄럼 적은 신발', desc: '흰여울 경사 구간' },
  { icon: '🪪', name: '해외카드·여권 사본', desc: '현장 결제 대비' },
];

function DialectFlashcards() {
  const { tx } = useI18n();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);

  function listen(phrase: (typeof DIALECT_PHRASES)[number]) {
    Speech.stop();
    setSpeakingId(phrase.id);
    Speech.speak(phrase.dialect, { language: 'ko-KR', rate: 0.9, onDone: () => setSpeakingId(null), onStopped: () => setSpeakingId(null), onError: () => setSpeakingId(null) });
  }

  return (
    <View style={styles.dialectSection}>
      <Text variant="title" weight="bold" style={styles.prepTitle}>{tx('부산 사투리 한마디', 'A word of Busan dialect')}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dialectScroll}>
        {DIALECT_PHRASES.map((phrase) => {
          const expanded = expandedId === phrase.id;
          return (
            <View key={phrase.id} style={[styles.dialectCard, expanded && styles.dialectCardExpanded]}>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ expanded }}
                onPress={() => setExpandedId(expanded ? null : phrase.id)}
                style={styles.dialectCardHeader}
              >
                <Text variant={expanded ? 'display' : 'title'} weight="bold">{phrase.dialect}</Text>
                {!expanded ? <Text variant="caption" color={color.text.muted}>{tx('눌러서 뜻 보기', 'Tap to see meaning')}</Text> : null}
              </Pressable>
              {expanded ? (
                <>
                  <Text variant="body" color={color.text.body}>{tx(phrase.standard, phrase.en)}</Text>
                  <Text variant="caption" color={color.text.muted}>{tx(phrase.situationKo, phrase.situationEn)}</Text>
                  <Pressable accessibilityRole="button" accessibilityLabel={tx(`${phrase.dialect} 발음 듣기`, `Listen to ${phrase.dialect}`)} onPress={() => listen(phrase)} style={styles.listenButton}>
                    <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id ? tx('재생 중', 'Playing') : tx('▶ 듣기', '▶ Listen')}</Text>
                  </Pressable>
                </>
              ) : null}
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}

export default function Prepare() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerCopy}>
          <Text variant="eyebrow" weight="bold">
            여행 전 · 8월 24일 출발
          </Text>
          <Text variant="display" weight="bold" style={styles.title}>
            부산 여행 준비
          </Text>
        </View>
        <LanguageBadge />
      </View>

      <View style={styles.weatherCard}>
        <View style={styles.weatherTopRow}>
          <Text variant="hero" weight="bold" color={color.text.heading}>
            24°
          </Text>
          <View style={styles.weatherStatus}>
            <Text variant="body" weight="bold">
              맑음 · 체감 25°
            </Text>
            <Text variant="caption" weight="medium" color={color.state.success}>
              미세먼지 좋음
            </Text>
          </View>
        </View>
        <Text variant="body" weight="medium" style={styles.weatherRain}>
          오후 5시 강수 60% · 일몰 19:04
        </Text>
      </View>

      <View style={styles.prepCard}>
        <Text variant="title" weight="bold" style={styles.prepTitle}>
          가볼래가 챙긴 준비물
        </Text>
        {PREP_ITEMS.map((item) => (
          <View key={item.name} style={styles.prepRow}>
            <Text variant="title">{item.icon}</Text>
            <View style={styles.prepBody}>
              <Text variant="body" weight="bold">
                {item.name}
              </Text>
            </View>
            <Text variant="caption" style={styles.prepDesc}>
              {item.desc}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.rainCard}>
        <Text variant="caption" weight="bold" color={color.text.accent}>
          비 예보 대응
        </Text>
        <Text variant="title" weight="bold" style={styles.rainTitle}>
          야외 1곳을 실내 코스로 바꿀까요?
        </Text>
        <Text variant="caption" style={styles.rainDesc}>
          흰여울 → 국립해양박물관 · 이동 12분 감소
        </Text>
      </View>

      <DialectFlashcards />

      <Button
        label="대체 일정 미리보기"
        variant="field"
        containerStyle={styles.cta}
        onPress={() => router.push(`/${tripId}/result`)}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  headerCopy: {
    flex: 1,
    gap: spacing[1],
  },
  title: {
    marginTop: spacing[1],
  },
  weatherCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.tint,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  weatherTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  weatherStatus: {
    gap: spacing[1],
  },
  weatherRain: {
    color: color.text.heading,
  },
  prepCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[3],
  },
  prepTitle: {
    marginBottom: spacing[1],
  },
  prepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  prepBody: {
    flex: 1,
  },
  prepDesc: {
    color: color.text.body,
  },
  rainCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  rainTitle: {
    marginTop: spacing[1],
  },
  rainDesc: {
    color: color.text.body,
  },
  cta: {
    marginTop: spacing[6],
  },
  dialectSection: {
    marginTop: spacing[4],
  },
  dialectScroll: {
    gap: spacing[3],
    paddingTop: spacing[1],
    paddingBottom: spacing[1],
  },
  dialectCard: {
    width: 160,
    minHeight: 120,
    justifyContent: 'center',
    gap: spacing[2],
    padding: spacing[4],
    borderRadius: radius.lg,
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  dialectCardHeader: {
    gap: spacing[2],
  },
  dialectCardExpanded: {
    width: 220,
    borderColor: color.brand.orange,
    borderWidth: 2,
    backgroundColor: color.surface.warm,
  },
  listenButton: {
    marginTop: spacing[1],
    minHeight: 36,
    paddingHorizontal: spacing[3],
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'flex-start',
    backgroundColor: color.brand.navy,
  },
});
