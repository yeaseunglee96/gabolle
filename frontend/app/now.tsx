// S-12 · 지금 갈 곳 (/now) — 상세설계서 참고. 위치 권한이 없어도 수동 입력으로 동작해야 하는 화면이라
// 기기 위치와 직접 입력을 항상 함께 보여준다. 추천 API(FR-REC-10)가 아직 없으므로 가짜 후보를 만들지 않고
// '연결 전' 상태를 그대로 보여준다.
import { useState } from 'react';
import { Linking, Pressable, StyleSheet, TextInput, View } from 'react-native';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { idleNowResult, requestNowRecommendations, type NowCandidate, type NowViewModel } from '@/plan/nowRecommendations';

const REMAINING_OPTIONS = [30, 60, 90, 120, 180] as const;

type LocationState = 'idle' | 'detecting' | 'granted' | 'denied';

function CandidateCard({ candidate, onOpen }: { candidate: NowCandidate; onOpen: () => void }) {
  const { tx } = useI18n();
  const STATUS_LABEL = { VERIFIED: tx('확인됨', 'Verified'), ESTIMATED: tx('추정', 'Estimated'), UNKNOWN: tx('미확인', 'Unconfirmed') } as const;
  return (
    <View style={styles.card}>
      <View style={styles.cardTop}>
        <Text variant="title" weight="bold" style={styles.grow}>{candidate.name}</Text>
        <View style={styles.statusChip}><Text variant="caption" weight="bold">{STATUS_LABEL[candidate.dataStatus]}</Text></View>
      </View>
      <Text variant="body" color={color.text.body}>{tx(`이동 ${candidate.travelMinutes}분`, `${candidate.travelMinutes} min away`)}</Text>
      <Text variant="body" color={candidate.minutesUntilClose == null ? color.text.muted : color.text.body}>
        {candidate.minutesUntilClose == null ? tx('영업 종료 시각 미확인', 'Closing time unconfirmed') : tx(`영업 종료까지 ${candidate.minutesUntilClose}분`, `${candidate.minutesUntilClose} min until closing`)}
      </Text>
      {candidate.reasons.length > 0 && (
        <View style={styles.tags}>{candidate.reasons.map((reason, index) => (
          <View key={`${reason}-${index}`} style={styles.tag}><Text variant="caption" weight="bold" color={color.text.muted}>#{reason}</Text></View>
        ))}</View>
      )}
      <Pressable accessibilityRole="button" onPress={onOpen} style={styles.detailLink}>
        <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('자세히 보기 ›', 'See details ›')}</Text>
      </Pressable>
    </View>
  );
}

