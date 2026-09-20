// 카테고리별 한국어 말하기 탭+목록 — PlacePhraseModal(장소 카드에서 여는 모달)과
// app/field/speak.tsx(현장 도구에서 여는 전체 화면)가 같은 문장·같은 동작을 쓰도록
// 공유한다. 장소 카테고리가 있으면 그 탭을 기본으로 고르고, 없으면 관광지부터 보여준다.
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { speakAloud, stopSpeaking } from '@/field/speakAloud';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { defaultTabForCategory, PLACE_PHRASES, PLACE_TABS, type PlacePhrase, type PlaceTabKey } from '@/field/placePhrases';
import { Text } from './Text';

const NORMAL_RATE = 0.95;
const SLOW_RATE = 0.55;

type PlacePhraseBrowserProps = {
  category?: string | null;
  onOpenTaxiCard?: () => void;
};

export function PlacePhraseBrowser({ category, onOpenTaxiCard }: PlacePhraseBrowserProps) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const [tab, setTab] = useState<PlaceTabKey>(() => defaultTabForCategory(category));
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  // 속도(rate)도 함께 들고 있는다 — id만 보면 "느리게"를 눌러도 "보통" 버튼이 재생 중으로
  // 바뀌는 버그가 난다(둘 다 같은 phrase.id를 쓰기 때문). 어느 버튼을 눌렀는지까지 구분한다.
  const [speakingRate, setSpeakingRate] = useState<number | null>(null);
  // speak를 빠르게 다시 누르면(같은 문장의 다른 속도, 또는 다른 문장) Speech.stop이
  // 취소한 "이전" 재생의 onDone/onError가 뒤늦게 도착해 방금 시작한 재생의 상태를
  // null로 덮어쓴다. 매 호출마다 토큰을 새로 발급해 자기 차례가 아니면 무시한다.
  const playTokenRef = useRef(0);

  useEffect(() => {
    setTab(defaultTabForCategory(category));
    setExpandedId(null);
  }, [category]);

  function speak(phrase: PlacePhrase, rate: number) {
    const token = ++playTokenRef.current;
    const finish = () => { if (playTokenRef.current === token) { setSpeakingId(null); setSpeakingRate(null); } };
    try {
      stopSpeaking();
      setSpeakingId(phrase.id);
      setSpeakingRate(rate);
      speakAloud(phrase.ko, { language: 'ko-KR', rate, onDone: finish, onStopped: finish, onError: finish });
    } catch {
      // 소리 기능이 없는 브라우저에서도 글자는 그대로 보인다.
      finish();
    }
  }

  const phrases = PLACE_PHRASES[tab];

  return (
    <View style={styles.container}>
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
              {/* 사용자 실사용 리포트(2026-09-16): 여행자는 영어 뜻을 이미 알고 한국어를
                  찾으러 온다 — 한국어를 먼저 크게 보여주면 "그게 무슨 뜻인지" 되짚어야 한다.
                  뜻(en)을 먼저 보여주고, 누르면 그제서야 한국어·발음·듣기 버튼이 나오게
                  순서를 뒤집는다. */}
              {/* 뜻은 고른 언어로 — 일본어 화면에서 영어 뜻을 읽게 하지 않는다. 한국어 화면만 영어 뜻(한국어를 두 번 적을 이유가 없다). */}
              {/* 세 줄을 한꺼번에 — 뜻(고른 언어) 크게, 그 밑에 한국어와 발음. 전에는 눌러야 한국어가 나와서
                  가게에서 폰을 내밀 때 한 번 더 눌러야 했다(2026-09-21 사용자 지적). 누르면 듣기 단추만 펼친다. */}
              <Text variant={expanded ? 'display' : 'title'} weight="bold">{language === 'ko' ? phrase.en : tx(phrase.ko, phrase.en)}</Text>
              <Text variant={expanded ? 'title' : 'body'} weight="bold" color={color.text.heading} style={styles.enText}>{phrase.ko}</Text>
              {/* 발음은 읽는 사람의 글자로 — 일본어는 가타카나, 그 밖은 로마자. */}
              <Text variant="caption" color={color.text.muted}>{language === 'ja' ? phrase.pronunciationJa : phrase.pronunciation}</Text>
              {expanded ? (
                <>
                  <View style={styles.speedRow}>
                    <Pressable accessibilityRole="button" accessibilityLabel={tx('보통 속도로 듣기', 'Listen at normal speed')} onPress={() => speak(phrase, NORMAL_RATE)} style={styles.speedButton}>
                      <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id && speakingRate === NORMAL_RATE ? tx('재생 중', 'Playing') : tx('▶ 보통', '▶ Normal')}</Text>
                    </Pressable>
                    <Pressable accessibilityRole="button" accessibilityLabel={tx('느린 속도로 듣기', 'Listen at slow speed')} onPress={() => speak(phrase, SLOW_RATE)} style={[styles.speedButton, styles.speedButtonSlow]}>
                      <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id && speakingRate === SLOW_RATE ? tx('재생 중', 'Playing') : tx('▶ 느리게', '▶ Slow')}</Text>
                    </Pressable>
                  </View>
                </>
              ) : null}
            </Pressable>
          );
        })}

        {tab === 'TAXI' ? (
          <Pressable accessibilityRole="button" onPress={() => { if (onOpenTaxiCard) onOpenTaxiCard(); else router.push('/field/speak?tab=taxi'); }} style={styles.taxiLink}>
            <Text variant="caption" weight="bold" color={color.action.primary}>{tx('택시 목적지 카드 전체보기 →', 'View full taxi destination card →')}</Text>
          </Pressable>
        ) : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing[3] },
  tabRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  tab: { minHeight: 40, flexGrow: 1, flexBasis: '45%', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  tabSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  list: { flexGrow: 0 },
  listContent: { gap: spacing[3], paddingVertical: spacing[1] },
  phraseCard: { gap: spacing[1], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  phraseCardExpanded: { borderColor: color.text.heading, borderWidth: 1.5, backgroundColor: color.surface.tint },
  enText: { marginTop: spacing[1] },
  speedRow: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  speedButton: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  speedButtonSlow: { backgroundColor: color.action.secondary },
  taxiLink: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});
