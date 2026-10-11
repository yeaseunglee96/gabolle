// 앱 안 업데이트 안내·새로워진 점(S15P21E201-2048). 앱만 — 웹은 늘 최신이라 아무것도 안 한다.
//
// - 켜질 때·앞으로 돌아올 때 하루 한 번 GET /api/v1/app/version 을 묻는다(로그인 전에도 열려 있다).
//   최신보다 낮으면 「새 버전이 나왔어요」 시트, 최소 지원 미만이면 업데이트 단추만 있는 화면으로 막는다.
// - 🔴 네트워크가 실패하거나 값이 이상하면 아무것도 안 한다. 막는 판정이 났을 때는 «확인 시각을 적지 않는다» —
//   적으면 하루 동안 다시 안 물어서 껐다 켜는 것으로 막힘을 빠져나간다.
// - 새 판을 처음 켜면 「새로워진 점」을 한 번 보인다. 처음 설치한 사람에게는 안 보인다.
import Constants from 'expo-constants';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Linking, Modal, Platform, StyleSheet, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { API_BASE_URL } from '@/api/client';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { useSheetBottomPadding } from '@/components/sheetBottomInset';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import {
  COPY,
  WHATS_NEW,
  decideUpdatePrompt,
  shouldCheckNow,
  shouldShowWhatsNew,
  type AppRelease,
  type Localized,
  type UpdatePrompt,
} from './appUpdateLogic';

const LAST_CHECK_KEY = 'gabolle.app-update.last-check';
const DISMISSED_KEY = 'gabolle.app-update.dismissed-version';
const LAST_SEEN_KEY = 'gabolle.app-update.last-seen-version';
/** OnboardingPreferences 가 쓰는 자리. hasEnteredApp 이 참이면 이 기기에서 앱을 써 본 사람이다. */
const ONBOARDING_KEY = 'gabolle:onboarding-preferences';

async function read(key: string): Promise<string | null> {
  try { return await AsyncStorage.getItem(key); } catch { return null; }
}
async function write(key: string, value: string): Promise<void> {
  try { await AsyncStorage.setItem(key, value); } catch { /* 못 적으면 다음에 한 번 더 보일 뿐이다 */ }
}

export function currentAppVersion(): string | null {
  return Constants.expoConfig?.version ?? null;
}

