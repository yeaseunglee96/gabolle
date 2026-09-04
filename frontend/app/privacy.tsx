import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const ITEMS = [
  ['위치', '여행 실행 중 앱이 화면에 보일 때만 사용하며, 거부하면 출발지를 직접 입력할 수 있어요.'],
  ['카메라', '메뉴·안내문 번역을 위해 촬영할 때만 사용하며, 촬영 이미지는 별도 동의 없이 저장하지 않아요.'],
  ['알림', '일정 생성과 여행 관련 안내를 받을 때 사용하며 언제든 기기 설정에서 끌 수 있어요.'],
] as const;

export default function Privacy() {
  const router = useRouter();
  return <Screen scroll style={styles.screen}><View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.back()} style={styles.back}><Text variant="title">‹</Text></Pressable><Text variant="title" weight="bold">개인정보 처리 안내</Text><View style={styles.spacer} /></View><Text color={color.text.body} style={styles.lead}>권한별 목적을 분리해 안내하며, 선택하지 않아도 수동 입력으로 주요 기능을 이용할 수 있습니다.</Text><View style={styles.list}>{ITEMS.map(([title, body]) => <View key={title} style={styles.item}><Text weight="bold" color={color.brand.orange}>{title}</Text><Text color={color.text.body}>{body}</Text></View>)}</View><View style={styles.notice}><Text variant="caption" color={color.text.body}>이 화면은 앱 내 권한 안내입니다. 정식 개인정보 처리방침 전문 URL이 확정되면 같은 진입점에 연결합니다.</Text></View></Screen>;
}

const styles = StyleSheet.create({ screen: { backgroundColor: color.brand.ivory }, header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, spacer: { width: 44 }, lead: { marginTop: spacing[6], lineHeight: 24 }, list: { gap: spacing[3], marginTop: spacing[6] }, item: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: '#eee5da', backgroundColor: color.surface.card }, notice: { marginTop: spacing[6], padding: spacing[4], borderRadius: radius.md, backgroundColor: '#fff1e8' } });
