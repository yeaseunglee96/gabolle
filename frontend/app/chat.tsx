import { useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, TextInput, View, type ImageSourcePropType } from 'react-native';
import { useRouter } from 'expo-router';

import { assistantUnavailable, understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
import { isNearBottom } from '@/assistant/chatScroll';
import { askAssistant, isAllowedNavigateHref, type AssistantTurn } from '@/assistant/assistantApi';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { BusIcon } from '@/field/BusIcon';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';
import { useI18n } from '@/i18n';

type Message = { id: number; role: 'user' | 'assistant'; text: string; action?: AssistantAction; applied?: boolean };
const SUGGESTIONS = [
  { ko: '해운대와 광안리 2명 맛집 일정 짜줘', en: 'Plan a food-focused trip for 2 in Haeundae and Gwangalli' },
  { ko: '사진 부탁할 때 한국어 문장 알려줘', en: 'Show me the Korean phrase for asking someone to take a photo' },
  { ko: '부산 로컬 스팟 보여줘', en: 'Show me local Busan spots' },
] as const;
// 은행 앱 챗봇처럼 "대화 없이 바로 실행" — 자유 대화보다 우리 기능으로 바로 연결한다(2026-09-16 지시).
// 🔴 S15P21E201-1396 — 셋(현장 도구·내 여행 보기·로컬 탐색)이라 2열 격자에서 한 칸이 비어 균형이 안 맞았다.
//    「내 여행 보기」「로컬 탐색」은 하단 탭에 이미 있어 여기서는 중복이고, 머리말 「여행 중 급할 때」와도
//    안 맞는다. 급할 때 실제로 여는 넷 — 메뉴판 번역·통역·환율·주변 버스 — 을 그림과 함께 2×2 로 두고,
//    날씨·준비물까지 다 있는 허브(/field/translate)는 아래 한 줄로 잇는다.
const dialectIcon = require('../assets/mascot/dongbaek-open.png');
type QuickTool = { key: string; labelKo: string; labelEn: string; hintKo: string; hintEn: string; href: '/field/exchange-rate' | '/field/transit' | '/field/dialect' | '/help'; icon: ImageSourcePropType | string | 'bus' };
const QUICK_TOOLS: QuickTool[] = [
  // 🔴 메뉴판 번역·통역은 여기 없다(S15P21E201-1422) — 홈 동백이 단추가 「메뉴판 번역 · 통역 · AI 챗봇」이라, 챗봇에 들어와서
  //    같은 둘을 또 보면 「이미 봤는데?」가 된다. 동백이 메뉴에 없는 넷만 둔다.
  { key: 'exchange', labelKo: '환율 계산', labelEn: 'Currency', hintKo: '가격표를 내 돈으로', hintEn: 'Convert a price tag', href: '/field/exchange-rate', icon: '₩' },
  { key: 'bus', labelKo: '주변 버스', labelEn: 'Buses nearby', hintKo: '몇 분 뒤에 오는지', hintEn: 'Minutes until arrival', href: '/field/transit', icon: 'bus' },
  { key: 'dialect', labelKo: '부산 사투리 한마디', labelEn: 'Busan dialect', hintKo: '진짜 부산 억양으로 듣기', hintEn: 'Hear a real Busan accent', href: '/field/dialect', icon: dialectIcon },
  { key: 'help', labelKo: '도움말·문의', labelEn: 'Help & support', hintKo: '앱 소개 · 자주 묻는 질문', hintEn: 'App tour · FAQ', href: '/help', icon: '?' },
];

export default function Chat() {
  const router = useRouter(); const { update } = usePlan();
  const { accessToken } = useAuth();
  const { width } = useLayout();
  const { tx, language } = useI18n();
  const desktop = isAtLeast(width, 'md');
  const [input, setInput] = useState('');
  // 인사말은 언어 환경설정이 뒤늦게 준비돼도 반영돼야 해서 state 초깃값(마운트 시 한 번만 평가됨)에
  // 넣지 않고, 렌더마다 tx 로 새로 계산해 목록 앞에 붙인다.
  const [messages, setMessages] = useState<Message[]>([]);
  // 서버 응답을 기다리는 동안 true — 입력을 막고 "답변 준비 중" 표시를 보여준다. 이게
  // 없으면 비동기 호출 중에 사용자가 여러 번 눌러 메시지를 겹쳐 보낼 수 있었다.
  const [pending, setPending] = useState(false);
  // id 발급을 ref 카운터로 둔다 — 서버 호출이 비동기라 연속으로 빠르게 보내면 messages
  // state 가 아직 안 바뀐 사이에 다음 send 가 같은 id를 다시 계산할 수 있다(state 파생값은
  // 렌더 지연을 겪는다). 카운터는 그 지연과 무관하게 그 자리에서 바로 늘어난다.
  const nextIdRef = useRef(1);
  // 🔴 답이 와도 화면이 안 내려가 「일정에 적용」 단추가 화면 밖에 숨었다 — S15P21E201-1349.
  //    바닥 근처에 있었을 때만 따라 내려간다. 위로 올려 지난 말을 읽는 사람을
  //    말풍선마다 바닥으로 끌어내리지 않는다.
  const listRef = useRef<ScrollView>(null);
  const stickToEnd = useRef(true);
  // 서버가 저장하지 않는 대화라(무상태) 매 요청마다 화면이 최근 몇 턴을 함께 보낸다 — 서버
  // (AssistantChatService)가 개수·길이를 다시 한 번 다듬으니 여기서는 넉넉히 최근 6개만 추린다.
  const MAX_HISTORY_TURNS = 6;
  function recentHistory(): AssistantTurn[] {
    return messages.slice(-MAX_HISTORY_TURNS).map((message) => ({ role: message.role, text: message.text }));
  }
  // 로그인 전에는 서버(AUTHENTICATED_ONLY)를 아예 부르지 않고 로컬 규칙으로 바로 넘어간다
  // 401 처리(토큰 갱신 시도 등)를 겪을 이유가 없다. 로그인 후에도 서버 호출이 실패하면
  // (네트워크 문제 등) 같은 로컬 규칙으로 자연스럽게 넘어간다 — 사용자는 항상 답을 받는다.
  /**
   * @param shown 🔴 말풍선에 남길 글. 제안을 누른 경우 **화면에 보여 준 문구**가 들어온다.
   *   보내는 값(`value`)은 한국어 원문 그대로 두어야 한다 — 비회원은 서버 대신 앱 안의
   *   키워드 매처로 답하는데 그 매처가 한국어만 알아듣기 때문이다(src/assistant/intent.ts).
   *   안 주면 보낸 글을 그대로 쓴다(직접 입력한 경우는 둘이 같다). S15P21E201-1327.
   */
  /** @param fixed 추천 칩처럼 우리가 만든, 뜻이 정해진 단추에서 왔나 */
  async function send(value = input, shown?: string, fixed = false) {
    const content = value.trim();
    if (!content || pending) return;
    setInput('');
    // 🔴 내가 방금 보낸 것에 대한 답은 예외다. 위로 올려 둔 채 질문한 사람이
    //    자기 답을 못 보면 아무 일도 안 일어난 것처럼 보인다.
    stickToEnd.current = true;
    const history = recentHistory();
    const userId = nextIdRef.current++;
    const assistantId = nextIdRef.current++;
    setMessages((current) => [...current, { id: userId, role: 'user', text: (shown ?? content).trim() }]);
    setPending(true);
    try {
      // 🔴 칩 「부산 로컬 스팟 보여줘」가 「새 여행 만들기」로 갔다 — S15P21E201-1517.
      //    서버 AI 는 /explore 로 보낼 수 없다(허용 목록 다섯에 없고, 안내문이 「장소 요청은
      //    /plan 또는 /trips 로」라고 정한다). 그래서 규칙대로 /plan 이 나왔다.
      //    칩은 뜻이 정해진 단추라, 서버가 못 가는 화면으로 가는 칩은 서버에 묻지 않는다.
      //    /field/dialect 가 같은 이유로 이미 「앱 안에서만 쓰는 이동」이다(-1422).
      //
      //    🔴 직접 친 글에는 쓰지 않는다. 로컬 해석기는 낱말만 보는 거친 도구라
      //       「로컬 맛집 일정 짜줘」도 /explore 로 보낸다 — 그건 서버 AI 가 더 잘 가른다.
      const local = fixed ? understandAssistantMessage(content) : null;
      const appOnly = local?.kind === 'navigate' && !isAllowedNavigateHref(local.href);
      const action = local && appOnly
        ? local
        : accessToken
          ? await askAssistant(content, accessToken, history, language).catch(() => assistantUnavailable(content))
          : understandAssistantMessage(content);
      setMessages((current) => [...current, { id: assistantId, role: 'assistant', text: action.reply, action }]);
    } finally {
      setPending(false);
    }
  }
  function applyPlan(id: number, action: Extract<AssistantAction, { kind: 'plan' }>) { update(action.patch); setMessages((current) => current.map((item) => item.id === id ? { ...item, applied: true } : item)); }

  const toolIcon = (icon: QuickTool['icon']) => icon === 'bus' ? <BusIcon /> : typeof icon === 'string' ? <Text variant="title" weight="bold" color={color.action.secondary}>{icon}</Text> : <Image source={icon} resizeMode="contain" style={styles.toolIconImage} />;
  const tools = <View accessibilityLabel={tx('여행 도구 바로가기', 'Trip tool shortcuts')} style={[styles.toolSection, desktop && styles.toolSectionDesktop]}>
    {!desktop ? <View style={styles.sectionHeading}><Text variant="body" weight="bold">{tx('대화 없이 바로 실행', 'Run these without chatting')}</Text><Text variant="caption" color={color.text.body}>{tx('메뉴판 번역·통역은 홈의 동백이 단추에 있어요.', 'Menu translation and interpreter live on the home Dongbaek button.')}</Text></View> : null}
    <View style={[styles.quickTools, desktop && styles.quickToolsDesktop]}>{QUICK_TOOLS.map((tool) => <Pressable key={tool.key} accessibilityRole="button" accessibilityLabel={`${tx(tool.labelKo, tool.labelEn)}, ${tx(tool.hintKo, tool.hintEn)}`} onPress={() => router.push(tool.href)} style={({ pressed }) => [styles.quickTool, desktop && styles.quickToolDesktop, pressed && styles.quickToolPressed]}>
      <View style={styles.toolIconBox}>{toolIcon(tool.icon)}</View>
      <View style={styles.toolBody}><Text variant="body" weight="bold" numberOfLines={1}>{tx(tool.labelKo, tool.labelEn)}</Text><Text variant="caption" color={color.text.body} numberOfLines={1}>{tx(tool.hintKo, tool.hintEn)}</Text></View>
    </Pressable>)}</View>
    <Pressable accessibilityRole="link" accessibilityLabel={tx('현장 도구 전부 보기', 'See all on-the-go tools')} onPress={() => router.push('/field/translate')} style={({ pressed }) => [styles.toolMore, pressed && styles.quickToolPressed]}><Text variant="caption" weight="bold" color={desktop ? color.text.onAction : color.action.secondary}>{tx('현장 도구 전부 보기 ›', 'See all on-the-go tools ›')}</Text></Pressable>
  </View>;

  return <Screen wide style={[styles.screen, desktop && styles.desktopScreen]}>
    <View style={[styles.header, desktop && styles.desktopHeader]}><View style={styles.identity}><GabolleMascot state="open" still style={desktop ? styles.desktopAvatar : styles.avatar} /><View style={styles.identityText}><Text variant={desktop ? 'display' : 'title'} weight="bold">{tx('가볼래 AI', 'GABOLLE AI')}</Text><Text variant="caption" color={color.text.body}>{tx('앱 기능을 실행하는 부산 여행 도우미', 'A Busan travel assistant that runs app features for you')}</Text></View></View><Pressable accessibilityRole="button" accessibilityLabel={tx('채팅 닫기', 'Close chat')} onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.close}><Text variant="title">×</Text></Pressable></View>
    <View style={[styles.workspace, desktop && styles.workspaceDesktop]}>
      {desktop ? <View style={styles.sidebar}><Eyebrow>{tx('여행 도구', 'Travel tools')}</Eyebrow><Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 중 필요한 기능을 바로 실행하세요', 'Run the features you need for your trip right away')}</Text><Text variant="body" color={color.text.onAction}>{tx('현장 문장은 크게 보거나 음성으로 듣고, 메뉴판 번역 도구도 바로 열 수 있어요.', 'View on-the-go phrases in large text or hear them aloud, and open the menu translation tool right away.')}</Text>{tools}</View> : null}
      <View style={[styles.chatPanel, desktop && styles.chatPanelDesktop]}>
        <ScrollView
          ref={listRef}
          style={styles.messages}
          contentContainerStyle={[styles.messageContent, desktop && styles.messageContentDesktop]}
          keyboardShouldPersistTaps="handled"
          scrollEventThrottle={16}
          onScroll={(event) => { stickToEnd.current = isNearBottom(event.nativeEvent); }}
          onContentSizeChange={() => { if (stickToEnd.current && messages.length > 0) listRef.current?.scrollToEnd({ animated: true }); }}
        >
          <View style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.heading}>{tx('안녕하세요! 부산 일정과 여행 중 필요한 말을 앱 기능으로 바로 도와드릴게요.', 'Hi! I can help with your Busan itinerary and useful phrases for your trip, right from the app.')}</Text></View>
          {/* 사용자 요청(2026-09-16): 이 화면은 자유롭게 대화하는 진짜 챗봇이 아니라, 은행 앱
              챗봇처럼 「기능을 찾아 바로 실행하는 검색 도구」다. 그래서 실제로 누르면 바로 실행되는
              기능 버튼(tools)과 검색 예시(suggestions)를 인사말 바로 아래, 자유 입력창보다 먼저
              보여준다 — 자유 대화가 주된 사용법이라는 인상을 주지 않기 위해서다. */}
          {!desktop ? tools : null}
          {messages.length === 0 ? <View style={styles.suggestionSection}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 검색해 보세요', 'Try searching for these')}</Text><View style={styles.suggestions}>{SUGGESTIONS.map((suggestion) => <Pressable key={suggestion.ko} accessibilityRole="button" accessibilityState={{ disabled: pending }} disabled={pending} onPress={() => send(suggestion.ko, tx(suggestion.ko, suggestion.en), true)} style={styles.suggestion}><Text variant="body" weight="medium">{tx(suggestion.ko, suggestion.en)}</Text></Pressable>)}</View></View> : null}
          {messages.map((message) => <View key={message.id} style={[styles.bubble, desktop && styles.bubbleDesktop, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}><Text color={message.role === 'user' ? color.text.onAction : color.text.heading}>{message.text}</Text>
            {message.action?.kind === 'plan' && message.action.summary.length ? <View style={styles.actionCard}><Text variant="caption" weight="bold">{tx('찾은 여행 조건', 'Conditions found')}</Text><Text variant="caption" color={color.text.body}>{message.action.summary.join(' · ')}</Text><Button label={message.applied ? tx('일정 초안에 적용됨 ✓', 'Applied to draft itinerary ✓') : tx('일정에 적용하고 확인하기', 'Apply to itinerary and review')} variant="outline" disabled={message.applied} onPress={() => { applyPlan(message.id, message.action as Extract<AssistantAction, { kind: 'plan' }>); router.push('/plan'); }} /></View> : null}
            {message.action?.kind === 'phrase' ? <View style={styles.actionCard}><Text variant="title" weight="bold">{message.action.korean}</Text><Text variant="caption" color={color.text.muted}>{message.action.pronunciation}</Text><Button label={tx('크게 보고 듣기', 'View large & listen')} variant="field" onPress={() => router.push({ pathname: '/field/speak', params: { phrase: (message.action as Extract<AssistantAction, { kind: 'phrase' }>).korean } })} /></View> : null}
            {message.action?.kind === 'navigate' ? <Button label={message.action.label} variant="tertiary" onPress={() => router.push((message.action as Extract<AssistantAction, { kind: 'navigate' }>).href as never)} /> : null}
          </View>)}
          {pending ? <View accessibilityLabel={tx('답변 준비 중', 'Preparing a reply')} style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.body}>{tx('생각하는 중…', 'Thinking…')}</Text></View> : null}
        </ScrollView>
        {/* 검색창처럼 보이도록 한 줄 높이로 두고(예전엔 여러 줄로 늘어나는 대화창 모양이었다)
            왼쪽에 검색 글자(⌕, app-intro.tsx 검색 미리보기와 같은 글자)를 붙인다. */}
        <View style={styles.composer}><Text weight="bold" color={color.text.muted} style={styles.searchMark}>⌕</Text><TextInput accessibilityLabel={tx('기능이나 궁금한 점 검색', 'Search for a feature or ask something')} value={input} onChangeText={setInput} onSubmitEditing={() => send()} editable={!pending} returnKeyType="search" placeholder={tx('예: 광안리 맛집 2명 일정 짜줘', 'e.g. Gwangalli food trip for 2')} placeholderTextColor={color.text.muted} style={styles.input} />{input.length > 0 && <Pressable accessibilityRole="button" accessibilityLabel={tx('입력 지우기', 'Clear input')} onPress={() => setInput('')} style={styles.clear}><Text variant="body" color={color.text.muted}>✕</Text></Pressable>}<Pressable accessibilityRole="button" accessibilityLabel={tx('검색', 'Search')} accessibilityState={{ disabled: !input.trim() || pending }} disabled={!input.trim() || pending} onPress={() => send()} style={[styles.send, (!input.trim() || pending) && styles.sendDisabled]}><Text weight="bold" color={color.text.onAction}>↑</Text></Pressable></View>
        <Text variant="caption" color={color.text.muted} style={styles.disclaimer}>{tx('안전 조건은 AI가 변경하지 않으며, 일정 적용 전 반드시 확인합니다.', 'The AI never changes your safety conditions, and you always review before applying to your itinerary.')}</Text>
      </View>
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { gap: spacing[3] }, desktopScreen: { paddingTop: spacing[4] }, header: { marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, desktopHeader: { minHeight: 64, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, identity: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, identityText: { flex: 1, minWidth: 0 }, avatar: { width: 44, height: 44 }, desktopAvatar: { width: 52, height: 52 }, close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  workspace: { flex: 1 }, workspaceDesktop: { flexDirection: 'row', gap: spacing[4], minHeight: 0 }, sidebar: { width: 290, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, chatPanel: { flex: 1, gap: spacing[3], minHeight: 0 }, chatPanelDesktop: { padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card },
  messages: { flex: 1 }, messageContent: { gap: spacing[3], paddingVertical: spacing[3] }, messageContentDesktop: { paddingHorizontal: spacing[2] }, bubble: { maxWidth: '88%', padding: spacing[3], borderRadius: radius.lg, gap: spacing[3] }, bubbleDesktop: { maxWidth: '72%' }, userBubble: { alignSelf: 'flex-end', backgroundColor: color.brand.navy, borderBottomRightRadius: radius.sm }, assistantBubble: { alignSelf: 'flex-start', backgroundColor: color.surface.blush, borderBottomLeftRadius: radius.sm }, actionCard: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  suggestionSection: { gap: spacing[2], marginTop: spacing[4] }, suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, suggestion: { minHeight: 48, maxWidth: '100%', justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.action.secondary, borderRadius: radius.full, backgroundColor: color.surface.card },
  toolSection: { marginTop: spacing[4], gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.blush }, toolSectionDesktop: { marginTop: spacing[3], padding: 0, backgroundColor: 'transparent' }, sectionHeading: { gap: spacing[1] }, quickTools: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, quickToolsDesktop: { flexDirection: 'column', flexWrap: 'nowrap' }, quickTool: { width: '48%', flexGrow: 1, minHeight: 72, gap: spacing[2], padding: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card }, quickToolDesktop: { width: '100%', flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 64 }, quickToolPressed: { opacity: 0.76, backgroundColor: color.state.warningBg }, toolIconBox: { width: 40, height: 40, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.blush }, toolIconImage: { width: 22, height: 22 }, toolBody: { flex: 1, gap: 2, minWidth: 0 }, toolMore: { minHeight: 40, justifyContent: 'center', alignSelf: 'flex-start', paddingHorizontal: spacing[1] },
  composer: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: spacing[2], paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card }, searchMark: { fontSize: 16 }, input: { flex: 1, minWidth: 0, minHeight: 44, paddingVertical: spacing[2], color: color.text.heading, fontSize: 16 }, clear: { minWidth: 28, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, send: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary }, sendDisabled: { opacity: 0.4 }, disclaimer: { textAlign: 'center' },
});
