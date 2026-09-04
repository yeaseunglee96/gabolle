// 신규 화면 — Figma 23 화면 표에 없다. 원본 요청서에서 새로 추가된 자리라 최대한 단순하게 만든다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { parseLanguage, parseMobility, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

function optionalParse<T>(value: string | string[] | undefined, parse: (candidate: string | string[] | undefined) => T) {
  try {
    return parse(value);
  } catch {
    return undefined;
  }
}

export default function AgeGate() {
  const router = useRouter();
  const params = useLocalSearchParams<{ language?: string; mobility?: string }>();
  const preferences = useOnboardingPreferences();
  const { tx } = useI18n();
  const { kind } = useLayout();
  const [checked, setChecked] = useState(false);

  const queryLanguage = optionalParse(params.language, parseLanguage);
  const queryMobility = optionalParse(params.mobility, parseMobility);
  const language = queryLanguage ?? preferences.language;
  const mobility = queryMobility ?? preferences.mobility;

  useEffect(() => {
    if ((queryLanguage || queryMobility)
      && (language !== preferences.language || mobility !== preferences.mobility)) {
      preferences.setPreferences(language, mobility);
    }
  }, [language, mobility, preferences, queryLanguage, queryMobility]);

  function continueOnboarding() {
    preferences.setPreferences(language, mobility);
    router.replace({ pathname: '/sign-in', params: { returnTo: '/permissions' } });
  }

  return (
    <Screen style={kind === 'tablet' ? styles.webCanvas : styles.canvas}>
      <View style={[styles.panel, kind === 'tablet' && styles.webPanel]}>
      <View style={styles.header}><BrandLogoLink imageStyle={styles.logo} /><View style={styles.step}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('가입 전 확인', 'Before you continue')}</Text></View></View>
      <View style={styles.body}>
        <View style={styles.ageMark}><Text variant="title" weight="bold" color={color.brand.orange}>14+</Text></View>
        <Text variant="display" weight="bold" color={color.brand.navy}>
          {tx('만 14세 이상이신가요?', 'Are you 14 or older?')}
        </Text>
        <Text variant="body" style={styles.description}>
          {tx('생년월일은 묻지 않아요. 이 확인 사실 외에는 아무것도 저장하지 않습니다.', 'We do not ask for your date of birth. Only this confirmation is saved.')}
        </Text>
      </View>

      <View style={styles.footer}>
        <Pressable accessibilityRole="checkbox" accessibilityState={{ checked }} style={({ pressed }) => [styles.checkboxRow, pressed && styles.pressed]} onPress={() => setChecked((prev) => !prev)}>
          <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
            {checked && (
              <Text variant="caption" weight="bold" color={color.text.onAction}>
                ✓
              </Text>
            )}
          </View>
          <Text variant="body">{tx('만 14세 이상이며, 위 내용을 확인했어요.', 'I am 14 or older and understand the information above.')}</Text>
        </Pressable>

        <Button label={tx('계속', 'Continue')} disabled={!checked} onPress={continueOnboarding} containerStyle={styles.continueButton} />
      </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.brand.ivory },
  webCanvas: { justifyContent: 'center', backgroundColor: '#f5eee8' },
  panel: { flex: 1 },
  webPanel: { maxHeight: 620, padding: spacing[8], borderRadius: radius.lg, backgroundColor: color.brand.ivory, shadowColor: color.brand.navy, shadowOpacity: 0.1, shadowRadius: 20, shadowOffset: { width: 0, height: 8 }, elevation: 5 },
  header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  logo: { width: 100, height: 24 },
  step: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: '#fff1e8' },
  body: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[3],
  },
  ageMark: { width: 64, height: 64, marginBottom: spacing[2], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff1e8', borderWidth: 1, borderColor: '#f7cdbd' },
  description: {
    color: color.text.body,
  },
  footer: {
    gap: spacing[4],
  },
  checkboxRow: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    paddingHorizontal: spacing[3],
    borderRadius: radius.md,
    backgroundColor: color.surface.card,
  },
  pressed: { opacity: 0.72, transform: [{ scale: 0.99 }] },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    borderColor: color.surface.field,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxChecked: {
    backgroundColor: color.brand.orange,
    borderColor: color.brand.orange,
  },
  continueButton: { minHeight: 54, borderRadius: radius.full, backgroundColor: color.brand.orange },
});
