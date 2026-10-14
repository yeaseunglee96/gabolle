// 가입 화면의 언어 선택 — 여섯 언어가 줄바꿈되는 칩으로 나온다 (S15P21E201-2053).
//
// 이메일 가입(app/(auth)/sign-up.tsx)과 소셜 가입 마무리(app/(auth)/oauth-signup.tsx)가 같이 쓴다.
// 전에는 두 화면이 한국어·영어 두 칸을 각자 그렸다 — 앱은 여섯 언어를 아는데 가입에서만 둘이었고,
// 일본어·중국어·베트남어를 고른 사람도 가입하면서 한국어·영어로 되돌려야 했다.
//
// 🔴 한 줄에 여섯이 안 들어간다. 폰 폭 390 에서 칸 하나가 55pt 라 「Tiếng Việt」가 잘린다.
//    그래서 줄바꿈 칩이다(마이페이지 › 앱 언어의 AppLanguageSetting 과 같은 방식) — 칸은 «최소» 30% 만 차지하고
//    글자 길이만큼 넓어지며, 넘치면 다음 줄로 간다. 폭을 30% 로 박으면 글자를 키운 기기에서 낱말 중간에서 꺾인다
//    (S15P21E201-1983). 순서는 첫 화면의 언어 시트(WelcomeLanguageSheet)와 같다 — 목록은 LANGUAGE_OPTIONS 한 곳이다.
import { Pressable, StyleSheet, View } from 'react-native';

import { ChipLabel } from '@/components/ChipLabel';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS, type LanguageCode } from '@/i18n/languages';

export function SignupLanguagePicker({ value, onSelect }: {
  /** 지금 고른 언어. 첫 화면에서 고른 언어가 처음 값이다 — 호출하는 쪽이 온보딩 설정에서 읽어 준다. */
  value: LanguageCode;
  onSelect: (next: LanguageCode) => void;
}) {
  const { tx } = useI18n();
  return (
    <View testID="signup-language-group" accessibilityRole="radiogroup" accessibilityLabel={tx('언어', 'Language')} style={styles.row}>
      {LANGUAGE_OPTIONS.map((option) => {
        const selected = option.code === value;
        return (
          <Pressable
            key={option.code}
            testID={`signup-language-${option.code}`}
            accessibilityRole="radio"
            accessibilityLabel={option.endonym}
            accessibilityState={{ selected }}
            // 웹은 accessibilityState 를 DOM 에 옮기지 않는다 — 어느 칸이 골라졌는지 화면 읽기 프로그램이 못 읽는다. aria-checked 는 웹·앱 둘 다 간다.
            aria-checked={selected}
            onPress={() => onSelect(option.code)}
            style={[styles.chip, selected && styles.chipSelected]}
          >
            {/* 칩 폭 전체로 편 글자(ChipLabel) — 「繁體中文」 끝 글자가 잘린 적이 있다(S15P21E201-1988 · S15P21E201-2054). 이유는 ChipLabel.tsx 머리말에. */}
            <ChipLabel variant="body" color={selected ? color.text.onAction : color.text.heading}>{option.endonym}</ChipLabel>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  // 3 + 3 으로 채운다 — 줄마다 남은 폭은 칸들이 나눠 갖는다(flexGrow). 높이는 44pt 터치 기준 위다.
  chip: { flexGrow: 1, minWidth: '30%', minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], paddingVertical: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
});
