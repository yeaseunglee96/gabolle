// 「개인정보 처리방침이 바뀌었어요」 — 한 번 알린다(S15P21E201-1694, 사용자 결정 — 조율 세션 전달).
//
// 🔴 재동의를 강제하지 않는다. 알리고, 「확인」을 누르면 새 판의 동의로 남긴다(PATCH {PRIVACY_POLICY:true}).
//    「나중에」로 닫아도 앱은 그대로 쓴다. 같은 판은 이 기기에서 다시 묻지 않는다.
// 🔴 서버가 지금 판(currentPolicyVersion)을 알려 줄 때만 띄운다. 그 칸이 없으면(지금 운영) 아무것도 안 한다 —
//    판을 모르면서 「바뀌었어요」라고 하면 거짓말이다.
// 로그인 코드(authApi)는 부르기만 하고 고치지 않는다 — 동의 한 줄을 남기는 요청은 여기서 직접 보낸다.
import { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { getMyConsents } from '@/auth/authApi';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const SEEN_KEY = 'gabolle.privacy-policy-notice-seen';

type ConsentsWithVersion = {
  currentPolicyVersion?: string | null;
  consents?: { consentType: string; status: string; policyVersion?: string | null }[] | null;
};

/**
 * 알려야 하는 새 판. 없으면 null.
 * 판은 「2026-01」처럼 연-월 모양이라 글자 비교가 곧 날짜 비교다.
 */
export function policyVersionToNotify(dto: ConsentsWithVersion | null | undefined, seenVersion: string | null): string | null {
  const current = dto?.currentPolicyVersion;
  if (!current) return null;
  const mine = dto?.consents?.find((consent) => consent.consentType === 'PRIVACY_POLICY' && consent.status === 'GRANTED');
  if (!mine?.policyVersion) return null;
  if (!(mine.policyVersion < current)) return null;
  if (seenVersion === current) return null;
  return current;
}

async function readSeen(): Promise<string | null> {
  try { return await AsyncStorage.getItem(SEEN_KEY); } catch { return null; }
}
async function writeSeen(version: string): Promise<void> {
  try { await AsyncStorage.setItem(SEEN_KEY, version); } catch { /* 못 적으면 다음에 한 번 더 알릴 뿐이다 */ }
}

export function PolicyUpdateNotice() {
  const { accessToken, user } = useAuth();
  const router = useRouter();
  const { tx } = useI18n();
  const [version, setVersion] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken || !user) { setVersion(null); return undefined; }
    let alive = true;
    void (async () => {
      try {
        const [dto, seen] = await Promise.all([getMyConsents(accessToken), readSeen()]);
        if (alive) setVersion(policyVersionToNotify(dto as ConsentsWithVersion, seen));
      } catch {
        // 못 물어보면 안 띄운다 — 알림 하나 때문에 다른 화면을 막지 않는다.
      }
    })();
    return () => { alive = false; };
  }, [accessToken, user]);

  const close = (accept: boolean) => {
    const current = version;
    setVersion(null);
    if (!current) return;
    void writeSeen(current);
    if (accept && accessToken) {
      void apiRequest('/api/v1/auth/me/consents', { method: 'PATCH', accessToken, body: { consents: { PRIVACY_POLICY: true } } }).catch(() => undefined);
    }
  };

  return (
    <Modal visible={version !== null} transparent animationType="fade" onRequestClose={() => close(false)}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <Text variant="title" weight="bold">{tx('개인정보 처리방침이 바뀌었어요', 'Our Privacy Policy has changed')}</Text>
          <Text color={color.text.body}>{tx('무엇이 바뀌었는지 확인해 주세요. 확인을 누르면 바뀐 방침을 읽었다고 남겨요.', 'Please take a look at what changed. Tapping OK records that you have read the updated policy.')}</Text>
          <Pressable accessibilityRole="link" onPress={() => { close(false); router.push('/legal/privacy'); }} hitSlop={8} style={({ pressed }) => [styles.linkWrap, pressed && styles.pressed]}>
            <Text weight="bold" color={color.action.primary} style={styles.link}>{tx('처리방침 보기', 'View the policy')}</Text>
          </Pressable>
          <View style={styles.actions}>
            <Button label={tx('나중에', 'Later')} variant="tertiary" onPress={() => close(false)} containerStyle={styles.action} />
            <Button label={tx('확인', 'OK')} onPress={() => close(true)} containerStyle={styles.action} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  // 글자만큼만 — 웹은 창이 열리면 여기에 초점을 두는데, 폭 전체로 늘어나면 초점 테두리가 창 폭만큼 그려졌다.
  linkWrap: { alignSelf: 'flex-start' },
  link: { textDecorationLine: 'underline' },
  actions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  action: { flex: 1 },
  pressed: { opacity: 0.82 },
});
