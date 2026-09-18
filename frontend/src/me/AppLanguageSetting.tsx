import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS, needsTranslationNotice, type LanguageCode } from '@/i18n/languages';

// 이후에도 이 화면만 한국어·영어 둘뿐이었다 — 사용자 리포트.
function toAccountLanguage(code: LanguageCode): 'KO' | 'EN' {
  return code === 'ko' ? 'KO' : 'EN';
}

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
      if (user) await updateProfile({ language: toAccountLanguage(next) });
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
      {LANGUAGE_OPTIONS.map((option) => <Pressable key={option.code} accessibilityRole="radio" accessibilityLabel={option.endonym} accessibilityState={{ selected: language === option.code, disabled: saving }} disabled={saving} onPress={() => void change(option.code)} style={[styles.option, language === option.code && styles.selected, saving && styles.disabled]}>
        <Text weight="bold" color={language === option.code ? color.text.onAction : color.text.heading}>{language === option.code ? '✓ ' : ''}{option.endonym}</Text>
      </Pressable>)}
    </View>
    {/* 번역이 아직 없다는 사실을 숨기지 않는다(app/index.tsx의 언어 선택과 같은 문구 규칙)
        일본어·중국어를 고르면 화면 문구는 영어로 나온다는 것을 미리 말한다.
    */}
    {needsTranslationNotice(language) ? <Text variant="caption" color={color.text.muted}>{tx('메뉴는 아직 영어로 나와요. 장소 이름과 안내는 고른 언어로 나와요.', 'Menus are in English for now. Place names and guides come in your language.')}</Text> : null}
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
