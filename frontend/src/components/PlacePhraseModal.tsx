// 장소 카드에서 여는 한국어 말하기 모달 — 4개 탭(관광지·식당카페·택시·숙소),.
// 탭+목록 자체는 PlacePhraseBrowser 로 옮겨 app/field/speak.tsx(현장 도구에서 여는
// 전체 화면)와 공유한다 — 두 화면이 서로 다른 문장을 보여주던 것을 하나로 합쳤다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { PlacePhraseBrowser } from './PlacePhraseBrowser';
import { Text } from './Text';

type PlacePhraseModalProps = {
  visible: boolean;
  onClose: () => void;
  category?: string | null;
};

export function PlacePhraseModal({ visible, onClose, category }: PlacePhraseModalProps) {
  const router = useRouter();
  const { tx } = useI18n();

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('한국어로 말하기', 'Speak Korean')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.pressed]}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <PlacePhraseBrowser category={category} onOpenTaxiCard={() => { onClose(); router.push('/field/speak?tab=taxi'); }} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 480, maxHeight: '85%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  close: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
});
