// 17 현장 말하기·택시 카드 — Figma 17_현장 말하기·택시 카드 실측 그대로.
import { useEffect, useRef, useState } from 'react';
import { Image, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Clipboard from 'expo-clipboard';
import { speakAloud as speakWithAudioSession, stopSpeaking } from '@/field/speakAloud';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { PlacePhraseBrowser } from '@/components/PlacePhraseBrowser';
import { useI18n } from '@/i18n';
import { toBcp47 } from '@/i18n/languages';
import { useAuth } from '@/auth/AuthProvider';
import { directionForLanguage, speechLanguageFor, translateText, TRANSLATE_MAX_LENGTH, type TranslationBlockedReason } from '@/field/translate';
import { canSearchDestination, destinationSubtitle, searchTaxiDestinations, taxiCardHref, type TaxiDestinationOutcome } from '@/field/taxiDestination';
import { KOREAN_OR_ENGLISH_HINT, needsKoreanOrEnglishName } from '@/discovery/nameSearchHint';
import { txf } from '@/i18n/format';

// 입력칸 상한은 번역 모듈과 한 값을 쓴다 — 두 벌이 되면 화면은 받아 놓고 보낼 때 잘린다.
// (서버 한도와는 다른 값이다. 왜 120 인지는 TRANSLATE_MAX_LENGTH 주석 참고.)
const CUSTOM_PHRASE_MAX_LENGTH = TRANSLATE_MAX_LENGTH;

type Tab = 'speak' | 'taxi';

const taxiIcon = require('../../assets/icons/common/taxi.png');
const speakerIcon = require('../../assets/icons/common/speaker.png');

export default function Speak() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { tab: initialTab, phrase: handedPhrase } = useLocalSearchParams<{ tab?: string; phrase?: string }>();
  const [tab, setTab] = useState<Tab>(initialTab === 'taxi' ? 'taxi' : 'speak');
  // 목록에 없는 문장을 직접 입력해 들려주는 기능.
  const direction = directionForLanguage(language);
  const [customPhrase, setCustomPhrase] = useState('');
  const [customSpeaking, setCustomSpeaking] = useState(false);
  const [translating, setTranslating] = useState(false);
  const [spokenText, setSpokenText] = useState<string | null>(null);
  const [translateNotice, setTranslateNotice] = useState<string | null>(null);
  const [resultCopied, setResultCopied] = useState(false);
  const customPlayToken = useRef(0);

  // 택시 목적지 고르기.
  const [destinationQuery, setDestinationQuery] = useState('');
  const [destination, setDestination] = useState<TaxiDestinationOutcome>({ state: 'idle' });
  const [destinationSearching, setDestinationSearching] = useState(false);

  useEffect(() => {
    if (!canSearchDestination(destinationQuery)) {
      setDestination({ state: 'idle' });
      setDestinationSearching(false);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setDestinationSearching(true);
      const outcome = await searchTaxiDestinations(destinationQuery, controller.signal, accessToken);
      if (controller.signal.aborted) return;
      setDestination(outcome);
      setDestinationSearching(false);
    }, 250);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [destinationQuery, accessToken]);

  function blockedNotice(reason: TranslationBlockedReason): string {
    if (reason === 'signed-out') return tx('번역은 로그인한 뒤에 쓸 수 있어요. 지금은 입력한 그대로 읽어드릴게요.', "Translation needs you to sign in. For now we'll read out what you typed, as it is.");
    if (reason === 'not-built') return tx('번역 기능이 아직 서버에 없어요. 입력한 그대로 읽어드릴게요.', "Translation isn't on the server yet. We'll read out what you typed, as it is.");
    if (reason === 'vendor') return tx('번역이 잠시 안 돼요. 잠시 후 다시 시도해 주세요. 지금은 입력한 그대로 읽어드릴게요.', "Translation is down for a moment — please try again shortly. For now we'll read out what you typed, as it is.");
    return tx('번역하지 못했어요. 입력한 그대로 읽어드릴게요.', "We couldn't translate that. We'll read out what you typed, as it is.");
  }

  /** 기기 음성으로 읽는다. 언어를 문장에 맞춰 준다 — 영어를 한국어 음성으로 읽으면 못 알아듣는다. */
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
      // 고른 언어 방향을 서버가 아직 몰라 영어 방향으로 번역했으면 그 사실을 한 줄 남긴다 — 결과가 어색해도 이유를 안다.
      setTranslateNotice(outcome.viaEnglish ? tx('이 언어의 직접 번역은 준비 중이라 영어를 거쳐 번역했어요.', 'Direct translation for this language is on the way — this one went through English.') : null);
      speakAloud(outcome.text, speechLanguageFor(direction));
      return;
    }
    // 번역이 안 되면 막다른 길로 두지 않는다 — 왜 안 되는지 말하고, 원문이라도 읽어 준다.
    // 이때는 원문의 언어로 읽는다. 영어 문장을 한국어 음성으로 읽으면 아무 쓸모가 없다.
    setSpokenText(null);
    setTranslateNotice(blockedNotice(outcome.reason));
    speakAloud(text, toBcp47(language));
  }

  // 🔴 챗봇이 문장을 들려 보내면 그것을 크게 띄우고 바로 읽는다 — S15P21E201-1502.
  //    전에는 단추가 이 화면을 «열기만» 해서, 「크게 보고 듣기」를 눌러도 문장도 소리도 없이
  //    현장 도구 첫 화면만 떴다. 실기에서 고장으로 올라온 자리다.
  //
  //    읽는 언어는 언제나 한국어다. 이 문장은 «상대에게 들려주려고» 받은 한국어라,
  //    앱 언어가 일본어여도 일본어로 읽으면 아무 쓸모가 없다. 그래서 번역을 거치지 않는다.
  const handedOnce = useRef(false);
  useEffect(() => {
    if (handedOnce.current) return;
    const text = typeof handedPhrase === 'string' ? handedPhrase.trim() : '';
    if (!text) return;
    handedOnce.current = true;
    setTab('speak');
    setSpokenText(text);
    speakAloud(text, 'ko-KR');
    // speakAloud 는 이 컴포넌트가 다시 그려져도 같은 함수라, 의존성에 넣지 않아도 한 번만 돈다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [handedPhrase]);

  async function copySpokenText() {
    if (!spokenText) return;
    await Clipboard.setStringAsync(spokenText);
    setResultCopied(true);
  }

  return (
    <Screen scroll>
      {/* 여기에도 흰여울문화마을이 박혀 있었다 — 어디에 있든 그렇게 적혔다. */}
      <Eyebrow>{tx('여행 중', 'On your trip')}</Eyebrow>
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

            {/* 한국어를 화면에도 보여 준다. 현장에서는 소리보다 화면을
                내미는 것이 잘 통한다 — 시끄럽거나, 상대가 못 알아들었을 때 다시 말할 필요가 없다.
            */}
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
        // 「길찾기 앱으로 열기」(카카오맵·Google·Apple) 줄도 같이 걷어냈다. 우리 앱에서
        // 하던 일을 남의 앱에서 끝내게 만드는 자리였다 (사용자 보고 11번).
        <View style={styles.taxiPane}>
          <Text variant="body" weight="bold">{tx('어디로 가세요?', 'Where are you going?')}</Text>
          <Text variant="caption" color={color.text.body}>
            {tx('고르면 기사님께 보여줄 카드를 만들어 드려요.', "Pick one and we'll make a card to show the driver.")}
          </Text>

          <View style={styles.searchRow}>
            <TextInput
              accessibilityLabel={tx('목적지 이름', 'Destination name')}
              value={destinationQuery}
              onChangeText={setDestinationQuery}
              placeholder={tx('예: 해운대해수욕장', 'e.g. Haeundae Beach')}
              placeholderTextColor={color.text.muted}
              returnKeyType="search"
              autoCorrect={false}
              style={styles.searchInput}
            />
            {/* 지우기 버튼은 필수다 — 한 글자씩 지우게 두면 다시 검색하려는 사람이 지친다.
                검색 필드 오른쪽에 「검색」 버튼은 두지 않는다. 치는 대로 찾아 준다.
            */}
            {destinationQuery.length > 0 ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={tx('입력한 목적지 지우기', 'Clear destination')}
                onPress={() => setDestinationQuery('')}
                style={({ pressed }) => [styles.clearButton, pressed && styles.pressed]}
              >
                <Text variant="caption" weight="bold" color={color.text.body}>✕</Text>
              </Pressable>
            ) : null}
          </View>

          {/* 두 글자가 될 때까지는 「결과 없음」을 띄우지 않는다. 아직 다 치지도 않은
              사람에게 없다고 말하면 고장으로 읽힌다.
          */}
          {destination.state === 'idle' && destinationQuery.trim().length > 0 ? (
            <Text variant="caption" color={color.text.muted}>
              {tx('두 글자 이상 입력해 주세요', 'Type at least two characters')}
            </Text>
          ) : null}

          {destinationSearching ? (
            <Text variant="caption" color={color.text.muted}>{tx('찾는 중…', 'Searching…')}</Text>
          ) : null}

          {destination.state === 'empty' ? (
            <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted}>
              {/* 🔴 한자·가나로만 치면 서버가 원래 못 찾는다(nameKo·nameEn 만 대조, S15P21E201-1519).
                  「다르게 적어 보세요」로는 어떻게 다르게인지 모른다 — 찾아지는 글자를 말해 준다. */}
              {needsKoreanOrEnglishName(destinationQuery)
                ? tx(KOREAN_OR_ENGLISH_HINT.ko, KOREAN_OR_ENGLISH_HINT.en)
                : tx('그 이름의 장소를 못 찾았어요. 다르게 적어 보세요.', "We couldn't find that place. Try another spelling.")}
            </Text>
          ) : null}

          {destination.state === 'blocked' ? (
            <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.muted}>
              {destination.reason === 'signed-out'
                ? tx('로그인하면 장소를 찾을 수 있어요.', 'Sign in to search for places.')
                : tx('지금은 장소를 못 찾았어요. 잠시 후 다시 시도해 주세요.', "We couldn't search right now. Please try again shortly.")}
            </Text>
          ) : null}

          {destination.state === 'ready' ? (
            <View style={styles.destinationList}>
              {destination.items.map((item) => {
                const subtitle = destinationSubtitle(item);
                return (
                  <Pressable
                    key={item.key}
                    accessibilityRole="button"
                    accessibilityLabel={txf(tx, '%s 택시 카드 열기', 'Open taxi card for %s', item.name)}
                    onPress={() => router.push(taxiCardHref(item))}
                    style={({ pressed }) => [styles.destinationItem, pressed && styles.pressed]}
                  >
                    <View style={styles.destinationBody}>
                      <Text variant="body" weight="bold">{item.name}</Text>
                      {/* 주소가 없으면 아예 안 적는다 — 「정보 없음」은 줄만 차지한다. */}
                      {subtitle ? <Text variant="caption" color={color.text.body}>{subtitle}</Text> : null}
                    </View>
                    <Text variant="title" weight="bold" color={color.action.secondary}>›</Text>
                  </Pressable>
                );
              })}
            </View>
          ) : null}
        </View>
      )}
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
    backgroundColor: color.surface.blush,
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
  taxiPane: { gap: spacing[3], marginTop: spacing[4] },
  searchRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 56, paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card },
  searchInput: { flex: 1, minHeight: 56, fontSize: 16, color: color.text.heading },
  clearButton: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.tint },
  destinationList: { gap: spacing[2] },
  destinationItem: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 64, paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  destinationBody: { flex: 1, gap: spacing[1] },
  pressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
});
