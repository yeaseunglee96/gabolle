// 통화 표식 — 국기 + 코드. 시안 5 의 Exchange 보드(docs/design_handoff_brand_first_run 참조).
//
// 코드 세 글자(USD)만 있으면 처음 보는 사람이 「내 돈이 어느 것인지」를 한 번 더 생각한다. 국기가
// 있으면 안 생각한다. 국기 그림이 없는 통화(EUR 등)는 통화 기호(€)를 동그라미에 넣는다 — 없는
// 국기를 지어내지 않는다.
import { Image, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius } from '@/design/tokens';

// require 는 번들러가 정적으로 읽어야 해서 표에 직접 적는다(WelcomeLanguageSheet 와 같은 이유).
// 서버가 주는 통화 전부에 국기가 있다(S15P21E201-1395) — 「몇 개만 있는 것」은 사용자 눈에 덜 만든 것으로 읽힌다.
// flagcdn.com 의 공개 국기 그림(w160). CNH(역외 위안)도 중국 국기.
const FLAGS: Record<string, ReturnType<typeof require>> = {
  KRW: require('../../assets/flags/kr.png'),
  USD: require('../../assets/flags/us.png'),
  JPY: require('../../assets/flags/jp.png'),
  CNY: require('../../assets/flags/cn.png'),
  CNH: require('../../assets/flags/cn.png'),
  TWD: require('../../assets/flags/tw.png'),
  HKD: require('../../assets/flags/hk.png'),
  SGD: require('../../assets/flags/sg.png'),
  MYR: require('../../assets/flags/my.png'),
  THB: require('../../assets/flags/th.png'),
  IDR: require('../../assets/flags/id.png'),
  BND: require('../../assets/flags/bn.png'),
  AUD: require('../../assets/flags/au.png'),
  NZD: require('../../assets/flags/nz.png'),
  EUR: require('../../assets/flags/eu.png'),
  GBP: require('../../assets/flags/gb.png'),
  CHF: require('../../assets/flags/ch.png'),
  DKK: require('../../assets/flags/dk.png'),
  NOK: require('../../assets/flags/no.png'),
  SEK: require('../../assets/flags/se.png'),
  CAD: require('../../assets/flags/ca.png'),
  AED: require('../../assets/flags/ae.png'),
  SAR: require('../../assets/flags/sa.png'),
  KWD: require('../../assets/flags/kw.png'),
  BHD: require('../../assets/flags/bh.png'),
};

const SYMBOLS: Record<string, string> = { EUR: '€', GBP: '£', HKD: 'HK$', SGD: 'S$', AUD: 'A$', CAD: 'C$', THB: '฿', VND: '₫', PHP: '₱', IDR: 'Rp', MYR: 'RM' };

/** 「JPY(100)」 같은 단위 붙은 코드에서 세 글자만. */
export function currencyKey(code: string): string {
  return code.replace(/\(.*\)/, '').trim().toUpperCase();
}

/**
 * 통화 이름을 고른 언어로 — 운영체제의 Intl 이 안다(USD → 「미국 달러」·「US Dollar」·「米ドル」·「美元」).
 * 서버가 준 한국어 이름은 Intl 이 없을 때의 대체다.
 */
export function currencyDisplayName(code: string, locale: string, fallback: string): string {
  try {
    const names = new Intl.DisplayNames([locale], { type: 'currency' });
    return names.of(currencyKey(code)) ?? fallback;
  } catch {
    return fallback;
  }
}

export function CurrencyBadge({ code, size = 'small' }: { code: string; size?: 'small' | 'large' }) {
  const key = currencyKey(code);
  const flag = FLAGS[key];
  const box = size === 'large' ? styles.flagLarge : styles.flagSmall;
  if (flag) return <Image source={flag} resizeMode="cover" accessibilityIgnoresInvertColors style={[styles.flag, box]} />;
  return (
    <View style={[styles.symbol, box]}>
      <Text variant={size === 'large' ? 'body' : 'caption'} weight="bold" color={color.text.heading}>{SYMBOLS[key] ?? key.slice(0, 1)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // 국기는 3:2 그대로 — 잘리면 다른 나라로 오인된다. 흰 바탕 국기(일본)가 카드에 녹지 않게 실선 하나.
  flag: { borderRadius: 4, borderWidth: StyleSheet.hairlineWidth, borderColor: color.surface.field },
  flagSmall: { width: 24, height: 16 },
  flagLarge: { width: 36, height: 24 },
  symbol: { alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm, backgroundColor: color.surface.tint },
});
