import { useRouter, type Href } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar, type TabKey } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

type Props = { active: TabKey; eyebrow: string; title: string; description: string; actionLabel: string; actionPath: Href };

export function EmptyTabScreen({ active, eyebrow, title, description, actionLabel, actionPath }: Props) {
  const router = useRouter();
  const { tx } = useI18n();
  return <View style={styles.shell}><Screen style={styles.screen}>
    <View style={styles.heading}><Text variant="caption" weight="bold" color={color.brand.orange}>{eyebrow}</Text><Text variant="display" weight="bold">{title}</Text></View>
    <View accessibilityLiveRegion="polite" style={styles.empty}><View style={styles.mark}><Text variant="display" color={color.brand.orange}>⌁</Text></View><Text variant="title" weight="bold">{tx('아직 표시할 내용이 없어요', 'Nothing to show yet')}</Text><Text color={color.text.body} style={styles.description}>{description}</Text><Button label={actionLabel} containerStyle={styles.action} onPress={() => router.push(actionPath)} /></View>
  </Screen><TabBar active={active} /></View>;
}

const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory }, screen: { flex: 1, backgroundColor: color.brand.ivory }, heading: { gap: spacing[2], marginBottom: spacing[6] }, empty: { flex: 1, minHeight: 360, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card }, mark: { width: 72, height: 72, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.tint }, description: { maxWidth: 420, textAlign: 'center' }, action: { minWidth: 220, marginTop: spacing[3] } });
