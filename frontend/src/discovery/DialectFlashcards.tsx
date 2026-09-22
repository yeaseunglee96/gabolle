// 부산 사투리 카드 — 뜻은 고른 언어로, 소리는 진짜 경상도 목소리로. S15P21E201-1422.
//
// 전에는 여행 준비물 화면(app/(trip)/[id]/prepare.tsx) 안에만 있어 찾기 어려웠고, 소리는 기기 TTS
// (서울말 엔진)라 사투리 글을 표준어 억양으로 읽었다. 이제 독립 화면(app/field/dialect.tsx)의 주인공이고,
// 미리 만든 개나리(여)·용식이(남) 목소리를 튼다. 클립이 없는 문장만 기기 TTS 로 내려간다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { DIALECT_PHRASES, type DialectPhrase } from '@/discovery/dialectPhrases';
import { hasDialectClip, loadDialectVoice, playDialectClip, saveDialectVoice, stopDialectClip, type DialectVoice } from '@/field/dialectVoice';
import { speakAloud, stopSpeaking } from '@/field/speakAloud';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

export function DialectFlashcards({ showTitle = true }: { showTitle?: boolean }) {
  const { tx, language } = useI18n();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [voice, setVoice] = useState<DialectVoice>('FEMALE');
  useEffect(() => { void loadDialectVoice().then(setVoice); return () => { stopDialectClip(); stopSpeaking(); }; }, []);

  function pickVoice(next: DialectVoice) { setVoice(next); void saveDialectVoice(next); }

  async function listen(phrase: DialectPhrase) {
    stopDialectClip(); stopSpeaking();
    setSpeakingId(phrase.id);
    const done = () => setSpeakingId((id) => (id === phrase.id ? null : id));
    if (hasDialectClip(phrase.id) && await playDialectClip(phrase.id, voice, done)) return;
    try {
      speakAloud(phrase.dialect, { language: 'ko-KR', rate: 0.9, onDone: done, onStopped: done, onError: done });
    } catch {
      // 소리 기능이 없는 브라우저(Web Speech API 미지원 등)에서도 카드는 그대로 둔다.
      done();
    }
  }

  return (
    <View style={styles.section}>
      {showTitle ? <Text variant="title" weight="bold">{tx('부산 사투리 한마디', 'A word of Busan dialect')}</Text> : null}
      {/* 목소리 고르기 — 둘 다 진짜 경상도 억양(타입캐스트 개나리·용식이). 고른 것은 기기에 남는다. */}
      <View accessibilityRole="radiogroup" style={styles.voiceRow}>
        <Text variant="caption" color={color.text.muted}>{tx('목소리', 'Voice')}</Text>
        {([['FEMALE', tx('개나리 (여)', 'Nari (F)')], ['MALE', tx('용식이 (남)', 'Yongsik (M)')]] as const).map(([value, label]) => (
          <Pressable key={value} accessibilityRole="radio" accessibilityState={{ checked: voice === value }} onPress={() => pickVoice(value)} style={[styles.voiceChip, voice === value && styles.voiceChipOn]}>
            <Text variant="caption" weight="bold" color={voice === value ? color.text.onAction : color.text.body}>{label}</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.list}>
        {DIALECT_PHRASES.map((phrase) => {
          const expanded = expandedId === phrase.id;
          return (
            <View key={phrase.id} style={[styles.card, expanded && styles.cardExpanded]}>
              <Pressable accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setExpandedId(expanded ? null : phrase.id)} style={styles.cardHeader}>
                <Text variant={expanded ? 'display' : 'title'} weight="bold">{phrase.dialect}</Text>
                {!expanded ? <Text variant="caption" color={color.text.muted}>{tx('눌러서 뜻 보기', 'Tap to see meaning')}</Text> : null}
              </Pressable>
              {expanded ? (
                <>
                  {/* 🔴 뜻은 고른 언어로 — 외국인은 자기 말로 뜻을 보고, 누르면 한국어 사투리 소리를 듣는다.
                      한국어 사용자에게는 표준어 뜻이 그 자리다(같은 tx 가 ko 면 표준어를 돌려준다). */}
                  <View style={styles.meaningRow}>
                    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{language === 'ko' ? tx('표준어', 'Meaning') : tx('뜻', 'Meaning')}</Text>
                    <Text variant="body" color={color.text.body}>{tx(phrase.standard, phrase.en)}</Text>
                  </View>
                  {language !== 'ko' ? (
                    <View style={styles.meaningRow}>
                      <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('표준어', 'Standard Korean')}</Text>
                      <Text variant="body" color={color.text.body}>{phrase.standard}</Text>
                    </View>
                  ) : null}
                  <Text variant="caption" color={color.text.muted}>{tx(phrase.situationKo, phrase.situationEn)}</Text>
                  <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 발음 듣기', 'Listen to %s', phrase.dialect)} onPress={() => void listen(phrase)} style={styles.listenButton}>
                    <Text variant="caption" weight="bold" color={color.text.onAction}>{speakingId === phrase.id ? tx('재생 중', 'Playing') : tx('▶ 부산 사투리로 듣기', '▶ Hear it in Busan dialect')}</Text>
                  </Pressable>
                </>
              ) : null}
            </View>
          );
        })}
      </View>
      <Text variant="caption" color={color.text.muted}>{tx('목소리: 타입캐스트(Typecast) AI 음성 「개나리」·「용식이」', 'Voices: Typecast AI voices "Nari" and "Yongsik"')}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: spacing[3] },
  voiceRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  voiceChip: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, justifyContent: 'center' },
  voiceChipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  list: { gap: spacing[3] },
  card: { minHeight: 84, justifyContent: 'center', gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  cardHeader: { gap: spacing[2] },
  cardExpanded: { borderColor: color.action.secondary, borderWidth: 1.5, backgroundColor: color.surface.tint },
  meaningRow: { gap: spacing[1] },
  listenButton: { marginTop: spacing[1], minHeight: 40, paddingHorizontal: spacing[4], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', alignSelf: 'flex-start', backgroundColor: color.brand.navy },
});
