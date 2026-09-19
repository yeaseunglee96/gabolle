import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { LegalSection } from './legalContent';

export function LegalDocumentScreen({ title, lead, sections }: { title: [string, string]; lead: [string, string]; sections: LegalSection[] }) {
  const router = useRouter();
  const { tx } = useI18n();
  return <Screen scroll style={styles.screen}>
    <View style={styles.top}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.back}><Text variant="title">‹</Text></Pressable></View>
    <View accessibilityRole="header" style={styles.heading}><Eyebrow>{tx('가볼래 · 약관', 'GABOLLE · Legal')}</Eyebrow><Text variant="display" weight="bold">{tx(...title)}</Text><Text color={color.text.body} style={styles.lead}>{tx(...lead)}</Text></View>
    <View style={styles.list}>{sections.map((section) => <View key={section.title[0]} style={styles.section}><Text variant="title" weight="bold">{tx(...section.title)}</Text>{section.paragraphs.map((paragraph, index) => <Text key={index} color={color.text.body} style={styles.body}>{tx(...paragraph)}</Text>)}</View>)}</View>
    <View style={styles.draft}><Text variant="caption" weight="bold" color={color.state.warning}>{tx('초안 · 팀 확정 예정', 'Draft · Pending team confirmation')}</Text><Text variant="caption" color={color.text.body}>{tx('법률 검토 전 문서이며, 미확정 운영 정보는 확정 즉시 갱신합니다.', 'This document has not yet received legal review. Pending operational details will be updated once confirmed.')}</Text></View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas }, top: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, heading: { gap: spacing[2], marginTop: spacing[6], marginBottom: spacing[6] }, lead: { lineHeight: 24 }, list: { gap: spacing[3] }, section: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card }, body: { lineHeight: 24 }, draft: { gap: spacing[2], marginTop: spacing[6], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.warningBg },
});
