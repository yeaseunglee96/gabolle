// 위치 동의 문 — 위치를 읽기 전에 모든 화면이 여기를 지난다(S15P21E201-1691).
//
// 두 가지 길이 있다:
//   · ensure  — 위치를 «처음» 쓰려는 순간(여행 「출발」). 물은 적 없을 때만 묻는다. 거절했으면 조르지 않는다.
//   · request — 사람이 「내 위치로」를 직접 눌렀을 때. 동의가 없으면(거절했어도) 다시 묻는다 — 누른 것이 곧 쓰고 싶다는 뜻이다.
// 화면을 열 때 조용히 읽는 자리는 둘 다 부르지 않고 `consent === true` 일 때만 읽는다.
//
// 창은 부른 화면이 그린다(sheet) — 화면 쌓임(Stack)에서 뒤 화면도 살아 있어서, 공용 창 하나를 두면 둘이 뜬다
// (behavior 동의 묻기 consentAsk.tsx 와 같은 까닭).
import { useCallback, useRef, useState } from 'react';
import { Modal, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { loadLocationConsent, syncLocationConsent, useLocationConsent } from './locationConsent';

export function useLocationGate(accessToken: string | null) {
  const { tx } = useI18n();
  const { consent, ready, set } = useLocationConsent(accessToken);
  const [open, setOpen] = useState(false);
  const pending = useRef<((granted: boolean) => void) | null>(null);

  const current = useCallback(() => (ready ? loadLocationConsent() : syncLocationConsent(accessToken)), [accessToken, ready]);

  const prompt = useCallback(() => new Promise<boolean>((resolve) => {
    pending.current?.(false);
    pending.current = resolve;
    setOpen(true);
  }), []);

  const ensure = useCallback(async () => {
    const now = await current();
    return now === null ? prompt() : now;
  }, [current, prompt]);

  const request = useCallback(async () => {
    const now = await current();
    return now === true ? true : prompt();
  }, [current, prompt]);

  /** null — 답하지 않고 닫음(뒤로 가기). 적지 않는다: 다음에 또 물어야 한다. */
  const answer = (granted: boolean | null) => {
    setOpen(false);
    if (granted !== null) set(granted);
    pending.current?.(granted === true);
    pending.current = null;
  };

  const sheet = (
    <Modal visible={open} transparent animationType="fade" onRequestClose={() => answer(null)}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{tx('위치를 써도 될까요?', 'May we use your location?')}</Text>
          <Text color={color.text.body}>{tx('내 주변 · 지금 갈 곳 · 지도 — 가까운 장소와 버스를 찾을 때 위치를 보내요. 둘러보기와 버스는 약 100m 단위로 줄여서 보내요.', 'Nearby, Go now, and the map — we send your location to find places and buses near you. For Explore and buses we round it to about 100 m.')}</Text>
          <Text color={color.text.body}>{tx('방문 인증 — 후기를 쓰기 전에 그 장소에 있는지 확인해요. 남기는 것은 거리 결과뿐이에요.', 'Visit check — before you review a place, we check that you are there. We keep only the distance result.')}</Text>
          <Text color={color.text.body}>{tx('여행 중 도착·출발 — 「출발」을 누른 동안 도착·출발을 알아채 시각만 남겨요. 좌표는 이 기기 밖으로 보내지 않아요.', 'Arrivals and departures on your trip — while you have pressed Start, we notice when you arrive and leave and keep only the times. Coordinates never leave this device.')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('앱을 닫으면 위치를 보지 않아요. 마이페이지 설정의 「위치 사용」에서 언제든 끌 수 있어요.', 'We stop looking at your location when the app is closed. You can turn this off anytime under “Use location” in My page settings.')}</Text>
          <View style={styles.actions}>
            <Button label={tx('위치 없이 쓰기', 'Continue without')} variant="tertiary" onPress={() => answer(false)} containerStyle={styles.action} />
            <Button label={tx('동의하기', 'Allow')} onPress={() => answer(true)} containerStyle={styles.action} />
          </View>
        </View>
      </View>
    </Modal>
  );

  return { consent, ready, ensure, request, sheet };
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  actions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  action: { flex: 1 },
});
