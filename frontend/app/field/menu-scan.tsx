// 22-1 메뉴판 촬영 — 한글 메뉴판을 찍으면 무슨 글자가 적혀 있는지 읽어 준다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { speakAloud } from '@/field/speakAloud';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { findDishImage, type DishMatch } from '@/field/dishImages';
import { describeDish, loadDishImage, DISH_IMAGE_POLL, type Dish } from '@/field/dish';
import { allergenNotice, emptyNotice, scanMenu, unreadNotice, type MenuLine, type MenuScan } from '@/field/menuScan';
import { useI18n } from '@/i18n';
import { LANGUAGE_OPTIONS, toBcp47, type LanguageCode } from '@/i18n/languages';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';

type Phase =
  | { state: 'idle' }
  | { state: 'reading'; photoUri: string }
  | { state: 'done'; photoUri: string; scan: MenuScan }
  | { state: 'failed'; photoUri: string; message: string };

export default function MenuScanScreen() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { width } = useLayout();
  const { accessToken } = useAuth();
  const [phase, setPhase] = useState<Phase>({ state: 'idle' });
  const wide = isAtLeast(width, 'lg');

  const read = async (photoUri: string) => {
    setPhase({ state: 'reading', photoUri });
    const result = await scanMenu(photoUri, accessToken, tx, language);
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
      <Text color={color.text.body}>{tx('사진에 적힌 글자를 읽고, 지금 쓰는 언어로 옮겨 드려요.', 'We read the text on the photo and translate it into your current language.')}</Text>
    </View>

    {/* 보내기 전에 무엇이 어디로 가는지 적는다. 방침에 없는 처리를 하면 우리가 우리
        방침을 어긴다 — 동의 값에서 겪은 그대로다.
    */}
    <View style={styles.privacyCard} accessibilityRole="summary">
      <Text variant="caption" weight="bold">{tx('사진은 글자를 읽는 동안만 쓰이고 저장하지 않아요', 'The photo is used only while reading the text and is not stored')}</Text>
      <Text variant="caption" color={color.text.body}>{tx('글자를 읽어 주는 바깥 서비스로 사진이 한 번 전달돼요. 촬영 위치 정보는 보내기 전에 지워요. 사람 얼굴이나 영수증이 함께 찍히지 않게 해주세요.', 'The photo is sent once to an outside text-reading service. Location data is removed before sending. Please avoid capturing faces or receipts.')}</Text>
    </View>

    {phase.state === 'idle' && <View style={styles.actions}>
      <Button label={tx('사진 찍기', 'Take a photo')} variant="field" onPress={() => void pickFromCamera()} containerStyle={styles.action} />
      <Button label={tx('앨범에서 고르기', 'Choose from album')} variant="tertiary" onPress={() => void pickFromAlbum()} containerStyle={styles.action} />
    </View>}

    {phase.state !== 'idle' && phase.photoUri !== '' && (
      <Image source={{ uri: phase.photoUri }} resizeMode="contain" accessibilityLabel={tx('찍은 메뉴판 사진', 'The menu photo you took')} style={[styles.photo, wide && styles.photoWide]} />
    )}

    {phase.state === 'reading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}>
      <ActivityIndicator color={color.action.primary} />
      <Text variant="title" weight="bold">{tx('글자를 읽고 있어요', 'Reading the text')}</Text>
      <Text color={color.text.body}>{tx('메뉴가 많으면 20초쯤 걸려요. 화면을 켜 둔 채 기다려 주세요.', 'A menu with many dishes takes about 20 seconds. Please keep this screen open.')}</Text>
    </View>}

    {phase.state === 'failed' && <View accessibilityRole="alert" style={styles.stateCard}>
      <Text variant="title" weight="bold">{tx('읽지 못했어요', 'Could not read it')}</Text>
      <Text color={color.text.body}>{phase.message}</Text>
      <Button label={tx('다른 사진으로 다시', 'Try another photo')} variant="tertiary" onPress={() => setPhase({ state: 'idle' })} containerStyle={styles.action} />
    </View>}

    {phase.state === 'done' && <ScanResult scan={phase.scan} onRetry={() => setPhase({ state: 'idle' })} />}
  </Screen>;
}

