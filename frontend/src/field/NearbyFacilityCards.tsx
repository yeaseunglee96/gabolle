// 근처 편의시설 카드 넷 — 주차장 · 약국 · 공중화장실 · 지하철 물품보관함 (S15P21E201-2052).
//
// 카드를 누르면 «가는 법»이 열린다(app/field/nearby.tsx 가 정한다). 이 파일은 그리기만 한다 — 위치·서버·길 안내는 모른다.
// 🔴 지도와 짝지어 쓸 때(link)는 카드를 누르면 «지도에서 그 점이 골라진다» — 가까운 도움(nearby-help.tsx)의 목록과 같은 규칙이다(S15P21E201-2052).
//    그때 가는 법은 카드 아래의 단추(길 안내 · 지도 앱 · 전화)가 맡는다. link 가 없으면(카드만 따로 쓸 때) 예전처럼 누르면 가는 법이 열린다.
//
// 🔴 «모른다»를 «없다»로 그리지 않는다.
//    · 요금을 모르는 유료 주차장은 「무료」도 「0원」도 아니고 「요금 정보 없음」이다(feeSummaryOf).
//    · 영업 시간을 모르는 곳은 「영업 종료」가 아니라 아무 말 없이 「운영시간 정보 없음」이다(openStatusOf).
//    · 물품보관함의 칸 수는 «설치된 칸 수»이지 «비어 있는 칸 수»가 아니다 — 그렇게 적는다.
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { addressForLanguage } from '@/discovery/localAddress';
import { romanizeKorean } from '@/discovery/romanize';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { enPlural, txf } from '@/i18n/format';

import {
  feeSummaryOf,
  formatWon,
  LOCKER_SIZES,
  openStatusOf,
  parseLockerFee,
  walkMinutesFor,
  type FacilityItem,
  type LockerSize,
  type OpenStatus,
} from './nearbyFacilities';

type Tx = (ko: string, en: string) => string;

/** 카드 아래의 «가는 법» 동작들. 길 안내는 내 위치를 알 때만 있다(모르는 위치에서 찾은 길은 틀린다). */
export type FacilityActions = {
  /** 앱 안의 길 안내(/route-detail). 내 위치를 모르면 null */
  onRoute: (() => void) | null;
  /** 지도 앱(카카오맵·구글맵·애플맵)으로 그 자리를 연다 */
  onMap: () => void;
};

/**
 * 위의 지도와 짝지을 때 카드가 받는 것 — 지도의 번호 점과 맞는 번호, 지금 골라졌는가, 눌렀을 때 «고르는» 함수.
 * 안 주면(null·생략) 카드는 지도와 상관없이 따로 선 카드다.
 */
export type ListLink = { number: number; selected: boolean; onSelect: () => void };

type CardProps<K extends FacilityItem['kind']> = {
  item: Extract<FacilityItem, { kind: K }>;
  actions: FacilityActions;
  link?: ListLink | null;
};

// ── 공통 부품 ──────────────────────────────────────────────────────────────

type BadgeTone = 'success' | 'neutral' | 'info' | 'warning';

const BADGE_COLORS: Record<BadgeTone, { bg: string; fg: string }> = {
  success: { bg: color.state.successBg, fg: color.state.success },
  neutral: { bg: color.surface.tint, fg: color.text.body },
  info: { bg: color.state.infoBg, fg: color.state.info },
  warning: { bg: color.state.warningBg, fg: color.state.warning },
};

function Badge({ text, tone = 'neutral', testID }: { text: string; tone?: BadgeTone; testID?: string }) {
  const palette = BADGE_COLORS[tone];
  return (
    <View testID={testID} style={[styles.badge, { backgroundColor: palette.bg }]}>
      <Text variant="caption" weight="bold" color={palette.fg}>{text}</Text>
    </View>
  );
}

/** 이름 줄 — 외국어 화면에서는 간판과 같은 한국어를 굵게, 읽는 법(로마자)을 아래에 작게. */
function nameParts(name: string, language: string): { title: string; sub: string | null } {
  if (language === 'ko') return { title: name, sub: null };
  const roman = romanizeKorean(name);
  return { title: name, sub: roman && roman !== name ? roman : null };
}

