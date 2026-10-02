// 환율 계산기 — S15P21E201-1137.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
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
  unitsPerQuote,
  type ExchangeBlockedReason,
  type ExchangeRate,
} from '@/field/exchangeRates';
import { vendorNotReadyMessage } from '@/api/vendorReady';
import { useI18n } from '@/i18n';
import { CurrencyBadge, currencyDisplayName } from '@/field/CurrencyBadge';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { txf } from '@/i18n/format';

/** 숫자만 남긴다. 자리 구분 쉼표를 붙여 넣어도 산다. */
function digitsOnly(value: string): string {
  return value.replace(/[^\d]/g, '').slice(0, 12);
}

/**
 * 자리 구분 쉼표. 표기는 고른 언어를 따른다 — 문구가 영어여도 숫자 읽는 법은 그 나라 방식이 맞다.
 *
 * 🔴 «자료가 없으면 조용히 딴 로케일로 갈아치운다»는 `Intl` 의 성질은 `Intl.NumberFormat`
 *    에도 똑같이 있다(S15P21E201-1399 — 같은 판단이 `CurrencyBadge.tsx`·`i18n/datetime.ts`
 *    에도 있다). 쓰기 전에 `supportedLocalesOf` 로 묻고, 자료가 없으면 로케일에 기대지
 *    않는 자리 구분(쉼표)으로 내려간다 — 환전 화면에서 숫자가 잘못 보이면 실제 돈 액수를
 *    오해하게 되므로, 여기서만은 «다른 나라 표기가 섞이는 것»조차 허용하지 않는다.
 */
