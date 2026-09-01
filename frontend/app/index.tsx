// 01 Welcome·언어 선택 — 이 셸에서 유일하게 제대로 만드는 화면이다.
// 나머지 화면은 ScreenStub 자리표시자로 남아 있다.
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { color, radius, spacing, type } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

const REASONS = ['휠체어로 갈 수 있는지', '그늘 있는 길로', '알레르기 걸러서'];

// 태블릿(폴드8 펼침 포함)에서 카드가 화면 폭 전체로 늘어지지 않게 잡아두는 최대 폭.
const MAX_CONTENT_WIDTH = 480;

export default function Welcome() {
  const router = useRouter();
  const { kind } = useLayout();
  const [lang, setLang] = useState<'ko' | 'en'>('ko');

  return (
    <SafeAreaView style={styles.screen}>
      <View style={[styles.content, kind === 'tablet' && styles.contentTablet]}>
        <View style={styles.previewBox}>
          {/* TODO: 실제 화면 컴포넌트를 축소해 자동 재생 (Reanimated) */}
          <Text style={styles.previewLabel}>앱 흐름 자동 재생</Text>
        </View>

        <View style={styles.copy}>
          <Text style={styles.headline}>부산, 갈 수 있는 곳만</Text>
          {REASONS.map((reason) => (
            <Text key={reason} style={styles.reason}>
              · {reason}
            </Text>
          ))}
        </View>

        <View style={styles.actions}>
          <Pressable
            style={styles.googleButton}
            onPress={() => router.push('/age-gate')}
          >
            <Text style={styles.googleButtonText}>Continue with Google</Text>
          </Pressable>

          <View style={styles.langToggle}>
            <Pressable hitSlop={spacing[2]} onPress={() => setLang('ko')}>
              <Text style={[styles.langOption, lang === 'ko' && styles.langOptionActive]}>
                KO
              </Text>
            </Pressable>
            <Text style={styles.langDivider}>/</Text>
            <Pressable hitSlop={spacing[2]} onPress={() => setLang('en')}>
              <Text style={[styles.langOption, lang === 'en' && styles.langOptionActive]}>
                EN
              </Text>
            </Pressable>
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: color.bg[0],
  },
  content: {
    flex: 1,
    width: '100%',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[6],
  },
  // 태블릿(폴드8 펼침 포함)에서는 가운데 정렬 + 최대폭 제한.
  contentTablet: {
    alignSelf: 'center',
    maxWidth: MAX_CONTENT_WIDTH,
  },
  previewBox: {
    flex: 1,
    borderRadius: radius.lg,
    backgroundColor: color.bg[1],
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewLabel: {
    color: color.text.low,
    fontFamily: 'Inter',
    fontSize: type.caption.size,
    lineHeight: type.caption.lineHeight,
  },
  copy: {
    marginTop: spacing[6],
    gap: spacing[1],
  },
  headline: {
    color: color.text.hi,
    fontFamily: 'NotoSansKR',
    fontSize: type.display.size,
    lineHeight: type.display.lineHeight,
    marginBottom: spacing[2],
  },
  reason: {
    color: color.text.mid,
    fontFamily: 'NotoSansKR',
    fontSize: type.body.size,
    lineHeight: type.body.lineHeight,
  },
  actions: {
    marginTop: spacing[6],
    alignItems: 'center',
    gap: spacing[3],
  },
  googleButton: {
    width: '100%',
    borderRadius: radius.md,
    backgroundColor: color.brand,
    paddingVertical: spacing[3],
    alignItems: 'center',
  },
  googleButtonText: {
    color: color.text.hi,
    fontFamily: 'Inter',
    fontSize: type.body.size,
    lineHeight: type.body.lineHeight,
  },
  langToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  langOption: {
    color: color.text.low,
    fontFamily: 'Inter',
    fontSize: type.caption.size,
    lineHeight: type.caption.lineHeight,
  },
  langOptionActive: {
    color: color.text.hi,
  },
  langDivider: {
    color: color.text.low,
    fontSize: type.caption.size,
  },
});
