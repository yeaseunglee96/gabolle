import { useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
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
// 🔴 아래 예시 문구는 한국어 입력만 인식하는 이해 로직(src/assistant/intent.ts)에 맞춘 것이다.
// 영어로 바꾸면 그 매처가 알아듣지 못해 기능이 깨지므로, 영어 모드에서도 예시는 한국어로 남긴다.
const SUGGESTIONS = ['해운대와 광안리 2명 맛집 일정 짜줘', '사진 부탁할 때 한국어 문장 알려줘', '부산 로컬 스팟 보여줘'];
// 은행 앱 챗봇처럼 "대화 없이 바로 실행" 목록을 넓혔다 — 사용자가 직접 요청한 방향
// (자유 대화보다 우리 기능으로 바로 연결)이라 실제로 동작하는 화면만 올린다.
// S15P21E201-909: "지금 갈 곳"·"부산 축제"는 -900으로 진입점을 뺀 화면이라 여기서도 뺐다 —
// 홈에서는 숨겨 놓고 챗봇으로는 계속 안내하면 이 목록의 원칙이 깨진다.
const QUICK_TOOLS = [
  { labelKo: '일정 만들기', labelEn: 'Plan a trip', hintKo: '대화 조건 적용', hintEn: 'Applies chat conditions', href: '/plan/basic' },
  { labelKo: '현장 도구', labelEn: 'On-the-go tools', hintKo: '현장 말하기·날씨 준비물', hintEn: 'On-the-go phrases · weather prep', href: '/field/translate' },
  { labelKo: '내 여행 보기', labelEn: 'View my trips', hintKo: '저장한 일정 열기', hintEn: 'Open your saved itineraries', href: '/trips' },
  // S15P21E201-914: 갈래 개수는 GET /api/v1/places/facets 가 정한다(explore.tsx) — 여기서
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
  // 넣지 않고, 렌더마다 tx() 로 새로 계산해 목록 앞에 붙인다.
  const [messages, setMessages] = useState<Message[]>([]);
  // 🔴 서버 응답을 기다리는 동안 true — 입력을 막고 "답변 준비 중" 표시를 보여준다. 이게
  // 없으면 비동기 호출 중에 사용자가 여러 번 눌러 메시지를 겹쳐 보낼 수 있었다.
  const [pending, setPending] = useState(false);
  // 🔴 id 발급을 ref 카운터로 둔다 — 서버 호출이 비동기라 연속으로 빠르게 보내면 messages
  // state 가 아직 안 바뀐 사이에 다음 send() 가 같은 id를 다시 계산할 수 있다(state 파생값은
  // 렌더 지연을 겪는다). 카운터는 그 지연과 무관하게 그 자리에서 바로 늘어난다.
  const nextIdRef = useRef(1);
  // 서버가 저장하지 않는 대화라(무상태) 매 요청마다 화면이 최근 몇 턴을 함께 보낸다 — 서버
  // (AssistantChatService)가 개수·길이를 다시 한 번 다듬으니 여기서는 넉넉히 최근 6개만 추린다.
  const MAX_HISTORY_TURNS = 6;
  function recentHistory(): AssistantTurn[] {
    return messages.slice(-MAX_HISTORY_TURNS).map((message) => ({ role: message.role, text: message.text }));
  }
  // 🔴 로그인 전에는 서버(AUTHENTICATED_ONLY)를 아예 부르지 않고 로컬 규칙으로 바로 넘어간다 —
  // 401 처리(토큰 갱신 시도 등)를 겪을 이유가 없다. 로그인 후에도 서버 호출이 실패하면
  // (네트워크 문제 등) 같은 로컬 규칙으로 자연스럽게 넘어간다 — 사용자는 항상 답을 받는다.
  async function send(value = input) {
    const content = value.trim();
    if (!content || pending) return;
    setInput('');
    const history = recentHistory();
    const userId = nextIdRef.current++;
    const assistantId = nextIdRef.current++;
    setMessages((current) => [...current, { id: userId, role: 'user', text: content }]);
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
    <View style={[styles.quickTools, desktop && styles.quickToolsDesktop]}>{visibleTools.map((tool) => <Pressable key={tool.href} accessibilityRole="button" accessibilityLabel={`${tx(tool.labelKo, tool.labelEn)}, ${tx(tool.hintKo, tool.hintEn)}`} onPress={() => router.push(tool.href)} style={({ pressed }) => [styles.quickTool, desktop && styles.quickToolDesktop, pressed && styles.quickToolPressed]}><Text variant="body" weight="bold">{tx(tool.labelKo, tool.labelEn)}</Text><Text variant="caption" color={color.text.body}>{tx(tool.hintKo, tool.hintEn)}</Text><Text variant="title" weight="bold" color={color.brand.orange} style={styles.toolArrow}>›</Text></Pressable>)}</View>
  </View>;

  return <Screen wide style={[styles.screen, desktop && styles.desktopScreen]}>
    <View style={[styles.header, desktop && styles.desktopHeader]}><View style={styles.identity}><GabolleMascot state="open" delay={180} style={desktop ? styles.desktopAvatar : styles.avatar} /><View><Text variant={desktop ? 'display' : 'title'} weight="bold">{tx('가볼래 AI', 'GABOLLE AI')}</Text><Text variant="caption" color={color.text.body}>{tx('앱 기능을 실행하는 부산 여행 도우미', 'A Busan travel assistant that runs app features for you')}</Text></View></View><Pressable accessibilityRole="button" accessibilityLabel={tx('채팅 닫기', 'Close chat')} onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.close}><Text variant="title">×</Text></Pressable></View>
    <View style={[styles.workspace, desktop && styles.workspaceDesktop]}>
      {desktop ? <View style={styles.sidebar}><Eyebrow>{tx('여행 도구', 'Travel tools')}</Eyebrow><Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 중 필요한 기능을 바로 실행하세요', 'Run the features you need for your trip right away')}</Text><Text variant="body" color={color.text.onAction}>{tx('현장 문장은 크게 보거나 음성으로 듣고, 메뉴판 번역 도구도 바로 열 수 있어요.', 'View on-the-go phrases in large text or hear them aloud, and open the menu translation tool right away.')}</Text>{tools}</View> : null}
      <View style={[styles.chatPanel, desktop && styles.chatPanelDesktop]}>
        <ScrollView style={styles.messages} contentContainerStyle={[styles.messageContent, desktop && styles.messageContentDesktop]} keyboardShouldPersistTaps="handled">
          <View style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.heading}>{tx('안녕하세요! 부산 일정과 여행 중 필요한 말을 앱 기능으로 바로 도와드릴게요.', 'Hi! I can help with your Busan itinerary and useful phrases for your trip, right from the app.')}</Text></View>
          {messages.map((message) => <View key={message.id} style={[styles.bubble, desktop && styles.bubbleDesktop, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}><Text color={message.role === 'user' ? color.text.onAction : color.text.heading}>{message.text}</Text>
            {message.action?.kind === 'plan' && message.action.summary.length ? <View style={styles.actionCard}><Text variant="caption" weight="bold">{tx('찾은 여행 조건', 'Conditions found')}</Text><Text variant="caption" color={color.text.body}>{message.action.summary.join(' · ')}</Text><Button label={message.applied ? tx('일정 초안에 적용됨 ✓', 'Applied to draft itinerary ✓') : tx('일정에 적용하고 확인하기', 'Apply to itinerary and review')} disabled={message.applied} onPress={() => { applyPlan(message.id, message.action as Extract<AssistantAction, { kind: 'plan' }>); router.push('/plan/basic'); }} /></View> : null}
            {message.action?.kind === 'phrase' ? <View style={styles.actionCard}><Text variant="title" weight="bold">{message.action.korean}</Text><Text variant="caption" color={color.text.muted}>{message.action.pronunciation}</Text><Button label={tx('크게 보고 듣기', 'View large & listen')} variant="field" onPress={() => router.push('/field/speak')} /></View> : null}
            {message.action?.kind === 'navigate' ? <Button label={message.action.label} variant="ghost" onPress={() => router.push((message.action as Extract<AssistantAction, { kind: 'navigate' }>).href as never)} /> : null}
          </View>)}
          {pending ? <View accessibilityLabel={tx('답변 준비 중', 'Preparing a reply')} style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.body}>{tx('생각하는 중…', 'Thinking…')}</Text></View> : null}
        </ScrollView>
        {messages.length === 0 ? <View style={styles.suggestionSection}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 물어보세요', 'Try asking like this')}</Text><View style={styles.suggestions}>{SUGGESTIONS.map((suggestion) => <Pressable key={suggestion} accessibilityRole="button" accessibilityState={{ disabled: pending }} disabled={pending} onPress={() => send(suggestion)} style={styles.suggestion}><Text variant="body" weight="medium">{suggestion}</Text></Pressable>)}</View></View> : null}
        {!desktop ? tools : null}
        <View style={styles.composer}><TextInput accessibilityLabel={tx('가볼래 AI에게 메시지', 'Message to GABOLLE AI')} value={input} onChangeText={setInput} onSubmitEditing={() => send()} editable={!pending} returnKeyType="send" multiline placeholder={tx('예: 광안리 맛집 위주로 2명 일정 짜줘', 'e.g. plan a trip for 2 focused on Gwangalli restaurants')} placeholderTextColor={color.text.muted} style={styles.input} /><Pressable accessibilityRole="button" accessibilityLabel={tx('메시지 보내기', 'Send message')} accessibilityState={{ disabled: !input.trim() || pending }} disabled={!input.trim() || pending} onPress={() => send()} style={[styles.send, (!input.trim() || pending) && styles.sendDisabled]}><Text weight="bold" color={color.text.onAction}>↑</Text></Pressable></View>
        <Text variant="caption" color={color.text.muted} style={styles.disclaimer}>{tx('안전 조건은 AI가 변경하지 않으며, 일정 적용 전 반드시 확인합니다.', 'The AI never changes your safety conditions, and you always review before applying to your itinerary.')}</Text>
      </View>
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { gap: spacing[3] }, desktopScreen: { paddingTop: spacing[4] }, header: { marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, desktopHeader: { minHeight: 64, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, identity: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, avatar: { width: 44, height: 44 }, desktopAvatar: { width: 52, height: 52 }, close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  workspace: { flex: 1 }, workspaceDesktop: { flexDirection: 'row', gap: spacing[4], minHeight: 0 }, sidebar: { width: 290, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, chatPanel: { flex: 1, gap: spacing[3], minHeight: 0 }, chatPanelDesktop: { padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card },
  messages: { flex: 1 }, messageContent: { gap: spacing[3], paddingVertical: spacing[3] }, messageContentDesktop: { paddingHorizontal: spacing[2] }, bubble: { maxWidth: '88%', padding: spacing[3], borderRadius: radius.lg, gap: spacing[3] }, bubbleDesktop: { maxWidth: '72%' }, userBubble: { alignSelf: 'flex-end', backgroundColor: color.brand.navy, borderBottomRightRadius: radius.sm }, assistantBubble: { alignSelf: 'flex-start', backgroundColor: color.surface.soft, borderBottomLeftRadius: radius.sm }, actionCard: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  suggestionSection: { gap: spacing[2] }, suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, suggestion: { minHeight: 48, maxWidth: '100%', justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.action.secondary, borderRadius: radius.full, backgroundColor: color.surface.card },
  toolSection: { gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.soft }, toolSectionDesktop: { marginTop: spacing[3], padding: 0, backgroundColor: 'transparent' }, sectionHeading: { gap: spacing[1] }, quickTools: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, quickToolsDesktop: { flexDirection: 'column', flexWrap: 'nowrap' }, quickTool: { position: 'relative', width: '47%', minHeight: 72, justifyContent: 'center', gap: spacing[1], paddingLeft: spacing[3], paddingRight: spacing[6], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card }, quickToolDesktop: { width: '100%', minHeight: 76, paddingHorizontal: spacing[3] }, quickToolPressed: { opacity: 0.76, backgroundColor: color.state.warningBg }, toolArrow: { position: 'absolute', right: spacing[3] },
  composer: { flexDirection: 'row', alignItems: 'flex-end', gap: spacing[2], padding: spacing[2], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, input: { flex: 1, minHeight: 44, maxHeight: 112, paddingHorizontal: spacing[2], paddingVertical: spacing[2], color: color.text.heading, fontSize: 16 }, send: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange }, sendDisabled: { opacity: 0.4 }, disclaimer: { textAlign: 'center' },
});
