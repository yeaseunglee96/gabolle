// 첫 화면의 언어 시트 — 시안 4 의 00b (docs/design_handoff_brand_first_run/WelcomeLang.png).
//
// 예전 첫 화면은 국기 다섯 개를 한 줄에 놓고 누르면 바로 시작했다. 국기 하나가 56px 이라
// 처음 보는 사람은 어느 국기가 어느 언어인지 순간 헷갈렸고, 「누르면 곧 시작」이라
// 잘못 눌러도 되돌릴 자리가 없었다. 이제는 카드 하나(지금 언어)를 누르면 이 시트가 뜨고,
// 여기서 고른 뒤 「○○로 시작하기」를 눌러야 넘어간다 — 고르는 것과 시작하는 것을 나눴다.
//
// 국기는 유니코드 그림문자(🇰🇷)가 아니라 실제 이미지를 쓴다 — 윈도우 브라우저는
// 국가 그림문자를 지원하지 않아 KR·US 같은 두 글자로 떨어진다.
import { Image, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS, type LanguageCode, type LanguageOption } from '@/i18n/languages';

// 언어 목록은 src/i18n/languages.ts 한 곳에 있다. require 는 번들러가 정적으로 읽어야
// 해서 그 배열에 못 넣고 여기서 code 로 바로 잇는다.
export const FLAG_IMAGES: Record<LanguageCode, ReturnType<typeof require>> = {
  ko: require('../../assets/flags/kr.png'),
  en: require('../../assets/flags/us.png'),
  ja: require('../../assets/flags/jp.png'),
  'zh-Hans': require('../../assets/flags/cn.png'),
  'zh-Hant': require('../../assets/flags/tw.png'),
};

/** 그 언어를 모르는 사람이 읽을 이름 — 화면 언어가 한국어면 한국어로, 아니면 영어로. */
const KOREAN_NAMES: Record<LanguageCode, string> = { ko: '한국어', en: '영어', ja: '일본어', 'zh-Hans': '중국어 간체', 'zh-Hant': '중국어 번체' };
/** 「○○로 시작하기」 — 고른 언어 **그 언어로** 적는다. 일본어를 고른 사람이 읽을 단추라 한국어면 안 된다. */
const START_LABEL: Record<LanguageCode, string> = { ko: '한국어로 시작하기', en: 'Start in English', ja: '日本語で始める', 'zh-Hans': '用简体中文开始', 'zh-Hant': '用繁體中文開始' };

function CloseIcon() {
  return (
    <Svg width={20} height={20} viewBox="0 0 24 24" fill="none">
      <Path d="M6 6l12 12M18 6L6 18" stroke={color.text.heading} strokeWidth={1.9} strokeLinecap="round" />
    </Svg>
  );
}

