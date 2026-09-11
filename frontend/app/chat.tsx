import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';
import { useI18n } from '@/i18n';

type Message = { id: number; role: 'user' | 'assistant'; text: string; action?: AssistantAction; applied?: boolean };
// 🔴 아래 예시 문구는 한국어 입력만 인식하는 이해 로직(src/assistant/intent.ts)에 맞춘 것이다.
// 영어로 바꾸면 그 매처가 알아듣지 못해 기능이 깨지므로, 영어 모드에서도 예시는 한국어로 남긴다.
const SUGGESTIONS = ['해운대와 광안리 2명 맛집 일정 짜줘', '사진 부탁할 때 한국어 문장 알려줘', '메뉴판 번역하고 싶어'];
const QUICK_TOOLS = [
  { labelKo: '일정 만들기', labelEn: 'Plan a trip', hintKo: '대화 조건 적용', hintEn: 'Applies chat conditions', href: '/plan/basic' },
  { labelKo: '현장 말하기', labelEn: 'On-the-go phrases', hintKo: '문장 크게 보기·음성', hintEn: 'Large text · voice', href: '/field/speak' },
  { labelKo: '메뉴판 번역', labelEn: 'Menu translation', hintKo: '카메라 번역 준비 중', hintEn: 'Camera translation coming soon', href: '/field/translate' },
] as const;

