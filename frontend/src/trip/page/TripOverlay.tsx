// 여행 페이지 위에 뜨는 창 — 동행 초대 · 날씨 (S15P21E201-1561).
//
// 시안(frontend/docs/design_handoff_trip_page/README):
//   · 넓은 화면 동행 초대 — 가운데 520 창, radius 20, padding 24, 뒤는 rgba(25,25,25,.4)
//   · 넓은 화면 날씨     — 오른쪽 420 서랍, 바탕 #F5F5F7(canvas)
//   · 폰 둘 다           — 아래 시트
// 🔴 전에는 알약이 옛 화면(/share, /prepare)으로 «이동» 했다. 그러면 뒤로 가기 한 번에 코스 고른 상태가 날아간다.
import type { ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

/** `share` — 읽기 전용 링크만 담은 창(S15P21E201-1593). */
export type TripOverlayKind = 'invite' | 'share' | 'weather';
type Shape = 'center' | 'drawer' | 'sheet';

export function TripOverlay({ visible, shape, onClose, children }: { visible: boolean; shape: Shape; onClose: () => void; children: ReactNode }) {
  const { tx } = useI18n();
  return (
    <Modal visible={visible} transparent animationType={shape === 'sheet' ? 'slide' : 'fade'} onRequestClose={onClose}>
      <View style={[styles.backdrop, shape === 'center' && styles.backdropCenter, shape === 'drawer' && styles.backdropDrawer, shape === 'sheet' && styles.backdropSheet]}>
        {/* 뒤의 어두운 곳을 누르면 닫힌다. 창 자체보다 먼저 깔아야 창 안의 누름을 안 가로챈다. */}
        <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={StyleSheet.absoluteFill} />
        <View accessibilityViewIsModal style={[styles.panel, styles[shape]]}>
          {shape === 'sheet' ? <View style={styles.grabber} /> : null}
          <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={({ pressed }) => [styles.close, pressed && styles.pressed]}>
            <Text weight="bold">✕</Text>
          </Pressable>
          <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>{children}</ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(25,25,25,0.4)' },
  backdropCenter: { alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  backdropDrawer: { alignItems: 'flex-end' },
  backdropSheet: { justifyContent: 'flex-end' },
  panel: { overflow: 'hidden' },
  center: { width: '100%', maxWidth: 520, maxHeight: '90%', borderRadius: 20, backgroundColor: color.surface.card },
  drawer: { width: 420, maxWidth: '100%', height: '100%', backgroundColor: color.canvas, shadowColor: color.brand.navy, shadowOpacity: 0.12, shadowRadius: 24, shadowOffset: { width: -8, height: 0 } },
  sheet: { maxHeight: '88%', borderTopLeftRadius: 24, borderTopRightRadius: 24, backgroundColor: color.surface.card },
  grabber: { alignSelf: 'center', width: 36, height: 5, marginTop: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.field },
  close: { position: 'absolute', top: spacing[4], right: spacing[4], zIndex: 1, width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
  body: { padding: spacing[6], paddingTop: spacing[6] },
});
