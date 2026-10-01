// 공유된 일정을 로그인 없이 보는 화면(상세설계서 Part II P-32, /s/:token) — S15P21E201-335.
// 이 서비스를 처음 보는 사람이 친구 링크로 들어오는 입구라, 여행 화면 껍데기(Screen 안의
// 탭바 등)를 안 쓰는 단독 레이아웃으로 둔다. "내 조건으로 새 여행 만들기"는
// 기존 plan 흐름(plan/basic → … → confirm)을 그대로 타되, PlanProvider 에 공유 표(token)를
// 심어 두면 마지막 제출 지점(recommendationJob.ts)이 일반 생성 대신 복제(clone) API를 부른다.
import { txf } from '@/i18n/format';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Clipboard from 'expo-clipboard';

import { APP_WEB_BASE_URL } from '@/api/client';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatClock, formatDayHeading, formatFullDate } from '@/i18n/datetime';
import { koreanTopic } from '@/i18n/korean';
import { LANGUAGE_OPTIONS } from '@/i18n/languages';
import { browserLanguages, sentenceStart, sharedPageInitialLanguage } from '@/share/sharedPageLanguage';
import { stopNameForLanguage } from '@/discovery/romanize';
import { useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';
import { PLACE_CATEGORY_LABELS } from '@/discovery/placeCategoryLabels';
import { usePlan } from '@/plan/PlanProvider';
import { getSharedItinerary, type SharedItineraryDto } from '@/share/sharedItinerary';

type Status =
  | { state: 'loading' }
  | { state: 'success'; data: SharedItineraryDto }
  | { state: 'expired' }
  | { state: 'not_found' }
  | { state: 'error'; message: string };

// 백엔드 SharedItineraryResponse.NOT_SHARED 와 이름을 맞춘다 — 코드가 늘면 여기도 같이 늘린다.
const NOT_SHARED_LABEL: Record<string, { ko: string; en: string }> = {
  origin: { ko: '출발지', en: 'starting point' },
  contact: { ko: '연락처', en: 'contact info' },
  budget: { ko: '예산', en: 'budget' },
  partySize: { ko: '인원', en: 'party size' },
};

/** 「10월 3일 (토)」 — 못 읽는 날짜면 받은 그대로. */
function dayHeading(value: string, locale: string): string {
  return formatDayHeading(value, locale) ?? value;
}

/** 서버는 장소의 분류 코드(SEA_BEACH 등)를 그대로 싣는다 — 이름표로 바꾸고, 모르는 코드는 안 보인다(S15P21E201-1677). */
function categoryText(code: string | null, tx: (ko: string, en: string) => string): string | null {
  const label = code ? PLACE_CATEGORY_LABELS[code] : undefined;
  return label ? tx(label[0], label[1]) : null;
}

/** 로그인 없이 보는 화면이라 설정 화면에 갈 수 없다 — 여기서 바로 언어를 바꾼다. */
function LanguageSwitch() {
  const { language, setLanguage, tx } = useI18n();
  return <View accessibilityRole="radiogroup" accessibilityLabel={tx('언어', 'Language')} style={styles.languages}>
    {LANGUAGE_OPTIONS.map((option) => {
      const on = option.code === language;
      return <Pressable key={option.code} accessibilityRole="radio" accessibilityState={{ checked: on }} accessibilityLabel={option.endonym} onPress={() => setLanguage(option.code)} style={[styles.language, on && styles.languageOn]}>
        <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.body}>{option.endonym}</Text>
      </Pressable>;
    })}
  </View>;
}

