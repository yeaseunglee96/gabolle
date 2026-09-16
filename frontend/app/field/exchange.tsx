// 환율 계산기 — S15P21E201-1137.
//
// 🔴 이 화면이 답하는 질문은 하나다: **"이 가격이 내 돈으로 얼마?"**
//    메뉴판·택시비·숙소 값을 볼 때마다 하는 계산이고, 이게 없으면 앱을 나가서 다른 앱을
//    켠다. 나간 사람은 잘 안 돌아온다.
//
// 🔴 그래서 화면에 질문을 하나만 둔다. 금액 칸 하나, 통화 하나, 결과 하나. 수수료 계산·
//    환전소 안내·추이 그래프를 얹고 싶어지지만, 그 순간 "얼마?" 를 묻는 사람이 자기 답을
//    찾기까지 한 단계가 더 생긴다.
//
// 🔴 매매기준율로 계산하고 매도율을 같이 적는다. 기준율만 크게 보여주면 환전소에서 그 값이
//    안 나와 "앱이 틀렸다" 가 된다. 차이를 숨기지 않고 작게 말해 준다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  defaultCurrencyFor,
  displayCode,
  foreignToKrw,
  krwToForeign,
  loadExchangeRates,
  pickRate,
  type ExchangeBlockedReason,
  type ExchangeRate,
} from '@/field/exchangeRates';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

/** 숫자만 남긴다. 자리 구분 쉼표를 붙여 넣어도 산다. */
function digitsOnly(value: string): string {
  return value.replace(/[^\d]/g, '').slice(0, 12);
}

/** 자리 구분 쉼표. 🔴 표기는 고른 언어를 따른다 — 문구가 영어여도 숫자 읽는 법은 그 나라 방식이 맞다. */
function grouped(value: number, locale: string): string {
  return value.toLocaleString(locale, { maximumFractionDigits: 0 });
}

