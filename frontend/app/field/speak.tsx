// 17 현장 말하기·택시 카드 — Figma 17_현장 말하기·택시 카드 실측 그대로.
//
// "말하기" 탭은 더 이상 문장 하나만 보여주지 않는다 — 장소 카드 모달(PlacePhraseModal)과
// 같은 PlacePhraseBrowser 를 써서 관광지·식당카페·택시·숙소 문장을 전부 보여준다.
// 홈·챗봇·현장 도구 어디서 들어와도 같은 경험이 되도록 맞춘 것(구조 정리, UX 통합).
// 번역 업체 계약과 무관하게 기기 TTS·클립보드·지도 링크로 완결할 수 있는 택시 카드는
// 그대로 Expo 네이티브 API로 동작시킨다.
import { useRef, useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import * as Clipboard from 'expo-clipboard';
import { speakAloud as speakWithAudioSession, stopSpeaking } from '@/field/speakAloud';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { PlacePhraseBrowser } from '@/components/PlacePhraseBrowser';
import { useI18n } from '@/i18n';
import { useAuth } from '@/auth/AuthProvider';
import { directionForLanguage, speechLanguageFor, translateText, TRANSLATE_MAX_LENGTH, type TranslationBlockedReason } from '@/field/translate';

// 입력칸 상한은 번역 모듈과 한 값을 쓴다 — 두 벌이 되면 화면은 받아 놓고 보낼 때 잘린다.
// (서버 한도와는 다른 값이다. 왜 120 인지는 TRANSLATE_MAX_LENGTH 주석 참고.)
const CUSTOM_PHRASE_MAX_LENGTH = TRANSLATE_MAX_LENGTH;

type Tab = 'speak' | 'taxi';

const MAP_APPS = [
  { id: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', url: `https://map.kakao.com/link/search/${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'google', labelKo: 'Google', labelEn: 'Google', url: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
  { id: 'apple', labelKo: 'Apple 지도', labelEn: 'Apple Maps', url: `https://maps.apple.com/?q=${encodeURIComponent('부산 영도구 영선동4가 605-3')}` },
] as const;
// 🔴 이 한국어 주소는 번역 대상이 아니다 — 실제로 택시 기사에게 보여줄 한국어 주소라,
// 영어로 바뀌면 현장에서 그대로 쓸모가 없어진다.
const TAXI_ADDRESS = '부산 영도구 영선동4가 605-3';
const taxiIcon = require('../../assets/icons/common/taxi.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

export default function Speak() {
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { tab: initialTab } = useLocalSearchParams<{ tab?: string }>();
  const [tab, setTab] = useState<Tab>(initialTab === 'taxi' ? 'taxi' : 'speak');
  const [copied, setCopied] = useState(false);
  // 목록에 없는 문장을 직접 입력해 들려주는 기능.
  //
  // 🔴 2026-09-16 정정 (S15P21E201-1088). 여기 있던 "번역은 안 한다. 입력한 한국어 그대로
  //    읽어 줄 뿐이다" 는 **도구가 목적과 정반대로 서 있던 것**이었다. 이 화면은 한국어를
  //    못 하는 사람이 현장에서 쓰라고 만든 것인데, 영어 화면에서도 "한국어로 입력하세요"
  //    라고 적혀 있었다. 한국어를 모르니까 이 화면에 온 사람에게 한국어를 요구한 것이다.
  //    (사용자 지적)
  //
  //    맞는 방향은 **내 말로 쓰고 한국어로 들려주는 것**이다. 서버에 번역 경로가 이미
  //    있으므로(POST /api/v1/tools/translate, S15P21E201-343) 그것을 부른다.
  //
  // 🔴 한국어 화면에서는 번역하지 않는다. 한국어로 써서 한국어로 말하면 되므로 부를 것이
  //    없다 — 번역을 거치면 느려지기만 한다(directionForLanguage 가 null 을 준다).
  const direction = directionForLanguage(language);
  const [customPhrase, setCustomPhrase] = useState('');
  const [customSpeaking, setCustomSpeaking] = useState(false);
  const [translating, setTranslating] = useState(false);
  const [spokenText, setSpokenText] = useState<string | null>(null);
  const [translateNotice, setTranslateNotice] = useState<string | null>(null);
  const [resultCopied, setResultCopied] = useState(false);
  const customPlayToken = useRef(0);

  function blockedNotice(reason: TranslationBlockedReason): string {
    if (reason === 'signed-out') return tx('번역은 로그인한 뒤에 쓸 수 있어요. 지금은 입력한 그대로 읽어드릴게요.', "Translation needs you to sign in. For now we'll read out what you typed, as it is.");
    if (reason === 'not-built') return tx('번역 기능이 아직 서버에 없어요. 입력한 그대로 읽어드릴게요.', "Translation isn't on the server yet. We'll read out what you typed, as it is.");
    if (reason === 'vendor') return tx('번역이 잠시 안 돼요. 잠시 후 다시 시도해 주세요. 지금은 입력한 그대로 읽어드릴게요.', "Translation is down for a moment — please try again shortly. For now we'll read out what you typed, as it is.");
    return tx('번역하지 못했어요. 입력한 그대로 읽어드릴게요.', "We couldn't translate that. We'll read out what you typed, as it is.");
  }

  /** 기기 음성으로 읽는다. 🔴 언어를 문장에 맞춰 준다 — 영어를 한국어 음성으로 읽으면 못 알아듣는다. */
  function speakAloud(text: string, speechLanguage: string) {
    const token = ++customPlayToken.current;
    const finish = () => { if (customPlayToken.current === token) setCustomSpeaking(false); };
    try {
      stopSpeaking();
      setCustomSpeaking(true);
      speakWithAudioSession(text, { language: speechLanguage, rate: 0.95, onDone: finish, onStopped: finish, onError: finish });
    } catch {
      finish();
    }
  }

  async function speakCustomPhrase() {
    const text = customPhrase.trim();
    if (!text || translating) return;
    setResultCopied(false);
    // 한국어 화면 — 번역할 것이 없다. 종전 그대로 읽는다.
    if (!direction) {
      setSpokenText(null);
      setTranslateNotice(null);
      speakAloud(text, 'ko-KR');
      return;
    }
    setTranslating(true);
    const outcome = await translateText(text, direction, accessToken);
    setTranslating(false);
    if (outcome.state === 'translated') {
      setSpokenText(outcome.text);
      setTranslateNotice(null);
      speakAloud(outcome.text, speechLanguageFor(direction));
      return;
    }
    // 번역이 안 되면 막다른 길로 두지 않는다 — 왜 안 되는지 말하고, 원문이라도 읽어 준다.
    // 🔴 이때는 원문의 언어로 읽는다. 영어 문장을 한국어 음성으로 읽으면 아무 쓸모가 없다.
    setSpokenText(null);
    setTranslateNotice(blockedNotice(outcome.reason));
    speakAloud(text, language === 'en' ? 'en-US' : 'ko-KR');
  }

  async function copySpokenText() {
    if (!spokenText) return;
    await Clipboard.setStringAsync(spokenText);
    setResultCopied(true);
  }

  async function copyAddress() {
    await Clipboard.setStringAsync(TAXI_ADDRESS);
    setCopied(true);
  }

  return (
    <Screen scroll>
      <Eyebrow>
        {tx('여행 중 · 흰여울문화마을', 'Traveling · Huinnyeoul Culture Village')}
      </Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        {tx('현장에서 바로 쓰기', 'Use it right now')}
      </Text>

      <View style={styles.segment}>
        <Pressable
          style={[styles.segmentItem, tab === 'speak' && styles.segmentItemActive]}
          onPress={() => setTab('speak')}
        >
          <View style={styles.segmentLabel}>
            <Image source={speakerIcon} resizeMode="contain" style={styles.segmentIcon} />
            <Text variant="caption" weight="bold" color={tab === 'speak' ? color.text.accent : color.text.body}>
              {tx('말하기', 'Speak')}
            </Text>
          </View>
        </Pressable>
        <Pressable
          style={[styles.segmentItem, tab === 'taxi' && styles.segmentItemActive]}
          onPress={() => setTab('taxi')}
        >
          <View style={styles.segmentLabel}>
            <Image source={taxiIcon} resizeMode="contain" style={styles.segmentIcon} />
            <Text variant="caption" weight="bold" color={tab === 'taxi' ? color.text.accent : color.text.body}>
              {tx('택시 카드', 'Taxi card')}
            </Text>
          </View>
        </Pressable>
      </View>

      {tab === 'speak' ? (
        <View style={styles.speakSection}>
          <View style={styles.customCard}>
            <Text variant="body" weight="bold">{tx('내가 원하는 문장 말하기', 'Say it in Korean')}</Text>
            <Text variant="caption" color={color.text.muted} style={styles.customHint}>
              {tx('아래 목록에 없는 문장은 한국어로 입력하면 그대로 읽어드려요.', "Type it in English. We'll turn it into Korean, say it out loud, and show it so you can hand your phone over.")}
            </Text>
            <TextInput
              accessibilityLabel={tx('직접 입력할 한국어 문장', 'Your sentence in English')}
              value={customPhrase}
              onChangeText={(text) => { setCustomPhrase(text.slice(0, CUSTOM_PHRASE_MAX_LENGTH)); setSpokenText(null); setTranslateNotice(null); }}
              placeholder={tx('예: 얼음 빼주세요', 'e.g. No ice, please')}
              placeholderTextColor={color.text.muted}
              multiline
              style={styles.customInput}
            />
            <View style={styles.customFooter}>
              <Text variant="caption" color={color.text.muted}>{`${customPhrase.length}/${CUSTOM_PHRASE_MAX_LENGTH}`}</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={tx('입력한 문장 듣기', 'Translate and play the sentence')}
                accessibilityState={{ disabled: !customPhrase.trim() || translating, busy: translating }}
                disabled={!customPhrase.trim() || translating}
                onPress={() => void speakCustomPhrase()}
                style={[styles.customSpeakButton, (!customPhrase.trim() || translating) && styles.customSpeakButtonDisabled]}
              >
                <Text variant="caption" weight="bold" color={color.text.onAction}>
                  {translating ? tx('번역 중', 'Translating') : customSpeaking ? tx('재생 중', 'Playing') : direction ? tx('▶ 말하기', '▶ Say it in Korean') : tx('▶ 말하기', '▶ Speak')}
                </Text>
              </Pressable>
            </View>

            {/* 🔴 한국어를 화면에도 보여 준다 (S15P21E201-1088). 현장에서는 소리보다 화면을
                내미는 것이 잘 통한다 — 시끄럽거나, 상대가 못 알아들었을 때 다시 말할 필요가 없다. */}
            {spokenText ? (
              <View accessibilityLiveRegion="polite" style={styles.translatedBox}>
                <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('읽어드린 문장', 'Shown to them, in Korean')}</Text>
                <Text variant="body" weight="bold" style={styles.translatedText}>{spokenText}</Text>
                <View style={styles.translatedActions}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx('한국어 문장 복사', 'Copy the Korean sentence')}
                    onPress={() => void copySpokenText()}
                    style={styles.translatedAction}
                  >
                    <Text variant="caption" weight="bold">{resultCopied ? tx('복사했어요', 'Copied') : tx('복사', 'Copy')}</Text>
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={tx('한국어 문장 다시 듣기', 'Play the Korean sentence again')}
                    onPress={() => speakAloud(spokenText, 'ko-KR')}
                    style={styles.translatedAction}
                  >
                    <Text variant="caption" weight="bold">{tx('다시 듣기', 'Play again')}</Text>
                  </Pressable>
                </View>
              </View>
            ) : null}

            {translateNotice ? (
              <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted} style={styles.customHint}>{translateNotice}</Text>
            ) : null}
          </View>
          <PlacePhraseBrowser onOpenTaxiCard={() => setTab('taxi')} />
        </View>
      ) : (
        <View style={styles.taxiCard}>
          <Text variant="body" weight="bold">
            {tx('택시 기사님께 보여주세요', 'Show this to the taxi driver')}
          </Text>
          <Text variant="title" weight="bold" color={color.action.field} style={styles.taxiPlace}>
            {tx('흰여울문화마을 안내센터', 'Huinnyeoul Culture Village Info Center')}
          </Text>
          <View style={styles.taxiAddressRow}>
            <Text variant="body" weight="medium" style={styles.taxiAddress}>
              {TAXI_ADDRESS}
            </Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('주소 복사', 'Copy address')} onPress={() => void copyAddress()}>
              <Text variant="caption" weight="bold" color={color.text.accent}>
                {copied ? tx('복사됨 ✓', 'Copied ✓') : tx('주소 복사', 'Copy address')}
              </Text>
            </Pressable>
          </View>
          <Text variant="caption" style={styles.taxiNotice}>
            {tx('※ 하차 후 경사 없는 우회 진입로 안내', '※ After getting off, use the step-free detour entrance')}
          </Text>
        </View>
      )}

      {tab === 'taxi' ? (
        <>
          <Text variant="body" weight="bold" style={styles.mapAppsTitle}>
            {tx('길찾기 앱으로 열기', 'Open in a map app')}
          </Text>
          <View style={styles.mapAppsRow}>
            {MAP_APPS.map((app) => (
              <Pressable accessibilityRole="link" accessibilityLabel={tx(`${app.labelKo}에서 목적지 열기`, `Open destination in ${app.labelEn}`)} key={app.id} onPress={() => void Linking.openURL(app.url)} style={({ pressed }) => [styles.mapAppButton, pressed && styles.pressed]}>
                <Text variant="body" weight="bold" color={color.action.field}>
                  {tx(app.labelKo, app.labelEn)}
                </Text>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  segment: {
    flexDirection: 'row',
    backgroundColor: color.surface.soft,
    borderRadius: radius.lg,
    padding: spacing[1],
    gap: spacing[1],
  },
  segmentItem: {
    flex: 1,
    alignItems: 'center',
    borderRadius: radius.md,
    paddingVertical: spacing[2],
  },
  segmentItemActive: {
    backgroundColor: color.surface.card,
  },
  segmentLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  segmentIcon: {
    width: 14,
    height: 14,
  },
  speakSection: {
    marginTop: spacing[4],
  },
  customCard: {
    marginBottom: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  customHint: {
    marginBottom: spacing[1],
  },
  customInput: {
    minHeight: 56,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: color.surface.field,
    backgroundColor: color.brand.ivory,
    color: color.text.heading,
    fontSize: 15,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    textAlignVertical: 'top',
  },
  customFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  customSpeakButton: {
    minHeight: 40,
    paddingHorizontal: spacing[4],
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: color.brand.navy,
  },
  translatedBox: { gap: spacing[2], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.tint },
  translatedText: { lineHeight: 26 },
  translatedActions: { flexDirection: 'row', gap: spacing[2] },
  translatedAction: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  customSpeakButtonDisabled: {
    opacity: 0.4,
  },
  taxiCard: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[1],
  },
  taxiPlace: {
    marginTop: spacing[1],
  },
  taxiAddressRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[1],
  },
  taxiAddress: {
    flex: 1,
  },
  taxiNotice: {
    marginTop: spacing[2],
    color: color.text.body,
  },
  mapAppsTitle: {
    marginTop: spacing[6],
    marginBottom: spacing[3],
  },
  mapAppsRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  mapAppButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
  },
  pressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
});
