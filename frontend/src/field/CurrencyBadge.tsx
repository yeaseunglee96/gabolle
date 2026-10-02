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
 *
 * 🔴 «없을 때»만으로는 모자라다 — S15P21E201-1399. Intl 은 자료가 없는 로케일에 던지지
 *    않고 조용히 딴 로케일로 갈아치운다. 그러면 아래 catch 가 한 번도 안 걸린 채 번체를
 *    고른 사람이 딴 언어 통화 이름을 본다. 그래서 쓰기 전에 supportedLocalesOf 로 묻고,
 *    자료가 없으면 서버가 준 이름으로 내려간다. (같은 판단이 i18n/datetime.ts 에도 있다)
 */
/**
 * 🔴 Intl 자료(ICU — 브라우저·운영체제에 들어 있는 언어 자료)가 틀리게 적은 이름을 여기서 고친다 — S15P21E201-1790.
 *    크롬·노드의 한국어 자료가 BND 를 「부루나이 달러」로 준다(표준 표기는 「브루나이 달러」, 서버 수출입은행 자료도 이렇게 준다).
 *    우리 코드나 서버가 아니라 바깥 자료의 오기라 고칠 곳이 없어 보여 주기 직전에 바꾼다. 알려진 것만 적는다.
 */
const INTL_NAME_FIXES: Record<string, Record<string, string>> = {
  ko: { '부루나이 달러': '브루나이 달러' },
};

/**
 * 🔴 안드로이드 앱(Hermes)에는 Intl.DisplayNames 가 없다 — 그래서 영어·일본어·중국어 화면에서도 서버가 준 한국어 이름
 *    (「미국 달러」)이 그대로 나왔다(실기기 10/2, S15P21E201-1913). 깃발이 있는 통화(=수출입은행이 주는 것)만 앱에 이름을 둔다.
 *    [영어, 일본어, 간체, 번체]
 */
const NAMES: Record<string, [string, string, string, string]> = {
  KRW: ['South Korean Won', '韓国ウォン', '韩元', '韓元'],
  USD: ['US Dollar', '米ドル', '美元', '美元'],
  JPY: ['Japanese Yen', '日本円', '日元', '日圓'],
  CNY: ['Chinese Yuan', '中国人民元', '人民币', '人民幣'],
  CNH: ['Chinese Yuan (offshore)', '中国人民元（オフショア）', '离岸人民币', '離岸人民幣'],
  TWD: ['New Taiwan Dollar', '新台湾ドル', '新台币', '新台幣'],
  HKD: ['Hong Kong Dollar', '香港ドル', '港元', '港幣'],
  SGD: ['Singapore Dollar', 'シンガポールドル', '新加坡元', '新加坡幣'],
  MYR: ['Malaysian Ringgit', 'マレーシアリンギット', '马来西亚林吉特', '馬來西亞令吉'],
  THB: ['Thai Baht', 'タイバーツ', '泰铢', '泰銖'],
  IDR: ['Indonesian Rupiah', 'インドネシアルピア', '印度尼西亚盾', '印尼盾'],
  BND: ['Brunei Dollar', 'ブルネイドル', '文莱元', '汶萊元'],
  AUD: ['Australian Dollar', 'オーストラリアドル', '澳大利亚元', '澳幣'],
  NZD: ['New Zealand Dollar', 'ニュージーランドドル', '新西兰元', '紐西蘭幣'],
  EUR: ['Euro', 'ユーロ', '欧元', '歐元'],
  GBP: ['British Pound', '英国ポンド', '英镑', '英鎊'],
  CHF: ['Swiss Franc', 'スイスフラン', '瑞士法郎', '瑞士法郎'],
  DKK: ['Danish Krone', 'デンマーククローネ', '丹麦克朗', '丹麥克朗'],
  NOK: ['Norwegian Krone', 'ノルウェークローネ', '挪威克朗', '挪威克朗'],
  SEK: ['Swedish Krona', 'スウェーデンクローナ', '瑞典克朗', '瑞典克朗'],
  CAD: ['Canadian Dollar', 'カナダドル', '加拿大元', '加幣'],
  AED: ['UAE Dirham', 'UAEディルハム', '阿联酋迪拉姆', '阿聯酋迪拉姆'],
  SAR: ['Saudi Riyal', 'サウジアラビアリヤル', '沙特里亚尔', '沙烏地里亞爾'],
  KWD: ['Kuwaiti Dinar', 'クウェートディナール', '科威特第纳尔', '科威特第納爾'],
  BHD: ['Bahraini Dinar', 'バーレーンディナール', '巴林第纳尔', '巴林第納爾'],
};

function tableName(key: string, locale: string): string | null {
  const row = NAMES[key];
  if (!row) return null;
  const tag = locale.toLowerCase();
  if (tag.startsWith('ko')) return null;
  if (tag.startsWith('ja')) return row[1];
  if (tag.startsWith('zh')) return /hant|tw|hk|mo/.test(tag) ? row[3] : row[2];
  return row[0];
}

export function currencyDisplayName(code: string, locale: string, fallback: string): string {
  const fromTable = tableName(currencyKey(code), locale);
  try {
    if (typeof Intl.DisplayNames !== 'function' || !Intl.DisplayNames.supportedLocalesOf([locale]).length) return fromTable ?? fallback;
    const names = new Intl.DisplayNames([locale], { type: 'currency' });
    const name = names.of(currencyKey(code));
    // 브라우저가 모르는 코드(CNH)는 코드를 그대로 돌려준다 — 이름 칸이 비었다(운영 웹 10/2, S15P21E201-1941).
    if (!name || name.toUpperCase() === currencyKey(code)) return fromTable ?? fallback;
    return INTL_NAME_FIXES[locale.split('-')[0].toLowerCase()]?.[name] ?? name;
  } catch {
    return fromTable ?? fallback;
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
