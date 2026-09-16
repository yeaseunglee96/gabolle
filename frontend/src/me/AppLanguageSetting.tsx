import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { LanguageCode } from '@/onboarding/OnboardingPreferences';

/** 앱 언어는 비회원도 바꿀 수 있다. 회원은 계정 저장이 성공한 뒤 적용한다. */
export function AppLanguageSetting() {
  const { user, updateProfile } = useAuth();
  const { language, setLanguage, tx } = useI18n();
  const [saving, setSaving] = useState(false);
  const [failed, setFailed] = useState(false);
  async function change(next: LanguageCode) {
    if (saving || next === language) return;
    setSaving(true);
    setFailed(false);
    try {
      if (user) await updateProfile({ language: next === 'en' ? 'EN' : 'KO' });
      setLanguage(next);
    } catch {
      setFailed(true);
    } finally {
      setSaving(false);
    }
  }
  return <View style={styles.section}>
    <Text weight="bold">{tx('앱 언어', 'App language')}</Text>
    <View style={styles.options} accessibilityRole="radiogroup" accessibilityLabel={tx('앱 언어', 'App language')}>
      {(['ko', 'en'] as const).map((code) => <Pressable key={code} accessibilityRole="radio" accessibilityLabel={code === 'ko' ? '한국어' : 'English'} accessibilityState={{ selected: language === code, disabled: saving }} disabled={saving} onPress={() => void change(code)} style={[styles.option, language === code && styles.selected, saving && styles.disabled]}>
        <Text weight="bold" color={language === code ? color.text.onAction : color.text.heading}>{language === code ? '✓ ' : ''}{code === 'ko' ? '한국어' : 'English'}</Text>
      </Pressable>)}
    </View>
    {saving && <Text accessibilityLiveRegion="polite">{tx('언어를 저장하고 있어요.', 'Saving language…')}</Text>}
    {failed && <Text accessibilityRole="alert">{tx('언어를 저장하지 못했어요. 연결을 확인하고 다시 선택해 주세요.', 'Could not save your language. Check your connection and select it again.')}</Text>}
  </View>;
}
const styles = StyleSheet.create({
  section: { padding: spacing[4], gap: spacing[3] },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  option: { minHeight: 48, paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  selected: { backgroundColor: color.brand.navy },
  disabled: { opacity: 0.58 },
});
