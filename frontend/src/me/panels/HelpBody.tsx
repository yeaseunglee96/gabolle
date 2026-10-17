// 도움말·문의 본문 — 화면(`app/help.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 머리(뒤로·로고·제목·설명)는 화면만 그린다. 패널은 껍데기가 이미 제목을 그린다.
import { useEffect, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { LOCAL_PICK_PLACES } from '@/home/homeCuration';

import { requestAllGuidesAgain, requestHomeCoachAgain, requestScreenGuideAgain, screenGuidesSeen, type ScreenGuide } from '@/onboarding/firstRun';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

const FAQS = [
  ['가볼래는 어떤 앱인가요?', 'What is GABOLLE?', '취향·예산·이동 조건을 반영해 부산 일정을 만들고, 장소 저장과 현장 말하기까지 이어주는 여행 앱이에요.', 'GABOLLE builds a Busan itinerary from your tastes, budget, and mobility needs, then helps with saved places and useful phrases.'],
  ['로그인하지 않아도 쓸 수 있나요?', 'Can I use it without signing in?', '홈·로컬 탐색·장소 정보는 로그인 없이 볼 수 있어요. 일정을 만들고 저장하려면 로그인해 주세요.', 'Home, local picks, and place pages work without signing in. Sign in to create and keep itineraries.'],
  ['일정이 바로 만들어지지 않아요.', 'My itinerary is not ready yet.', '내 여행에서 준비 상태를 확인해 주세요. 실패로 표시되면 다시 만들기를 누르고, 같은 문제가 반복되면 화면의 요청 ID와 함께 문의해 주세요.', 'Check its status in My trips. If it failed, retry; if it repeats, contact us with the request ID shown on screen.'],
  ['위치 권한을 거부해도 되나요?', 'Can I deny location access?', '네. 출발지나 장소를 직접 검색할 수 있어요. 위치 권한은 주변 장소와 방문 확인을 더 편하게 쓸 때만 필요해요.', 'Yes. You can search for a starting point or place manually. Location access only improves nearby results and visit check-ins.'],
  ['내 정보는 어떻게 삭제하나요?', 'How do I delete my data?', '마이페이지 → 프로필 → 회원 탈퇴에서 삭제되는 항목을 확인한 뒤 계정을 영구 삭제할 수 있어요.', 'Go to My page → Profile → Delete account to review and permanently remove your account data.'],
] as const;

/** 처음 쓰는 분께 — 세 걸음이면 첫 여행이 된다(2026-10-17 사용자 지시: 도움말을 쓰기 쉽게 전면 개편). */
const FIRST_STEPS: ReadonlyArray<[string, string, string, string]> = [
  ['여행 만들기', 'Plan a trip', '출발지·날짜·인원만 고르면 AI가 부산 일정을 짜요.', 'Pick a start point, dates, and party size — AI plans your Busan trip.'],
  ['일정 확인', 'Check your plan', '내 여행에서 장소와 장소 사이 이동 길을 봐요.', 'Open My trips to see places and how to get between them.'],
  ['여행 중엔 동백이', 'Dongbaek on the go', '번역·통역·편의시설이 오른쪽 아래 동백이에 있어요.', 'Translation, interpreting, and nearby facilities live in Dongbaek at the bottom right.'],
];

/** 기능별 사용법 — 무엇을 어디서 어떻게. 바로 가기가 있는 것은 그 화면으로 데려간다. */
const FEATURES: ReadonlyArray<{ title: [string, string]; body: [string, string]; path?: string }> = [
  { title: ['여행 만들기', 'Plan a trip'], body: ['홈의 「여행 계획 시작해 보세요」나 아래 가운데 + 단추를 눌러요. 출발지·날짜·인원을 고르고 취향 질문에 원하는 만큼만 답하면 AI가 일정을 만들어요. 장소를 직접 고르고 싶으면 「스스로 일정 계획하기」를 눌러요.', 'Tap “Start planning” on Home or the + button at the bottom. Pick a start point, dates, and party size, answer as many taste questions as you like, and AI builds the plan. To choose places yourself, tap “Plan it myself”.'], path: '/plan' },
  { title: ['일정 보기와 길 안내', 'Your plan and directions'], body: ['아래 「내 여행」에서 여행을 열어요. 장소 사이의 이동 칸을 누르면 대중교통·자동차·걷기 길과 걸리는 시간이 나오고, 가파른 길은 색으로 알려 줘요.', 'Open a trip from My trips. Tap the row between two places for transit, car, or walking directions and times; steep stretches are colored.'], path: '/trips' },
  { title: ['동행 초대와 공유', 'Invite and share'], body: ['여행 화면의 「동행 초대」로 친구를 불러 함께 일정을 보고, 「공유」로 일정을 링크로 보내요. 「여행 경비」에는 지출을 적어요.', 'Use “Invite” on a trip to plan with friends and “Share” to send it as a link. Log spending under “Trip expenses”.'], path: '/trips' },
  { title: ['로컬 탐색', 'Local picks'], body: ['홈의 로컬 탐색에서 축제·맛집·카페·야경 같은 갈래를 골라 부산 사람들이 가는 곳을 찾아요. 맛집·카페는 구와 동으로 좁힐 수 있어요.', 'Pick a theme such as festivals, restaurants, cafés, or night views under Local picks on Home. Restaurants and cafés can be narrowed by district.'], path: '/explore' },
  { title: ['장소 저장', 'Save places'], body: ['장소 화면에서 「내 여행 후보에 저장」을 누르면 마이페이지 › 저장한 장소에 모여요.', 'Tap “Save” on a place page and it is kept under My page › Saved places.'] },
  { title: ['동백이 AI 챗봇', 'Dongbaek AI chat'], body: ['오른쪽 아래 동백이를 누르고 「AI 챗봇」을 골라요. 어느 언어로 물어도 맛집·길·일정을 답해 줘요.', 'Tap Dongbaek at the bottom right and choose “AI chat”. Ask about food, routes, or plans in any language.'], path: '/chat' },
  { title: ['메뉴판 번역', 'Menu translation'], body: ['동백이 메뉴 › 메뉴판 번역에서 메뉴판을 찍으면 음식 이름과 설명을 내 언어로 읽어 줘요.', 'In the Dongbaek menu › Menu translation, snap a menu and read dishes in your language.'], path: '/field/menu-scan' },
  { title: ['통역', 'Interpreter'], body: ['동백이 메뉴 › 통역에서 내 언어로 말하면 한국어로 바꿔 소리로 들려줘요.', 'In the Dongbaek menu › Interpreter, speak in your language and hear it in Korean.'], path: '/field/speak' },
  { title: ['택시 기사님께 보여주기', 'Show to a taxi driver'], body: ['장소 화면의 「기사님께 보여주기」를 누르면 한국어 이름과 주소가 크게 떠요. 화면을 기사님께 보여 주면 돼요.', 'Tap “Show to driver” on a place page to show its Korean name and address in large type.'] },
  { title: ['근처 편의시설', 'Nearby facilities'], body: ['동백이 메뉴 › 근처 편의시설에서 주차장·약국·화장실·짐보관을 지도로 찾아요. 위치를 허락하지 않으면 서면 기준으로 보여 줘요.', 'In the Dongbaek menu › Nearby facilities, find parking, pharmacies, restrooms, and lockers on a map. Without location access it centers on Seomyeon.'], path: '/field/nearby' },
  { title: ['긴급 도움', 'Emergency help'], body: ['급할 때는 긴급 도움에서 119(구급)·112(경찰)에 바로 전화하고, 분실물 찾는 곳도 볼 수 있어요.', 'In an emergency, call 119 (ambulance) or 112 (police) from Emergency help and find lost-and-found offices.'], path: '/emergency' },
  { title: ['여행 기록과 피드', 'Travel records and feed'], body: ['아래 「피드」에서 다른 여행자의 부산 기록을 보고, 여행 화면의 「기록 남기기」로 내 사진과 이야기를 남겨요.', 'Browse other travelers’ Busan records in Feed, and post your own photos and notes with “Write a record” on a trip.'], path: '/feed' },
];

/** 약관·개인정보 처리방침(legalContent)에 적힌 문의 주소와 같은 값. */
const SUPPORT_EMAIL_FALLBACK = 'gabolle.support@gmail.com';

/** 화면별 첫 안내 — 놓친 사람이 다시 켠다(UI 캔버스 ㉔-4, S15P21E201-1885). 제목은 안내 말풍선의 제목 그대로. */
const GUIDES: ReadonlyArray<{ key: ScreenGuide; title: [string, string]; where: [string, string]; path: string }> = [
  { key: 'taxi', title: ['말이 안 통해도 택시를 탈 수 있어요', 'Take a taxi even without Korean'], where: ['장소 정보 · 기사님께 보여주기', 'Place page · Show to driver'], path: `/place/${LOCAL_PICK_PLACES.NIGHT_VIEW}` },
  { key: 'assistant', title: ['여행 중에 막히면 여기예요', 'Stuck on the road? Start here'], where: ['오른쪽 아래 동백이 메뉴 · 눌러서 열면 떠요', 'Dongbaek menu, bottom right · shows when you open it'], path: '/home' },
  { key: 'legs', title: ['장소 사이 이 칸이 길 안내예요', 'This row between places is your directions'], where: ['여행 일정 · 장소 사이 이동 칸 · 여행을 열면 떠요', 'Trip plan · row between places · shows when you open a trip'], path: '/trips' },
];

export function HelpBody() {
  const router = useRouter();
  const { tx } = useI18n();
  const [open, setOpen] = useState<number | null>(0);
  const [seen, setSeen] = useState<Record<ScreenGuide, boolean> | null>(null);
  const [guideNote, setGuideNote] = useState<string | null>(null);
  useEffect(() => { let alive = true; void screenGuidesSeen().then((next) => { if (alive) setSeen(next); }); return () => { alive = false; }; }, []);
  // 「다시 보기」는 그 화면으로 바로 데려간다 — 전에는 「다음에 들어가면 떠요」 한 줄만 남겨, 어디로 가야 하는지 사람이 찾아야 했다.
  const again = async (guide: ScreenGuide, path: string) => {
    await requestScreenGuideAgain(guide);
    setSeen((prev) => (prev ? { ...prev, [guide]: false } : prev));
    router.push(path as never);
  };
  const [openFeature, setOpenFeature] = useState<number | null>(null);
  const allAgain = async () => {
    await requestAllGuidesAgain();
    setSeen((prev) => (prev ? Object.fromEntries(Object.keys(prev).map((key) => [key, false])) as Record<ScreenGuide, boolean> : prev));
    setGuideNote(tx('처음 안내를 전부 다시 켰어요. 화면에 처음 들어갈 때 한 번씩 떠요.', 'All first-time guides are back on. Each shows once when you open its screen.'));
  };
  // 🔴 빌드 설정이 비면 약관·방침에 이미 적힌 문의 주소를 쓴다(S15P21E201-1867). 전에는 설정이 없는 빌드(배포 웹)에서
  //    「문의 수신 주소를 준비하고 있어요. 그 전에는 팀에 전달해 주세요」가 사용자에게 나왔다 — 사용자는 팀을 모른다.
  const supportEmail = process.env.EXPO_PUBLIC_SUPPORT_EMAIL?.trim() || SUPPORT_EMAIL_FALLBACK;
  const contact = async () => {
    if (!supportEmail) return;
    const subject = encodeURIComponent(tx('[가볼래] 앱 문의', '[GABOLLE] App support'));
    const body = encodeURIComponent(tx('문의 내용:\n\n문제가 발생한 화면:\n\n재현 순서:\n', 'Question:\n\nScreen where it happened:\n\nSteps to reproduce:\n'));
    await Linking.openURL(`mailto:${supportEmail}?subject=${subject}&body=${body}`);
  };

  return <>

    <View style={styles.startCard}>
      <Text variant="title" weight="bold">{tx('처음 쓰는 분께', 'New to GABOLLE?')}</Text>
      {FIRST_STEPS.map(([koTitle, enTitle, koBody, enBody], index) => (
        <View key={koTitle} style={styles.stepRow}>
          <View style={styles.stepNumber}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
          <View style={styles.stepCopy}><Text weight="bold">{tx(koTitle, enTitle)}</Text><Text variant="caption" color={color.text.body}>{tx(koBody, enBody)}</Text></View>
        </View>
      ))}
      <Button label={tx('첫 여행 만들기', 'Plan my first trip')} variant="primary" onPress={() => router.push('/plan' as never)} />
    </View>

    <View style={styles.tourCard}><View style={styles.tourCopy}><Text variant="title" weight="bold" color={color.text.onAction}>{tx('가볼래 한눈에 보기', 'Tour GABOLLE')}</Text><Text variant="caption" color={color.text.onDarkMuted}>{tx('AI 일정·부산 장소·현장 말하기를 1분 안에 둘러봐요.', 'See AI planning, Busan places, and field phrases in under a minute.')}</Text></View><Button label={tx('앱 소개 다시 보기', 'Replay app tour')} onPress={() => router.push({ pathname: '/app-intro', params: { returnTo: '/help' } })} variant="outline" /><Button label={tx('홈 안내 다시 보기', 'Replay the home guide')} onPress={() => { void requestHomeCoachAgain().then(() => router.replace('/home')); }} variant="secondary" /></View>

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('기능별 사용법', 'How to use each feature')}</Text>
    <View style={styles.faqList}>{FEATURES.map((feature, index) => { const expanded = openFeature === index; return <Pressable key={feature.title[0]} accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setOpenFeature(expanded ? null : index)} style={({ pressed }) => [styles.faq, pressed && styles.pressed]}><View style={styles.faqHeading}><Text weight="bold" style={styles.faqTitle}>{tx(...feature.title)}</Text><Text weight="bold" color={color.text.muted}>{expanded ? '−' : '+'}</Text></View>{expanded ? <><Text color={color.text.body} style={styles.answer}>{tx(...feature.body)}</Text>{feature.path ? <Button label={tx('바로 가기', 'Go there')} variant="outline" compact onPress={() => router.push(feature.path as never)} containerStyle={styles.featureGo} /> : null}</> : null}</Pressable>; })}</View>

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('화면별 안내 다시 보기', 'Replay screen guides')}</Text>
    <Text variant="caption" color={color.text.muted} style={styles.guideLead}>{tx('「다시 보기」를 누르면 그 화면으로 바로 가서 안내가 한 번 더 떠요.', 'Tap “Replay” to jump to that screen and see its guide once more.')}</Text>
    <View style={styles.guideList}>
      {GUIDES.map((guide, index) => (
        <View key={guide.key} style={[styles.guideRow, index === GUIDES.length - 1 && styles.guideRowLast]}>
          <View style={styles.guideCopy}>
            <Text weight="bold">{tx(...guide.title)}</Text>
            <Text variant="caption" color={color.text.muted}>{tx(...guide.where)}{seen ? ` · ${seen[guide.key] ? tx('봤어요', 'Seen') : tx('아직 안 봤어요', 'Not seen yet')}` : ''}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 다시 보기', 'Replay %s', tx(...guide.title))} onPress={() => void again(guide.key, guide.path)} style={({ pressed }) => [styles.guideAgain, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold">{tx('다시 보기', 'Replay')}</Text>
          </Pressable>
        </View>
      ))}
    </View>
    <Button label={tx('처음 안내 전부 다시 켜기', 'Turn all first-time guides back on')} variant="tertiary" onPress={() => void allAgain()} containerStyle={styles.guideAll} />
    {guideNote ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.text.body}>{guideNote}</Text> : null}

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('자주 묻는 질문', 'Frequently asked questions')}</Text>
    <View style={styles.faqList}>{FAQS.map(([koTitle, enTitle, koBody, enBody], index) => { const expanded = open === index; return <Pressable key={koTitle} accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setOpen(expanded ? null : index)} style={({ pressed }) => [styles.faq, pressed && styles.pressed]}><View style={styles.faqHeading}><Text weight="bold" style={styles.faqTitle}>{tx(koTitle, enTitle)}</Text><Text weight="bold" color={color.text.muted}>{expanded ? '−' : '+'}</Text></View>{expanded && <Text color={color.text.body} style={styles.answer}>{tx(koBody, enBody)}</Text>}</Pressable>; })}</View>

    <View style={styles.contactCard}><Text variant="title" weight="bold">{tx('답을 찾지 못했나요?', "Couldn't find an answer?")}</Text>{supportEmail ? <><Text color={color.text.body}>{tx('문의 메일에는 문제가 난 화면과 재현 순서를 함께 적어 주세요.', 'Include the screen and steps to reproduce in your email.')}</Text><Button variant="outline" label={tx('이메일로 문의하기', 'Contact by email')} onPress={() => void contact()} /></> : <Text color={color.text.body}>{tx('문의 수신 주소를 준비하고 있어요. 그 전에는 팀에 화면 이름과 증상을 전달해 주세요.', 'The support inbox is being prepared. For now, tell the team the screen name and what happened.')}</Text>}</View>
    <View style={styles.legalLinks}><Pressable accessibilityRole="link" onPress={() => router.push('/legal/privacy')}><Text weight="bold" color={color.action.secondary}>{tx('개인정보 처리방침', 'Privacy Policy')}</Text></Pressable><Pressable accessibilityRole="link" onPress={() => router.push('/legal/terms')}><Text weight="bold" color={color.action.secondary}>{tx('이용약관', 'Terms of Service')}</Text></Pressable></View>
  </>;
}