function ScanResult({ scan, onRetry }: { scan: MenuScan; onRetry: () => void }) {
  const { tx } = useI18n();
  const allergen = allergenNotice(scan, tx);
  const unread = unreadNotice(scan, tx);
  const empty = emptyNotice(scan, tx);
  // 사전에 없거나 앞에 재료가 남으면 null 이다 — 「새우국밥」에 「국밥」 사진이 붙지
  // 않는다. 사진이 한 장도 없는 동안에는 전부 null 이라 화면이 지금과 똑같다.
  const dishes = scan.lines.map((line) => findDishImage(line.text));

  return <View style={styles.result}>
    {/* 알레르기 안내가 제일 위다. 그리고 「찾은 낱말」과 「직접 확인하라」는 함께 온다
        둘을 떼어 놓을 수 없게 한 함수가 같이 낸다.
    */}
    <View style={styles.allergenCard} accessibilityRole="summary">
      <Text variant="title" weight="bold">{allergen.headline}</Text>
      {allergen.words.length > 0 && <View style={styles.wordRow}>
        {allergen.words.map((word) => <View key={word} style={styles.word}><Text variant="caption" weight="bold" color={color.state.danger}>{word}</Text></View>)}
      </View>}
      <Text color={color.text.body}>{allergen.caution}</Text>
    </View>

    {unread && <View style={styles.unreadCard} accessibilityRole="summary"><Text variant="caption" weight="bold">{unread}</Text></View>}

    {empty
      ? <View style={styles.stateCard}><Text variant="title" weight="bold">{empty}</Text></View>
      : <View style={styles.lines}>
          <Text variant="caption" weight="bold" color={color.text.muted}>{tx('사진에서 읽은 글자 · 추정', 'Text read from the photo · estimated')}</Text>
          {/* 예시 사진도 하나의 주장이다. 한 장이라도 붙는 날에만 이 줄이 뜬다
              「이 식당의 음식」으로 읽히면 축제 사진에서 고친 것과 같은 거짓말이 된다.
          */}
          {dishes.some((dish) => dish !== null) && (
            <View style={styles.exampleNotice}><Text variant="caption" weight="bold">{tx('사진은 예시예요 — 이 식당의 음식이 아니에요', 'Photos are examples — not this restaurant’s dishes')}</Text></View>
          )}
          {scan.lines.map((line, index) => (
            <MenuLineRow key={`${index}-${line.text}`} line={line} bundled={dishes[index]} />
          ))}
        </View>}

    <Button label={tx('다른 메뉴판 찍기', 'Scan another menu')} variant="tertiary" onPress={onRetry} containerStyle={styles.action} />
  </View>;
}

/** 그 언어를 쓰는 사람이 읽을 수 있는 이름 — 「한국어」·「日本語」·「繁體中文」. */
function endonymOf(language: LanguageCode): string {
  return LANGUAGE_OPTIONS.find((option) => option.code === language)?.endonym ?? language;
}

/**
 * 소리내어 읽어 주는 버튼 — S15P21E201-1295.
 *
 * 🔴 **한국어와 자기 언어가 둘 다 필요하다.** 식당에서 직원에게 들려주려면 한국어가
 * 필요하고, 자기가 무슨 음식인지 알려면 자기 언어가 필요하다. 하나만 두면 둘 중 하나는
 * 못 한다.
 *
 * 🔴 **버튼 이름을 그 언어로 적는다.** 「내 언어로 듣기」라고 적으면 그 언어를 못 읽는
 * 사람에게는 아무 말도 아니다 — 이 화면을 쓰는 사람이 바로 그 사람이다.
 *
 * 🔴 **읽는 음성도 그 언어로 맞춘다.** 영어를 한국어 음성으로 읽으면 상대가 못 알아듣는다
 * (`speakAloud` 의 javadoc 이 같은 말을 한다).
 */
