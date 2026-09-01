// 14 동백이 AI 챗봇 — Figma 14_동백이 AI 챗봇 실측 그대로.
//
// 🔴 Jira 에도 명세에도 없는 완전 신규 화면이다. Figma 만 있고 백엔드 계약이 없다.
// TODO: 챗봇 API 미정 (Jira·명세에 없음). 아래 대화·응답은 전부 고정 목업이고
// 실제로 어디에도 전송되지 않는다 — 입력창과 빠른 질문을 눌러도 정해진 문장만 돌아온다.
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';

type Sender = 'user' | 'bot';
type Message = { id: string; sender: Sender; text: string };

type QuickQuestion = {
  key: string;
  question: string;
  label: string;
  reply: string;
  tinted?: boolean;
};

const QUICK_QUESTIONS: QuickQuestion[] = [
  { key: 'weather', question: '비 오는 날 코스로 바꿔줘', label: '날씨 반영', reply: '비 예보를 반영해 실내 코스로 바꿔드릴게요.' },
  {
    key: 'accessibility',
    question: '걷기 덜 힘든 길로 안내해줘',
    label: '접근성',
    reply: '경사·계단을 피한 동선으로 다시 계산할게요.',
    tinted: true,
  },
  { key: 'congestion', question: '덜 붐비는 맛집 알려줘', label: '혼잡도', reply: '지금 혼잡도가 낮은 맛집으로 바꿔서 알려드릴게요.' },
  { key: 'edit', question: '이 장소를 일정에서 빼줘', label: '일정 수정', reply: '해당 장소를 일정에서 빼고 동선을 다시 계산할게요.' },
];

const INITIAL_MESSAGES: Message[] = [
  { id: 'm1', sender: 'user', text: '광안리 대신 조용한 바다로 바꿔줘' },
  { id: 'm2', sender: 'bot', text: '다대포로 바꾸고 동선도 다시 계산할게요.' },
];

export default function Chat() {
  const router = useRouter();
  const [messages, setMessages] = useState<Message[]>(INITIAL_MESSAGES);
  const [input, setInput] = useState('');

  function send(question: string, reply: string) {
    const trimmed = question.trim();
    if (!trimmed) return;
    setMessages((prev) => [
      ...prev,
      { id: `u-${prev.length}`, sender: 'user', text: trimmed },
      { id: `b-${prev.length}`, sender: 'bot', text: reply },
    ]);
  }

  function handleSend() {
    send(input, '확인했어요. 반영해서 다시 알려드릴게요.');
    setInput('');
  }

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerLeft}>
          <View style={styles.avatar}>
            <Text variant="title">🌺</Text>
          </View>
          <View>
            <Text variant="title" weight="bold">
              동백이
            </Text>
            <Text variant="caption">부산 여행 AI 도우미</Text>
          </View>
        </View>
        <View style={styles.headerRight}>
          <View style={styles.onlineBadge}>
            <View style={styles.onlineDot} />
            <Text variant="caption" weight="bold" color={color.action.primary}>
              온라인
            </Text>
          </View>
          <Pressable style={styles.closeButton} onPress={() => router.back()}>
            <Text variant="title" color={color.text.body}>
              ×
            </Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.greeting}>
        <Text variant="title">🌺</Text>
        <View style={styles.greetingCopy}>
          <Text variant="body" weight="bold">
            좋은 아침이에요, 미리님!
          </Text>
          <Text variant="caption" style={styles.greetingSub}>
            오늘 일정과 현장 정보를 같이 살펴볼까요?
          </Text>
        </View>
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        오늘의 여행
      </Text>
      <View style={styles.summaryCard}>
        <Text variant="caption" weight="bold" color={color.action.primary}>
          DAY 1 · 바다와 골목
        </Text>
        <Text variant="body" weight="bold" style={styles.summaryRoute}>
          송도 → 남포동 → 흰여울
        </Text>
        <Text variant="caption" style={styles.summarySub}>
          다음 장소 12:30 · 이동 15분 · 혼잡도 보통
        </Text>
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        바로 물어보기
      </Text>
      <View style={styles.quickGrid}>
        {QUICK_QUESTIONS.map((item) => (
          <Pressable
            key={item.key}
            style={[styles.quickCard, item.tinted && styles.quickCardTinted]}
            onPress={() => send(item.question, item.reply)}
          >
            <Text variant="caption" weight="bold" style={styles.quickQuestion}>
              {item.question}
            </Text>
            <Text variant="caption" color={item.tinted ? color.action.primary : color.text.body}>
              {item.label}
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.conversation}>
        {messages.map((message) =>
          message.sender === 'user' ? (
            <View key={message.id} style={styles.userMessage}>
              <Text variant="caption" color={color.text.body}>
                미리
              </Text>
              <Text variant="body" weight="medium" style={styles.userText}>
                {message.text}
              </Text>
            </View>
          ) : (
            <View key={message.id} style={styles.botBubble}>
              <Text variant="caption" weight="medium" color={color.action.primary}>
                {message.text}
              </Text>
            </View>
          ),
        )}
      </View>

      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="동백이에게 물어보세요"
          placeholderTextColor={color.text.body}
          value={input}
          onChangeText={setInput}
          onSubmitEditing={handleSend}
          returnKeyType="send"
        />
        <Pressable style={styles.sendButton} onPress={handleSend}>
          <Text variant="body" weight="bold" color={color.text.onAction}>
            ➤
          </Text>
        </Pressable>
      </View>
      <Text variant="caption" style={styles.footerNote}>
        추천 근거와 변경 내용을 함께 알려드려요.
      </Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  headerLeft: {
    flexDirection: 'row',
    gap: spacing[3],
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: radius.full,
    backgroundColor: color.surface.tint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  onlineBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
    backgroundColor: color.surface.tint,
    borderRadius: radius.full,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  onlineDot: {
    width: 7,
    height: 7,
    borderRadius: radius.full,
    backgroundColor: color.state.success,
  },
  closeButton: {
    width: 38,
    height: 38,
    borderRadius: radius.full,
    backgroundColor: color.surface.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  greeting: {
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[4],
    backgroundColor: color.surface.tint,
    borderRadius: radius.lg,
    padding: spacing[4],
    alignItems: 'flex-start',
  },
  greetingCopy: {
    flex: 1,
    gap: spacing[1],
  },
  greetingSub: {
    color: color.text.body,
  },
  sectionTitle: {
    marginTop: spacing[6],
    marginBottom: spacing[3],
  },
  summaryCard: {
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  summaryRoute: {
    marginTop: spacing[1],
  },
  summarySub: {
    color: color.text.body,
  },
  quickGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[3],
  },
  quickCard: {
    width: '47%',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
    gap: spacing[2],
  },
  quickCardTinted: {
    backgroundColor: color.surface.tint,
  },
  quickQuestion: {
    color: color.text.heading,
  },
  conversation: {
    marginTop: spacing[6],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[3],
  },
  userMessage: {
    gap: spacing[1],
  },
  userText: {
    color: color.text.heading,
  },
  botBubble: {
    alignSelf: 'flex-start',
    maxWidth: '80%',
    backgroundColor: color.surface.tint,
    borderRadius: radius.md,
    padding: spacing[3],
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  input: {
    flex: 1,
    fontSize: 15,
    color: color.text.heading,
    paddingVertical: spacing[2],
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    backgroundColor: color.action.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footerNote: {
    marginTop: spacing[3],
    textAlign: 'center',
  },
});
