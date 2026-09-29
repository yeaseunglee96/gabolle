import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Animated, ImageBackground, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { sendAppEvent } from '@/analytics/appEvents';
import { useBehaviorConsentAsk } from '@/personalization/consentAsk';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { PhotoSubjectBadge } from '@/components/PhotoSubjectBadge';
import { Screen } from '@/components/Screen';
import { PhotoCredit } from '@/components/PhotoCredit';
import { PlacePhotoGallery } from '@/components/PlacePhotoGallery';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { formatBreakTime, formatCheckInOut, formatFeatureSlot, formatLastOrderTime, formatSlopePercent, formatSoloFriendly, formatStairsPresent, getPlace, hasFoodSafetyConfirmed, needsFoodSafetyCheck, photoLabels, placePhotos, type Place as ApiPlace } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { DEMO_PLACES, loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { PlacePhraseModal } from '@/components/PlacePhraseModal';
import { txf } from '@/i18n/format';
import { otherNameFor } from '@/discovery/localNames';

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
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const wide = useLayout().desktop;
  const { id } = useLocalSearchParams<{ id?: string }>();
  const demoPlace = id && id in PLACES ? PLACES[id as keyof typeof PLACES] : null;
  const [isSaved, setIsSaved] = useState(false);
  const [feedback, setFeedback] = useState('');
  const consent = useBehaviorConsentAsk(accessToken);
  const [remote, setRemote] = useState<RemoteState>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);
  const [phraseModalOpen, setPhraseModalOpen] = useState(false);
  const [heroLoaded, setHeroLoaded] = useState(false);
  const heroReveal = useRef(new Animated.Value(0)).current;
  // 여러 장일 때 지금 보이는 사진 — 출처 줄이 그 사진의 것이어야 한다(S15P21E201-1839).
  const [photoIndex, setPhotoIndex] = useState(0);

  // 데모 3곳은 로컬 값을, 그 밖의 id 는 방금 받아온 API 응답을 같은 모양으로 맞춘다.
  // useMemo 로 묶는다 — 안 묶으면 매 렌더 새 객체가 생겨 resolved 를 의존성으로 삼는
  // 아래 effect 들이 재실행 루프에 빠질 수 있다.
  // 🔴 주소가 없는 장소(OSM 출처 등, address=null)도 있다 — 번역 함수에 null 을 넘기면 일·중 화면이 하얗게 죽었다
  //    (S15P21E201-1725 · 번역 함수 쪽 방어 !1720 에 더한 호출부 방어 S15P21E201-1726). 없으면 부제를 비운다.
  const resolved = useMemo(() => (
    demoPlace
      ? { title: tx(demoPlace.titleKo, demoPlace.titleEn), subtitle: tx(demoPlace.subtitleKo, demoPlace.subtitleEn), apiPlace: null as ApiPlace | null }
      : remote.status === 'loaded'
        ? { title: placeNameForLanguage(remote.place.nameKo, otherNameFor(remote.place.nameEn, remote.place.localNames, language), language), subtitle: remote.place.address ? tx(remote.place.address, remote.place.addressEn ?? remote.place.address) : '', apiPlace: remote.place }
        : null
  ), [demoPlace, remote, tx]);
  // 사진 목록 — 서버가 photos 를 보내면 그것, 안 보내면 옛 photoUrl 한 장(S15P21E201-1839).
  const photos = useMemo(() => (resolved?.apiPlace ? placePhotos(resolved.apiPlace) : []), [resolved]);
  const photoUrl = photos[0]?.url ?? null;
  const currentPhoto = photos[Math.min(photoIndex, Math.max(0, photos.length - 1))] ?? null;
  // 사진 출처 줄 — 위키미디어 사진은 라이선스 이름을 덧붙이고, 누르면 파일 페이지가 열린다(S15P21E201-1610).
  //    여러 장이면 지금 보이는 사진의 출처·라이선스다. 「행사장 사진」 표시는 대표 사진(첫 장)에 대한 말이라 첫 장에서만.
  const photoCredit = resolved?.apiPlace && currentPhoto
    ? photoLabels({ photoSource: currentPhoto.source, photoLicense: currentPhoto.license, photoSubject: photoIndex === 0 ? resolved.apiPlace.photoSubject : null }, tx)
    : null;
  const taxiPlaceId = resolved?.apiPlace?.placeId ?? null;

  useEffect(() => {
    setHeroLoaded(false);
    setPhotoIndex(0);
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

  // 🔴 「이 장소를 봤다」 — place_view (S15P21E201-638). 서버 취향 귀속의 마지막 조각(1482, 기여값 +0.1).
  //    화면당 «한 번만» 보낸다 — 재렌더·뒤로가기·재시도로 두 번 나가면 「두 번 본 것」이 되어 가중치가 부푼다.
  //    «로딩이 끝난 뒤에만» — not-found 인 장소를 봤다고 적지 않는다. demoPlace(내장 견본)는 실제 장소가 아니라 뺀다.
  //    requestId 는 이번 범위가 아니다(랭킹 평가용 — 취향 귀속에는 필요 없다, 별도 티켓).
  const viewed = useRef<Set<string>>(new Set());
  useEffect(() => {
    if (!id || demoPlace || remote.status !== 'loaded') return;
    if (viewed.current.has(id)) return;
    viewed.current.add(id);
    sendAppEvent({ type: 'place_view', accessToken, payload: { placeId: id, surface: 'place_detail' } });
  }, [id, demoPlace, remote.status, accessToken]);

  // — 계정에 저장된 것과 기기 것을 합쳐서 본다.
  // 🔴 이 답이 오기 전에는 저장 단추를 잠근다(S15P21E201-1644). 전에는 먼저 누르면 늦게 온 이 답이 방금 누른 표시를
  //    되돌려서, 사람 눈에는 「눌렀는데 아무 일도 없다」로 보였다(운영 끝까지 흐르는 시험에서 찾음).
  const [savedKnown, setSavedKnown] = useState(false);
  useEffect(() => {
    if (!id || !resolved) return;
    let alive = true;
    setSavedKnown(false);
    void loadSavedPlaceIds(accessToken).then((ids) => { if (!alive) return; setIsSaved(ids.includes(id)); setSavedKnown(true); });
    return () => { alive = false; };
  }, [id, resolved, accessToken]);

  const toggleSaved = async () => {
    if (!id || !resolved) return;
    // 🔴 비회원은 로그인으로 보낸다 (S15P21E201-1795).
    //
    // 전에는 그냥 저장되고 「내 여행 후보에 저장했어요. 일정을 만들 때 이 장소를 먼저
    // 넣어요」가 떴다. 기기에는 남으므로 거짓말은 아니지만, 저 문구는 «서버가 일정에
    // 반영한다»는 약속이다 — 비회원의 저장은 서버에 안 간다(savedPlaces 가 device-only 로
    // 돌려준다). 로그인해서 돌아오면 그 장소는 어디에도 없다.
    //
    // 아래 `sync === 'failed'` 가지는 502 때 거짓 성공을 막으려고 넣은 것인데,
    // device-only 는 그 가지에 안 걸려 그대로 성공 문구가 나갔다.
    if (!accessToken) { router.push({ pathname: '/sign-in', params: { returnTo: `/place/${id}` } }); return; }
    const nextSaved = !isSaved;
    const { sync } = await setSavedPlace(id, nextSaved, accessToken);
    setIsSaved(nextSaved);
    // — 서버가 받지 못했으면 "저장했어요" 라고 말하지 않는다.
    // 배포 중 502 가 나는 동안 이 화면은 서버에 안 간 저장을 성공이라고 알렸다.
    // 기기의 선택은 그대로 지키되(다음 목록 조회에서 다시 맞춰진다) 말은 사실대로 한다.
    if (sync === 'failed') {
      setFeedback(tx('이 기기에만 저장했어요. 서버에 아직 반영하지 못했어요.', 'Saved on this device only — not synced to the server yet.'));
    } else {
      // 🔴 어디에 저장됐는지까지 말한다 — S15P21E201-1489(B-11). 「저장했어요」만 들으면
      //    어디에 담겼는지 모른다 — 이쪽은 일정 만들 때 쓰는 후보다.
      setFeedback(nextSaved
        ? tx('내 여행 후보에 저장했어요. 일정을 만들 때 이 장소를 먼저 넣어요.', 'Saved to your trip candidates — we will use it first when building an itinerary.')
        : tx('저장을 해제했어요.', 'Removed from saved.'));
      // 서버에 저장된 첫 하트 — 다음 추천에 반영할지 한 번 묻는다(S15P21E201-1644).
      if (nextSaved) void consent.askOnce();
    }
    // 🔴 place_like 는 여기서 보내지 않는다 — S15P21E201-1486. 저장 API(PUT /me/saved-places)가
    //    서버에서 같은 트랜잭션으로 PLACE_LIKE 를 적는다(inserted == 1 일 때만이라 연타·재시도에도
    //    한 건). 앱이 또 보내면 하트 한 번에 두 건이 됐다. place_view 는 서버 짝이 없어 그대로 둔다.
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

      {loading ? <View style={styles.notice} accessibilityLiveRegion="polite"><ActivityIndicator color={color.action.primary} /><Text color={color.text.body} style={styles.noticeCopy}>{tx('장소 정보를 불러오고 있어요', 'Loading place details')}</Text></View> : null}

      {resolved ? <>
        {demoPlace ? (
          <ImageBackground source={demoPlace.image} resizeMode="cover" style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]} imageStyle={styles.heroImage}>
            <View style={styles.shade} />
            <View style={styles.heroCopy}>
              <Text variant="display" weight="bold" color={color.text.onAction}>{resolved.title}</Text>
              <Text color={color.text.onAction}>{resolved.subtitle}</Text>
            </View>
          </ImageBackground>
        ) : photos.length > 1 ? (
          // 여러 장 — 옆으로 넘긴다(S15P21E201-1839). 그늘·글자는 사진 판 위에 한 벌만 얹고, 손가락은 아래 판까지 통과시킨다.
          <View style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={[StyleSheet.absoluteFill, styles.heroPlaceholder]} />
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: heroReveal }]}>
              <PlacePhotoGallery urls={photos.map((photo) => photo.url)} onFirstLoad={() => setHeroLoaded(true)} onIndexChange={setPhotoIndex} />
            </Animated.View>
            <View pointerEvents="box-none" style={[StyleSheet.absoluteFill, styles.heroFill]}>
              <View pointerEvents="none" style={styles.shade} />
              <View pointerEvents="none" style={styles.shadeLow} />
              <View pointerEvents="none" style={styles.shadeLower} />
              <View pointerEvents="none" style={styles.shadeLowest} />
              <View pointerEvents="box-none" style={styles.heroCopy}>
                <Text variant="display" weight="bold" color={color.text.onAction} style={styles.heroText}>{resolved.title}</Text>
                <Text color={color.text.onAction} style={styles.heroText}>{resolved.subtitle}</Text>
                {/* 「행사장 사진」 표시·출처 줄의 접근성 라벨이 왜 있는지는 아래 한 장 그림의 주석에 있다. */}
                <PhotoSubjectBadge photoSubject={photoIndex === 0 ? resolved.apiPlace?.photoSubject : null} style={styles.subjectBadge} />
                {photoCredit?.credit ? (
                  <PhotoCredit testID="place-photo-credit" accessibilityLabel={photoCredit.credit} credit={photoCredit.credit} licenseUrl={photoCredit.licenseUrl} variant="caption" color={color.text.onAction} style={[styles.photoCredit, styles.heroText]} />
                ) : null}
              </View>
            </View>
          </View>
        ) : photoUrl ? (
          <View style={[styles.hero, isAtLeast(width, 'md') && styles.heroWide]}>
            <View style={[StyleSheet.absoluteFill, styles.heroPlaceholder]} />
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: heroReveal, transform: [{ scale: heroReveal.interpolate({ inputRange: [0, 1], outputRange: [1.04, 1] }) }] }]}>
              <ImageBackground source={{ uri: photoUrl }} resizeMode="cover" style={styles.heroFill} imageStyle={styles.heroImage} onLoad={() => setHeroLoaded(true)} onError={() => setHeroLoaded(true)}>
                <View style={styles.shade} />
                <View style={styles.shadeLow} />
                <View style={styles.shadeLower} />
                <View style={styles.shadeLowest} />
                <View style={styles.heroCopy}>
                  <Text variant="display" weight="bold" color={color.text.onAction} style={styles.heroText}>{resolved.title}</Text>
                  <Text color={color.text.onAction} style={styles.heroText}>{resolved.subtitle}</Text>
                  {/* 사진이 이 장소를 찍은 것이 아니면 그렇게 말한다 — S15P21E201-1206.
                      여태 축제 화면만 말하고 여기는 아무 말도 안 했다.
                  */}
                  <PhotoSubjectBadge photoSubject={resolved.apiPlace?.photoSubject} style={styles.subjectBadge} />
                  {/* 🔴 accessibilityLabel 을 반드시 함께 준다 (2026-09-22, build 41 실기기).

                      iOS 에서는 testID 가 accessibility identifier 로 나가는데, 라벨이 없으면
                      **그 식별자가 읽을 글자로 새어 나온다.** 접근성 트리에 실제 출처 문구와
                      별도로 `place-photo-credit` 이라는 항목이 하나 더 잡혔고(실측),
                      VoiceOver 는 그것을 영어 알파벳 그대로 읽는다. 화면에는 안 보인다.

                      라벨을 주면 식별자는 자동화용으로만 남고 소리로는 안 나간다. */}
                  {photoCredit?.credit ? (
                    <PhotoCredit testID="place-photo-credit" accessibilityLabel={photoCredit.credit} credit={photoCredit.credit} licenseUrl={photoCredit.licenseUrl} variant="caption" color={color.text.onAction} style={[styles.photoCredit, styles.heroText]} />
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
            {formatFeatureSlot(resolved.apiPlace.openingHours, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('영업시간', 'Hours')}</Text><Text variant="body" style={styles.infoValue}>{formatFeatureSlot(resolved.apiPlace.openingHours, tx)}</Text></View> : null}
            {/* : 숙박은 영업시간 대신 체크인·체크아웃이 온다 — 둘이 같은 장소에
                동시에 뜨는 일은 없다(원본 데이터가 한쪽만 채운다), 그래도 나란히 둬서 어느
                쪽이든 뜬 줄이 같은 자리에 보이게 한다.
            */}
            {formatCheckInOut(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('체크인·체크아웃', 'Check-in/out')}</Text><Text variant="body" style={styles.infoValue}>{formatCheckInOut(resolved.apiPlace, tx)}</Text></View> : null}
            {formatFeatureSlot(resolved.apiPlace.priceLevel, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('가격대', 'Price level')}</Text><Text variant="body" style={styles.infoValue}>{formatFeatureSlot(resolved.apiPlace.priceLevel, tx)}</Text></View> : null}
          </View>
        ) : null}
        {/* : 현장 이용 정보 — 영어 메뉴·해외카드·예약 필요 여부는 이 셋과 달리
            place_feature 에 해당 표식 자체가 아직 없어(백엔드 스키마 미정) 이번 증분에 안 넣는다.
            셋 다 없으면 구역 자체를 숨긴다 — "정보 없음"을 줄줄이 나열하지 않는다.
        */}
        {resolved.apiPlace && (formatSoloFriendly(resolved.apiPlace, tx) || formatBreakTime(resolved.apiPlace, tx) || formatLastOrderTime(resolved.apiPlace, tx)) ? (
          <View style={styles.infoRows}>
            {formatSoloFriendly(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('혼밥', 'Solo dining')}</Text><Text variant="body" style={styles.infoValue}>{formatSoloFriendly(resolved.apiPlace, tx)}</Text></View> : null}
            {formatBreakTime(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('브레이크타임', 'Break time')}</Text><Text variant="body" style={styles.infoValue}>{formatBreakTime(resolved.apiPlace, tx)}</Text></View> : null}
            {formatLastOrderTime(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('라스트오더', 'Last order')}</Text><Text variant="body" style={styles.infoValue}>{formatLastOrderTime(resolved.apiPlace, tx)}</Text></View> : null}
          </View>
        ) : null}
        {/* : 이동약자 접근성 — 여기 있는 건 이 장소 자체의 경사·계단 정보다.
            "구간(경로)" 단위 접근성은 아직 백엔드에 없어 이 증분에 없다 — 티켓 코멘트 참고.
        */}
        {resolved.apiPlace && (formatStairsPresent(resolved.apiPlace, tx) || formatSlopePercent(resolved.apiPlace, tx)) ? (
          <View style={styles.infoRows}>
            {formatStairsPresent(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('계단', 'Stairs')}</Text><Text variant="body" style={styles.infoValue}>{formatStairsPresent(resolved.apiPlace, tx)}</Text></View> : null}
            {formatSlopePercent(resolved.apiPlace, tx) ? <View style={styles.infoRow}><Text variant="caption" weight="bold" color={color.text.muted} style={styles.infoLabel}>{tx('경사', 'Slope')}</Text><Text variant="body" style={styles.infoValue}>{formatSlopePercent(resolved.apiPlace, tx)}</Text></View> : null}
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
            바꿔 화면을 덜 차지하면서 한눈에 들어오게 한다. Button 컴포넌트 자체(여러 화면이 같이 쓴다 — 개수는 적지 않는다, 적으면 낡는다)는
            그대로 두고, 이 화면의 containerStyle 폭만 절반으로 좁힌다 — Button.tsx 상단 주석이
            경고하는 "containerStyle 로 배경색을 흉내내는" 것과는 다르다(폭은 바깥 껍데기의
            레이아웃일 뿐, 안쪽 Pressable 의 색과 부딪히지 않는다).
        */}
        {/* 🔴 네 단추가 같은 무게라 무엇을 먼저 할지 안 보였다. 이 화면에서 할 일은 「후보에 저장」 하나 —
            그것만 주 단추(빨강 한 번)로 전폭, 현장 도구 둘은 그 아래 반씩 연회색, 리뷰는 글자 링크.
            저장한 뒤에는 빼기라 주 단추가 아니다(연회색). */}
        <View style={styles.actions}>
          {/* 🔴 저장했는지 알기 전에는 누를 수 없다 — 위 savedKnown 의 주석(S15P21E201-1644). */}
          <Button label={isSaved ? tx('후보에서 빼기', 'Unsave') : tx('내 여행 후보에 저장', 'Save')} variant={isSaved ? 'tertiary' : 'primary'} disabled={!savedKnown} onPress={() => void toggleSaved()} containerStyle={styles.actionFull} />
          {taxiPlaceId ? <Button label={tx('기사님께 보여주기', 'Show to driver')} variant="tertiary" onPress={() => router.push(`/taxi-card/${taxiPlaceId}`)} containerStyle={styles.actionHalf} /> : null}
          <Button label={tx('한국어로 말하기', 'Speak Korean')} variant="tertiary" onPress={() => setPhraseModalOpen(true)} containerStyle={styles.actionHalf} />
          {taxiPlaceId ? (
            <Pressable accessibilityRole="link" onPress={() => router.push(`/place-reviews/${taxiPlaceId}`)} hitSlop={8} style={({ pressed }) => [styles.reviewLink, pressed && styles.pressed]}>
              <Text weight="bold">{tx('리뷰 보기', 'See reviews')} ›</Text>
            </Pressable>
          ) : null}
          {feedback ? <Text accessibilityLiveRegion="polite" color={color.text.body} style={styles.feedback}>{feedback}</Text> : null}
        </View>
        {consent.prompt}
        </View>
        </View>
      </> : null}

      <PlacePhraseModal visible={phraseModalOpen} onClose={() => setPhraseModalOpen(false)} category={resolved?.apiPlace?.category} />

      {notFound ? <View style={styles.notice} accessibilityRole="alert">
        <Text variant="title" weight="bold">{tx('장소를 찾을 수 없어요', 'Place not found')}</Text>
        {/* 문구와 단추가 같은 곳을 가리킨다(S15P21E201-1680). 전에는 「목록으로 돌아가」라고 하고 단추는 홈으로 갔다.
            들어온 길이 없으면(주소로 바로 연 경우) 뒤로 갈 곳이 없어 홈으로 — 위의 ‹ 단추와 같은 규칙이다. */}
        <Text color={color.text.body}>{router.canGoBack() ? tx('뒤로 가서 다른 장소를 골라 주세요.', 'Go back and choose another place.') : tx('홈에서 다른 장소를 골라 주세요.', 'Choose another place from home.')}</Text>
        <Button label={router.canGoBack() ? tx('뒤로 가기', 'Go back') : tx('홈으로', 'Go home')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/home'))} containerStyle={styles.recoveryButton} />
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
  screen: { backgroundColor: color.canvas },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 154, height: 28 },
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
  // opacity 0.8 을 뽑았다. 이 줄은 공공누리 이용 조건이라
  // 읽힐 수 있어야 한다 — 지켜야 하는 표기를 일부러 흐리게 할 이유가 없다.
  photoCredit: { marginTop: spacing[1] },
  infoRows: { marginTop: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  infoRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  // 이름표는 줄지 않고 값만 준다 — 값(요일별 영업시간)이 길면 이름표가 한 글자 폭까지 눌려 세로로 꺾였다(S15P21E201-1727).
  //    여러 줄이 된 값이 카드 위아래 끝에 닿지 않게 세로 여백도 둔다 — 한 줄이면 minHeight 52 안이라 높이가 그대로다.
  infoLabel: { flexShrink: 0 },
  infoValue: { flexShrink: 1 },
  safetyNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  safetyConfirmed: { gap: spacing[1], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
  notice: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.lg, backgroundColor: color.surface.card },
  noticeCopy: { lineHeight: 22 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], marginTop: spacing[4] }, wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] }, mainColumn: { flex: 1, minWidth: 0 }, asideColumn: { width: 320 },
  // 두 버튼씩 한 줄에 앉힌다(사용자 리포트 — 네 개가 세로로 쌓여 읽기 어려웠다).
  // flexBasis 로 최소 폭을 잡고 flexGrow 로 남는 자리를 채운다 — 홀수 개(리뷰·택시 버튼이
  // 없는 장소)일 때도 마지막 버튼이 어색하게 반쪽만 남지 않고 자연스럽게 늘어난다.
  actionHalf: { flexBasis: '46%', flexGrow: 1 },
  actionFull: { width: '100%' },
  reviewLink: { width: '100%', minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  feedback: { textAlign: 'center', width: '100%' },
  recoveryButton: { marginTop: spacing[2] },
});
