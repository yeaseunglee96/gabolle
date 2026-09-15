// 22-1 메뉴판 촬영 — 한글 메뉴판을 찍으면 무슨 글자가 적혀 있는지 읽어 준다.
//
// S15P21E201-329 (스토리 -86). 이 화면은 22 현장 도구·번역에서 "번역 API 업체가 아직
// 안 정해져서"(S15P21E201-77) 통째로 빠져 있던 것이다. GMS(교육용 API 중계)가 살아 있는
// 것을 2026-09-16 에 확인해 그 막힘이 없어졌다.
//
// 🔴 이 화면이 지키는 규칙은 하나다 — **읽은 것만 말하고, 「없다」는 말하지 않는다.**
//    문구를 정하는 판단은 전부 src/field/menuScan.ts 의 함수에 있고 시험이 붙들고 있다.
//    여기서 조건문으로 문구를 만들지 않는다.
//
// 🔴 티켓이 요구한 것 중 셋은 일부러 안 했다. 안 한 이유를 남긴다.
//    · **맵기 배지** — 사진에 안 적혀 있으면 모델이 지어내야 한다. 먹는 것 앞에서 지어내지 않는다
//    · **알레르기 "경고"** — 경고는 없을 때 "없다"를 뜻하게 된다. 찾은 낱말만 나열한다
//    · **사진 위에 겹쳐 보여주기** — 글자 좌표가 필요한데 계약에 없다. 목록으로만 그린다
import { useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import * as Speech from 'expo-speech';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { findDishImage } from '@/field/dishImages';
import { allergenNotice, emptyNotice, scanMenu, unreadNotice, type MenuScan } from '@/field/menuScan';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

type Phase =
  | { state: 'idle' }
  | { state: 'reading'; photoUri: string }
  | { state: 'done'; photoUri: string; scan: MenuScan }
  | { state: 'failed'; photoUri: string; message: string };

export default function MenuScanScreen() {
  const router = useRouter();
  const { tx } = useI18n();
  const { width } = useLayout();
  const { accessToken } = useAuth();
  const [phase, setPhase] = useState<Phase>({ state: 'idle' });
  const wide = isAtLeast(width, 'lg');

  const read = async (photoUri: string) => {
    setPhase({ state: 'reading', photoUri });
    const result = await scanMenu(photoUri, accessToken, tx);
    setPhase(result.state === 'success'
      ? { state: 'done', photoUri, scan: result.scan }
      : { state: 'failed', photoUri, message: result.message });
  };

  const pickFromCamera = async () => {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      setPhase({ state: 'failed', photoUri: '', message: tx('카메라를 쓰려면 권한이 필요해요. 설정에서 허용해 주세요.', 'Camera permission is needed. Please allow it in settings.') });
      return;
    }
    const result = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled) return;
    void read(result.assets[0].uri);
  };

  const pickFromAlbum = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled) return;
    void read(result.assets[0].uri);
  };

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/field/translate')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable>
      <BrandLogoLink href="/home" imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>

    <View style={styles.heading}>
      <Eyebrow>{tx('현장 도구', 'Field tool')}</Eyebrow>
      <Text variant="display" weight="bold">{tx('메뉴판을 찍어 보세요', 'Take a photo of the menu')}</Text>
      <Text color={color.text.body}>{tx('사진에 적힌 글자를 읽어 드려요. 번역이 아니라 읽기예요.', 'We read the text printed on the photo — reading, not translating.')}</Text>
    </View>

    {/* 🔴 보내기 전에 무엇이 어디로 가는지 적는다. 방침에 없는 처리를 하면 우리가 우리
        방침을 어긴다 — 동의 값에서 겪은 그대로다(S15P21E201-735). */}
    <View style={styles.privacyCard} accessibilityRole="summary">
      <Text variant="caption" weight="bold">{tx('사진은 글자를 읽는 동안만 쓰이고 저장하지 않아요', 'The photo is used only while reading the text and is not stored')}</Text>
      <Text variant="caption" color={color.text.body}>{tx('글자를 읽어 주는 바깥 서비스로 사진이 한 번 전달돼요. 촬영 위치 정보는 보내기 전에 지워요. 사람 얼굴이나 영수증이 함께 찍히지 않게 해주세요.', 'The photo is sent once to an outside text-reading service. Location data is removed before sending. Please avoid capturing faces or receipts.')}</Text>
    </View>

    {phase.state === 'idle' && <View style={styles.actions}>
      <Button label={tx('사진 찍기', 'Take a photo')} onPress={() => void pickFromCamera()} containerStyle={styles.action} />
      <Button label={tx('앨범에서 고르기', 'Choose from album')} variant="ghost" onPress={() => void pickFromAlbum()} containerStyle={styles.action} />
    </View>}

    {phase.state !== 'idle' && phase.photoUri !== '' && (
      <Image source={{ uri: phase.photoUri }} resizeMode="contain" accessibilityLabel={tx('찍은 메뉴판 사진', 'The menu photo you took')} style={[styles.photo, wide && styles.photoWide]} />
    )}

    {phase.state === 'reading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}>
      <ActivityIndicator color={color.brand.orange} />
      <Text variant="title" weight="bold">{tx('글자를 읽고 있어요', 'Reading the text')}</Text>
      <Text color={color.text.body}>{tx('사진이 클수록 조금 더 걸려요.', 'Larger photos take a little longer.')}</Text>
    </View>}

    {phase.state === 'failed' && <View accessibilityRole="alert" style={styles.stateCard}>
      <Text variant="title" weight="bold">{tx('읽지 못했어요', 'Could not read it')}</Text>
      <Text color={color.text.body}>{phase.message}</Text>
      <Button label={tx('다른 사진으로 다시', 'Try another photo')} variant="ghost" onPress={() => setPhase({ state: 'idle' })} containerStyle={styles.action} />
    </View>}

    {phase.state === 'done' && <ScanResult scan={phase.scan} onRetry={() => setPhase({ state: 'idle' })} />}
  </Screen>;
}

