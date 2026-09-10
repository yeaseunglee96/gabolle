// 16 여행 준비·날씨 — Figma 16_여행 준비·날씨 실측 그대로.
//
// 날씨·준비물은 전부 하드코딩 목업이다. 실제 기상청 API 연동 전까지는 이 값 그대로 둔다.
import { useState } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
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
  { icon: require('../../../assets/icons/common/umbrella.png'), nameKo: '접이식 우산', nameEn: 'Folding umbrella', descKo: '오후 비 예보', descEn: 'Rain forecast in the afternoon' },
  { icon: require('../../../assets/icons/common/shoes.png'), nameKo: '미끄럼 적은 신발', nameEn: 'Non-slip shoes', descKo: '흰여울 경사 구간', descEn: 'Huinnyeoul has a slope section' },
  { icon: require('../../../assets/icons/common/idcard.png'), nameKo: '해외카드·여권 사본', nameEn: 'Overseas card · passport copy', descKo: '현장 결제 대비', descEn: 'In case you need to pay on site' },
];

function DialectFlashcards() {
  const { tx } = useI18n();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);

  function listen(phrase: (typeof DIALECT_PHRASES)[number]) {
    try {
      Speech.stop();
      setSpeakingId(phrase.id);
      Speech.speak(phrase.dialect, { language: 'ko-KR', rate: 0.9, onDone: () => setSpeakingId(null), onStopped: () => setSpeakingId(null), onError: () => setSpeakingId(null) });
    } catch {
      // 소리 기능이 없는 브라우저(Web Speech API 미지원 등)에서도 카드는 그대로 둔다.
      setSpeakingId(null);
    }
  }

  return (
    <View style={styles.dialectSection}>
      <Text variant="title" weight="bold" style={styles.prepTitle}>{tx('부산 사투리 한마디', 'A word of Busan dialect')}</Text>
      <View style={styles.dialectList}>
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
                  <View style={styles.dialectMeaningRow}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('표준어', 'Standard Korean')}</Text>
                    <Text variant="body" color={color.text.body}>{phrase.standard}</Text>
                  </View>
                  <View style={styles.dialectMeaningRow}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('영문 뜻', 'English meaning')}</Text>
                    <Text variant="body" color={color.text.body}>{phrase.en}</Text>
                  </View>
                  <Text variant="caption" color={color.text.muted}>{tx(phrase.situationKo, phrase.situationEn)}</Text>
                  <Pressable accessibilityRole="button" accessibilityLabel={tx(`${phrase.dialect} 발음 듣기`, `Listen to ${phrase.dialect}`)} onPress={() => listen(phrase)} style={styles.listenButton}>
                    <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id ? tx('재생 중', 'Playing') : tx('▶ 듣기', '▶ Listen')}</Text>
                  </Pressable>
                </>
              ) : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}

export default function Prepare() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerCopy}>
          <Text variant="eyebrow" weight="bold">
            {tx('여행 전 · 8월 24일 출발', 'Before the trip · Departing Aug 24')}
          </Text>
          <Text variant="display" weight="bold" style={styles.title}>
            {tx('부산 여행 준비', 'Getting ready for Busan')}
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
              {tx('맑음 · 체감 25°', 'Clear · Feels like 25°')}
            </Text>
            <Text variant="caption" weight="medium" color={color.state.success}>
              {tx('미세먼지 좋음', 'Fine dust: Good')}
            </Text>
          </View>
        </View>
        <Text variant="body" weight="medium" style={styles.weatherRain}>
          {tx('오후 5시 강수 60% · 일몰 19:04', '60% chance of rain at 5 PM · Sunset 19:04')}
        </Text>
      </View>

      <View style={styles.prepCard}>
        <Text variant="title" weight="bold" style={styles.prepTitle}>
          {tx('가볼래가 챙긴 준비물', 'What GABOLLE packed for you')}
        </Text>
        {PREP_ITEMS.map((item) => (
          <View key={item.nameKo} style={styles.prepRow}>
            <Image source={item.icon} resizeMode="contain" style={styles.prepIcon} />
            <View style={styles.prepBody}>
              <Text variant="body" weight="bold">
                {tx(item.nameKo, item.nameEn)}
              </Text>
            </View>
            <Text variant="caption" style={styles.prepDesc}>
              {tx(item.descKo, item.descEn)}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.rainCard}>
        <Text variant="caption" weight="bold" color={color.text.accent}>
          {tx('비 예보 대응', 'Responding to the rain forecast')}
        </Text>
        <Text variant="title" weight="bold" style={styles.rainTitle}>
          {tx('야외 1곳을 실내 코스로 바꿀까요?', 'Swap 1 outdoor stop for an indoor one?')}
        </Text>
        <Text variant="caption" style={styles.rainDesc}>
          {tx('흰여울 → 국립해양박물관 · 이동 12분 감소', 'Huinnyeoul → National Maritime Museum · 12 min less travel')}
        </Text>
      </View>

      <DialectFlashcards />

      <Button
        label={tx('대체 일정 미리보기', 'Preview the alternative plan')}
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
  prepIcon: {
    width: 26,
    height: 26,
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
  dialectList: {
    gap: spacing[3],
    marginTop: spacing[1],
  },
  dialectCard: {
    minHeight: 84,
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
    borderColor: color.brand.orange,
    borderWidth: 2,
    backgroundColor: color.surface.warm,
  },
  dialectMeaningRow: {
    gap: spacing[1],
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