function grouped(value: number, locale: string): string {
  const rounded = Math.round(value);
  try {
    if (Intl.NumberFormat.supportedLocalesOf([locale]).length) {
      return new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(rounded);
    }
  } catch {
    // 아래 자리 구분으로 내려간다.
  }
  return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/**
 * 화면에 적을 통화 표기. 100단위로 고시되는 통화는 «그 수를 앞에 붙인다».
 *
 * 🔴 계산은 늘 맞았다(unitsPerQuote). 틀린 것은 «보여줄 때»다 — displayCode 가
 *    `JPY(100)` 에서 괄호를 걷어내면서 100 이 화면에서 사라져, 목록 줄이 「JPY … ₩881」이
 *    됐다. 1엔이 881원으로 읽힌다. 부산은 일본·중국 여행객이 주 대상이라 JPY 는 대표
 *    통화이고, 100배 틀린 값으로 읽히는 숫자를 가격표 옆에 두면 이 화면이 하려던 일과
 *    정반대가 된다(S15P21E201-1449).
 *
 * 말이 아니라 «숫자와 코드»로만 적는다 — 어느 언어로 보든 같게 읽힌다.
 */
function quotedCode(currencyCode: string): string {
  const units = unitsPerQuote(currencyCode);
  const code = displayCode(currencyCode);
  return units > 1 ? `${units} ${code}` : code;
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
  // 🔴 서버는 스무 통화를 알파벳순으로 준다(AED·AUD·BHD…). 부산에 오는 사람이 실제로 쓰는 통화를 앞에 둔다 —
  //    2026-09-21 실서버 실기. 뒤는 그대로 알파벳순. 이름이 없는 통화(CNH)는 코드가 두 번 보이지 않게 뺀다.
  const orderedRates = useMemo(() => {
    const rank = (item: ExchangeRate) => { const i = PINNED_CURRENCIES.indexOf(displayCode(item.currencyCode)); return i < 0 ? PINNED_CURRENCIES.length : i; };
    return [...rates].sort((x, y) => rank(x) - rank(y) || displayCode(x.currencyCode).localeCompare(displayCode(y.currencyCode)));
  }, [rates]);
  const [asOf, setAsOf] = useState('');
  const [code, setCode] = useState(() => defaultCurrencyFor(language));
  // "외화 → 원" 으로 시작한다. 이 화면에 오는 사람 대부분이 한국 가격표를 보고 있는 것이
  // 아니라, 자기 돈이 여기서 얼마인지를 먼저 궁금해한다. 한국어 사용자만 반대다.
  const [fromKrw, setFromKrw] = useState(language === 'ko');
  // 🔴 웹에서는 고른 언어가 저장소에서 늦게 온다 — 첫 그림의 언어(영어)로 정한 USD 가 중국어 화면에 남았다
  //    (운영 웹 10/2, S15P21E201-1941). 사람이 직접 고르거나 바꾸기 전까지는 언어가 바뀌면 기본값을 다시 맞춘다.
  const touched = useRef({ code: false, direction: false });
  useEffect(() => {
    if (!touched.current.code) setCode(defaultCurrencyFor(language));
    if (!touched.current.direction) setFromKrw(language === 'ko');
  }, [language]);
  const [amount, setAmount] = useState('');

  // 화면 상태를 눈으로 확인하기 위한 자리 — (plan)/confirm.tsx 의 preview=api-error 와 같은
  // 방식이다. 환율은 로그인해야 받을 수 있어서, 로그인 없이 "계산되는 화면" 을 볼 길이 달리
  // 없다. __DEV__ 에서만 산다 — 배포본에는 이 가지가 아예 안 들어간다.
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
    // — 열쇠가 안 꽂힌 것은 「잠시 뒤」가 아니다. 그렇게 말하면 거짓말이다.
    if (r === 'not-ready') return {
      title: tx('환율은 아직 준비 중이에요', 'Exchange rates are not set up yet'),
      body: vendorNotReadyMessage(tx),
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
          {/* — 준비되지 않은 기능에는 「다시 시도」를 안 보여준다.
              눌러도 달라지지 않는 단추는 없는 것보다 나쁘다 — 사람을 거기 묶어 둔다.
          */}
          {reason === 'signed-out'
            ? <Button label={tx('로그인하기', 'Sign in')} containerStyle={styles.cta} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/field/exchange-rate' } })} />
            : reason === 'not-ready'
              ? null
              : <Button label={tx('다시 시도', 'Try again')} variant="tertiary" containerStyle={styles.cta} onPress={() => void load()} />}
        </View>
      ) : null}

      {state === 'ready' && rate ? (
        <>
          <View style={[styles.card, wide && styles.cardWide]}>
            <View style={styles.row}>
              <View style={styles.codeRow}><CurrencyBadge code={inCode} /><Text variant="caption" weight="bold" color={color.text.eyebrow}>{inCode}</Text></View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={tx('바꾸는 방향 뒤집기', 'Swap direction')}
                onPress={() => { touched.current.direction = true; setFromKrw((v) => !v); }}
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
              <View style={styles.codeRow}><CurrencyBadge code={outCode} /><Text variant="caption" weight="bold" color={color.text.eyebrow}>{outCode}</Text></View>
              <Text variant="display" weight="bold" style={styles.result}>{grouped(Math.round(converted), locale)}</Text>
            </View>

            {/* 기준율과 매도율을 같이 적는다. 기준율만 보여주면 환전소에서 그 값이 안 나온다. */}
            <Text variant="caption" color={color.text.muted}>
              {unitsPerQuote(rate.currencyCode) > 1 ? `${quotedCode(rate.currencyCode)} · ` : ''}
              {txf(tx, '매매기준율 %s원 · 살 때 %s원', 'Base %s KRW · You pay about %s KRW', grouped(rate.baseRate, locale), grouped(rate.sellingRate, locale))}
            </Text>
            <Text variant="caption" color={color.text.muted}>
              {txf(tx, '%s 고시 · 환전소 값은 조금 달라요', 'As of %s · exchange booths differ a little', asOf)}
            </Text>
          </View>

          <Text variant="caption" weight="bold" color={color.text.eyebrow} style={styles.pickerTitle}>
            {tx('통화 고르기', 'Choose a currency')}
          </Text>
          {/* 시안 5 Exchange — 국기 · 코드 · 이름(고른 언어) · 기준율을 한 줄에. 코드만 있는 칩은 처음 보는 사람이 한 번 더 생각한다. */}
          <View accessibilityRole="radiogroup" style={styles.currencyList}>
            {orderedRates.map((item) => {
              const selected = displayCode(item.currencyCode) === displayCode(rate.currencyCode);
              const name = currencyDisplayName(item.currencyCode, locale, item.currencyName);
              return (
                <Pressable
                  key={item.currencyCode}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  accessibilityLabel={`${quotedCode(item.currencyCode)} ${name}`}
                  onPress={() => { touched.current.code = true; setCode(item.currencyCode); }}
                  style={({ pressed }) => [styles.currencyRow, selected && styles.currencyRowSelected, pressed && styles.pressed]}
                >
                  <CurrencyBadge code={item.currencyCode} size="large" />
                  <View style={styles.currencyCopy}>
                    <Text variant="body" weight="bold">{quotedCode(item.currencyCode)}</Text>
                    {name && name !== displayCode(item.currencyCode) ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{name}</Text> : null}
                  </View>
                  <Text variant="body" weight="bold">₩{grouped(item.baseRate, locale)}</Text>
                </Pressable>
              );
            })}
          </View>
        </>
      ) : null}
    </Screen>
  );
}

/** 앞에 둘 통화 — 국기가 있는 넷 + 홍콩·유로·파운드·싱가포르. */
const PINNED_CURRENCIES = ['USD', 'JPY', 'CNY', 'TWD', 'HKD', 'EUR', 'GBP', 'SGD'];

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
  codeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  currencyList: { gap: spacing[2] },
  currencyRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 60, paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1.5, borderColor: color.surface.border, backgroundColor: color.surface.card },
  currencyRowSelected: { borderColor: color.action.secondary, backgroundColor: color.surface.tint },
  currencyCopy: { flex: 1, gap: 1 },
  chips: { gap: spacing[2], paddingVertical: spacing[1] },
  chip: { minHeight: 44, minWidth: 64, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipSelected: { borderColor: color.brand.navy, backgroundColor: color.brand.navy },
  pressed: { opacity: 0.78 },
});