function ListenButtons({ korean, translated }: { korean: string; translated: string }) {
  const { tx, language } = useI18n();
  // 번역이 원문과 같으면(한국어 사용자이거나 번역이 없는 줄) 같은 것을 두 번 그리지 않는다.
  const onlyKorean = language === 'ko' || translated === '' || translated === korean;

  const button = (label: string, text: string, bcp47: string, key: string) => (
    <Pressable
      key={key}
      accessibilityRole="button"
      accessibilityLabel={tx(`${text} ${label}로 듣기`, `Hear ${text} in ${label}`)}
      onPress={() => speakAloud(text, { language: bcp47 })}
      style={({ pressed }) => [styles.speak, pressed && styles.pressed]}
    >
      <Text variant="caption" weight="bold" color={color.brand.navy}>{label}</Text>
    </Pressable>
  );

  if (onlyKorean) {
    return button(tx('듣기', 'Listen'), korean, 'ko-KR', 'ko');
  }
  return <View style={styles.listenRow}>
    {button(endonymOf('ko'), korean, 'ko-KR', 'ko')}
    {button(endonymOf(language), translated, toBcp47(language), 'mine')}
  </View>;
}

/**
 * 메뉴 한 줄.
 *
 * 🔴 음식 줄과 그렇지 않은 줄을 다르게 그린다. `name` 이 빈 줄은 가게 이름이나
 * 안내문이라 — 그것을 음식처럼 그리면 「※ 모든 메뉴에 공깃밥이 포함됩니다」의 설명과
 * 그림을 만들게 된다.
 */
function MenuLineRow({ line, bundled }: { line: MenuLine; bundled: DishMatch | null }) {
  const { tx } = useI18n();
  const [open, setOpen] = useState(false);
  const isFood = line.name !== '';

  // 🔴 음식 줄에서는 translatedText 를 안 쓴다. 그 값에는 가격이 들어 있어서
  //    (「Pork and rice soup 9,000 won」) 가격 칸과 함께 그리면 같은 값이 두 번 보인다.
  //    서버가 그래서 translatedName 을 따로 준다 — S15P21E201-1271.
  const heading = isFood ? (line.translatedName || line.name) : line.translatedText;
  const original = isFood ? line.name : line.text;

  return <View style={styles.lineBlock}>
    <View style={styles.line}>
      {bundled && <Image source={bundled.image.asset} resizeMode="cover" accessibilityLabel={tx(`${bundled.key} 예시 사진`, `Example photo of ${bundled.key}`)} style={styles.dishThumb} />}
      <View style={styles.lineCopy}>
        <Text>{heading}</Text>
        {/* 같은 글자를 두 번 그리지 않는다 — 한국어를 골랐거나 옛 앱 빌드면 둘이 같다. */}
        {heading !== original && <Text variant="caption" color={color.text.muted}>{original}</Text>}
        {line.allergenWords.length > 0 && <Text variant="caption" color={color.state.danger}>{line.allergenWords.join(' · ')}</Text>}
      </View>
      {/* 가격은 사진에서 읽은 그대로다 — 숫자로 바꾸거나 통화를 붙이지 않는다. */}
      {line.price !== '' && <View style={styles.price}><Text weight="bold">{line.price}</Text></View>}
      {/* 우리가 이미 보여주고 있는 글자를 그대로 소리내 준다 — 지어내는 것이 없다.
          음식 줄에서는 이름만 읽는다. 가격까지 읽으면 가리키는 데 방해가 된다. */}
      <ListenButtons korean={original} translated={heading} />
    </View>

    {/* 🔴 출처는 줄 안이 아니라 아래 전체 폭에 둔다. 가격 칸이 생기면서 글자 칸이
        좁아져 「관광사진갤러 / 리」로 잘렸다 — 띄워 보고 알았다. 출처를 줄이거나 빼는
        것은 답이 아니다(dishImages.ts: 「출처를 한 줄로 못 적는 사진은 안 쓴다」). */}
    {/* 🔴 일본어 화면에 「Example · 한국관광공사 관광사진갤러리」로, 반은 영어 반은
        한국어로 뜬다. 문구에 값이 끼면 번역표의 키가 실행할 때마다 달라져 **영원히 못
        찾기** 때문이다. 고치려면 번역표에 줄을 넣어야 하는데 그 파일을 지금 다른
        사람이 잡고 있다 — 값이 끼어든 tx 208곳과 함께 따로 잡는다(S15P21E201-1335). */}
    {bundled && <Text variant="caption" color={color.text.muted} style={styles.credit}>{tx(`예시 · ${bundled.image.source}`, `Example · ${bundled.image.source}`)}</Text>}

    {isFood && <View style={styles.askRow}>
      <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={() => setOpen((was) => !was)} style={({ pressed }) => [styles.askChip, pressed && styles.pressed]}>
        <Text variant="caption" weight="bold" color={color.brand.navy}>
          {open ? tx('접기', 'Hide') : tx('이건 어떤 음식인가요?', 'What is this dish?')}
        </Text>
      </Pressable>
    </View>}

    {isFood && open && <DishPanel name={line.name} bundled={bundled} />}
  </View>;
}

