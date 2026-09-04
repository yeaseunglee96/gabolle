import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export default function Chat() {
  const router = useRouter();

  return (
    <Screen>
      <View style={styles.header}>
        <View style={styles.identity}>
          <GabolleMascot state="open" style={styles.avatarImage} />
          <View><Text variant="title" weight="bold">가볼래</Text><Text variant="caption" color={color.text.body}>부산 여행 AI 도우미</Text></View>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="가볼래 화면 닫기" onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
          <Text variant="title" color={color.text.body}>×</Text>
        </Pressable>
      </View>

      <View style={styles.content}>
        <View style={styles.icon}><GabolleMascot state="open" style={styles.heroMascot} /></View>
        <View style={styles.copy}>
          <Text variant="caption" weight="bold" color={color.brand.orange}>기능 준비 중</Text>
          <Text variant="display" weight="bold">가볼래가 여행을{`\n`}더 잘 배우고 있어요</Text>
          <Text variant="body" color={color.text.body}>아직 대화 API가 연결되지 않아 질문을 받거나 일정을 바꾸지는 않아요. 준비되기 전까지 실제 기능처럼 보이는 답변도 만들지 않을게요.</Text>
        </View>
        <View style={styles.notice} accessibilityRole="summary">
          <Text variant="caption" weight="bold" color={color.text.heading}>지금 바로 쓸 수 있는 기능</Text>
          <Text variant="caption" color={color.text.body}>현장에서 문장을 크게 보여주고 음성으로 들려주거나, 메뉴를 번역해 보세요.</Text>
        </View>
      </View>

      <View style={styles.actions}>
        <Button label="현장 말하기" variant="field" onPress={() => router.push('/field/speak')} />
        <Button label="메뉴·안내 번역" variant="ghost" onPress={() => router.push('/field/translate')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  identity: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  avatarImage: { width: 44, height: 44 },
  closeButton: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft },
  pressed: { opacity: 0.7, transform: [{ scale: 0.96 }] },
  content: { flex: 1, justifyContent: 'center', gap: spacing[6] },
  icon: { width: 112, height: 112, alignItems: 'center', justifyContent: 'center' },
  heroMascot: { width: 112, height: 112 },
  copy: { gap: spacing[3] },
  notice: { gap: spacing[2], borderRadius: radius.md, padding: spacing[4], backgroundColor: color.state.warningBg },
  actions: { gap: spacing[3] },
});