function ScanResult({ scan, onRetry }: { scan: MenuScan; onRetry: () => void }) {
  const { tx } = useI18n();
  const allergen = allergenNotice(scan, tx);
  const unread = unreadNotice(scan, tx);
  const empty = emptyNotice(scan, tx);
  // 🔴 사전에 없거나 앞에 재료가 남으면 null 이다 — 「새우국밥」에 「국밥」 사진이 붙지
  //    않는다. 사진이 한 장도 없는 동안에는 전부 null 이라 화면이 지금과 똑같다.
  const dishes = scan.lines.map((line) => findDishImage(line.text));

  return <View style={styles.result}>
    {/* 🔴 알레르기 안내가 제일 위다. 그리고 「찾은 낱말」과 「직접 확인하라」는 함께 온다 —
        둘을 떼어 놓을 수 없게 한 함수가 같이 낸다. */}
    <View style={styles.allergenCard} accessibilityRole="summary">
      <Text variant="title" weight="bold">{allergen.headline}</Text>
      {allergen.words.length > 0 && <View style={styles.wordRow}>
        {allergen.words.map((word) => <View key={word} style={styles.word}><Text variant="caption" weight="bold" color={color.text.onAction}>{word}</Text></View>)}
      </View>}
      <Text color={color.text.body}>{allergen.caution}</Text>
    </View>

    {unread && <View style={styles.unreadCard} accessibilityRole="summary"><Text variant="caption" weight="bold">{unread}</Text></View>}

    {empty
      ? <View style={styles.stateCard}><Text variant="title" weight="bold">{empty}</Text></View>
      : <View style={styles.lines}>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('사진에서 읽은 글자 · 추정', 'Text read from the photo · estimated')}</Text>
          {/* 🔴 예시 사진도 하나의 주장이다. 한 장이라도 붙는 날에만 이 줄이 뜬다 —
              「이 식당의 음식」으로 읽히면 축제 사진에서 고친 것과 같은 거짓말이 된다. */}
          {dishes.some((dish) => dish !== null) && (
            <View style={styles.exampleNotice}><Text variant="caption" weight="bold">{tx('사진은 예시예요 — 이 식당의 음식이 아니에요', 'Photos are examples — not this restaurant’s dishes')}</Text></View>
          )}
          {scan.lines.map((line, index) => <View key={`${index}-${line.text}`} style={styles.line}>
            {dishes[index] && <Image source={dishes[index]!.image.asset} resizeMode="cover" accessibilityLabel={tx(`${dishes[index]!.key} 예시 사진`, `Example photo of ${dishes[index]!.key}`)} style={styles.dishThumb} />}
            <View style={styles.lineCopy}>
              <Text>{line.text}</Text>
              {line.allergenWords.length > 0 && <Text variant="caption" color={color.brand.orange}>{line.allergenWords.join(' · ')}</Text>}
              {dishes[index] && <Text variant="caption" color={color.text.muted}>{tx(`예시 · ${dishes[index]!.image.source}`, `Example · ${dishes[index]!.image.source}`)}</Text>}
            </View>
            {/* 우리가 이미 보여주고 있는 글자를 그대로 소리내 준다 — 지어내는 것이 없다.
                식당에서 손가락으로 가리키는 것보다 이쪽이 빠르다. */}
            <Pressable accessibilityRole="button" accessibilityLabel={tx(`${line.text} 한국어로 듣기`, `Hear ${line.text} in Korean`)} onPress={() => Speech.speak(line.text, { language: 'ko-KR' })} style={({ pressed }) => [styles.speak, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('듣기', 'Listen')}</Text>
            </Pressable>
          </View>)}
        </View>}

    <Button label={tx('다른 메뉴판 찍기', 'Scan another menu')} variant="ghost" onPress={onRetry} containerStyle={styles.action} />
  </View>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[4] },
  privacyCard: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint, marginBottom: spacing[4] },
  actions: { gap: spacing[2] },
  action: { width: '100%' },
  photo: { width: '100%', height: 220, borderRadius: radius.lg, backgroundColor: color.surface.card, marginBottom: spacing[4] },
  photoWide: { height: 320 },
  stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'flex-start' },
  result: { gap: spacing[4] },
  allergenCard: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.warningBg },
  wordRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  word: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.orange },
  exampleNotice: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  dishThumb: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: color.surface.tint },
  unreadCard: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  lines: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  line: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[2] },
  lineCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  speak: { minHeight: 44, minWidth: 56, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.tint },
});
