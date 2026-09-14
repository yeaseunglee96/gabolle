import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { OPEN_SOURCE_NOTICES } from '@/legal/openSourceNotices.generated';

export default function OpenSourceNotices() {
  const router = useRouter();
  const { tx } = useI18n();
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return keyword ? OPEN_SOURCE_NOTICES.filter((item) => item.name.toLowerCase().includes(keyword) || item.license.toLowerCase().includes(keyword)) : OPEN_SOURCE_NOTICES;
  }, [query]);

  return <Screen style={styles.screen}>
    <View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.back()} style={styles.back}><Text variant="title">‹</Text></Pressable><View style={styles.titleBlock}><Text variant="title" weight="bold">{tx('오픈소스 고지', 'Open-source notices')}</Text><Text variant="caption" color={color.text.muted}>{tx(`${OPEN_SOURCE_NOTICES.length}개 패키지`, `${OPEN_SOURCE_NOTICES.length} packages`)}</Text></View><View style={styles.badgeSpacer} /></View>
    <Text color={color.text.body} style={styles.lead}>{tx('가볼래를 만드는 데 사용한 오픈소스 소프트웨어와 라이선스입니다. 이 목록은 package-lock.json에서 자동으로 생성됩니다.', 'Open-source software and licenses used to build Gabolle. This list is generated automatically from package-lock.json.')}</Text>
    <TextInput accessibilityLabel={tx('패키지 또는 라이선스 검색', 'Search packages or licenses')} value={query} onChangeText={setQuery} placeholder={tx('패키지 또는 라이선스 검색', 'Search packages or licenses')} placeholderTextColor={color.text.muted} style={styles.search} />
    <FlatList data={filtered} keyExtractor={(item, index) => `${item.name}-${item.version}-${index}`} initialNumToRender={24} windowSize={7} contentContainerStyle={styles.list} renderItem={({ item }) => <View style={styles.item}><View style={styles.package}><Text weight="bold">{item.name}</Text><Text variant="caption" color={color.text.muted}>v{item.version}</Text></View><Text variant="caption" weight="bold" color={color.text.accent}>{item.license}</Text></View>} ListEmptyComponent={<Text color={color.text.body}>{tx('검색 결과가 없어요.', 'No packages found.')}</Text>} />
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { gap: spacing[4], backgroundColor: color.brand.ivory }, header: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, titleBlock: { alignItems: 'center', gap: spacing[1] }, badgeSpacer: { width: 140 }, lead: { lineHeight: 24 }, search: { minHeight: 48, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, color: color.text.heading, backgroundColor: color.surface.card }, list: { gap: spacing[2], paddingBottom: spacing[8] }, item: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card }, package: { flex: 1, gap: spacing[1] },
});