async function fetchRelease(platform: 'ios' | 'android'): Promise<AppRelease | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/app/version?platform=${platform}`, { headers: { Accept: 'application/json' } });
    if (!response.ok) return null;
    const body = (await response.json()) as { data?: AppRelease | null };
    return body?.data ?? null;
  } catch {
    return null;
  }
}

async function isExistingUser(): Promise<boolean> {
  const raw = await read(ONBOARDING_KEY);
  if (!raw) return false;
  try { return (JSON.parse(raw) as { hasEnteredApp?: unknown }).hasEnteredApp === true; } catch { return false; }
}

export function AppUpdateGate() {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') return null;
  return <NativeAppUpdateGate platform={Platform.OS} />;
}

function NativeAppUpdateGate({ platform }: { platform: 'ios' | 'android' }) {
  const { language } = useI18n();
  const pick = (text: Localized) => text[language] ?? text.en;
  const bottomPadding = useSheetBottomPadding(spacing[6]);
  const [prompt, setPrompt] = useState<UpdatePrompt>({ kind: 'none' });
  const [whatsNew, setWhatsNew] = useState<string | null>(null);
  const checking = useRef(false);

  const check = useCallback(async () => {
    if (checking.current) return;
    checking.current = true;
    try {
      const current = currentAppVersion();
      const last = Number(await read(LAST_CHECK_KEY));
      if (!shouldCheckNow(Number.isFinite(last) && last > 0 ? last : null, Date.now())) return;
      const release = await fetchRelease(platform);
      if (!release) return; // 실패하면 아무것도 안 한다 — 시각도 안 적어 다음에 다시 묻는다
      const decided = decideUpdatePrompt(current, release, await read(DISMISSED_KEY));
      if (decided.kind !== 'force') await write(LAST_CHECK_KEY, String(Date.now()));
      setPrompt(decided);
    } finally {
      checking.current = false;
    }
  }, [platform]);

  useEffect(() => {
    let alive = true;
    void (async () => {
      const current = currentAppVersion();
      const [lastSeen, existing] = await Promise.all([read(LAST_SEEN_KEY), isExistingUser()]);
      if (alive && current && shouldShowWhatsNew(current, lastSeen, existing)) setWhatsNew(current);
      if (current) await write(LAST_SEEN_KEY, current);
    })();
    void check();
    const subscription = AppState.addEventListener('change', (state) => { if (state === 'active') void check(); });
    return () => { alive = false; subscription.remove(); };
  }, [check]);

  const openStore = (url: string) => { void Linking.openURL(url).catch(() => undefined); };

  if (prompt.kind === 'force') {
    return (
      <Modal visible transparent={false} animationType="fade" onRequestClose={() => undefined}>
        <View accessibilityViewIsModal style={styles.force} testID="app-update-force">
          <Text variant="title" weight="bold" style={styles.center}>{pick(COPY.forceTitle)}</Text>
          <Text color={color.text.body} style={styles.center}>{pick(COPY.forceBody)}</Text>
          <Button label={pick(COPY.update)} onPress={() => openStore(prompt.storeUrl)} containerStyle={styles.forceButton} />
        </View>
      </Modal>
    );
  }

  const items = whatsNew ? WHATS_NEW[whatsNew] ?? [] : [];
  const closeSoft = () => {
    if (prompt.kind === 'soft') void write(DISMISSED_KEY, prompt.latestVersion);
    setPrompt({ kind: 'none' });
  };

  return (
    <>
      <Modal visible={whatsNew !== null} transparent animationType="slide" onRequestClose={() => setWhatsNew(null)}>
        <View style={styles.backdrop}>
          <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: bottomPadding }]} testID="app-whats-new">
            <Text variant="title" weight="bold">{pick(COPY.whatsNewTitle)}</Text>
            <View style={styles.list}>
              {items.map((item) => (
                <View key={item.ko} style={styles.row}>
                  <View style={styles.dot} />
                  <Text color={color.text.body} style={styles.rowText}>{pick(item)}</Text>
                </View>
              ))}
            </View>
            <Button label={pick(COPY.ok)} onPress={() => setWhatsNew(null)} />
          </View>
        </View>
      </Modal>
      <Modal visible={prompt.kind === 'soft' && whatsNew === null} transparent animationType="slide" onRequestClose={closeSoft}>
        <View style={styles.backdrop}>
          <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: bottomPadding }]} testID="app-update-soft">
            <Text variant="title" weight="bold">{pick(COPY.updateTitle)}</Text>
            <Text color={color.text.body}>{pick(COPY.updateBody)}</Text>
            <View style={styles.actions}>
              <Button label={pick(COPY.later)} variant="tertiary" onPress={closeSoft} containerStyle={styles.action} />
              <Button
                label={pick(COPY.update)}
                onPress={() => { if (prompt.kind === 'soft') openStore(prompt.storeUrl); closeSoft(); }}
                containerStyle={styles.action}
              />
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(25,25,25,0.62)' },
  sheet: {
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
    gap: spacing[3],
    paddingTop: spacing[6],
    paddingHorizontal: spacing[6],
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    backgroundColor: color.brand.ivory,
  },
  list: { gap: spacing[2], marginVertical: spacing[1] },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  dot: { width: 6, height: 6, borderRadius: 3, marginTop: 9, backgroundColor: color.action.primary },
  rowText: { flex: 1 },
  actions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  action: { flex: 1 },
  force: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing[4], padding: spacing[6], backgroundColor: color.canvas },
  center: { textAlign: 'center' },
  forceButton: { alignSelf: 'stretch', maxWidth: 420, width: '100%' },
});