/**
 * 음식 하나의 설명과 그림 — S15P21E201-1276 (서버는 -1272).
 *
 * 🔴 여기 있는 것은 메뉴판에서 «읽은» 것이 아니다. 설명은 모델이 «아는» 것이고 그림은
 * 모델이 «만든» 것이다. 위쪽 이름·가격·알레르기 낱말은 전부 «사진에서 읽은» 것이다.
 * 둘을 같은 무게로 그리면 사용자는 출처를 못 가르고, 모델이 지어낸 말을 메뉴판에 적힌
 * 것으로 읽는다. 그래서 이 칸은 안쪽으로 들여 그리고 출처를 매번 적는다.
 *
 * 🔴 알레르기 판단에 이 칸을 쓰지 않는다. 그 통로는 화면 맨 위의 칸 하나뿐이다.
 */
function DishPanel({ name, bundled }: { name: string; bundled: DishMatch | null }) {
  const { tx, language } = useI18n();
  const { accessToken } = useAuth();
  const [dish, setDish] = useState<Dish | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [gaveUp, setGaveUp] = useState(false);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; };
  }, []);

  useEffect(() => {
    void (async () => {
      const result = await describeDish(name, accessToken, tx, language);
      if (!alive.current) return;
      if (result.state === 'success') setDish(result.dish);
      else setFailure(result.message);
    })();
  }, [name, accessToken, language]);

  // 🔴 그림은 10초가 넘게 걸린다. 다 될 때까지 몇 초마다 물어보되 끝이 있어야 한다 —
  //    화면을 떠나면 멈추고, 아무리 늦어도 포기한다. 안 그러면 켜 둔 사람의 배터리와
  //    통신이 계속 나간다.
  useEffect(() => {
    // 🔴 PENDING 뿐 아니라 READY 도 여기를 지난다. 웹에서는 바이트를 직접 받아야
    //    그릴 수 있어서, 「이미 다 됐다」도 한 번은 받아 와야 한다.
    if (!dish || !dish.imageId || !accessToken) return;
    if (dish.imageStatus !== 'PENDING' && dish.imageStatus !== 'READY') return;
    const imageId = dish.imageId;
    const controller = new AbortController();
    const startedAt = Date.now();
    let timer: ReturnType<typeof setTimeout>;
    let revoke: (() => void) | null = null;

    const askOnce = async () => {
      const verdict = await loadDishImage(imageId, accessToken, controller.signal);
      if (!alive.current || controller.signal.aborted) {
        if (verdict.state === 'ready') verdict.revoke?.();
        return;
      }
      if (verdict.state === 'ready') { revoke = verdict.revoke; setImageUri(verdict.uri); return; }
      // 🔴 'gone' 은 「그만 물어봐」다. 'pending' 과 같게 다루면 영원히 묻는다.
      if (verdict.state === 'gone') { setGaveUp(true); return; }
      if (Date.now() - startedAt > DISH_IMAGE_POLL.giveUpAfterMs) { setGaveUp(true); return; }
      timer = setTimeout(() => void askOnce(), DISH_IMAGE_POLL.everyMs);
    };
    // 처음 한 번은 기다리지 않고 바로 묻는다 — 이미 만들어져 있으면 그 자리에서 뜬다.
    void askOnce();

    return () => { controller.abort(); clearTimeout(timer); revoke?.(); };
  }, [dish, accessToken]);

  if (failure) {
    return <View style={styles.dishPanel} accessibilityRole="alert">
      <Text variant="caption" color={color.text.body}>{failure}</Text>
    </View>;
  }

  if (!dish) {
    return <View style={styles.dishPanel} accessibilityLiveRegion="polite">
      <ActivityIndicator color={color.action.primary} />
      <Text variant="caption" color={color.text.muted}>{tx('어떤 음식인지 알아보고 있어요', 'Looking up this dish')}</Text>
    </View>;
  }

  // 🔴 사전에 진짜 사진이 있으면 그것을 쓴다. 만든 그림보다 낫고 값도 안 든다.
  const showGenerated = bundled === null && imageUri !== null;
  const stillPainting = bundled === null && dish.imageId !== null && imageUri === null && !gaveUp;
  // 🔴 한도는 그림 자리에만 그린다. 설명은 함께 와 있고 멀쩡하다 — S15P21E201-1294.
  //    이때 imageId 가 null 이라 위 stillPainting 은 저절로 거짓이다.
  const quotaSpent = bundled === null && dish.imageStatus === 'RATE_LIMITED';

  return <View style={styles.dishPanel}>
    {dish.description !== ''
      ? <>
          <Text variant="caption" color={color.text.body}>{dish.description}</Text>
          {/* 🔴 출처를 매번 적는다. 위의 이름·가격은 사진에서 읽은 것이고 이 줄은 아니다. */}
          <Text variant="caption" color={color.text.muted}>{tx('사진에서 읽은 것이 아니라 AI 가 덧붙인 설명이에요', 'Added by AI — not read from the photo')}</Text>
        </>
      : <Text variant="caption" color={color.text.muted}>{tx('이 음식은 아직 설명해 드릴 수 없어요.', 'We cannot describe this dish yet.')}</Text>}

    {quotaSpent && <Text variant="caption" color={color.text.muted}>
      {tx('그림은 조금 뒤에 다시 만들 수 있어요. 설명은 그대로 보실 수 있어요.',
        'Pictures can be made again in a moment. The description above still works.')}
    </Text>}

    {stillPainting && <View style={styles.dishImageWaiting} accessibilityLiveRegion="polite">
      <ActivityIndicator color={color.action.primary} />
      <Text variant="caption" color={color.text.muted}>{tx('그림을 그리고 있어요 (10초쯤 걸려요)', 'Drawing a picture (takes about 10 seconds)')}</Text>
    </View>}

    {showGenerated && <View style={styles.dishImageBlock}>
      <Image
        source={{ uri: imageUri! }}
        resizeMode="cover"
        accessibilityLabel={tx(`${name} 을(를) AI 가 그린 그림`, `An AI-drawn picture of ${name}`)}
        style={styles.dishImage}
      />
      {/* 🔴 이 문구는 그림에 붙어 있어야 한다. 접어 두거나 작게 쓰면 안 붙인 것과 같다.
          사전의 진짜 사진에 붙는 「예시예요」와 같은 말을 쓰지 않는다 — 그린 그림과
          찍은 사진은 다른 것이고, 한 문구로 뭉개면 사용자는 그린 것을 사진으로 본다. */}
      <Text variant="caption" weight="bold">{tx('AI 가 그린 그림이에요 — 실제 나오는 음식과 달라요', 'Drawn by AI — the real dish will look different')}</Text>
    </View>}
  </View>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
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
  word: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.state.dangerBg },
  exampleNotice: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  dishThumb: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: color.surface.tint },
  unreadCard: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  lines: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  lineBlock: { paddingVertical: spacing[1] },
  line: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingTop: spacing[2] },
  price: { flexShrink: 0 },
  credit: { marginTop: spacing[1] },
  // 44 는 손가락이 닿는 최소 크기다 — 칩 자체를 작게 만들지 않고 감싸는 칸으로 맞춘다.
  askRow: { minHeight: 44, justifyContent: 'center', alignItems: 'flex-start' },
  // 🔴 flexDirection 을 안 적으면 React Native 는 세로로 쌓는다. 이름이 listenRow 인데
  //    실제로는 세로로 쌓여 줄마다 키가 두 배가 되고 이름 칸이 좁아져 「キムチもやし
  //    クッパ」처럼 잘렸다 — S15P21E201-1335. 한국어를 고르면 버튼이 하나라 안 보였다.
  listenRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1], alignItems: 'center', justifyContent: 'flex-end' },
  askChip: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  dishPanel: { gap: spacing[2], marginLeft: spacing[3], marginBottom: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  dishImageBlock: { gap: spacing[1] },
  dishImage: { width: '100%', height: 160, borderRadius: radius.md, backgroundColor: color.surface.card },
  dishImageWaiting: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  lineCopy: { flex: 1, minWidth: 0, gap: spacing[1] },
  speak: { minHeight: 44, minWidth: 56, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.tint },
});