function Chevron({ tint }: { tint: string }) {
  return (
    <Svg width={18} height={18} viewBox="0 0 24 24" fill="none">
      <Path d="M9 5l7 7-7 7" stroke={tint} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

export function WelcomeLanguageSheet({ visible, language, onSelect, onClose, onStart }: {
  visible: boolean;
  language: LanguageCode;
  onSelect: (next: LanguageCode) => void;
  onClose: () => void;
  /** 「○○로 시작하기」. 고른 언어로 온보딩을 시작한다. */
  onStart: () => void;
}) {
  const { tx } = useI18n();
  const current = LANGUAGE_OPTIONS.find((item) => item.code === language) ?? LANGUAGE_OPTIONS[0];
  const secondaryName = (item: LanguageOption) => (language === 'ko' ? KOREAN_NAMES[item.code] : item.englishName);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      {/* 시트 바깥을 누르면 닫힌다 — 시트 자체는 눌러도 안 닫혀야 해서 안쪽은 Pressable 이 아니다. */}
      <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={styles.backdrop} />
      <View accessibilityViewIsModal style={styles.sheet}>
        <View style={styles.grabber} />
        <View style={styles.head}>
          <View style={styles.headCopy}>
            <Text variant="title" weight="bold">{tx('어떤 언어로 여행할까요?', 'Which language should we travel in?')}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('앱 안내 · 장소 정보 · 동백이 답변이 이 언어로 나와요', 'Guides, place info and Dongbaek’s answers come in this language')}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.pressed]}>
            <CloseIcon />
          </Pressable>
        </View>

        <ScrollView bounces={false} style={styles.list} contentContainerStyle={styles.listContent}>
          <View accessibilityRole="radiogroup" accessibilityLabel={tx('언어 선택', 'Choose a language')} style={styles.listContent}>
            {LANGUAGE_OPTIONS.map((item) => {
              const selected = item.code === language;
              return (
                // 언어 버튼은 code 로 찾는다(testID). 라벨로 찾으면 고르려는 언어가 곧
                // 찾을 이름이라 자동화가 닭과 달걀에 빠진다.
                <Pressable
                  key={item.code}
                  testID={`lang-${item.code}`}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  accessibilityLabel={item.englishName === item.endonym ? item.endonym : `${item.endonym} · ${item.englishName}`}
                  onPress={() => onSelect(item.code)}
                  style={({ pressed }) => [styles.option, selected && styles.optionSelected, pressed && styles.pressed]}
                >
                  <Image source={FLAG_IMAGES[item.code]} resizeMode="contain" style={styles.flag} accessibilityIgnoresInvertColors />
                  <View style={styles.optionCopy}>
                    <Text variant="body" weight="bold">{item.endonym}</Text>
                    {secondaryName(item) !== item.endonym ? <Text variant="caption" color={color.text.muted}>{secondaryName(item)}</Text> : null}
                  </View>
                  <View style={[styles.radio, selected && styles.radioOn]}>{selected ? <View style={styles.radioDot} /> : null}</View>
                </Pressable>
              );
            })}
          </View>
        </ScrollView>

        <Pressable testID="start-gabolle-sheet" accessibilityRole="button" onPress={onStart} style={({ pressed }) => [styles.start, pressed && styles.pressed]}>
          <Text variant="body" weight="bold" color={color.text.onAction}>{START_LABEL[current.code]}</Text>
          <Chevron tint={color.text.onAction} />
        </Pressable>
        <Text variant="caption" color={color.text.muted} style={styles.foot}>{tx('마이페이지 › 언어에서 언제든 바꿀 수 있어요', 'You can change it any time in My page › Language')}</Text>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(25,25,25,0.62)' },
  sheet: { backgroundColor: color.surface.card, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, paddingHorizontal: spacing[4], paddingTop: spacing[2], paddingBottom: spacing[6], gap: spacing[3], maxHeight: '88%' },
  grabber: { alignSelf: 'center', width: 40, height: 4, borderRadius: radius.full, backgroundColor: color.surface.border },
  head: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  headCopy: { flex: 1, gap: 2 },
  close: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  list: { flexGrow: 0 },
  listContent: { gap: spacing[2] },
  option: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 56, paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1.5, borderColor: color.surface.border, backgroundColor: color.surface.card },
  // 선택은 짙은 회색 선 — 빨강이 아니다(tokens.ts 규칙).
  optionSelected: { borderColor: color.action.secondary, backgroundColor: color.surface.tint },
  optionCopy: { flex: 1, gap: 1 },
  // 국기는 3:2 그대로 — 잘리면 다른 나라로 오인된다.
  // 흰 바탕 국기(일본)가 카드에 녹지 않게 실선 하나를 두른다.
  flag: { width: 32, height: 22, borderRadius: 4, borderWidth: StyleSheet.hairlineWidth, borderColor: color.surface.field },
  radio: { width: 22, height: 22, borderRadius: radius.full, borderWidth: 2, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' },
  radioOn: { borderColor: color.action.secondary },
  radioDot: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: color.action.secondary },
  start: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[2], minHeight: 54, borderRadius: radius.md, backgroundColor: color.action.primary },
  foot: { textAlign: 'center' },
  pressed: { opacity: 0.78 },
});