function Frame({ item, title, sub, actions, link, callAction, children }: {
  item: FacilityItem;
  title: string;
  sub: string | null;
  actions: FacilityActions;
  link?: ListLink | null;
  /** 약국의 전화 걸기 */
  callAction?: { label: string; onPress: () => void } | null;
  children: ReactNode;
}) {
  const { tx, language } = useI18n();
  const address = addressForLanguage({ address: item.address }, language);
  const summary = [title, distanceText(item.distanceM)].join(', ');
  // 지도와 짝지었으면 누르면 지도에서 그 점이 골라지고(가까운 도움과 같은 규칙), 아니면 예전처럼 가는 법이 열린다.
  const open = link ? link.onSelect : (actions.onRoute ?? actions.onMap);
  const label = link
    ? txf(tx, '%s 지도에서 보기', 'Show %s on the map', summary)
    : `${summary}, ${actions.onRoute ? tx('길 안내', 'Directions') : tx('지도 앱', 'Map app')}`;
  return (
    <View testID={`facility-card-${item.id}`} style={[styles.card, link?.selected && styles.cardOn]}>
      <Pressable
        testID={`facility-open-${item.id}`}
        accessibilityRole="button"
        accessibilityState={link ? { selected: link.selected } : undefined}
        accessibilityLabel={label}
        onPress={open}
        style={({ pressed }) => [styles.body, pressed && styles.pressed]}
      >
        <View style={styles.titleRow}>
          {link ? (
            // 지도의 번호 점과 같은 번호 — 점과 카드를 눈으로 맞춰 본다.
            <View testID={`facility-pin-${item.id}`} style={styles.pin}><Text variant="caption" weight="bold" color={color.text.onAction}>{link.number}</Text></View>
          ) : null}
          <View style={styles.titleCol}>
            <Text variant="body" weight="bold">{title}</Text>
            {sub ? <Text variant="caption" color={color.text.body}>{sub}</Text> : null}
          </View>
          <View style={styles.distanceCol}>
            <Text variant="body" weight="bold">{distanceText(item.distanceM)}</Text>
            <Text variant="caption" color={color.text.muted}>{txf(tx, '걸어서 %s분', '%s min walk', walkMinutesFor(item.distanceM))}</Text>
          </View>
        </View>
        {address ? <Text variant="caption" color={color.text.muted}>{address}</Text> : null}
        {children}
      </Pressable>
      <View style={styles.actions}>
        {callAction ? (
          <Button testID={`facility-call-${item.id}`} label={tx('전화', 'Call')} variant="secondary" compact accessibilityLabel={callAction.label} onPress={callAction.onPress} />
        ) : null}
        {actions.onRoute ? (
          <Button testID={`facility-route-${item.id}`} label={tx('길 안내', 'Directions')} variant="secondary" compact accessibilityLabel={txf(tx, '%s 길 안내', 'Directions to %s', title)} onPress={actions.onRoute} />
        ) : null}
        <Button testID={`facility-map-${item.id}`} label={tx('지도 앱', 'Map app')} variant="tertiary" compact accessibilityLabel={txf(tx, '%s 지도 앱으로 열기', 'Open %s in a map app', title)} onPress={actions.onMap} />
      </View>
    </View>
  );
}

/** 지금 여는가 — 열었다/닫았다/모른다 한 줄. 모르면 뱃지를 중립으로 둔다. */
function openBadge(status: OpenStatus, tx: Tx, testID: string) {
  if (status.kind === 'open') {
    if (status.allDay) return <Badge testID={testID} tone="success" text={tx('24시간 영업', 'Open 24 hours')} />;
    return status.closesAt
      ? <Badge testID={testID} tone="success" text={txf(tx, '지금 영업 중 · %s까지', 'Open now · until %s', status.closesAt)} />
      : <Badge testID={testID} tone="success" text={tx('지금 영업 중', 'Open now')} />;
  }
  if (status.kind === 'closed') {
    return status.todayHours
      ? <Badge testID={testID} tone="neutral" text={txf(tx, '지금은 닫았어요 · 오늘 %s–%s', 'Closed now · today %s–%s', status.todayHours.open, status.todayHours.close)} />
      : <Badge testID={testID} tone="neutral" text={tx('오늘 쉬어요', 'Closed today')} />;
  }
  return <Badge testID={testID} tone="neutral" text={tx('운영시간 정보 없음', 'Hours not available')} />;
}

