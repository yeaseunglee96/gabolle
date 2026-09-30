// 장소 상세의 추가 정보 카드 둘 — 정보 줄 카드 + 「대표 메뉴」 카드(S15P21E201-1888).
// 모양은 [id].tsx 의 infoRows·infoRow 와 같게 맞춘다. 값 해석은 detailExtras.ts 가 한다.
// 그릴 것이 하나도 없으면 아무것도 안 그린다(null) — 백엔드 MR !1930 전의 서버에서도 화면이 그대로다.
import { useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { PlaceFeature } from '@/discovery/places';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

import { extraRows, homepageUrl, menuLines, MENU_PREVIEW_COUNT } from './detailExtras';

type Props = { category?: string; features?: readonly PlaceFeature[] };

export function PlaceDetailExtras({ category, features }: Props) {
  const { tx, language } = useI18n();
  const [menuOpen, setMenuOpen] = useState(false);
  const rows = extraRows(category, features, tx);
  const homepage = homepageUrl(features);
  const menu = menuLines(features, language, tx);
  const shownMenu = menuOpen ? menu : menu.slice(0, MENU_PREVIEW_COUNT);
  const hidden = menu.length - shownMenu.length;

  return (
    <>
      {rows.length || homepage ? (
        <View testID="place-detail-extras" style={styles.card}>
          {rows.map((row) => (
            <View key={row.key} testID={`place-extra-${row.key}`} style={styles.row}>
              <Text variant="caption" weight="bold" color={color.text.muted} style={styles.label}>{row.label}</Text>
              <View style={styles.valueBox}>
                <Text variant="body" style={styles.valueText}>{row.value}</Text>
                {row.caption ? <Text variant="caption" color={color.text.muted} style={styles.valueText}>{row.caption}</Text> : null}
              </View>
            </View>
          ))}
          {homepage ? (
            <Pressable testID="place-extra-homepage" accessibilityRole="link" onPress={() => { void Linking.openURL(homepage); }} style={styles.row}>
              <Text variant="caption" weight="bold" color={color.text.muted} style={styles.label}>{tx('홈페이지', 'Website')}</Text>
              <Text variant="body" color={color.text.accent} numberOfLines={1} style={styles.valueText}>{homepage.replace(/^https?:\/\//i, '')}</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}
      {menu.length ? (
        <View testID="place-menu-items" style={styles.card}>
          <Text variant="body" weight="bold" style={styles.heading}>{tx('대표 메뉴', 'Signature menu')}</Text>
          {shownMenu.map((item, index) => (
            <View key={`${item.name}-${index}`} style={styles.menuRow}>
              <View style={styles.menuText}>
                <Text variant="body" weight="bold">{item.name}</Text>
                {item.subName ? <Text variant="caption" color={color.text.muted}>{item.subName}</Text> : null}
                {item.ingredients ? <Text variant="caption" color={color.text.muted}>{item.ingredients}</Text> : null}
              </View>
              {item.price ? <Text variant="body" weight="bold" style={styles.price}>{item.price}</Text> : null}
            </View>
          ))}
          {hidden > 0 ? (
            <Pressable testID="place-menu-more" accessibilityRole="button" onPress={() => setMenuOpen(true)} style={styles.more}>
              <Text variant="caption" weight="bold" color={color.text.accent}>{txf(tx, '더 보기 (%s)', 'Show more (%s)', String(hidden))}</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  card: { marginTop: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  row: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  label: { flexShrink: 0 },
  valueBox: { flexShrink: 1, alignItems: 'flex-end' },
  valueText: { flexShrink: 1, textAlign: 'right' },
  heading: { paddingHorizontal: spacing[4], paddingTop: spacing[4], paddingBottom: spacing[2] },
  menuRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  menuText: { flexShrink: 1, gap: 2 },
  price: { flexShrink: 0 },
  more: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
});
