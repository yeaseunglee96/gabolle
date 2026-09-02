import { useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { type LanguageCode, type MobilityCode, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';

type SheetKind = 'language' | 'mobility' | null;

const LANGUAGES: { code: LanguageCode; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
  { code: 'ja', label: '日本語' },
  { code: 'zh-Hans', label: '简体中文' },
  { code: 'zh-Hant', label: '繁體中文' },
];

const MOBILITIES: { code: MobilityCode; label: string; sentence: string }[] = [
  { code: 'none', label: '해당 없음', sentence: '누구나 갈 수 있는 곳으로' },
  { code: 'wheelchair', label: '휠체어', sentence: '휠체어로 편하게 갈 수 있는 곳으로' },
  { code: 'stroller', label: '유아차', sentence: '유아차로 편하게 갈 수 있는 곳으로' },
  { code: 'slow', label: '천천히', sentence: '천천히 걸어도 좋은 곳으로' },
];

const MAX_CONTENT_WIDTH = 520;

export default function Welcome() {
  const router = useRouter();
  const { kind } = useLayout();
  const { setPreferences } = useOnboardingPreferences();
  const [language, setLanguage] = useState<LanguageCode>('ko');
  const [mobility, setMobility] = useState<MobilityCode>('none');
  const [sheet, setSheet] = useState<SheetKind>(null);

  const languageLabel = LANGUAGES.find((item) => item.code === language)!.label;
  const mobilitySentence = MOBILITIES.find((item) => item.code === mobility)!.sentence;
  const options = sheet === 'language' ? LANGUAGES : MOBILITIES;

  function continueWithGoogle() {
    setPreferences(language, mobility);
    router.push({ pathname: '/age-gate', params: { language, mobility } });
  }

  return (
    <View style={styles.screen}>
      <SafeAreaView style={[styles.frame, kind === 'tablet' && styles.frameTablet]}>
        <View style={styles.brandRow}>
          <View style={styles.brandMark}>
            <Text variant="title" weight="bold" color={color.text.onAction}>가</Text>
          </View>
          <Text variant="body" weight="bold">GABOLLE · 가볼래</Text>
        </View>

        <View style={styles.hero}>
          <Text variant="eyebrow" weight="bold" color={color.text.eyebrow}>부산을 내 방식대로 걷는 여행</Text>
          <Text variant="hero" weight="bold" style={styles.sentence}>
            <TextSlot label={`${languageLabel}로`} onPress={() => setSheet('language')} />
            {',\n'}
            <TextSlot label={mobilitySentence} onPress={() => setSheet('mobility')} />
            {'\n'}부산을 걸을 거예요.
          </Text>
          <Text variant="body" color={color.text.body} style={styles.description}>
            언어와 이동 조건을 먼저 알려주면, 갈 수 있는 장소부터 일정에 담아드려요.
          </Text>
        </View>

        <View style={styles.footer}>
          <View style={styles.factCard}>
            <Text variant="body" weight="bold" color={color.text.heading}>6.7만 개</Text>
            <Text variant="caption" color={color.text.body}>부산 보행 구간을 직접 비교해 골라요</Text>
          </View>
          <Button label="Google로 시작하기" onPress={continueWithGoogle} />
          <Text variant="caption" style={styles.footerNote}>선택한 언어와 이동 조건은 다음 화면에도 이어져요.</Text>
        </View>
      </SafeAreaView>

      <Modal visible={sheet !== null} transparent animationType="slide" onRequestClose={() => setSheet(null)}>
        <Pressable style={styles.backdrop} onPress={() => setSheet(null)}>
          <Pressable style={styles.modalContent} onPress={(event) => event.stopPropagation()}>
            <View style={styles.handle} />
            <Text variant="title" weight="bold">
              {sheet === 'language' ? '어떤 언어로 여행할까요?' : '어떻게 걷는 여행인가요?'}
            </Text>
            <Text variant="caption" color={color.text.body}>
              {sheet === 'language' ? '앱 전체 안내에 적용돼요.' : '갈 수 있는 장소와 이동 부담을 판단해요.'}
            </Text>
            <View style={styles.optionList} accessibilityRole="radiogroup">
              {options.map((item) => {
                const selected = sheet === 'language' ? item.code === language : item.code === mobility;
                return (
                  <Pressable
                    key={item.code}
                    accessibilityRole="radio"
                    accessibilityState={{ selected }}
                    onPress={() => {
                      if (sheet === 'language') setLanguage(item.code as LanguageCode);
                      if (sheet === 'mobility') setMobility(item.code as MobilityCode);
                      setSheet(null);
                    }}
                    style={[styles.option, selected && styles.optionSelected]}
                  >
                    <Text variant="body" weight={selected ? 'bold' : 'medium'}>{item.label}</Text>
                    <View style={[styles.radio, selected && styles.radioSelected]}>
                      {selected && <View style={styles.radioDot} />}
                    </View>
                  </Pressable>
                );
              })}
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

function TextSlot({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Text variant="hero" weight="bold" color={color.text.eyebrow} accessibilityRole="button" onPress={onPress} style={styles.slot}>
      {label}
    </Text>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.canvas },
  frame: { flex: 1, width: '100%', paddingHorizontal: spacing[6], paddingBottom: spacing[4] },
  frameTablet: { alignSelf: 'center', maxWidth: MAX_CONTENT_WIDTH },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingTop: spacing[2] },
  brandMark: { width: 34, height: 34, borderRadius: radius.md, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  hero: { flex: 1, justifyContent: 'center' },
  sentence: { marginTop: spacing[3], color: color.text.heading },
  slot: { textDecorationLine: 'underline', textDecorationStyle: 'dotted' },
  description: { marginTop: spacing[4], maxWidth: 410 },
  footer: { gap: spacing[3] },
  factCard: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], backgroundColor: color.surface.soft, borderRadius: radius.md, padding: spacing[3] },
  footerNote: { textAlign: 'center' },
  backdrop: { flex: 1, justifyContent: 'flex-end', alignItems: 'center', backgroundColor: 'rgba(21, 34, 56, 0.38)' },
  modalContent: { width: '100%', maxWidth: MAX_CONTENT_WIDTH, backgroundColor: color.surface.card, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, paddingHorizontal: spacing[6], paddingTop: spacing[3], paddingBottom: spacing[8], gap: spacing[2] },
  handle: { width: 42, height: 4, alignSelf: 'center', borderRadius: radius.full, backgroundColor: color.surface.field, marginBottom: spacing[3] },
  optionList: { marginTop: spacing[3], gap: spacing[2] },
  option: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, paddingHorizontal: spacing[4] },
  optionSelected: { backgroundColor: color.surface.tint, borderColor: color.action.primary },
  radio: { width: 22, height: 22, borderRadius: radius.full, borderWidth: 1.5, borderColor: color.text.muted, alignItems: 'center', justifyContent: 'center' },
  radioSelected: { borderColor: color.action.primary },
  radioDot: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: color.action.primary },
});