export default function SharedItinerary() {
  const router = useRouter();
  const { tx, locale, language, setLanguage } = useI18n();
  const { hydrated, hasEnteredApp } = useOnboardingPreferences();
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { update, clear } = usePlan();
  const [status, setStatus] = useState<Status>({ state: 'loading' });
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const next = sharedPageInitialLanguage({ web: Platform.OS === 'web', hydrated, hasEnteredApp, current: language, browserLanguages: browserLanguages() });
    if (next) setLanguage(next);
    // 저장소를 다 읽은 뒤 한 번만 — 언어가 바뀔 때마다 다시 보면 사람이 고른 언어를 브라우저 언어로 되돌린다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated]);

  useEffect(() => {
    if (!token) { setStatus({ state: 'not_found' }); return; }
    let active = true;
    (async () => {
      const result = await getSharedItinerary(token);
      if (active) setStatus(result);
    })();
    return () => { active = false; };
  }, [token]);

  async function startMyOwnTrip() {
    if (!token) return;
    await clear();
    update({ cloneShareToken: token });
    router.push('/plan');
  }

  async function copyLink() {
    if (!token) return;
    await Clipboard.setStringAsync(`${APP_WEB_BASE_URL}/s/${token}`);
    setCopied(true);
  }

  if (status.state === 'loading') {
    return <Screen style={styles.center}>
      <Text accessibilityLiveRegion="polite" weight="bold">{tx('공유 일정을 불러오는 중이에요…', 'Loading the shared itinerary…')}</Text>
    </Screen>;
  }

  if (status.state === 'expired') {
    return <Screen style={styles.center}>
      <View accessibilityRole="alert" style={styles.card}>
        <Text variant="title" weight="bold">{tx('공유 링크가 만료되었어요', 'This share link has expired')}</Text>
        <Text color={color.text.body}>{tx('공유 링크는 발급 후 30일 동안만 볼 수 있어요.', 'Share links can be viewed for 30 days after they’re created.')}</Text>
        <Button label={tx('내 조건으로 새 여행 만들기', 'Create a new trip with my own conditions')} onPress={() => router.push('/plan')} />
      </View>
    </Screen>;
  }

  if (status.state === 'not_found' || status.state === 'error') {
    return <Screen style={styles.center}>
      <View accessibilityRole="alert" style={styles.card}>
        <Text variant="title" weight="bold">{tx('공유 일정을 찾을 수 없어요', 'This shared itinerary could not be found')}</Text>
        <Text color={color.text.body}>{status.state === 'error' ? status.message : tx('링크가 정확한지 확인해 주세요. 지워졌거나 잘못된 링크일 수 있어요.', 'Please check the link. It may be invalid, or the trip may have been deleted.')}</Text>
        <Button label={tx('홈으로', 'Go home')} variant="tertiary" onPress={() => router.replace('/home')} />
      </View>
    </Screen>;
  }

  const { data } = status;
  const notSharedLabels = data.notShared
    .map((code) => (NOT_SHARED_LABEL[code] ? tx(NOT_SHARED_LABEL[code].ko, NOT_SHARED_LABEL[code].en) : code))
    .join(tx(' · ', ', '));

  return <Screen scroll style={styles.screen}>
    <LanguageSwitch />
    <View style={styles.heading}>
      <Text variant="eyebrow" weight="bold">{tx('공유된 여행 일정', 'Shared trip itinerary')}</Text>
      <Text variant="display" weight="bold">{data.title}</Text>
      {/* 날짜는 내 여행 목록과 같은 모양 — 「2026-10-03」 기계 모양을 그대로 보이지 않는다(S15P21E201-1677). */}
      <Text color={color.text.body}>{`${dayHeading(data.startDate, locale)} – ${dayHeading(data.finishDate, locale)}`}</Text>
      <Text variant="caption" color={color.text.muted}>{txf(tx, '이 링크는 %s까지 볼 수 있어요.', 'This link is viewable until %s.', formatFullDate(data.expiresAt, locale))}</Text>
    </View>

    <View style={styles.notice}>
      <Text variant="caption" weight="bold">{tx('공유되지 않는 정보', 'Not shared')}</Text>
      <Text variant="caption" color={color.text.body}>{sentenceStart(txf(tx, `%s${koreanTopic(notSharedLabels)} 공유되지 않아요.`, '%s are not shared.', notSharedLabels))}</Text>
    </View>

    {data.days.length === 0 ? (
      <View style={styles.dayCard}><Text color={color.text.body}>{tx('아직 짜인 일정이 없어요.', 'No itinerary has been planned yet.')}</Text></View>
    ) : data.days.map((day, index) => (
      <View key={day.date} style={styles.dayCard}>
        <Text variant="title" weight="bold" style={styles.dayTitle}>{txf(tx, '%s일차 · %s', 'Day %s · %s', index + 1, dayHeading(day.date, locale))}</Text>
        {day.items.length === 0 ? (
          <Text variant="caption" color={color.text.muted}>{tx('이 날은 일정이 없어요.', 'Nothing planned for this day.')}</Text>
        ) : day.items.map((item) => (
          <View key={item.sequence} style={styles.itemRow}>
            <Text variant="caption" weight="bold" color={color.text.accent} style={styles.itemTime}>
              {item.startsAt ? formatClock(item.startsAt, locale) : '–'}
            </Text>
            <View style={styles.itemCopy}>
              <Text weight="bold">{stopNameForLanguage(item.placeName, null, language)}</Text>
              <Text variant="caption" color={color.text.muted}>
                {[categoryText(item.category, tx), item.stayMinutes ? tx(`${item.stayMinutes}분 머묾`, `${item.stayMinutes} min stay`) : null].filter(Boolean).join(' · ')}
              </Text>
            </View>
          </View>
        ))}
      </View>
    ))}

    <View style={styles.actions}>
      <Button label={tx('내 조건으로 새 여행 만들기', 'Create a new trip with my own conditions')} onPress={() => void startMyOwnTrip()} />
      <Button label={copied ? tx('링크 복사됨 ✓', 'Link copied ✓') : tx('링크 복사', 'Copy link')} variant="tertiary" onPress={() => void copyLink()} />
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  center: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.ivory },
  card: { gap: spacing[3], width: '100%', maxWidth: 420, padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  heading: { gap: spacing[1], marginTop: spacing[3], marginBottom: spacing[4] },
  languages: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end', gap: spacing[1], marginTop: spacing[4] },
  language: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  languageOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  notice: { gap: spacing[1], marginBottom: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  dayCard: { gap: spacing[2], marginBottom: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  dayTitle: { marginBottom: spacing[1] },
  itemRow: { flexDirection: 'row', gap: spacing[3] },
  itemTime: { width: 52 },
  itemCopy: { flex: 1, gap: spacing[1] },
  actions: { gap: spacing[3], marginTop: spacing[2], marginBottom: spacing[8] },
});
