// 신규 화면 — Figma 23 화면 표에 없다. 원본 요청서에서 새로 추가된 자리라 최대한 단순하게 만든다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
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
    router.replace('/permissions');
  }

  return (
    <Screen wide style={kind === 'tablet' ? styles.webCanvas : styles.canvas}>
      <View style={[styles.panel, kind === 'tablet' && styles.webPanel]}>
      {kind === 'tablet' && <View style={styles.webIntro}><Eyebrow>{tx('가볼래 · 부산', 'GABOLLE · Busan')}</Eyebrow><Text variant="display" weight="bold" color={color.text.onAction} style={styles.webIntroTitle}>{tx('누구나 안심하고\n부산을 여행하도록', 'So anyone can travel\nBusan with confidence')}</Text><Text variant="body" color={color.text.onDarkMuted}>{tx('연령 확인은 안전한 서비스 이용을 위한 최소한의 절차예요. 생년월일은 수집하지 않습니다.', 'Age verification is a minimal step to keep the service safe. We do not collect your birth date.')}</Text><View style={styles.webTrust}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('✓ 생년월일 미수집', '✓ No birth date collected')}</Text><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('✓ 한 번만 확인', '✓ Verified only once')}</Text></View></View>}
      <View style={[styles.gateContent, kind === 'tablet' && styles.webGateContent]}>
      <View style={styles.header}><BrandLogoLink href={kind === 'tablet' ? '/' : '/home'} imageStyle={styles.logo} /><View style={styles.step}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('가입 전 확인', 'Before you continue')}</Text></View></View>
      <View style={styles.body}>
        <View style={styles.ageMark}><Text variant="title" weight="bold" color={color.action.secondary}>14+</Text></View>
        <Text variant="display" weight="bold" color={color.brand.navy}>
          {tx('만 14세 이상이신가요?', 'Are you 14 or older?')}
        </Text>
        <Text variant="body" style={styles.description}>
          {tx('생년월일은 묻지 않아요. 이 확인 사실 외에는 아무것도 저장하지 않습니다.', 'We do not ask for your date of birth. Only this confirmation is saved.')}
        </Text>
      </View>

      <View style={styles.footer}>
        <Pressable testID="age-gate-check" accessibilityRole="checkbox" accessibilityState={{ checked }} style={({ pressed }) => [styles.checkboxRow, pressed && styles.pressed]} onPress={() => setChecked((prev) => !prev)}>
          <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
            {checked && (
              <Text variant="caption" weight="bold" color={color.text.onAction}>
                ✓
              </Text>
            )}
          </View>
          <Text variant="body">{tx('만 14세 이상이며, 위 내용을 확인했어요.', 'I am 14 or older and understand the information above.')}</Text>
        </Pressable>

        <Button testID="age-gate-continue" label={tx('계속', 'Continue')} variant="primary" pill disabled={!checked} onPress={continueOnboarding} />
      </View>
      </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  canvas: { backgroundColor: color.canvas },
  webCanvas: { justifyContent: 'center', backgroundColor: color.canvas },
  panel: { flex: 1 },
  webPanel: { minHeight: 600, maxHeight: 700, flexDirection: 'row', overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.brand.ivory, shadowColor: color.brand.navy, shadowOpacity: 0.1, shadowRadius: 20, shadowOffset: { width: 0, height: 8 }, elevation: 5 },
  gateContent: { flex: 1 },
  webGateContent: { padding: spacing[8] },
  webIntro: { width: '44%', justifyContent: 'center', gap: spacing[4], padding: spacing[6], backgroundColor: color.brand.navy },
  webIntroTitle: { fontSize: 28, lineHeight: 38 },
  webTrust: { marginTop: spacing[4], gap: spacing[2] },
  header: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  logo: { width: 132, height: 24 },
  step: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.tint },
  body: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[3],
  },
  ageMark: { width: 64, height: 64, marginBottom: spacing[2], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
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
    backgroundColor: color.action.tertiary,
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
    backgroundColor: color.action.secondary,
    borderColor: color.action.secondary,
  },
});
