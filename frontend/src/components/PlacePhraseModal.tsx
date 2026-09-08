// 장소 카드에서 여는 한국어 말하기 모달 — 4개 탭(관광지·식당카페·택시·숙소), S15P21E201-389.
import { useEffect, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as Speech from 'expo-speech';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { defaultTabForCategory, PLACE_PHRASES, PLACE_TABS, type PlacePhrase, type PlaceTabKey } from '@/field/placePhrases';
import { Text } from './Text';

type PlacePhraseModalProps = {
  visible: boolean;
  onClose: () => void;
  category?: string | null;
};

export function PlacePhraseModal({ visible, onClose, category }: PlacePhraseModalProps) {
  const router = useRouter();
  const { tx } = useI18n();
  const [tab, setTab] = useState<PlaceTabKey>(() => defaultTabForCategory(category));
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);

  useEffect(() => {
    if (visible) { setTab(defaultTabForCategory(category)); setExpandedId(null); }
  }, [visible, category]);

  function speak(phrase: PlacePhrase, rate: number) {
    try {
      Speech.stop();
      setSpeakingId(phrase.id);
      Speech.speak(phrase.ko, { language: 'ko-KR', rate, onDone: () => setSpeakingId(null), onStopped: () => setSpeakingId(null), onError: () => setSpeakingId(null) });
    } catch {
      // 소리 기능이 없는 브라우저에서도 글자는 그대로 보인다.
      setSpeakingId(null);
    }
  }

  const phrases = PLACE_PHRASES[tab];

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('한국어로 말하기', 'Speak Korean')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.pressed]}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <View accessibilityRole="tablist" style={styles.tabRow}>
            {PLACE_TABS.map((item) => {
              const selected = tab === item.key;
              return (
                <Pressable key={item.key} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => { setTab(item.key); setExpandedId(null); }} style={[styles.tab, selected && styles.tabSelected]}>
                  <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.body}>{item.icon} {tx(item.labelKo, item.labelEn)}</Text>
                </Pressable>
              );
            })}
          </View>

          <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
            {phrases.map((phrase) => {
              const expanded = expandedId === phrase.id;
              return (
                <Pressable key={phrase.id} accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setExpandedId(expanded ? null : phrase.id)} style={[styles.phraseCard, expanded && styles.phraseCardExpanded]}>
                  <Text variant={expanded ? 'display' : 'title'} weight="bold">{phrase.ko}</Text>
                  <Text variant="caption" color={color.text.muted}>{phrase.pronunciation}</Text>
                  {expanded ? (
                    <>
                      <Text variant="body" color={color.text.body} style={styles.enText}>{phrase.en}</Text>
                      <View style={styles.speedRow}>
                        <Pressable accessibilityRole="button" accessibilityLabel={tx('보통 속도로 듣기', 'Listen at normal speed')} onPress={() => speak(phrase, 0.95)} style={styles.speedButton}>
                          <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id ? tx('재생 중', 'Playing') : tx('▶ 보통', '▶ Normal')}</Text>
                        </Pressable>
                        <Pressable accessibilityRole="button" accessibilityLabel={tx('느린 속도로 듣기', 'Listen at slow speed')} onPress={() => speak(phrase, 0.55)} style={[styles.speedButton, styles.speedButtonSlow]}>
                          <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('▶ 느리게', '▶ Slow')}</Text>
                        </Pressable>
                      </View>
                    </>
                  ) : null}
                </Pressable>
              );
            })}

            {tab === 'TAXI' ? (
              <Pressable accessibilityRole="button" onPress={() => { onClose(); router.push('/field/speak'); }} style={styles.taxiLink}>
                <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('택시 목적지 카드 전체보기 →', 'View full taxi destination card →')}</Text>
              </Pressable>
            ) : null}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 480, maxHeight: '85%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  close: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
  tabRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  tab: { minHeight: 40, flexGrow: 1, flexBasis: '45%', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  tabSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  list: { flexGrow: 0 },
  listContent: { gap: spacing[3], paddingVertical: spacing[1] },
  phraseCard: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  phraseCardExpanded: { borderColor: color.brand.orange, borderWidth: 2, backgroundColor: color.surface.warm },
  enText: { marginTop: spacing[1] },
  speedRow: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  speedButton: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  speedButtonSlow: { backgroundColor: color.text.eyebrow },
  taxiLink: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});
