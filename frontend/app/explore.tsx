// 로컬 탐색 화면 (S15P21E201-472, 상세설계서 v2 P-17). 8개 갈래(축제·야시장·전통시장·액티비티·
// 산책·자연·야경·기념품샵)를 아코디언으로 접었다 펴며 보여준다. 한 번에 하나만 열리고, 열 때
// 그 갈래의 장소만 부른다 — 화면 진입 시 8개를 한꺼번에 부르지 않는다.
//
// 갈래 이름·순서는 GET /api/v1/places/facets 응답을 그대로 쓴다(jaehyeon 님 2026-09-08:
// "목록을 화면 코드에 박지 마세요" — 서버가 갈래를 추가하거나 이름을 바꿔도 앱을 다시 배포하지
// 않아도 되게 하려는 것). 그래서 여기엔 8개 이름의 하드코딩 배열이 없다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { getFacets, type FacetKeyEntry, type FacetsLoadResult } from '@/discovery/localExplore';
import { useI18n } from '@/i18n';

// 여덟 갈래의 실제 값(jaehyeon 님 확인) — 서버가 이 여덟을 항상 함께 돌려주므로, 응답에서
// 이 값과 일치하는 항목만 골라 순서는 서버가 준 그대로 둔다. 화면 쪽에서 새로 만들지 않는다.
const KNOWN_FACET_KEYS = new Set(['FESTIVAL', 'NIGHT_MARKET', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE', 'NIGHT_VIEW', 'SOUVENIR_SHOP']);

function flattenLocalFacets(result: FacetsLoadResult): FacetKeyEntry[] | null {
  if (result.state !== 'success') return null;
  const flat = result.facets.flatMap((group) => group.keys);
  const local = flat.filter((entry) => KNOWN_FACET_KEYS.has(entry.featureKey));
  return local.length ? local : flat;
}

export default function LocalExplore() {
  const router = useRouter();
  const { tx } = useI18n();
  const [result, setResult] = useState<FacetsLoadResult>({ state: 'success', facets: [] });
  const [loading, setLoading] = useState(true);
  const [openKey, setOpenKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setResult(await getFacets());
    setLoading(false);
  }, []);

  useEffect(() => { void load(); }, [load]);

  const facets = flattenLocalFacets(result);

  return (
    <Screen scroll style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      <View style={styles.heading}>
        <Eyebrow>{tx('로컬 탐색', 'Local explore')}</Eyebrow>
        <Text variant="display" weight="bold">{tx('부산 로컬 탐색', 'Explore Busan like a local')}</Text>
        <Text color={color.text.body}>{tx('갈래를 눌러 열면 그 자리에서 장소를 찾아요.', 'Tap a category to load places for it.')}</Text>
      </View>

      {loading ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body}>{tx('갈래를 불러오고 있어요', 'Loading categories')}</Text></View>
      ) : null}

      {!loading && result.state !== 'success' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('로컬 탐색 API를 기다리고 있어요', 'Waiting for the local explore API') : tx('갈래를 불러오지 못했어요', 'Could not load categories')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} />
        </View>
      ) : null}

      {!loading && facets ? (
        <View style={styles.accordion}>
          {facets.map((facet) => {
            const disabled = facet.placeCount === 0;
            const open = openKey === facet.featureKey;
            return (
              <View key={facet.featureKey} style={styles.branch}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ expanded: open, disabled }}
                  disabled={disabled}
                  onPress={() => setOpenKey(open ? null : facet.featureKey)}
                  style={[styles.branchHeader, disabled && styles.branchHeaderDisabled]}
                >
                  <Text variant="body" weight="bold" color={disabled ? color.text.muted : color.text.heading}>{facet.labelKo}</Text>
                  <View style={styles.branchRight}>
                    <View style={[styles.countBadge, disabled && styles.countBadgeDisabled]}><Text variant="caption" weight="bold" color={disabled ? color.text.muted : color.text.body}>{facet.placeCount}</Text></View>
                    <Text variant="title" color={disabled ? color.text.muted : color.text.heading}>{open ? '︿' : '﹀'}</Text>
                  </View>
                </Pressable>
                {open ? <LocalBranchList facetKey={facet.featureKey} /> : null}
              </View>
            );
          })}
        </View>
      ) : null}
    </Screen>
  );
}

// 열린 갈래 하나의 장소 목록. GET /api/v1/places/nearby 응답 모양을 아직 못 받아서(요청 칸만
// 확인됨 — jaehyeon 님께 여쭤 놓은 상태) 실제 카드는 아직 못 그린다. 지어낸 응답 모양으로
// 화면을 만들면 실제 계약이 오는 순간 조용히 깨지므로, 여기서는 "무엇을 기다리는지"를 정직하게
// 보여준다 — 다른 일곱 갈래는 이 갈래와 무관하게 정상 동작한다(완료 기준: 한 갈래 실패가 나머지를
// 막지 않는다).
function LocalBranchList({ facetKey }: { facetKey: string }) {
  const { tx } = useI18n();
  return (
    <View style={styles.branchBody}>
      <Text color={color.text.body}>{tx('이 갈래의 장소 목록은 좌표 검색 API 응답 모양이 확정되면 이어서 채웁니다.', 'The place list for this category will be filled in once the coordinate-search API response shape is confirmed.')}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  accordion: { gap: spacing[2] },
  branch: { borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  branchHeader: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[4] },
  branchHeaderDisabled: { opacity: 0.55 },
  branchRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  countBadge: { minWidth: 28, minHeight: 24, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  countBadgeDisabled: { backgroundColor: color.surface.field },
  branchBody: { padding: spacing[4], paddingTop: 0 },
});
