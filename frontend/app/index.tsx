// 01 Welcome·언어 선택 — 앱을 켜면 처음 보는 화면. 다시 만든다(전에는 어두운 배경으로 잘못
// 만들어져 있었다 — 이 앱은 화면 23개가 전부 흰색 계열 배경인 밝은 테마다).
//
// 🔴 Figma 원본 화면 이름은 "LOCAL ROUTE" 다. Figma 가 만들어진 뒤 제품 이름이
// GABOLLE(가볼래) 로 정해졌는데 Figma 가 갱신되지 않았다. 화면에는 가볼래 로 쓴다.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

type Language = {
  code: string;
  flag: string;
  label: string;
  sub: string;
};

// Figma "Language options" 5 종 그대로.
const LANGUAGES: Language[] = [
  { code: 'ko', flag: '🇰🇷', label: '한국어', sub: '현재 선택' },
  { code: 'en', flag: '🇺🇸', label: 'English', sub: '영어' },
  { code: 'ja', flag: '🇯🇵', label: '日本語', sub: '일본어' },
  { code: 'zh-Hans', flag: '🇨🇳', label: '简体中文', sub: '중국어 간체' },
  { code: 'zh-Hant', flag: '🇹🇼', label: '繁體中文', sub: '중국어 번체' },
];

// 태블릿(폴드8 펼침 포함)에서 시트가 화면 폭 전체로 늘어지지 않게 잡아두는 최대 폭.
const MAX_CONTENT_WIDTH = 480;

export default function Welcome() {
  const router = useRouter();
  const { kind } = useLayout();
  const [lang, setLang] = useState('ko');

  return (
    // 히어로가 상태 바 뒤까지 색을 채워야 해서(Figma 실측) 화면 전체는 SafeAreaView 로 감싸지
    // 않는다 — 위쪽은 히어로가, 아래쪽은 시트 내부가 각자 안전 영역을 잡는다.
    <View style={styles.screen}>
      <View style={[styles.frame, kind === 'tablet' && styles.frameTablet]}>
        <View style={styles.hero}>
          {/* TODO: 히어로 영상/이미지. 실제 자산이 오기 전까지 브랜드 색 면으로 대체한다.
              그라데이션(Figma 실측)을 넣으려면 expo-linear-gradient 가 필요한데 새 의존성이라
              먼저 사람 확인이 필요해 지금은 단색으로 둔다. */}
          <SafeAreaView edges={['top']} style={styles.heroSafeArea}>
            <View style={styles.heroCopy}>
              <Text variant="hero" weight="bold" style={styles.brand}>
                GABOLLE{'\n'}가볼래
              </Text>
              <Text variant="body" color={color.text.onAction} style={styles.intro}>
                현지인이 다시 가는 곳으로,{'\n'}나만의 부산 여행을 설계해요.
              </Text>
              <View style={styles.chip}>
                <View style={styles.chipDot} />
                <Text variant="caption" weight="bold" color={color.text.eyebrow}>
                  송도 해상 케이블카 · 자동 재생
                </Text>
              </View>
            </View>
          </SafeAreaView>
        </View>

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.sheetHandle} />

          <View style={styles.sheetHeader}>
            <Text variant="title" weight="bold">
              어떤 언어로 여행할까요?
            </Text>
            <Text variant="caption">앱 안내·장소 정보·동백이 답변에 적용돼요.</Text>
          </View>

          <View style={styles.langList}>
            {LANGUAGES.map((item) => {
              const selected = item.code === lang;
              return (
                <Pressable
                  key={item.code}
                  onPress={() => setLang(item.code)}
                  style={[styles.langOption, selected && styles.langOptionSelected]}
                >
                  <View style={styles.flag}>
                    <Text variant="title">{item.flag}</Text>
                  </View>
                  <View style={styles.langLabelBox}>
                    <Text variant="body" weight="bold" color={selected ? color.text.eyebrow : color.text.heading}>
                      {item.label}
                    </Text>
                    <Text variant="caption">{item.sub}</Text>
                  </View>
                  {selected && (
                    <View style={styles.langCheck}>
                      <Text variant="caption" weight="bold" color={color.text.onAction}>
                        ✓
                      </Text>
                    </View>
                  )}
                </Pressable>
              );
            })}
          </View>

          {/* Figma 의 "한국어로 시작하기" CTA 를 이 앱의 실제 로그인 수단인
              Google 로그인으로 바꾼다 — 이 화면에서 내비게이션이 일어나는 유일한 버튼이다. */}
          <Button label="Continue with Google" onPress={() => router.push('/age-gate')} />

          <Text variant="caption" style={styles.footer}>
            설정에서 언제든 언어를 바꿀 수 있어요 · 로그인
          </Text>
        </SafeAreaView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: color.canvas,
  },
  frame: {
    flex: 1,
    width: '100%',
  },
  // 태블릿(폴드8 펼침 포함)에서는 가운데 정렬 + 최대폭 제한.
  frameTablet: {
    alignSelf: 'center',
    maxWidth: MAX_CONTENT_WIDTH,
  },
  hero: {
    flex: 1,
    backgroundColor: color.action.primary,
  },
  heroSafeArea: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  heroCopy: {
    paddingHorizontal: spacing[6],
    paddingBottom: spacing[6],
    gap: spacing[3],
  },
  brand: {
    marginBottom: spacing[1],
  },
  intro: {
    opacity: 0.92,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: spacing[1],
    backgroundColor: color.surface.card,
    borderRadius: radius.full,
    paddingVertical: spacing[1],
    paddingHorizontal: spacing[3],
  },
  chipDot: {
    width: 7,
    height: 7,
    borderRadius: radius.full,
    backgroundColor: color.state.danger,
  },
  sheet: {
    backgroundColor: color.surface.card,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingHorizontal: spacing[6],
    paddingTop: spacing[3],
    paddingBottom: spacing[4],
    gap: spacing[4],
  },
  sheetHandle: {
    alignSelf: 'center',
    width: 44,
    height: 4,
    borderRadius: radius.full,
    backgroundColor: color.surface.field,
  },
  sheetHeader: {
    gap: spacing[1],
  },
  langList: {
    gap: spacing[2],
  },
  langOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    borderRadius: radius.md,
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[2],
  },
  langOptionSelected: {
    backgroundColor: color.surface.soft,
  },
  flag: {
    width: 30,
    height: 30,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  langLabelBox: {
    flex: 1,
  },
  langCheck: {
    width: 24,
    height: 24,
    borderRadius: radius.full,
    backgroundColor: color.action.secondary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footer: {
    textAlign: 'center',
  },
});