const styles = StyleSheet.create({
  guideList: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  guideRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderBottomWidth: 1, borderBottomColor: color.surface.border },
  guideRowLast: { borderBottomWidth: 0 },
  guideCopy: { flex: 1, minWidth: 0, gap: 2 },
  guideAgain: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1.5, borderColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  guideAll: { marginTop: spacing[3] },
  guideLead: { marginBottom: spacing[3] },
  startCard: { gap: spacing[3], marginBottom: spacing[6], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.soft },
  stepRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] },
  stepNumber: { width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  stepCopy: { flex: 1, minWidth: 0, gap: 2 },
  featureGo: { marginTop: spacing[3], alignSelf: 'flex-start' },
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, logo: { width: 154, height: 28 }, spacer: { width: 44 }, title: { marginTop: spacing[2] }, lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
  tourCard: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, tourCopy: { gap: spacing[2] }, sectionTitle: { marginTop: spacing[8], marginBottom: spacing[3] },
  faqList: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, faq: { minHeight: 60, padding: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border }, faqHeading: { minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, faqTitle: { flex: 1 }, answer: { marginTop: spacing[2], lineHeight: 23 }, pressed: { backgroundColor: color.surface.tint },
  contactCard: { gap: spacing[3], marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.soft }, legalLinks: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[6], marginTop: spacing[6] },
});