// ── 주차장 ─────────────────────────────────────────────────────────────────

export function ParkingCard({ item, actions, link, stayHours }: CardProps<'PARKING'> & { stayHours: number }) {
  const { tx, language } = useI18n();
  const detail = item.detail;
  const { title, sub } = nameParts(item.name, language);
  const fee = feeSummaryOf(detail);
  const stayLabel = txf(tx, '%s시간 기준', `for %s ${enPlural(stayHours, 'hour', 'hours')}`, stayHours);
  return (
    <Frame item={item} title={title} sub={sub} actions={actions} link={link}>
      <View style={styles.feeBlock}>
        {fee.kind === 'free' ? (
          <Text testID={`facility-fee-${item.id}`} variant="title" weight="bold" color={color.state.success}>{tx('무료', 'Free')}</Text>
        ) : null}
        {fee.kind === 'estimate' ? (
          <>
            <Text testID={`facility-fee-${item.id}`} variant="title" weight="bold">{txf(tx, '약 %s원', 'About ₩%s', formatWon(fee.won))}</Text>
            <Text variant="caption" color={color.text.muted}>{stayLabel}</Text>
          </>
        ) : null}
        {fee.kind === 'partial' ? (
          <>
            <Text testID={`facility-fee-${item.id}`} variant="body" weight="bold">{txf(tx, '기본 %s분 %s원', 'First %s min ₩%s', fee.baseMinutes, formatWon(fee.baseWon))}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('추가요금은 현장에서 확인해 주세요', 'Check the extra fee on site')}</Text>
          </>
        ) : null}
        {fee.kind === 'unknown' ? (
          <>
            <Text testID={`facility-fee-${item.id}`} variant="body" weight="bold" color={color.text.muted}>{tx('요금 정보 없음', 'No fee information')}</Text>
            <Text variant="caption" color={color.text.muted}>{tx('현장에서 확인해 주세요', 'Please check on site')}</Text>
          </>
        ) : null}
        {fee.kind !== 'free' && fee.kind !== 'unknown' && detail.fee?.dayMaxWon ? (
          <Text variant="caption" color={color.text.muted}>{txf(tx, '하루 최대 %s원', 'Daily max ₩%s', formatWon(detail.fee.dayMaxWon))}</Text>
        ) : null}
      </View>
      <View style={styles.badges}>
        {detail.operator === 'PUBLIC' ? <Badge text={tx('공영', 'Public')} tone="info" /> : null}
        {detail.operator === 'PRIVATE' ? <Badge text={tx('민영', 'Private')} /> : null}
        {openBadge(openStatusOf(detail), tx, `facility-status-${item.id}`)}
        {detail.spaces ? <Badge text={txf(tx, '%s면', '%s spaces', detail.spaces)} /> : null}
      </View>
      {detail.note ? <Text variant="caption" color={color.text.muted}>{detail.note}</Text> : null}
    </Frame>
  );
}

// ── 약국 ───────────────────────────────────────────────────────────────────

export function PharmacyCard({ item, actions, link, onCall }: CardProps<'PHARMACY'> & { onCall: ((tel: string) => void) | null }) {
  const { tx, language } = useI18n();
  const detail = item.detail;
  const { title, sub } = nameParts(item.name, language);
  const call = detail.tel && onCall ? { label: txf(tx, '%s에 전화하기', 'Call %s', title), onPress: () => onCall(detail.tel as string) } : null;
  return (
    <Frame item={item} title={title} sub={sub} actions={actions} link={link} callAction={call}>
      <View style={styles.badges}>
        {openBadge(openStatusOf(detail), tx, `facility-status-${item.id}`)}
      </View>
      {detail.opensSunday || detail.opensHoliday || detail.lateNight ? (
        <View style={styles.badges}>
          {detail.opensSunday ? <Badge testID={`facility-sunday-${item.id}`} tone="info" text={tx('일요일 영업', 'Open Sundays')} /> : null}
          {detail.opensHoliday ? <Badge testID={`facility-holiday-${item.id}`} tone="info" text={tx('공휴일 영업', 'Open holidays')} /> : null}
          {detail.lateNight ? <Badge testID={`facility-late-${item.id}`} tone="warning" text={tx('심야 영업', 'Open late')} /> : null}
        </View>
      ) : null}
      {detail.directions ? <Text variant="caption" color={color.text.body}>{txf(tx, '찾아가는 길 · %s', 'How to find it · %s', detail.directions)}</Text> : null}
      {detail.note ? <Text variant="caption" color={color.text.muted}>{detail.note}</Text> : null}
    </Frame>
  );
}

