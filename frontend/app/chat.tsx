import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { usePlan } from '@/plan/PlanProvider';

type Message = { id: number; role: 'user' | 'assistant'; text: string; action?: AssistantAction; applied?: boolean };
const SUGGESTIONS = ['해운대와 광안리 2명 맛집 일정 짜줘', '사진 부탁할 때 한국어 문장 알려줘', '메뉴판 번역하고 싶어'];
const QUICK_TOOLS = [
  { label: '일정 만들기', hint: '대화 조건 적용', href: '/plan/basic' },
  { label: '현장 말하기', hint: '문장 크게 보기·음성', href: '/field/speak' },
  { label: '메뉴판 번역', hint: '카메라 번역 준비 중', href: '/field/translate' },
] as const;

export default function Chat() {
  const router = useRouter(); const { update } = usePlan();
  const { width } = useLayout();
  const desktop = width >= 768;
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([{ id: 1, role: 'assistant', text: '안녕하세요! 부산 일정과 여행 중 필요한 말을 앱 기능으로 바로 도와드릴게요.' }]);
  const nextId = useMemo(() => Math.max(...messages.map((message) => message.id)) + 1, [messages]);
  function send(value = input) { const content = value.trim(); if (!content) return; const action = understandAssistantMessage(content); setMessages((current) => [...current, { id: nextId, role: 'user', text: content }, { id: nextId + 1, role: 'assistant', text: action.reply, action }]); setInput(''); }
  function applyPlan(id: number, action: Extract<AssistantAction, { kind: 'plan' }>) { update(action.patch); setMessages((current) => current.map((item) => item.id === id ? { ...item, applied: true } : item)); }

  const visibleTools = QUICK_TOOLS.slice(1);
  const tools = <View accessibilityLabel="여행 도구 바로가기" style={[styles.toolSection, desktop && styles.toolSectionDesktop]}>
    {!desktop ? <View style={styles.sectionHeading}><Text variant="body" weight="bold">대화 없이 바로 실행</Text><Text variant="caption" color={color.text.body}>여행 중 급할 때 바로 열어보세요.</Text></View> : null}
    <View style={[styles.quickTools, desktop && styles.quickToolsDesktop]}>{visibleTools.map((tool) => <Pressable key={tool.href} accessibilityRole="button" accessibilityLabel={`${tool.label}, ${tool.hint}`} onPress={() => router.push(tool.href)} style={({ pressed }) => [styles.quickTool, desktop && styles.quickToolDesktop, pressed && styles.quickToolPressed]}><Text variant="body" weight="bold">{tool.label}</Text><Text variant="caption" color={color.text.body}>{tool.hint}</Text><Text variant="title" weight="bold" color={color.brand.orange} style={styles.toolArrow}>›</Text></Pressable>)}</View>
  </View>;

  return <Screen wide style={[styles.screen, desktop && styles.desktopScreen]}>
    <View style={[styles.header, desktop && styles.desktopHeader]}><View style={styles.identity}><GabolleMascot state="open" delay={180} style={desktop ? styles.desktopAvatar : styles.avatar} /><View><Text variant={desktop ? 'display' : 'title'} weight="bold">가볼래 AI</Text><Text variant="caption" color={color.text.body}>앱 기능을 실행하는 부산 여행 도우미</Text></View></View><Pressable accessibilityRole="button" accessibilityLabel="채팅 닫기" onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={styles.close}><Text variant="title">×</Text></Pressable></View>
    <View style={[styles.workspace, desktop && styles.workspaceDesktop]}>
      {desktop ? <View style={styles.sidebar}><Text variant="eyebrow" weight="bold" color={color.brand.orange}>TRAVEL TOOLS</Text><Text variant="title" weight="bold" color={color.text.onAction}>여행 중 필요한 기능을 바로 실행하세요</Text><Text variant="body" color={color.text.onAction}>현장 문장은 크게 보거나 음성으로 듣고, 메뉴판 번역 도구도 바로 열 수 있어요.</Text>{tools}</View> : null}
      <View style={[styles.chatPanel, desktop && styles.chatPanelDesktop]}>
        <ScrollView style={styles.messages} contentContainerStyle={[styles.messageContent, desktop && styles.messageContentDesktop]} keyboardShouldPersistTaps="handled">
          {messages.map((message) => <View key={message.id} style={[styles.bubble, desktop && styles.bubbleDesktop, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}><Text color={message.role === 'user' ? color.text.onAction : color.text.heading}>{message.text}</Text>
            {message.action?.kind === 'plan' && message.action.summary.length ? <View style={styles.actionCard}><Text variant="caption" weight="bold">찾은 여행 조건</Text><Text variant="caption" color={color.text.body}>{message.action.summary.join(' · ')}</Text><Button label={message.applied ? '일정 초안에 적용됨 ✓' : '일정에 적용하고 확인하기'} disabled={message.applied} onPress={() => { applyPlan(message.id, message.action as Extract<AssistantAction, { kind: 'plan' }>); router.push('/plan/basic'); }} /></View> : null}
            {message.action?.kind === 'phrase' ? <View style={styles.actionCard}><Text variant="title" weight="bold">{message.action.korean}</Text><Text variant="caption" color={color.text.muted}>{message.action.pronunciation}</Text><Button label="크게 보고 듣기" variant="field" onPress={() => router.push('/field/speak')} /></View> : null}
            {message.action?.kind === 'navigate' ? <Button label={message.action.label} variant="ghost" onPress={() => router.push((message.action as Extract<AssistantAction, { kind: 'navigate' }>).href as never)} /> : null}
          </View>)}
        </ScrollView>
        {messages.length <= 1 ? <View style={styles.suggestionSection}><Text variant="caption" weight="bold" color={color.text.eyebrow}>이렇게 물어보세요</Text><View style={styles.suggestions}>{SUGGESTIONS.map((suggestion) => <Pressable key={suggestion} accessibilityRole="button" onPress={() => send(suggestion)} style={styles.suggestion}><Text variant="body" weight="medium">{suggestion}</Text></Pressable>)}</View></View> : null}
        {!desktop ? tools : null}
        <View style={styles.composer}><TextInput accessibilityLabel="가볼래 AI에게 메시지" value={input} onChangeText={setInput} onSubmitEditing={() => send()} returnKeyType="send" multiline placeholder="예: 광안리 맛집 위주로 2명 일정 짜줘" placeholderTextColor={color.text.muted} style={styles.input} /><Pressable accessibilityRole="button" accessibilityLabel="메시지 보내기" accessibilityState={{ disabled: !input.trim() }} disabled={!input.trim()} onPress={() => send()} style={[styles.send, !input.trim() && styles.sendDisabled]}><Text weight="bold" color={color.text.onAction}>↑</Text></Pressable></View>
        <Text variant="caption" color={color.text.muted} style={styles.disclaimer}>안전 조건은 AI가 변경하지 않으며, 일정 적용 전 반드시 확인합니다.</Text>
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
