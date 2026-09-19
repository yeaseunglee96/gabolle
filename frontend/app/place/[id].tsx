import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Animated, ImageBackground, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { sendAppEvent } from '@/analytics/appEvents';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { PhotoSubjectBadge } from '@/components/PhotoSubjectBadge';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { formatBreakTime, formatCheckInOut, formatFeatureSlot, formatLastOrderTime, formatSlopePercent, formatSoloFriendly, formatStairsPresent, getPlace, hasFoodSafetyConfirmed, hasLocalityScore, needsFoodSafetyCheck, type Place as ApiPlace } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { DEMO_PLACES, loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { PlacePhraseModal } from '@/components/PlacePhraseModal';

// 데모 3곳·저장 키는 src/discovery/savedPlaces.ts 로 옮겼다 — (tabs)/saved.tsx 도 같은 값을 쓴다.
const PLACES = DEMO_PLACES;

type RemoteState =
  | { status: 'loading' }
  | { status: 'loaded'; place: ApiPlace }
  | { status: 'not-found' }
  | { status: 'error' };

export default function Place() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const { width } = useWindowDimensions();
  // — 넓은 화면에서만 본문과 행동 버튼을 나눈다(피드·일정과 같은 1024 기준).
  const wide = isAtLeast(width, 'lg');
  const { id } = useLocalSearchParams<{ id?: string }>();
  const demoPlace = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [remote, setRemote] = useState<RemoteState>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [phraseModalOpen, setPhraseModalOpen] = useState(false);
  const [heroLoaded, setHeroLoaded] = useState(false);
  const heroReveal = useRef(new Animated.Value(0)).current;

  // 데모 3곳은 로컬 값을, 그 밖의 id 는 방금 받아온 API 응답을 같은 모양으로 맞춘다.
  // useMemo 로 묶는다 — 안 묶으면 매 렌더 새 객체가 생겨 resolved 를 의존성으로 삼는
  // 아래 effect 들이 재실행 루프에 빠질 수 있다.
  const resolved = useMemo(() => (
    demoPlace
      ? { title: tx(demoPlace.titleKo, demoPlace.titleEn), subtitle: tx(demoPlace.subtitleKo, demoPlace.subtitleEn), apiPlace: null as ApiPlace | null }
      : remote.status === 'loaded'
        ? { title: placeNameForLanguage(remote.place.nameKo, remote.place.nameEn, language), subtitle: tx(remote.place.address, remote.place.addressEn ?? remote.place.address), apiPlace: remote.place }
        : null
  ), [demoPlace, remote, tx]);
  const photoUrl = resolved?.apiPlace?.photoUrl ?? null;
  const taxiPlaceId = resolved?.apiPlace?.placeId ?? null;

  useEffect(() => {
    setHeroLoaded(false);
    heroReveal.setValue(0);
  }, [photoUrl, heroReveal]);

  useEffect(() => {
    if (!heroLoaded) return;
    Animated.timing(heroReveal, { toValue: 1, duration: 320, useNativeDriver: true }).start();
  }, [heroLoaded, heroReveal]);

  useEffect(() => {
    if (!id || demoPlace) return;
    let active = true;
    const controller = new AbortController();
    setRemote({ status: 'loading' });
    getPlace(id, controller.signal)
      .then((place) => { if (active) setRemote({ status: 'loaded', place }); })
      .catch((cause) => {
        if (!active) return;
        setRemote(cause instanceof ApiClientError && cause.status === 404 ? { status: 'not-found' } : { status: 'error' });
      });
    return () => { active = false; controller.abort(); };
  }, [id, demoPlace, retryCount]);

  // — 계정에 저장된 것과 기기 것을 합쳐서 본다.
  useEffect(() => {
    if (!id || !resolved) return;
    void loadSavedPlaceIds(accessToken).then((ids) => setIsSaved(ids.includes(id)));
  }, [id, resolved, accessToken]);

  const toggleSaved = async () => {
    if (!id || !resolved) return;
    const nextSaved = !isSaved;
    const { sync } = await setSavedPlace(id, nextSaved, accessToken);
    setIsSaved(nextSaved);
    // — 서버가 받지 못했으면 "저장했어요" 라고 말하지 않는다.
    // 배포 중 502 가 나는 동안 이 화면은 서버에 안 간 저장을 성공이라고 알렸다.
    // 기기의 선택은 그대로 지키되(다음 목록 조회에서 다시 맞춰진다) 말은 사실대로 한다.
    if (sync === 'failed') {
      setFeedback(tx('이 기기에만 저장했어요. 서버에 아직 반영하지 못했어요.', 'Saved on this device only — not synced to the server yet.'));
    } else {
      setFeedback(nextSaved ? tx('내 여행 후보에 저장했어요.', 'Saved to your trip candidates.') : tx('저장을 해제했어요.', 'Removed from saved.'));
    }
    // 저장할 때만 보낸다. 해제는 "싫다" 가 아니라 "취소" 다.
    if (nextSaved && !demoPlace) sendAppEvent({ type: 'place_like', accessToken, payload: { place_id: id, surface: 'place_detail' } });
  };

  const notFound = !demoPlace && remote.status === 'not-found';
  const loadFailed = !demoPlace && remote.status === 'error';
  const loading = !demoPlace && remote.status === 'loading';

  return (
    <Screen scroll wide style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      {loading ? <View style={styles.notice} accessibilityLiveRegion="polite"><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body} style={styles.noticeCopy}>{tx('장소 정보를 불러오고 있어요', 'Loading place details')}</Text></View> : null}

      {resolved ? <>
        {demoPlace ? (
          <ImageBackground source={demoPlace.image} resizeMode="cover" style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]} imageStyle={styles.heroImage}>
            <View style={styles.shade} />
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
            </View>
          </ImageBackground>
        ) : resolved.apiPlace?.photoUrl ? (
          <View style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={[StyleSheet.absoluteFill, styles.heroPlaceholder]} />
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: heroReveal, transform: [{ scale: heroReveal.interpolate({ inputRange: [0, 1], outputRange: [1.04, 1] }) }] }]}>
              <ImageBackground source={{ uri: resolved.apiPlace.photoUrl }} resizeMode="cover" style={styles.heroFill} imageStyle={styles.heroImage} onLoad={() => setHeroLoaded(true)} onError={() => setHeroLoaded(true)}>
                <View style={styles.shade} />
                <View style={styles.shadeLow} />
                <View style={styles.shadeLower} />
                <View style={styles.shadeLowest} />
                <View style={styles.heroCopy}>
                  <Text variant="display" weight="bold" color={color.text.onAction} style={styles.heroText}>{resolved.title}</Text>
                  <Text color={color.text.onAction} style={styles.heroText}>{resolved.subtitle}</Text>
                  {hasLocalityScore(resolved.apiPlace) ? (
                    <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>
                  ) : null}
                  {/* 사진이 이 장소를 찍은 것이 아니면 그렇게 말한다 —.
                      여태 축제 화면만 말하고 여기는 아무 말도 안 했다.
                  */}
                  <PhotoSubjectBadge photoSubject={resolved.apiPlace.photoSubject} style={styles.subjectBadge} />
                  {resolved.apiPlace.photoSource ? (
                    <Text testID="place-photo-credit" variant="caption" color={color.text.onAction} style={[styles.photoCredit, styles.heroText]}>{tx(`사진 제공: ${resolved.apiPlace.photoSource}`, `Photo: ${resolved.apiPlace.photoSource}`)}</Text>
                  ) : null}
                </View>
              </ImageBackground>
            </Animated.View>
          </View>
        ) : (
          <View style={[styles.hero, styles.heroPlain, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
              {resolved.apiPlace && hasLocalityScore(resolved.apiPlace) ? (
                <View style={styles.scoreBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('로컬 점수 있음', 'Has locality score')}</Text></View>
              ) : null}
            </View>
          </View>
        )}
        {/* — 넓은 화면에서 본문과 행동 버튼을 나눈다. 이 화면은 모든 목록의
            종착지라 아래로만 쌓이면 저장 버튼이 한참 밑에 있다. 폰은 지금처럼 한 줄이다.
        */}
        <View style={wide ? styles.wideGrid : undefined}>
        <View style={wide ? styles.mainColumn : undefined}>
        {resolved.apiPlace && (formatFeatureSlot(resolved.apiPlace.openingHours, tx) || formatFeatureSlot(resolved.apiPlace.priceLevel, tx) || formatCheckInOut(resolved.apiPlace, tx)) ? (
          <View style={styles.infoRows}>
            {formatFeatureSlot(resolved.apiPlace.openingHours, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('영업시간', 'Hours')}</Text><Text variant="body">{formatFeatureSlot(resolved.apiPlace.openingHours, tx)}</Text></View> : null}
            {/* : 숙박은 영업시간 대신 체크인·체크아웃이 온다 — 둘이 같은 장소에
                동시에 뜨는 일은 없다(원본 데이터가 한쪽만 채운다), 그래도 나란히 둬서 어느
                쪽이든 뜬 줄이 같은 자리에 보이게 한다.
            */}
            {formatCheckInOut(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('체크인·체크아웃', 'Check-in/out')}</Text><Text variant="body">{formatCheckInOut(resolved.apiPlace, tx)}</Text></View> : null}
            {formatFeatureSlot(resolved.apiPlace.priceLevel, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('가격대', 'Price level')}</Text><Text variant="body">{formatFeatureSlot(resolved.apiPlace.priceLevel, tx)}</Text></View> : null}
          </View>
        ) : null}
        {/* : 현장 이용 정보 — 영어 메뉴·해외카드·예약 필요 여부는 이 셋과 달리
            place_feature 에 해당 표식 자체가 아직 없어(백엔드 스키마 미정) 이번 증분에 안 넣는다.
            셋 다 없으면 구역 자체를 숨긴다 — "정보 없음"을 줄줄이 나열하지 않는다.
        */}
        {resolved.apiPlace && (formatSoloFriendly(resolved.apiPlace, tx) || formatBreakTime(resolved.apiPlace, tx) || formatLastOrderTime(resolved.apiPlace, tx)) ? (
          <View style={styles.infoRows}>
            {formatSoloFriendly(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('혼밥', 'Solo dining')}</Text><Text variant="body">{formatSoloFriendly(resolved.apiPlace, tx)}</Text></View> : null}
            {formatBreakTime(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('브레이크타임', 'Break time')}</Text><Text variant="body">{formatBreakTime(resolved.apiPlace, tx)}</Text></View> : null}
            {formatLastOrderTime(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('라스트오더', 'Last order')}</Text><Text variant="body">{formatLastOrderTime(resolved.apiPlace, tx)}</Text></View> : null}
          </View>
        ) : null}
        {/* : 이동약자 접근성 — 여기 있는 건 이 장소 자체의 경사·계단 정보다.
            "구간(경로)" 단위 접근성은 아직 백엔드에 없어 이 증분에 없다 — 티켓 코멘트 참고.
        */}
        {resolved.apiPlace && (formatStairsPresent(resolved.apiPlace, tx) || formatSlopePercent(resolved.apiPlace, tx)) ? (
          <View style={styles.infoRows}>
            {formatStairsPresent(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('계단', 'Stairs')}</Text><Text variant="body">{formatStairsPresent(resolved.apiPlace, tx)}</Text></View> : null}
            {formatSlopePercent(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('경사도', 'Slope')}</Text><Text variant="body">{formatSlopePercent(resolved.apiPlace, tx)}</Text></View> : null}
          </View>
        ) : null}
        {resolved.apiPlace && needsFoodSafetyCheck(resolved.apiPlace) ? (
          <View style={styles.safetyNotice} accessibilityRole="alert">
            <Text variant="caption" weight="bold" color={color.state.danger}>{tx('확인 필요', 'Needs confirmation')}</Text>
            <Text color={color.text.body}>{tx('알레르기·식단 정보가 없어 주문 전 확인이 필요합니다.', 'Allergy and dietary information is not available for this place — please check before ordering.')}</Text>
          </View>
        ) : null}
        {/* : "확인 못 함"(위 배너)과 "확인했고 문제 없음"을 다른 표시로 보여준다
            아무것도 안 보이는 빈 자리를 사용자가 "안전 확인됨"으로 착각하지 않게 한다.
        */}
        {resolved.apiPlace && hasFoodSafetyConfirmed(resolved.apiPlace) ? (
          <View style={styles.safetyConfirmed} accessibilityRole="text">
            <Text variant="caption" weight="bold" color={color.state.success}>{tx('확인됨', 'Confirmed')}</Text>
            <Text color={color.text.body}>{tx('알레르기·식단 정보를 확인했고, 등록된 유발 성분이 없습니다.', "Allergy and dietary info has been checked — no listed allergens or restrictions.")}</Text>
          </View>
        ) : null}
        {demoPlace ? (
          <View style={styles.notice} accessibilityLiveRegion="polite">
            <Text variant="title" weight="bold">{tx('상세 정보를 준비하고 있어요', 'Details are on the way')}</Text>
            <Text color={color.text.body} style={styles.noticeCopy}>{tx('영업시간·가격대 같은 상세 정보는 곧 추가돼요. 확인되지 않은 정보는 임의로 보여드리지 않아요.', "Details like hours and price level are coming soon. We never show unverified information.")}</Text>
          </View>
        ) : null}
        </View>
        <View style={wide ? styles.asideColumn : undefined}>
        {/* 사용자 리포트 — 버튼 네 개가 전폭으로 세로로 쌓여 가독성이 떨어졌다. 2열 그리드로
            바꿔 화면을 덜 차지하면서 한눈에 들어오게 한다. Button 컴포넌트 자체(26곳에서 쓴다)는
            그대로 두고, 이 화면의 containerStyle 폭만 절반으로 좁힌다 — Button.tsx 상단 주석이
            경고하는 "containerStyle 로 배경색을 흉내내는" 것과는 다르다(폭은 바깥 껍데기의
            레이아웃일 뿐, 안쪽 Pressable 의 색과 부딪히지 않는다).
        */}
        <View style={styles.actions}>
          <Button label={isSaved ? tx('내 여행 후보에서 빼기', 'Remove from candidates') : tx('내 여행 후보에 저장', 'Save to candidates')} variant="tertiary" onPress={() => void toggleSaved()} containerStyle={styles.actionHalf} />
          <Button label={tx('한국어로 말하기', 'Speak Korean')} onPress={() => setPhraseModalOpen(true)} containerStyle={styles.actionHalf} />
          {taxiPlaceId ? <Button label={tx('리뷰 보기', 'See reviews')} variant="tertiary" onPress={() => router.push(`/place-reviews/${taxiPlaceId}`)} containerStyle={styles.actionHalf} /> : null}
          {taxiPlaceId ? <Button label={tx('택시 기사에게 보여주기', 'Show to a taxi driver')} onPress={() => router.push(`/taxi-card/${taxiPlaceId}`)} containerStyle={styles.actionHalf} /> : null}
          {feedback ? <Text accessibilityLiveRegion="polite" color={color.text.body} style={styles.feedback}>{feedback}</Text> : null}
        </View>
        </View>
        </View>
      </> : null}

      <PlacePhraseModal visible={phraseModalOpen} onClose={() => setPhraseModalOpen(false)} category={resolved?.apiPlace?.category} />

      {notFound ? <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
        <Text color={color.text.body}>{tx('목록으로 돌아가 다른 장소를 선택해 주세요.', 'Go back to the list and choose another place.')}</Text>
        <Button label={tx('홈으로 돌아가기', 'Back to home')} onPress={() => router.replace('/home')} containerStyle={styles.recoveryButton} />
      </View> : null}

      {loadFailed ? <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소 정보를 불러오지 못했어요', "We couldn't load this place")}</Text>
        <Text color={color.text.body}>{tx('연결 상태를 확인하고 다시 시도해 주세요.', 'Check your connection and try again.')}</Text>
        <Button label={tx('다시 시도', 'Try again')} onPress={() => setRetryCount((count) => count + 1)} containerStyle={styles.recoveryButton} />
      </View> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  hero: { height: 240, justifyContent: 'flex-end', overflow: 'hidden', borderRadius: radius.lg },
  heroWide: { height: 360 },
  heroFill: { flex: 1, justifyContent: 'flex-end' },
  heroImage: { borderRadius: radius.lg },
  heroPlaceholder: { backgroundColor: color.surface.soft },
  heroPlain: { backgroundColor: color.brand.navy, padding: spacing[4] },
  // expo-linear-gradient 를 들이면 네이티브 모듈이 늘어 빌드 구성이 달라진다.
  // 그만한 일이 아니라 반투명 층을 겹쳐 근사한다 — 아래로 갈수록 누적되어 짬어진다.
  shade: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(8, 27, 53, 0.18)' },
  shadeLow: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '70%', backgroundColor: 'rgba(8, 27, 53, 0.16)' },
  shadeLower: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '45%', backgroundColor: 'rgba(8, 27, 53, 0.18)' },
  shadeLowest: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '24%', backgroundColor: 'rgba(8, 27, 53, 0.22)' },
  // 사진이 어떤 색이든 글자 윤곽이 서게 한다. 능짐 안 가리면서 가장 확실하다.
  heroText: { textShadowColor: 'rgba(8, 27, 53, 0.9)', textShadowOffset: { width: 0, height: 1 }, textShadowRadius: 6 },
  heroCopy: { gap: spacing[1], padding: spacing[4] },
  subjectBadge: { marginTop: spacing[2] },
  scoreBadge: { alignSelf: 'flex-start', marginTop: spacing[2], borderRadius: radius.full, paddingHorizontal: spacing[3], paddingVertical: spacing[1], backgroundColor: 'rgba(255,255,255,0.18)' },
  // opacity 0.8 을 뽑았다. 이 줄은 공공누리 이용 조건이라
  // 읽힐 수 있어야 한다 — 지켜야 하는 표기를 일부러 흐리게 할 이유가 없다.
  photoCredit: { marginTop: spacing[1] },
  infoRows: { marginTop: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  infoRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  safetyNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  safetyConfirmed: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], marginTop: spacing[4] }, wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] }, mainColumn: { flex: 1, minWidth: 0 }, asideColumn: { width: 320 },
  // 두 버튼씩 한 줄에 앉힌다(사용자 리포트 — 네 개가 세로로 쌓여 읽기 어려웠다).
  // flexBasis 로 최소 폭을 잡고 flexGrow 로 남는 자리를 채운다 — 홀수 개(리뷰·택시 버튼이
  // 없는 장소)일 때도 마지막 버튼이 어색하게 반쪽만 남지 않고 자연스럽게 늘어난다.
  actionHalf: { flexBasis: '46%', flexGrow: 1 },
  feedback: { textAlign: 'center', width: '100%' },
  recoveryButton: { marginTop: spacing[2] },
});
