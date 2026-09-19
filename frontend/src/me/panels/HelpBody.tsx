// 도움말·문의 본문 — 화면(`app/help.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 머리(뒤로·로고·제목·설명)는 화면만 그린다. 패널은 껍데기가 이미 제목을 그린다.
import { useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const FAQS = [
  ['가볼래는 어떤 앱인가요?', 'What is GABOLLE?', '취향·예산·이동 조건을 반영해 부산 일정을 만들고, 장소 저장과 현장 말하기까지 이어주는 여행 앱이에요.', 'GABOLLE builds a Busan itinerary from your tastes, budget, and mobility needs, then helps with saved places and useful phrases.'],
  ['일정이 바로 만들어지지 않아요.', 'My itinerary is not ready yet.', '내 여행에서 준비 상태를 확인해 주세요. 실패로 표시되면 다시 만들기를 누르고, 같은 문제가 반복되면 화면의 요청 ID와 함께 문의해 주세요.', 'Check its status in My trips. If it failed, retry; if it repeats, contact us with the request ID shown on screen.'],
  ['위치 권한을 거부해도 되나요?', 'Can I deny location access?', '네. 출발지나 장소를 직접 검색할 수 있어요. 위치 권한은 주변 장소와 방문 확인을 더 편하게 쓸 때만 필요해요.', 'Yes. You can search for a starting point or place manually. Location access only improves nearby results and visit check-ins.'],
  ['내 정보는 어떻게 삭제하나요?', 'How do I delete my data?', '마이페이지 → 프로필 → 회원 탈퇴에서 삭제되는 항목을 확인한 뒤 계정을 영구 삭제할 수 있어요.', 'Go to My page → Profile → Delete account to review and permanently remove your account data.'],
] as const;

export function HelpBody() {
  const router = useRouter();
  const { tx } = useI18n();
  const [open, setOpen] = useState<number | null>(0);
  const supportEmail = process.env.EXPO_PUBLIC_SUPPORT_EMAIL?.trim();
  const contact = async () => {
    if (!supportEmail) return;
    const subject = encodeURIComponent(tx('[가볼래] 앱 문의', '[GABOLLE] App support'));
    const body = encodeURIComponent(tx('문의 내용:\n\n문제가 발생한 화면:\n\n재현 순서:\n', 'Question:\n\nScreen where it happened:\n\nSteps to reproduce:\n'));
    await Linking.openURL(`mailto:${supportEmail}?subject=${subject}&body=${body}`);
  };

  return <>

    <View style={styles.tourCard}><View style={styles.tourCopy}><Text variant="title" weight="bold" color={color.text.onAction}>{tx('가볼래 한눈에 보기', 'Tour GABOLLE')}</Text><Text variant="caption" color={color.text.onDarkMuted}>{tx('AI 일정·부산 장소·현장 말하기를 1분 안에 둘러봐요.', 'See AI planning, Busan places, and field phrases in under a minute.')}</Text></View><Button label={tx('앱 소개 다시 보기', 'Replay app tour')} onPress={() => router.push({ pathname: '/app-intro', params: { returnTo: '/help' } })} variant="primary" /></View>

    <Text variant="title" weight="bold" style={styles.sectionTitle}>{tx('자주 묻는 질문', 'Frequently asked questions')}</Text>
    <View style={styles.faqList}>{FAQS.map(([koTitle, enTitle, koBody, enBody], index) => { const expanded = open === index; return <Pressable key={koTitle} accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setOpen(expanded ? null : index)} style={({ pressed }) => [styles.faq, pressed && styles.pressed]}><View style={styles.faqHeading}><Text weight="bold" style={styles.faqTitle}>{tx(koTitle, enTitle)}</Text><Text weight="bold" color={color.brand.orange}>{expanded ? '−' : '+'}</Text></View>{expanded && <Text color={color.text.body} style={styles.answer}>{tx(koBody, enBody)}</Text>}</Pressable>; })}</View>

    <View style={styles.contactCard}><Text variant="title" weight="bold">{tx('답을 찾지 못했나요?', "Couldn't find an answer?")}</Text>{supportEmail ? <><Text color={color.text.body}>{tx('문의 메일에는 문제가 난 화면과 재현 순서를 함께 적어 주세요.', 'Include the screen and steps to reproduce in your email.')}</Text><Button label={tx('이메일로 문의하기', 'Contact by email')} onPress={() => void contact()} /></> : <Text color={color.text.body}>{tx('문의 수신 주소를 준비하고 있어요. 그 전에는 팀에 화면 이름과 증상을 전달해 주세요.', 'The support inbox is being prepared. For now, tell the team the screen name and what happened.')}</Text>}</View>
    <View style={styles.legalLinks}><Pressable accessibilityRole="link" onPress={() => router.push('/legal/privacy')}><Text weight="bold" color={color.brand.orange}>{tx('개인정보 처리방침', 'Privacy Policy')}</Text></Pressable><Pressable accessibilityRole="link" onPress={() => router.push('/legal/terms')}><Text weight="bold" color={color.brand.orange}>{tx('이용약관', 'Terms of Service')}</Text></Pressable></View>
  </>;
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, logo: { width: 96, height: 28 }, spacer: { width: 44 }, title: { marginTop: spacing[2] }, lead: { marginTop: spacing[2], marginBottom: spacing[6], lineHeight: 24 },
  tourCard: { gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, tourCopy: { gap: spacing[2] }, sectionTitle: { marginTop: spacing[8], marginBottom: spacing[3] },
  faqList: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card }, faq: { minHeight: 60, padding: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border }, faqHeading: { minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, faqTitle: { flex: 1 }, answer: { marginTop: spacing[2], lineHeight: 23 }, pressed: { backgroundColor: color.surface.tint },
  contactCard: { gap: spacing[3], marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.soft }, legalLinks: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[6], marginTop: spacing[6] },
});