export default function Chat() {
  const router = useRouter(); const { update } = usePlan();
  const { width } = useLayout();
  const { tx } = useI18n();
  const desktop = isAtLeast(width, 'md');
  const [input, setInput] = useState('');
  // 인사말은 언어 환경설정이 뒤늦게 준비돼도 반영돼야 해서 state 초깃값(마운트 시 한 번만 평가됨)에
  // 넣지 않고, 렌더마다 tx() 로 새로 계산해 목록 앞에 붙인다.
  const [messages, setMessages] = useState<Message[]>([]);
  const nextId = useMemo(() => (messages.length ? Math.max(...messages.map((message) => message.id)) : 0) + 1, [messages]);
  function send(value = input) { const content = value.trim(); if (!content) return; const action = understandAssistantMessage(content); setMessages((current) => [...current, { id: nextId, role: 'user', text: content }, { id: nextId + 1, role: 'assistant', text: action.reply, action }]); setInput(''); }
  function applyPlan(id: number, action: Extract<AssistantAction, { kind: 'plan' }>) { update(action.patch); setMessages((current) => current.map((item) => item.id === id ? { ...item, applied: true } : item)); }

  const visibleTools = QUICK_TOOLS.slice(1);
  const tools = <View accessibilityLabel={tx('여행 도구 바로가기', 'Trip tool shortcuts')} style={[styles.toolSection, desktop && styles.toolSectionDesktop]}>
    {!desktop ? <View style={styles.sectionHeading}><Text variant="body" weight="bold">{tx('대화 없이 바로 실행', 'Run these without chatting')}</Text><Text variant="caption" color={color.text.body}>{tx('여행 중 급할 때 바로 열어보세요.', 'Open these right away when you need them on your trip.')}</Text></View> : null}
    <View style={[styles.quickTools, desktop && styles.quickToolsDesktop]}>{visibleTools.map((tool) => <Pressable key={tool.href} accessibilityRole="button" accessibilityLabel={`${tx(tool.labelKo, tool.labelEn)}, ${tx(tool.hintKo, tool.hintEn)}`} onPress={() => router.push(tool.href)} style={({ pressed }) => [styles.quickTool, desktop && styles.quickToolDesktop, pressed && styles.quickToolPressed]}><Text variant="body" weight="bold">{tx(tool.labelKo, tool.labelEn)}</Text><Text variant="caption" color={color.text.body}>{tx(tool.hintKo, tool.hintEn)}</Text><Text variant="title" weight="bold" color={color.brand.orange} style={styles.toolArrow}>›</Text></Pressable>)}</View>
  </View>;

  return <Screen wide style={[styles.screen, desktop && styles.desktopScreen]}>
    <View style={[styles.header, desktop && styles.desktopHeader]}><View style={styles.identity}><GabolleMascot state="open" delay={180} style={desktop ? styles.desktopAvatar : styles.avatar} /><View><Text variant={desktop ? 'display' : 'title'} weight="bold">{tx('가볼래 AI', 'GABOLLE AI')}</Text><Text variant="caption" color={color.text.body}>{tx('앱 기능을 실행하는 부산 여행 도우미', 'A Busan travel assistant that runs app features for you')}</Text></View></View><Pressable accessibilityRole="button" accessibilityLabel={tx('채팅 닫기', 'Close chat')} onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.close}><Text variant="title">×</Text></Pressable></View>
    <View style={[styles.workspace, desktop && styles.workspaceDesktop]}>
      {desktop ? <View style={styles.sidebar}><Text variant="eyebrow" weight="bold" color={color.brand.orange}>TRAVEL TOOLS</Text><Text variant="title" weight="bold" color={color.text.onAction}>{tx('여행 중 필요한 기능을 바로 실행하세요', 'Run the features you need for your trip right away')}</Text><Text variant="body" color={color.text.onAction}>{tx('현장 문장은 크게 보거나 음성으로 듣고, 메뉴판 번역 도구도 바로 열 수 있어요.', 'View on-the-go phrases in large text or hear them aloud, and open the menu translation tool right away.')}</Text>{tools}</View> : null}
      <View style={[styles.chatPanel, desktop && styles.chatPanelDesktop]}>
        <ScrollView style={styles.messages} contentContainerStyle={[styles.messageContent, desktop && styles.messageContentDesktop]} keyboardShouldPersistTaps="handled">
          <View style={[styles.bubble, desktop && styles.bubbleDesktop, styles.assistantBubble]}><Text color={color.text.heading}>{tx('안녕하세요! 부산 일정과 여행 중 필요한 말을 앱 기능으로 바로 도와드릴게요.', 'Hi! I can help with your Busan itinerary and useful phrases for your trip, right from the app.')}</Text></View>
          {messages.map((message) => <View key={message.id} style={[styles.bubble, desktop && styles.bubbleDesktop, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}><Text color={message.role === 'user' ? color.text.onAction : color.text.heading}>{message.text}</Text>
            {message.action?.kind === 'plan' && message.action.summary.length ? <View style={styles.actionCard}><Text variant="caption" weight="bold">{tx('찾은 여행 조건', 'Conditions found')}</Text><Text variant="caption" color={color.text.body}>{message.action.summary.join(' · ')}</Text><Button label={message.applied ? tx('일정 초안에 적용됨 ✓', 'Applied to draft itinerary ✓') : tx('일정에 적용하고 확인하기', 'Apply to itinerary and review')} disabled={message.applied} onPress={() => { applyPlan(message.id, message.action as Extract<AssistantAction, { kind: 'plan' }>); router.push('/plan/basic'); }} /></View> : null}
            {message.action?.kind === 'phrase' ? <View style={styles.actionCard}><Text variant="title" weight="bold">{message.action.korean}</Text><Text variant="caption" color={color.text.muted}>{message.action.pronunciation}</Text><Button label={tx('크게 보고 듣기', 'View large & listen')} variant="field" onPress={() => router.push('/field/speak')} /></View> : null}
            {message.action?.kind === 'navigate' ? <Button label={message.action.label} variant="ghost" onPress={() => router.push((message.action as Extract<AssistantAction, { kind: 'navigate' }>).href as never)} /> : null}
          </View>)}
        </ScrollView>
        {messages.length === 0 ? <View style={styles.suggestionSection}><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이렇게 물어보세요', 'Try asking like this')}</Text><View style={styles.suggestions}>{SUGGESTIONS.map((suggestion) => <Pressable key={suggestion} accessibilityRole="button" onPress={() => send(suggestion)} style={styles.suggestion}><Text variant="body" weight="medium">{suggestion}</Text></Pressable>)}</View></View> : null}
        {!desktop ? tools : null}
        <View style={styles.composer}><TextInput accessibilityLabel={tx('가볼래 AI에게 메시지', 'Message to GABOLLE AI')} value={input} onChangeText={setInput} onSubmitEditing={() => send()} returnKeyType="send" multiline placeholder={tx('예: 광안리 맛집 위주로 2명 일정 짜줘', 'e.g. plan a trip for 2 focused on Gwangalli restaurants')} placeholderTextColor={color.text.muted} style={styles.input} /><Pressable accessibilityRole="button" accessibilityLabel={tx('메시지 보내기', 'Send message')} accessibilityState={{ disabled: !input.trim() }} disabled={!input.trim()} onPress={() => send()} style={[styles.send, !input.trim() && styles.sendDisabled]}><Text weight="bold" color={color.text.onAction}>↑</Text></Pressable></View>
        <Text variant="caption" color={color.text.muted} style={styles.disclaimer}>{tx('안전 조건은 AI가 변경하지 않으며, 일정 적용 전 반드시 확인합니다.', 'The AI never changes your safety conditions, and you always review before applying to your itinerary.')}</Text>
      </View>
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { gap: spacing[3] }, desktopScreen: { paddingTop: spacing[4] }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, desktopHeader: { minHeight: 64, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, identity: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, avatar: { width: 44, height: 44 }, desktopAvatar: { width: 52, height: 52 }, close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  workspace: { flex: 1 }, workspaceDesktop: { flexDirection: 'row', gap: spacing[4], minHeight: 0 }, sidebar: { width: 290, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.navy }, chatPanel: { flex: 1, gap: spacing[3], minHeight: 0 }, chatPanelDesktop: { padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card },
  messages: { flex: 1 }, messageContent: { gap: spacing[3], paddingVertical: spacing[3] }, messageContentDesktop: { paddingHorizontal: spacing[2] }, bubble: { maxWidth: '88%', padding: spacing[3], borderRadius: radius.lg, gap: spacing[3] }, bubbleDesktop: { maxWidth: '72%' }, userBubble: { alignSelf: 'flex-end', backgroundColor: color.brand.navy, borderBottomRightRadius: radius.sm }, assistantBubble: { alignSelf: 'flex-start', backgroundColor: color.surface.soft, borderBottomLeftRadius: radius.sm }, actionCard: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  suggestionSection: { gap: spacing[2] }, suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, suggestion: { minHeight: 48, maxWidth: '100%', justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.action.secondary, borderRadius: radius.full, backgroundColor: color.surface.card },
  toolSection: { gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.soft }, toolSectionDesktop: { marginTop: spacing[3], padding: 0, backgroundColor: 'transparent' }, sectionHeading: { gap: spacing[1] }, quickTools: { flexDirection: 'row', gap: spacing[2] }, quickToolsDesktop: { flexDirection: 'column' }, quickTool: { position: 'relative', flex: 1, minHeight: 72, justifyContent: 'center', gap: spacing[1], paddingLeft: spacing[3], paddingRight: spacing[6], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, backgroundColor: color.surface.card }, quickToolDesktop: { flex: 0, minHeight: 76, paddingHorizontal: spacing[3] }, quickToolPressed: { opacity: 0.76, backgroundColor: color.state.warningBg }, toolArrow: { position: 'absolute', right: spacing[3] },
  composer: { flexDirection: 'row', alignItems: 'flex-end', gap: spacing[2], padding: spacing[2], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, input: { flex: 1, minHeight: 44, maxHeight: 112, paddingHorizontal: spacing[2], paddingVertical: spacing[2], color: color.text.heading, fontSize: 16 }, send: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange }, sendDisabled: { opacity: 0.4 }, disclaimer: { textAlign: 'center' },
});