export default function Now() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [locationState, setLocationState] = useState<LocationState>('idle');
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [manualLocation, setManualLocation] = useState('');
  const [remainingMinutes, setRemainingMinutes] = useState<number | null>(null);
  const [result, setResult] = useState<NowViewModel>(idleNowResult());

  async function detectLocation() {
    setLocationState('detecting');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) { setLocationState('denied'); return; }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }

  const canSearch = (coords !== null || manualLocation.trim().length > 0) && remainingMinutes !== null;

  async function search() {
    if (!canSearch || remainingMinutes === null) return;
    setResult({ state: 'loading', candidates: [], weatherApplied: false, message: tx('지금 갈 수 있는 곳을 찾고 있어요.', 'Finding places you can go right now.') });
    setResult(await requestNowRecommendations({
      latitude: coords?.latitude ?? null,
      longitude: coords?.longitude ?? null,
      manualLocation: coords ? null : manualLocation.trim(),
      remainingMinutes,
    }, accessToken));
  }

  return (
    <Screen scroll>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.backButton}>
          <Text variant="title">‹</Text>
        </Pressable>
        <Text variant="title" weight="bold">{tx('지금 갈 곳', 'Places nearby now')}</Text>
        <View style={styles.headerSpacer} />
      </View>
      <Text variant="body" color={color.text.muted} style={styles.intro}>{tx('남는 시간에 지금 갈 수 있는 곳을 바로 찾아드려요.', 'We find places you can visit right now, in the time you have.')}</Text>

      <View style={styles.section}>
        <Text variant="body" weight="bold">{tx('출발 위치', 'Starting point')}</Text>
        {locationState === 'granted' && coords ? (
          <View style={styles.locationDone}>
            <Text variant="body" color={color.state.success}>{tx('현재 위치를 사용해요', 'Using your current location')}</Text>
            <Pressable accessibilityRole="button" onPress={() => { setLocationState('idle'); setCoords(null); }}>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('직접 입력으로 바꾸기', 'Switch to manual entry')}</Text>
            </Pressable>
          </View>
        ) : (
          <>
            <Button
              label={locationState === 'detecting' ? tx('위치 확인 중…', 'Checking location…') : tx('현재 위치 사용', 'Use current location')}
              variant="secondary"
              disabled={locationState === 'detecting'}
              onPress={() => void detectLocation()}
              containerStyle={styles.locationButton}
            />
            {locationState === 'denied' && (
              <View style={styles.deniedRow}>
                <Text variant="caption" color={color.state.danger} style={styles.deniedText}>{tx('위치 권한이 필요합니다. 아래에 직접 입력하거나 설정에서 허용해 주세요.', 'Permission needed. Enter your starting point below, or allow it in settings.')}</Text>
                <Pressable accessibilityRole="button" onPress={() => void Linking.openSettings()} style={styles.settingsLink}>
                  <Text variant="caption" weight="bold" color={color.text.accent}>{tx('설정 열기 ›', 'Open settings ›')}</Text>
                </Pressable>
              </View>
            )}
            <TextInput
              value={manualLocation}
              onChangeText={setManualLocation}
              placeholder={tx('예: 해운대역, OO 호텔', 'e.g. Haeundae Station, OO Hotel')}
              placeholderTextColor={color.text.muted}
              style={styles.input}
              accessibilityLabel={tx('출발 위치 직접 입력', 'Enter starting point manually')}
            />
          </>
        )}
      </View>

      <View style={styles.section}>
        <Text variant="body" weight="bold">{tx('남는 시간', 'Time available')}</Text>
        <View style={styles.chipRow}>
          {REMAINING_OPTIONS.map((minutes) => (
            <Pressable
              key={minutes}
              accessibilityRole="radio"
              accessibilityState={{ checked: remainingMinutes === minutes }}
              onPress={() => setRemainingMinutes(minutes)}
              style={[styles.chip, remainingMinutes === minutes && styles.chipSelected]}
            >
              <Text variant="caption" weight="bold" color={remainingMinutes === minutes ? color.text.onAction : color.text.body}>{tx(`${minutes}분`, `${minutes} min`)}</Text>
            </Pressable>
          ))}
        </View>
      </View>

      <Button label={tx('지금 갈 곳 찾기', 'Find a place now')} disabled={!canSearch || result.state === 'loading'} onPress={() => void search()} containerStyle={styles.searchButton} />

      {result.state === 'loading' && (
        <View accessibilityLabel={result.message} style={styles.results}>
          {[0, 1, 2].map((key) => (
            <View key={key} style={styles.card}>
              <View style={styles.cardTop}><Skeleton width="60%" height={18} /><Skeleton width={56} height={20} radius={radius.full} /></View>
              <Skeleton width="40%" height={14} />
              <Skeleton width="70%" height={14} />
            </View>
          ))}
        </View>
      )}
      {(result.state === 'error' || result.state === 'offline') && (
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('지금 갈 곳을 찾지 못했어요', 'Could not find a place to go now')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void search()} />
        </View>
      )}
      {result.state === 'empty' && (
        <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('갈 만한 곳을 찾지 못했어요', 'No suitable place found')}</Text><Text color={color.text.body}>{result.message}</Text></View>
      )}
      {(result.state === 'success' || result.state === 'partial') && (
        <View style={styles.results}>
          {result.state === 'partial' && (
            <View style={styles.notice}><Text accessibilityRole="alert" variant="caption" weight="bold">{result.message}</Text></View>
          )}
          {result.candidates.map((candidate) => (
            <CandidateCard key={candidate.placeId} candidate={candidate} onOpen={() => router.push(`/place/${candidate.placeId}`)} />
          ))}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  backButton: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  headerSpacer: { width: 44 },
  intro: { marginTop: spacing[2], marginBottom: spacing[4] },
  section: { gap: spacing[2], marginBottom: spacing[4] },
  locationButton: { marginTop: spacing[1] },
  deniedRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  deniedText: { flex: 1 },
  settingsLink: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[2] },
  locationDone: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  input: { minHeight: 48, marginTop: spacing[1], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, color: color.text.heading },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  chipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  searchButton: { marginTop: spacing[1], marginBottom: spacing[6] },
  stateCard: { gap: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center' },
  notice: { marginBottom: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  results: { gap: spacing[3] },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  cardTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  grow: { flex: 1 },
  statusChip: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1] },
  tag: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.sm, backgroundColor: color.surface.tint },
  detailLink: { minHeight: 44, justifyContent: 'center' },
});
