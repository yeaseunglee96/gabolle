import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

const SECTIONS = [
  ['서비스 이용', '여행 조건에 따른 추천과 일정 관리 기능을 제공합니다. 추천 결과와 혼잡도는 실제 상황과 다를 수 있으므로 이동 전 현장 정보를 확인해 주세요.'],
  ['계정과 데이터', '일정을 저장·수정하려면 계정이 필요합니다. 계정 없이도 공개 화면은 둘러볼 수 있어요.'],
  ['공유와 게시물', '타인의 권리를 침해하거나 불법·유해한 내용을 공유하면 제한될 수 있어요. 공유 시 정확한 출발지와 연락처 등 민감한 정보는 제외합니다.'],
  ['계정 해지', '마이페이지에서 계정과 저장 데이터 삭제를 요청할 수 있도록 준비하고 있어요.'],
] as const;

export default function Terms() {
  const router = useRouter();
  return (
    <Screen scroll style={styles.screen}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.back()} style={styles.back}>
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="title" weight="bold">이용약관 안내</Text>
        <View style={styles.spacer} />
      </View>
      <Text color={color.text.body} style={styles.lead}>
        가볼래 서비스를 안전하고 편리하게 이용하기 위한 기본 원칙입니다.
      </Text>
      <View style={styles.list}>
        {SECTIONS.map(([title, body]) => (
          <View key={title} style={styles.item}>
            <Text weight="bold" color={color.brand.orange}>{title}</Text>
            <Text color={color.text.body}>{body}</Text>
          </View>
        ))}
      </View>
      <View style={styles.notice}>
        <Text variant="caption" color={color.text.body}>정식 약관의 시행일·사업자 정보·문의처는 팀 확정 후 공개 URL에 반영합니다.</Text>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  header: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  spacer: { width: 44 },
  lead: { marginTop: spacing[6], lineHeight: 24 },
  list: { gap: spacing[3], marginTop: spacing[6] },
  item: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: '#eee5da', backgroundColor: color.surface.card },
  notice: { marginTop: spacing[6], padding: spacing[4], borderRadius: radius.md, backgroundColor: '#fff1e8' },
});