export default function Exchange() {
  const router = useRouter();
  const { tx, language, locale } = useI18n();
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { accessToken, ready } = useAuth();
  const { width } = useLayout();
  const wide = isAtLeast(width, 'md');

  const [state, setState] = useState<'loading' | 'ready' | 'blocked'>('loading');
  const [reason, setReason] = useState<ExchangeBlockedReason | null>(null);
  const [rates, setRates] = useState<ExchangeRate[]>([]);
  const [asOf, setAsOf] = useState('');
  const [code, setCode] = useState(() => defaultCurrencyFor(language));
  // 🔴 "외화 → 원" 으로 시작한다. 이 화면에 오는 사람 대부분이 한국 가격표를 보고 있는 것이
  //    아니라, 자기 돈이 여기서 얼마인지를 먼저 궁금해한다. 한국어 사용자만 반대다.
  const [fromKrw, setFromKrw] = useState(language === 'ko');
  const [amount, setAmount] = useState('');

  // 🔴 화면 상태를 눈으로 확인하기 위한 자리 — (plan)/confirm.tsx 의 preview=api-error 와 같은
  //    방식이다. 환율은 로그인해야 받을 수 있어서, 로그인 없이 "계산되는 화면" 을 볼 길이 달리
  //    없다. **__DEV__ 에서만 산다** — 배포본에는 이 가지가 아예 안 들어간다.
  const previewRates: ExchangeRate[] = [
    { currencyCode: 'USD', currencyName: '미국 달러', baseRate: 1390, buyingRate: 1376, sellingRate: 1404 },
    { currencyCode: 'JPY(100)', currencyName: '일본 옌', baseRate: 940, buyingRate: 930, sellingRate: 950 },
    { currencyCode: 'CNY', currencyName: '중국 위안', baseRate: 195, buyingRate: 193, sellingRate: 197 },
    { currencyCode: 'TWD', currencyName: '대만 달러', baseRate: 44, buyingRate: 43, sellingRate: 45 },
    { currencyCode: 'EUR', currencyName: '유로', baseRate: 1512, buyingRate: 1497, sellingRate: 1527 },
  ];

  const load = useCallback(async () => {
    if (__DEV__ && preview === 'ui') {
      setRates(previewRates); setAsOf('2026-09-17'); setState('ready');
      return;
    }
    setState('loading');
    const out = await loadExchangeRates(accessToken);
    if (out.state === 'ready') {
      setRates(out.rates);
      setAsOf(out.asOf);
      setState('ready');
      return;
    }
    setReason(out.reason);
    setState('blocked');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, preview]);

  useEffect(() => { if (ready) void load(); }, [ready, load]);

  const rate = useMemo(() => pickRate(rates, code), [rates, code]);
  const numeric = Number(digitsOnly(amount) || '0');
  const converted = rate ? (fromKrw ? krwToForeign(numeric, rate) : foreignToKrw(numeric, rate)) : 0;

  const inCode = fromKrw ? 'KRW' : displayCode(rate?.currencyCode ?? code);
  const outCode = fromKrw ? displayCode(rate?.currencyCode ?? code) : 'KRW';

  function blockedText(r: ExchangeBlockedReason): { title: string; body: string } {
    if (r === 'signed-out') return {
      title: tx('로그인하면 오늘 환율로 계산해 드려요', 'Sign in to convert at today’s rate'),
      body: tx('환율은 한도가 있는 외부 자료라 로그인한 분에게만 보여드려요.', 'Rates come from a metered source, so they are for signed-in travelers.'),
    };
    if (r === 'not-built') return {
      title: tx('환율 기능이 아직 서버에 없어요', 'Rates are not on the server yet'),
      body: tx('곧 열려요. 조금 뒤에 다시 들러 주세요.', 'It is coming. Please check back a little later.'),
    };
    if (r === 'vendor') return {
      title: tx('오늘 환율을 못 받았어요', 'Could not get today’s rates'),
      body: tx('환율 제공처가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요.', 'The rate provider is not responding right now. Please try again shortly.'),
    };
    return {
      title: tx('환율을 불러오지 못했어요', 'Could not load rates'),
      body: tx('잠시 후 다시 시도해 주세요.', 'Please try again shortly.'),
    };
  }

  return (
    <Screen scroll wide>
      <Text variant="display" weight="bold">{tx('환율 계산', 'Currency')}</Text>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        {tx('가격표를 보고 바로 내 돈으로 바꿔 보세요.', 'Turn a price tag into your own money, right here.')}
      </Text>

      {state === 'loading' ? (
        <View style={styles.card}><Text color={color.text.muted}>{tx('오늘 환율을 확인하고 있어요…', 'Checking today’s rates…')}</Text></View>
      ) : null}

      {state === 'blocked' && reason ? (
        <View accessibilityLiveRegion="polite" style={styles.card}>
          <Text variant="title" weight="bold">{blockedText(reason).title}</Text>
          <Text color={color.text.body} style={styles.blockedBody}>{blockedText(reason).body}</Text>
          {reason === 'signed-out'
            ? <Button label={tx('로그인하기', 'Sign in')} containerStyle={styles.cta} onPress={() => router.push('/sign-in')} />
            : <Button label={tx('다시 시도', 'Try again')} variant="ghost" containerStyle={styles.cta} onPress={() => void load()} />}
        </View>
      ) : null}

      {state === 'ready' && rate ? (
        <>
          <View style={[styles.card, wide && styles.cardWide]}>
            <View style={styles.row}>
              <Text variant="caption" weight="bold" color={color.text.eyebrow}>{inCode}</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={tx('바꾸는 방향 뒤집기', 'Swap direction')}
                onPress={() => setFromKrw((v) => !v)}
                style={({ pressed }) => [styles.swap, pressed && styles.pressed]}
              >
                <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('방향 바꾸기 ⇅', 'Swap ⇅')}</Text>
              </Pressable>
            </View>
            <TextInput
              accessibilityLabel={tx('바꿀 금액', 'Amount to convert')}
              value={amount}
              onChangeText={(v) => setAmount(digitsOnly(v))}
              keyboardType="number-pad"
              placeholder="0"
              placeholderTextColor={color.text.muted}
              style={styles.amountInput}
            />

            <View style={styles.resultBox}>
              <Text variant="caption" weight="bold" color={color.text.eyebrow}>{outCode}</Text>
              <Text variant="display" weight="bold" style={styles.result}>{grouped(Math.round(converted), locale)}</Text>
            </View>

            {/* 🔴 기준율과 매도율을 같이 적는다. 기준율만 보여주면 환전소에서 그 값이 안 나온다. */}
            <Text variant="caption" color={color.text.muted}>
              {tx(
                `매매기준율 ${grouped(rate.baseRate, locale)}원 · 살 때 ${grouped(rate.sellingRate, locale)}원`,
                `Base ${grouped(rate.baseRate, locale)} KRW · You pay about ${grouped(rate.sellingRate, locale)} KRW`,
              )}
            </Text>
            <Text variant="caption" color={color.text.muted}>
              {tx(`${asOf} 고시 · 환전소 값은 조금 달라요`, `As of ${asOf} · exchange booths differ a little`)}
            </Text>
          </View>

          <Text variant="caption" weight="bold" color={color.text.eyebrow} style={styles.pickerTitle}>
            {tx('통화 고르기', 'Choose a currency')}
          </Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
            {rates.map((item) => {
              const selected = displayCode(item.currencyCode) === displayCode(rate.currencyCode);
              return (
                <Pressable
                  key={item.currencyCode}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  accessibilityLabel={`${displayCode(item.currencyCode)} ${item.currencyName}`}
                  onPress={() => setCode(item.currencyCode)}
                  style={({ pressed }) => [styles.chip, selected && styles.chipSelected, pressed && styles.pressed]}
                >
                  <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>
                    {displayCode(item.currencyCode)}
                  </Text>
                </Pressable>
              );
            })}
          </ScrollView>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[2], marginBottom: spacing[6] },
  card: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardWide: { maxWidth: 520 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  swap: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.tint },
  amountInput: { minHeight: 56, fontSize: 32, fontWeight: '700', color: color.text.heading, paddingVertical: spacing[2] },
  resultBox: { gap: spacing[1], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.tint },
  result: { lineHeight: 42 },
  blockedBody: { lineHeight: 22 },
  cta: { marginTop: spacing[2] },
  pickerTitle: { marginTop: spacing[6], marginBottom: spacing[2] },
  chips: { gap: spacing[2], paddingVertical: spacing[1] },
  chip: { minHeight: 44, minWidth: 64, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { borderColor: color.brand.navy, backgroundColor: color.brand.navy },
  pressed: { opacity: 0.78 },
});
