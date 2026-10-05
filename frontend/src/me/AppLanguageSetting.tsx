import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS, type LanguageCode } from '@/i18n/languages';

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
        {/* 🔴 한 문자열 · 한 줄 · 넘치면 글자를 줄인다(S15P21E201-1988) — 갤럭시 탭 세로에서 「繁體中文」이 「繁體中」으로 끝 글자가 잘렸다. */}
        <Text weight="bold" numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8} color={language === option.code ? color.text.onAction : color.text.heading}>{`${language === option.code ? '✓ ' : ''}${option.endonym}`}</Text>
      </Pressable>)}
    </View>
    {saving && <Text accessibilityLiveRegion="polite">{tx('언어를 저장하고 있어요.', 'Saving language…')}</Text>}
    {failed && <Text accessibilityRole="alert">{tx('언어를 저장하지 못했어요. 연결을 확인하고 다시 선택해 주세요.', 'Could not save your language. Check your connection and select it again.')}</Text>}
  </View>;
}
const styles = StyleSheet.create({
  section: { padding: spacing[4], gap: spacing[3] },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  // 🔴 다섯 칸을 3 + 2 로 고르게 채운다 — 글자 길이만큼만 차지하면 폭에 따라 「繁體中文」 하나만 다음 줄에 혼자
  //    떨어졌다(2026-10-02 서피스 프로 세로 실측). 줄마다 남은 폭은 칸들이 나눠 갖는다.
  //    폭은 「최소」 30% 다(S15P21E201-1983) — 30% 로 박아 두면 글자를 키운 기기에서 「English」가 「Eng / lish」로
  //    낱말 중간에서 꺾였다(갤럭시 탭 세로·글자 1.3배). 이름이 더 길면 칸이 넓어지고, 넘치면 다음 줄로 간다.
  option: { flexGrow: 1, minWidth: '30%', minHeight: 48, paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  selected: { backgroundColor: color.brand.navy },
  disabled: { opacity: 0.58 },
});
