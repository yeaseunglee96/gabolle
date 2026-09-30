// 도움말·문의 본문 — 화면(`app/help.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 머리(뒤로·로고·제목·설명)는 화면만 그린다. 패널은 껍데기가 이미 제목을 그린다.
import { useEffect, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { requestAllGuidesAgain, requestHomeCoachAgain, requestScreenGuideAgain, screenGuidesSeen, type ScreenGuide } from '@/onboarding/firstRun';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';

const FAQS = [
  ['가볼래는 어떤 앱인가요?', 'What is GABOLLE?', '취향·예산·이동 조건을 반영해 부산 일정을 만들고, 장소 저장과 현장 말하기까지 이어주는 여행 앱이에요.', 'GABOLLE builds a Busan itinerary from your tastes, budget, and mobility needs, then helps with saved places and useful phrases.'],
  ['일정이 바로 만들어지지 않아요.', 'My itinerary is not ready yet.', '내 여행에서 준비 상태를 확인해 주세요. 실패로 표시되면 다시 만들기를 누르고, 같은 문제가 반복되면 화면의 요청 ID와 함께 문의해 주세요.', 'Check its status in My trips. If it failed, retry; if it repeats, contact us with the request ID shown on screen.'],
  ['위치 권한을 거부해도 되나요?', 'Can I deny location access?', '네. 출발지나 장소를 직접 검색할 수 있어요. 위치 권한은 주변 장소와 방문 확인을 더 편하게 쓸 때만 필요해요.', 'Yes. You can search for a starting point or place manually. Location access only improves nearby results and visit check-ins.'],
  ['내 정보는 어떻게 삭제하나요?', 'How do I delete my data?', '마이페이지 → 프로필 → 회원 탈퇴에서 삭제되는 항목을 확인한 뒤 계정을 영구 삭제할 수 있어요.', 'Go to My page → Profile → Delete account to review and permanently remove your account data.'],
] as const;

/** 약관·개인정보 처리방침(legalContent)에 적힌 문의 주소와 같은 값. */
const SUPPORT_EMAIL_FALLBACK = 'gabolle.support@gmail.com';

/** 화면별 첫 안내 — 놓친 사람이 다시 켠다(UI 캔버스 ㉔-4, S15P21E201-1885). 제목은 안내 말풍선의 제목 그대로. */
const GUIDES: ReadonlyArray<{ key: ScreenGuide; title: [string, string]; where: [string, string] }> = [
  { key: 'taxi', title: ['말이 안 통해도 택시를 탈 수 있어요', 'Take a taxi even without Korean'], where: ['장소 정보 · 기사님께 보여주기', 'Place page · Show to driver'] },
  { key: 'assistant', title: ['여행 중에 막히면 여기예요', 'Stuck on the road? Start here'], where: ['오른쪽 아래 동백이 메뉴', 'Dongbaek menu, bottom right'] },
  { key: 'legs', title: ['장소 사이 이 칸이 길 안내예요', 'This row between places is your directions'], where: ['여행 일정 · 장소 사이 이동 칸', 'Trip plan · row between places'] },
];

export function HelpBody() {
  const router = useRouter();
  const { tx } = useI18n();
  const [open, setOpen] = useState<number | null>(0);
  const [seen, setSeen] = useState<Record<ScreenGuide, boolean> | null>(null);
  const [guideNote, setGuideNote] = useState<string | null>(null);
  useEffect(() => { let alive = true; void screenGuidesSeen().then((next) => { if (alive) setSeen(next); }); return () => { alive = false; }; }, []);
  const again = async (guide: ScreenGuide) => {
    await requestScreenGuideAgain(guide);
    setSeen((prev) => (prev ? { ...prev, [guide]: false } : prev));
    setGuideNote(tx('다음에 그 화면에 들어가면 한 번 더 떠요.', 'It will show once more next time you open that screen.'));
  };
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

    <View style={styles.tourCard}><View style={styles.tourCopy}><Text variant="title" weight="bold" color={color.text.onAction}>{tx('가볼래 한눈에 보기', 'Tour GABOLLE')}</Text><Text variant="caption" color={color.text.onDarkMuted}>{tx('AI 일정·부산 장소·현장 말하기를 1분 안에 둘러봐요.', 'See AI planning, Busan places, and field phrases in under a minute.')}</Text></View><Button label={tx('앱 소개 다시 보기', 'Replay app tour')} onPress={() => router.push({ pathname: '/app-intro', params: { returnTo: '/help' } })} variant="primary" /><Button label={tx('홈 안내 다시 보기', 'Replay the home guide')} onPress={() => { void requestHomeCoachAgain().then(() => router.replace('/home')); }} variant="secondary" /></View>

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('화면별 안내 다시 보기', 'Replay screen guides')}</Text>
    <View style={styles.guideList}>
      {GUIDES.map((guide, index) => (
        <View key={guide.key} style={[styles.guideRow, index === GUIDES.length - 1 && styles.guideRowLast]}>
          <View style={styles.guideCopy}>
            <Text weight="bold">{tx(...guide.title)}</Text>
            <Text variant="caption" color={color.text.muted}>{tx(...guide.where)}{seen ? ` · ${seen[guide.key] ? tx('봤어요', 'Seen') : tx('아직 안 봤어요', 'Not seen yet')}` : ''}</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 다시 보기', 'Replay %s', tx(...guide.title))} onPress={() => void again(guide.key)} style={({ pressed }) => [styles.guideAgain, pressed && styles.pressed]}>
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
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, logo: { width: 154, height: 28 }, spacer: { width: 44 }, title: { marginTop: spacing[2] }, lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
  tourCard: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, tourCopy: { gap: spacing[2] }, sectionTitle: { marginTop: spacing[8], marginBottom: spacing[3] },
  faqList: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, faq: { minHeight: 60, padding: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border }, faqHeading: { minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, faqTitle: { flex: 1 }, answer: { marginTop: spacing[2], lineHeight: 23 }, pressed: { backgroundColor: color.surface.tint },
  contactCard: { gap: spacing[3], marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.soft }, legalLinks: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[6], marginTop: spacing[6] },
});
