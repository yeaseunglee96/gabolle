import { useState } from 'react';
import { Modal, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Button } from './Button';
import { Text } from './Text';

const STEPS = [
  { title: ['부산에서 가고 싶은 곳부터', 'Start with a place in Busan'], body: ['홈의 로컬 탐색에서 장소 이름이나 관심 있는 갈래로 찾아보세요. 위치 권한 없이도 둘러볼 수 있어요.', 'Use Explore locally on Home to search by name or category. You can browse without location access.'], action: ['장소 찾아보기', 'Explore places'], route: '/explore' },
  { title: ['내 취향에 맞는 여행 만들기', 'Build a trip around you'], body: ['홈의 여행 계획 시작하기에서 날짜와 여행 조건을 정해요. 추천 결과를 살펴보고 원하는 일정을 선택하세요.', 'Start planning on Home lets you choose dates and travel conditions. Review the recommendations and pick your itinerary.'], action: ['여행 계획 시작하기', 'Start planning'], route: '/plan' },
  { title: ['여행 중에도 다시 찾아오기', 'Keep your trip close'], body: ['내 여행에서 일정을 다시 열 수 있어요. 식당이나 택시에서 말이 막히면 홈의 통역에서 필요한 한국어 문장을 찾아보세요.', 'Open your itinerary again in My trips. For restaurants and taxis, use Phrases on Home to find useful Korean expressions.'], action: ['현장 말하기 열기', 'Open useful phrases'], route: '/field/speak' },
] as const;

/** 필요한 사람이 열고, 기능을 실행하거나 닫은 뒤 언제든 다시 볼 수 있는 사용 안내. */
export function GettingStartedGuide() {
  const { tx } = useI18n();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(0);
  const current = STEPS[step];
  return <>
    <Button variant="ghost" label={tx('처음 오셨나요? 사용법 보기', 'New here? See how it works')} onPress={() => { setStep(0); setOpen(true); }} />
    <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
      <SafeAreaView style={styles.overlay}>
        <View style={styles.sheet} accessibilityViewIsModal>
          <View style={styles.header}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('가볼래 사용 안내', 'Your GABOLLE guide')} · {step + 1}/{STEPS.length}</Text><View style={styles.close}><Button variant="ghost" pill label={tx('닫기', 'Close')} onPress={() => setOpen(false)} /></View></View>
          <ScrollView contentContainerStyle={styles.body}>
            <Text variant="display" weight="bold" accessibilityRole="header">{tx(current.title[0], current.title[1])}</Text>
            <Text variant="body" style={styles.description}>{tx(current.body[0], current.body[1])}</Text>
            <View style={styles.note}><Text variant="caption">{tx('안내를 닫아도 홈의 사용법 보기에서 다시 열 수 있어요.', 'You can reopen this guide from Home at any time.')}</Text></View>
            {/* 🔴 사용자 리포트 — 이 버튼만 각진 모양이라 위 닫기·아래 이전/다음과 달라 보였다.
                한 시트 안의 버튼은 모두 같은 모양(pill)으로 맞춘다. */}
            <Button label={tx(current.action[0], current.action[1])} pill onPress={() => { setOpen(false); router.push(current.route); }} />
          </ScrollView>
          <View style={styles.footer}>
            <View style={styles.nav}><Button variant="ghost" pill label={tx('이전 설명', 'Previous')} disabled={step === 0} onPress={() => setStep(step - 1)} /></View>
            <View style={styles.nav}><Button variant="ghost" pill label={step === STEPS.length - 1 ? tx('안내 마치기', 'Finish guide') : tx('다음 설명', 'Next')} onPress={() => step === STEPS.length - 1 ? setOpen(false) : setStep(step + 1)} /></View>
          </View>
        </View>
      </SafeAreaView>
    </Modal>
  </>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(11,29,58,0.62)' },
  sheet: { width: '100%', maxWidth: 720, maxHeight: '100%', alignSelf: 'center', flexShrink: 1, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, backgroundColor: color.canvas },
  header: { paddingHorizontal: spacing[6], paddingTop: spacing[3], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  close: { minWidth: 64 },
  body: { padding: spacing[6], gap: spacing[4] },
  description: { lineHeight: 26 },
  note: { padding: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.soft },
  footer: { flexShrink: 0, flexDirection: 'row', gap: spacing[3], paddingHorizontal: spacing[6], paddingBottom: spacing[4] },
  nav: { flex: 1 },
});
