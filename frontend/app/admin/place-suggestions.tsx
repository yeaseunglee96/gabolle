// 장소 제안 검토(운영자) — S15P21E201-2031. 신고 검토(/admin/story-reports)처럼 탭에 안 올리고 이 주소로 직접 들어온다.
// 접근은 서버가 ADMIN(배포 설정 GABOLLE_ADMIN_EMAILS)으로 막는다 — 여기서는 403 이면 「권한 없음」만 보인다.
// 운영자용이라 한국어로만 쓴다(신고 검토와 같다).
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Linking, StyleSheet, TextInput, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { AppImage } from '@/components/AppImage';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { fetchReviewQueue, reviewSuggestion, SUGGEST_CATEGORIES, type PlaceSuggestion } from '@/discovery/placeSuggestions';
import { useLayout } from '@/layout/useLayout';
import { resolveStoryImageUrl } from '@/social/stories';

function categoryKo(code: string) {
  return SUGGEST_CATEGORIES.find((c) => c.code === code)?.ko ?? code;
}

export default function AdminPlaceSuggestions() {
  const { accessToken, ready } = useAuth();
  const { desktop } = useLayout();
  const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'error'>('loading');
  const [items, setItems] = useState<PlaceSuggestion[]>([]);
  const [errorMessage, setErrorMessage] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejectReasons, setRejectReasons] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!accessToken) return;
    setState('loading');
    const result = await fetchReviewQueue('PENDING', accessToken);
    if (result.state === 'success') { setItems(result.items); setState('ready'); }
    else if (result.state === 'forbidden') setState('forbidden');
    else { setErrorMessage(result.message); setState('error'); }
  }, [accessToken]);

  useEffect(() => {
    if (!ready) return;
    if (!accessToken) { setState('forbidden'); return; }
    void load();
  }, [ready, accessToken, load]);

  async function act(id: string, action: 'approve' | 'reject') {
    if (busyId) return;
    setBusyId(id);
    const result = await reviewSuggestion(id, action, accessToken, rejectReasons[id]);
    setBusyId(null);
    if (result.state === 'success') setItems((current) => current.filter((item) => item.suggestionId !== id));
    else if (result.state === 'forbidden') setState('forbidden');
    else setErrorMessage(result.message || '처리하지 못했어요.');
  }

  return (
    <Screen scroll wide={desktop}>
      <Text variant="display" weight="bold">장소 제안 검토</Text>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        승인하면 추천에 쓰이는 장소(현지인 추천)가 되고 제안한 사람에게 알림이 갑니다. 위치는 지도에서 꼭 확인해 주세요.
      </Text>
      {state === 'loading' ? <ActivityIndicator color={color.action.primary} /> : null}
      {state === 'forbidden' ? <Text color={color.text.body}>운영자만 볼 수 있어요.</Text> : null}
      {state === 'error' ? <Text color={color.state.danger}>{errorMessage || '목록을 불러오지 못했어요.'}</Text> : null}
      {state === 'ready' && items.length === 0 ? <Text color={color.text.body}>검토할 제안이 없어요.</Text> : null}
      {state === 'ready' && errorMessage ? <Text color={color.state.danger}>{errorMessage}</Text> : null}
      <View style={desktop ? styles.grid : styles.list}>
        {items.map((item) => (
          <View key={item.suggestionId} style={[styles.card, desktop && styles.cardWide]}>
            <View style={styles.row}>
              {item.photoUrl ? <AppImage source={{ uri: resolveStoryImageUrl(item.photoUrl) }} style={styles.photo} /> : null}
              <View style={styles.flex}>
                <Text variant="title" weight="bold">{item.name}</Text>
                <Text variant="caption" color={color.text.body}>{`${categoryKo(item.category)} · ${item.suggesterName ?? '(이름 없음)'} · ${new Date(item.createdAt).toLocaleString('ko-KR')}`}</Text>
              </View>
            </View>
            <Text>{`“${item.reason}”`}</Text>
            {item.openingHours ? <Text variant="caption" color={color.text.body}>{`영업시간 ${item.openingHours}`}</Text> : null}
            {item.phone ? <Text variant="caption" color={color.text.body}>{`전화 ${item.phone}`}</Text> : null}
            <Button label={`지도에서 위치 보기 (${item.lat.toFixed(5)}, ${item.lng.toFixed(5)})`} variant="tertiary" compact
              onPress={() => void Linking.openURL(`https://map.kakao.com/link/map/${encodeURIComponent(item.name)},${item.lat},${item.lng}`)} />
            <TextInput
              value={rejectReasons[item.suggestionId] ?? ''}
              onChangeText={(text) => setRejectReasons((current) => ({ ...current, [item.suggestionId]: text }))}
              placeholder="반려 사유(선택) — 제안한 사람에게 보여요"
              placeholderTextColor={color.text.muted}
              maxLength={200}
              style={styles.input}
            />
            <View style={styles.actions}>
              <Button label="승인" compact disabled={busyId !== null} onPress={() => void act(item.suggestionId, 'approve')} />
              <Button label="반려" variant="danger" compact disabled={busyId !== null} onPress={() => void act(item.suggestionId, 'reject')} />
            </View>
          </View>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[1], marginBottom: spacing[4] },
  list: { gap: spacing[3] },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardWide: { width: '48%' },
  row: { flexDirection: 'row', gap: spacing[3], alignItems: 'center' },
  flex: { flex: 1, minWidth: 0 },
  photo: { width: 72, height: 72, borderRadius: radius.md },
  input: { minHeight: 44, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, paddingHorizontal: spacing[3], color: color.text.heading },
  actions: { flexDirection: 'row', gap: spacing[2] },
});