// ── 환전소 ─────────────────────────────────────────────────────────────────

export function ExchangeCard({ item, actions, link, onCall }: CardProps<'EXCHANGE'> & { onCall: ((tel: string) => void) | null }) {
  const { tx, language } = useI18n();
  const detail = item.detail;
  const { title, sub } = nameParts(item.name, language);
  const call = detail.tel && onCall ? { label: txf(tx, '%s에 전화하기', 'Call %s', title), onPress: () => onCall(detail.tel as string) } : null;
  return (
    <Frame item={item} title={title} sub={sub} actions={actions} link={link} callAction={call}>
      {detail.tel ? <Text variant="caption" color={color.text.body}>{detail.tel}</Text> : null}
    </Frame>
  );
}

// ── 공중화장실 ─────────────────────────────────────────────────────────────

const TOILET_TYPE_LABEL: Record<string, readonly [string, string]> = {
  OPEN: ['개방화장실', 'Open-access restroom'],
  PUBLIC: ['공중화장실', 'Public restroom'],
  SIMPLE: ['간이화장실', 'Basic restroom'],
  MOBILE: ['이동화장실', 'Mobile restroom'],
};

const DIAPER_PLACE_LABEL: Record<string, readonly [string, string]> = {
  WOMEN: ['여자화장실', "Women's restroom"],
  MEN: ['남자화장실', "Men's restroom"],
  BOTH: ['남녀 화장실 모두', 'Both restrooms'],
};

export function ToiletCard({ item, actions, link }: CardProps<'TOILET'>) {
  const { tx, language } = useI18n();
  const detail = item.detail;
  const { title, sub } = nameParts(item.name, language);
  const typeLabel = detail.type && TOILET_TYPE_LABEL[detail.type] ? tx(...TOILET_TYPE_LABEL[detail.type]) : null;
  // 「12시간」처럼 시각 없이 «하루 몇 시간»만 적힌 개방시간 — 그대로 보이면 영어 화면에 「Open 12시간」이 나온다(운영 자료에 실제로 있다).
  const hoursPerDay = detail.openHours ? /^(\d{1,2})시간$/.exec(detail.openHours.trim())?.[1] ?? null : null;
  const place = detail.diaperPlace && DIAPER_PLACE_LABEL[detail.diaperPlace] ? tx(...DIAPER_PLACE_LABEL[detail.diaperPlace]) : null;
  return (
    <Frame item={item} title={title} sub={sub} actions={actions} link={link}>
      <View style={styles.badges}>
        {detail.accessible ? <Badge testID={`facility-accessible-${item.id}`} tone="info" text={`♿ ${tx('장애인용', 'Accessible')}`} /> : null}
        {detail.diaper ? (
          <Badge testID={`facility-diaper-${item.id}`} tone="info" text={place
            ? `👶 ${txf(tx, '기저귀 교환대 · %s', 'Diaper changing · %s', place)}`
            : `👶 ${tx('기저귀 교환대', 'Diaper changing')}`} />
        ) : null}
        {typeLabel ? <Badge text={typeLabel} /> : null}
      </View>
      {detail.alwaysOpen ? (
        <Text testID={`facility-hours-${item.id}`} variant="caption" color={color.text.body}>{tx('24시간 개방', 'Open 24 hours')}</Text>
      ) : hoursPerDay ? (
        <Text testID={`facility-hours-${item.id}`} variant="caption" color={color.text.body}>{txf(tx, '하루 %s시간 개방', 'Open %s hours a day', hoursPerDay)}</Text>
      ) : detail.openHours ? (
        <Text testID={`facility-hours-${item.id}`} variant="caption" color={color.text.body}>{txf(tx, '개방시간 %s', 'Open %s', detail.openHours)}</Text>
      ) : null}
    </Frame>
  );
}

