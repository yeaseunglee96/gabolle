import { useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
import { isNearBottom } from '@/assistant/chatScroll';
import { askAssistant, type AssistantTurn } from '@/assistant/assistantApi';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
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
// 은행 앱 챗봇처럼 "대화 없이 바로 실행" 목록을 넓혔다 — 사용자가 직접 요청한 방향
// (자유 대화보다 우리 기능으로 바로 연결)이라 실제로 동작하는 화면만 올린다.
// : "지금 갈 곳"·"부산 축제"는 -900으로 진입점을 뺀 화면이라 여기서도 뺐다
// 홈에서는 숨겨 놓고 챗봇으로는 계속 안내하면 이 목록의 원칙이 깨진다.
const QUICK_TOOLS = [
  { labelKo: '일정 만들기', labelEn: 'Plan a trip', hintKo: '대화 조건 적용', hintEn: 'Applies chat conditions', href: '/plan' },
  { labelKo: '현장 도구', labelEn: 'On-the-go tools', hintKo: '현장 말하기·날씨 준비물', hintEn: 'On-the-go phrases · weather prep', href: '/field/translate' },
  { labelKo: '내 여행 보기', labelEn: 'View my trips', hintKo: '저장한 일정 열기', hintEn: 'Open your saved itineraries', href: '/trips' },
  // : 갈래 개수는 GET /api/v1/places/facets 가 정한다(explore.tsx) — 여기서
  // 숫자를 박으면 백엔드가 갈래를 늘리거나 줄일 때마다 다시 어긋난다. 숫자를 빼고 말한다.
  { labelKo: '로컬 탐색', labelEn: 'Explore locally', hintKo: '축제·전통시장 등 다양한 카테고리', hintEn: 'Various local categories', href: '/explore' },
] as const;

export default function Chat() {
  const router = useRouter(); const { update } = usePlan();
  const { accessToken } = useAuth();
  const { width } = useLayout();
  const { tx } = useI18n();
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
  async function send(value = input, shown?: string) {
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
      const action = accessToken
        ? await askAssistant(content, accessToken, history).catch(() => understandAssistantMessage(content))
        : understandAssistantMessage(content);
      setMessages((current) => [...current, { id: assistantId, role: 'assistant', text: action.reply, action }]);
    } finally {
      setPending(false);
    }
  }
  function applyPlan(id: number, action: Extract<AssistantAction, { kind: 'plan' }>) { update(action.patch); setMessages((current) => current.map((item) => item.id === id ? { ...item, applied: true } : item)); }

  const visibleTools = QUICK_TOOLS.slice(1);
  const tools = <View accessibilityLabel={tx('여행 도구 바로가기', 'Trip tool shortcuts')} style={[styles.toolSection, desktop && styles.toolSectionDesktop]}>
    {!desktop ? <View style={styles.sectionHeading}><Text variant="body" weight="bold">{tx('대화 없이 바로 실행', 'Run these without chatting')}</Text><Text variant="caption" color={color.text.body}>{tx('여행 중 급할 때 바로 열어보세요.', 'Open these right away when you need them on your trip.')}</Text></View> : null}
    <View style={[styles.quickTools, desktop && styles.quickToolsDesktop]}>{visibleTools.map((tool) => <Pressable key={tool.href} accessibilityRole="button" accessibilityLabel={`${tx(tool.labelKo, tool.labelEn)}, ${tx(tool.hintKo, tool.hintEn)}`} onPress={() => router.push(tool.href)} style={({ pressed }) => [styles.quickTool, desktop && styles.quickToolDesktop, pressed && styles.quickToolPressed]}><Text variant="body" weight="bold">{tx(tool.labelKo, tool.labelEn)}</Text><Text variant="caption" color={color.text.body}>{tx(tool.hintKo, tool.hintEn)}</Text><Text variant="title" weight="bold" color={color.text.muted} style={styles.toolArrow}>›</Text></Pressable>)}</View>
  </View>;

  return <Screen wide style={[styles.screen, desktop && styles.desktopScreen]}>
    <View style={[styles.header, desktop && styles.desktopHeader]}><View style={styles.identity}><GabolleMascot state="open" delay={180} style={desktop ? styles.desktopAvatar : styles.avatar} /><View><Text variant={desktop ? 'display' : 'title'} weight="bold">{tx('가볼래 AI', 'GABOLLE AI')}</Text><Text variant="caption" color={color.text.body}>{tx('앱 기능을 실행하는 부산 여행 도우미', 'A Busan travel assistant that runs app features for you')}</Text></View></View><Pressable accessibilityRole="button" accessibilityLabel={tx('채팅 닫기', 'Close chat')} onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.close}><Text variant="title">×</Text></Pressable></View>
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
          onContentSizeChange={() => { if (stickToEnd.current) listRef.current?.scrollToEnd({ animated: true }); }}
        >
          <View style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.heading}>{tx('안녕하세요! 부산 일정과 여행 중 필요한 말을 앱 기능으로 바로 도와드릴게요.', 'Hi! I can help with your Busan itinerary and useful phrases for your trip, right from the app.')}</Text></View>
          {/* 사용자 요청(2026-09-16): 이 화면은 자유롭게 대화하는 진짜 챗봇이 아니라, 은행 앱
              챗봇처럼 「기능을 찾아 바로 실행하는 검색 도구」다. 그래서 실제로 누르면 바로 실행되는
              기능 버튼(tools)과 검색 예시(suggestions)를 인사말 바로 아래, 자유 입력창보다 먼저
              보여준다 — 자유 대화가 주된 사용법이라는 인상을 주지 않기 위해서다. */}
          {!desktop ? tools : null}
          {messages.length === 0 ? <View style={styles.suggestionSection}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 검색해 보세요', 'Try searching for these')}</Text><View style={styles.suggestions}>{SUGGESTIONS.map((suggestion) => <Pressable key={suggestion.ko} accessibilityRole="button" accessibilityState={{ disabled: pending }} disabled={pending} onPress={() => send(suggestion.ko, tx(suggestion.ko, suggestion.en))} style={styles.suggestion}><Text variant="body" weight="medium">{tx(suggestion.ko, suggestion.en)}</Text></Pressable>)}</View></View> : null}
          {messages.map((message) => <View key={message.id} style={[styles.bubble, desktop && styles.bubbleDesktop, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}><Text color={message.role === 'user' ? color.text.onAction : color.text.heading}>{message.text}</Text>
            {message.action?.kind === 'plan' && message.action.summary.length ? <View style={styles.actionCard}><Text variant="caption" weight="bold">{tx('찾은 여행 조건', 'Conditions found')}</Text><Text variant="caption" color={color.text.body}>{message.action.summary.join(' · ')}</Text><Button label={message.applied ? tx('일정 초안에 적용됨 ✓', 'Applied to draft itinerary ✓') : tx('일정에 적용하고 확인하기', 'Apply to itinerary and review')} variant="outline" disabled={message.applied} onPress={() => { applyPlan(message.id, message.action as Extract<AssistantAction, { kind: 'plan' }>); router.push('/plan'); }} /></View> : null}
            {message.action?.kind === 'phrase' ? <View style={styles.actionCard}><Text variant="title" weight="bold">{message.action.korean}</Text><Text variant="caption" color={color.text.muted}>{message.action.pronunciation}</Text><Button label={tx('크게 보고 듣기', 'View large & listen')} variant="field" onPress={() => router.push('/field/speak')} /></View> : null}
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
  screen: { gap: spacing[3] }, desktopScreen: { paddingTop: spacing[4] }, header: { marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, desktopHeader: { minHeight: 64, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, identity: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, avatar: { width: 44, height: 44 }, desktopAvatar: { width: 52, height: 52 }, close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  workspace: { flex: 1 }, workspaceDesktop: { flexDirection: 'row', gap: spacing[4], minHeight: 0 }, sidebar: { width: 290, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, chatPanel: { flex: 1, gap: spacing[3], minHeight: 0 }, chatPanelDesktop: { padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card },
  messages: { flex: 1 }, messageContent: { gap: spacing[3], paddingVertical: spacing[3] }, messageContentDesktop: { paddingHorizontal: spacing[2] }, bubble: { maxWidth: '88%', padding: spacing[3], borderRadius: radius.lg, gap: spacing[3] }, bubbleDesktop: { maxWidth: '72%' }, userBubble: { alignSelf: 'flex-end', backgroundColor: color.brand.navy, borderBottomRightRadius: radius.sm }, assistantBubble: { alignSelf: 'flex-start', backgroundColor: color.surface.blush, borderBottomLeftRadius: radius.sm }, actionCard: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  suggestionSection: { gap: spacing[2], marginTop: spacing[4] }, suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, suggestion: { minHeight: 48, maxWidth: '100%', justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.action.secondary, borderRadius: radius.full, backgroundColor: color.surface.card },
  toolSection: { marginTop: spacing[4], gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.blush }, toolSectionDesktop: { marginTop: spacing[3], padding: 0, backgroundColor: 'transparent' }, sectionHeading: { gap: spacing[1] }, quickTools: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, quickToolsDesktop: { flexDirection: 'column', flexWrap: 'nowrap' }, quickTool: { position: 'relative', width: '47%', minHeight: 72, justifyContent: 'center', gap: spacing[1], paddingLeft: spacing[3], paddingRight: spacing[6], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card }, quickToolDesktop: { width: '100%', minHeight: 76, paddingHorizontal: spacing[3] }, quickToolPressed: { opacity: 0.76, backgroundColor: color.state.warningBg }, toolArrow: { position: 'absolute', right: spacing[3] },
  composer: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: spacing[2], paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card }, searchMark: { fontSize: 16 }, input: { flex: 1, minWidth: 0, minHeight: 44, paddingVertical: spacing[2], color: color.text.heading, fontSize: 16 }, clear: { minWidth: 28, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, send: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.primary }, sendDisabled: { opacity: 0.4 }, disclaimer: { textAlign: 'center' },
});
