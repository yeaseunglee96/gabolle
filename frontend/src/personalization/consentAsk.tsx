// 행동 기록 동의를 첫 하트 때 한 번 묻는다 — S15P21E201-1644.
//
// 🔴 기본값은 그대로(동의 안 함)다. 동의는 사람이 고른다. 다만 켜는 자리가 마이페이지 설정 안쪽뿐이라 거의 아무도 몰랐다
//    (가입자 81명 중 1명). 하트로 장소를 처음 저장한 순간 — 「이게 추천에 쓰이나?」가 궁금해지는 순간 — 에 한 번만 묻는다.
//    「나중에」면 다시 조르지 않는다(이 기기에 한 번 물었음을 기억). 로그인 안 했으면 안 묻는다 — 동의는 계정에 남는다.
//
// 창은 누른 화면이 그린다(prompt). 화면 쌓임(Stack)에서는 홈이 장소 상세 밑에 살아 있어서, 공용 창을 여러 화면에 두면 둘이 뜬다.
import { useCallback, useRef, useState } from 'react';
import { Modal, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { loadBehaviorConsent, setBehaviorConsent } from './behaviorConsent';

const ASKED_KEY = 'gabolle.behavior-consent-asked';

async function wasAsked(): Promise<boolean> {
  try { return (await AsyncStorage.getItem(ASKED_KEY)) === '1'; } catch { return false; }
}
async function markAsked(): Promise<void> {
  try { await AsyncStorage.setItem(ASKED_KEY, '1'); } catch { /* 못 적으면 이번 실행 동안만 안 묻는다 */ }
}

export function useBehaviorConsentAsk(accessToken: string | null) {
  const { tx } = useI18n();
  const [open, setOpen] = useState(false);
  // 하트를 연달아 눌러도 한 번만 — 확인하는 동안 들어온 누름은 버린다.
  const asking = useRef(false);

  const askOnce = useCallback(async () => {
    if (!accessToken || asking.current) return;
    asking.current = true;
    try {
      if ((await loadBehaviorConsent()) || (await wasAsked())) return;
      // 열기 «전에» 적는다 — 창을 닫지 않고 앱을 꺼도 다음에 또 묻지 않는다.
      await markAsked();
      setOpen(true);
    } finally {
      asking.current = false;
    }
  }, [accessToken]);

  const answer = (yes: boolean) => {
    setOpen(false);
    if (yes) void setBehaviorConsent(true, accessToken);
  };

  const prompt = (
    <Modal visible={open} transparent animationType="fade" onRequestClose={() => answer(false)}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{tx('하트·저장한 곳을 다음 추천에 반영할까요?', 'Use your hearts and saves for your next recommendations?')}</Text>
          <Text color={color.text.body}>{tx('언제든 마이페이지 설정의 「맞춤 추천」에서 끌 수 있어요.', 'You can turn this off anytime under “Personalized picks” in My page settings.')}</Text>
          <View style={styles.actions}>
            <Button label={tx('나중에', 'Later')} variant="tertiary" onPress={() => answer(false)} containerStyle={styles.action} />
            <Button label={tx('반영하기', 'Use them')} onPress={() => answer(true)} containerStyle={styles.action} />
          </View>
        </View>
      </View>
    </Modal>
  );

  return { askOnce, prompt };
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  actions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  action: { flex: 1 },
});