// ── 지하철 물품보관함 ──────────────────────────────────────────────────────

const SIZE_LABEL: Record<LockerSize, readonly [string, string]> = {
  small: ['소형', 'Small'],
  medium: ['중형', 'Medium'],
  large: ['대형', 'Large'],
  xlarge: ['특대형', 'X-Large'],
};

export function LockerCard({ item, actions, link }: CardProps<'LOCKER'>) {
  const { tx, language } = useI18n();
  const detail = item.detail;
  // 외국어 화면은 영어 역명을 크게, 한국어 역명(안내 방송·표지판)을 아래에 — 영어 이름이 없으면 한국어를 크게.
  const foreign = language !== 'ko';
  const title = foreign && detail.stationEn ? detail.stationEn : item.name;
  const sub = foreign && detail.stationEn ? item.name : null;
  const fee = parseLockerFee(detail.feeText);
  const counts: Record<LockerSize, number> = { small: detail.small, medium: detail.medium, large: detail.large, xlarge: detail.xlarge };
  return (
    <Frame item={item} title={title} sub={sub} actions={actions} link={link}>
      <View style={styles.badges}>
        {detail.line ? <Badge testID={`facility-line-${item.id}`} tone="info" text={txf(tx, '%s호선', 'Line %s', detail.line)} /> : null}
      </View>
      {detail.location ? <Text variant="caption" color={color.text.body}>{detail.location}</Text> : null}
      <View style={styles.sizes}>
        {LOCKER_SIZES.map((size) => (
          <View key={size} testID={`facility-size-${size}-${item.id}`} style={[styles.sizeBox, counts[size] === 0 && styles.sizeBoxEmpty]}>
            <Text variant="caption" color={color.text.muted}>{tx(...SIZE_LABEL[size])}</Text>
            <Text variant="title" weight="bold" color={counts[size] === 0 ? color.text.muted : color.text.heading}>{counts[size]}</Text>
            {fee?.bySize[size] ? <Text variant="caption" color={color.text.body}>{txf(tx, '%s원', '₩%s', formatWon(fee.bySize[size] as number))}</Text> : null}
          </View>
        ))}
      </View>
      {fee ? (
        fee.perHours ? <Text testID={`facility-fee-${item.id}`} variant="caption" color={color.text.muted}>{txf(tx, '요금은 %s시간 기준이에요', `Prices are per %s ${enPlural(fee.perHours, 'hour', 'hours')}`, fee.perHours)}</Text> : null
      ) : detail.feeText ? (
        <Text testID={`facility-fee-${item.id}`} variant="caption" color={color.text.muted}>{txf(tx, '요금 · %s', 'Fee · %s', detail.feeText)}</Text>
      ) : null}
      <Text variant="caption" color={color.text.muted}>{tx('설치된 칸 수예요. 비어 있는지는 현장에서 확인해 주세요.', 'These are the lockers installed, not how many are free. Please check on site.')}</Text>
    </Frame>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[2], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.card },
  cardOn: { borderColor: color.text.heading },
  pin: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.text.heading, alignItems: 'center', justifyContent: 'center' },
  body: { gap: spacing[2] },
  pressed: { opacity: 0.7 },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  titleCol: { flex: 1, minWidth: 0, gap: 2 },
  distanceCol: { alignItems: 'flex-end', gap: 2, flexShrink: 0 },
  feeBlock: { gap: 2 },
  badges: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2] },
  badge: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[1] },
  sizes: { flexDirection: 'row', gap: spacing[2] },
  sizeBox: { flex: 1, minWidth: 0, alignItems: 'center', gap: 2, paddingVertical: spacing[2], paddingHorizontal: spacing[1], borderRadius: radius.md, backgroundColor: color.surface.tint },
  sizeBoxEmpty: { opacity: 0.6 },
});
