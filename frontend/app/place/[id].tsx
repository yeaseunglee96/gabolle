// 13 장소 상세 — Figma 13_장소 상세 실측 그대로.
//
// 히어로 이미지가 화면 가장자리까지 채워야 해서(Figma 실측) index.tsx(01 Welcome)와 같은
// 이유로 공용 Screen 컴포넌트를 쓰지 않는다 — 위쪽은 히어로가, 아래쪽 본문은 직접 좌우
// 여백(gutter)을 준다.
//
// 실제 API 연동 전까지 id 와 무관하게 흰여울문화마을 고정 목업을 보여준다(다른 상세류
// 화면인 11 여행 결과·12 지도·동선 도 같은 방식이다).
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { color, gutter, radius, spacing } from '@/design/tokens';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';

const TAGS = [
  { label: '혼잡 여유', bg: color.state.successBg, fg: color.state.success },
  { label: '외국어 안내', bg: color.surface.soft, fg: color.action.brand },
  { label: '부산 로컬 음식', bg: color.state.dangerBg, fg: color.state.danger },
];

const INFO_ROWS = [
  { label: '운영', value: '연중무휴 · 골목 상점 10:00–19:00' },
  { label: '이동', value: '흰여울 정류장 도보 6분' },
  { label: '접근성', value: '경사·계단 있음 · 우회 동선 제공' },
  { label: '언어', value: '한국어·영어·일본어 안내' },
];

export default function Place() {
  const router = useRouter();
  const [liked, setLiked] = useState(false);

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.scrollBody}>
        <View style={styles.hero}>
          {/* TODO: 실제 장소 사진. 자산이 오기 전까지 브랜드 색 면으로 대체한다. */}
          <SafeAreaView edges={['top']} style={styles.heroTopRow}>
            <Pressable style={styles.heroButton} onPress={() => router.back()}>
              <Text variant="title" weight="bold" color={color.text.heading}>
                ‹
              </Text>
            </Pressable>
            <Pressable style={styles.heroButton} onPress={() => setLiked((prev) => !prev)}>
              <Text variant="title" color={liked ? color.state.danger : color.text.heading}>
                {liked ? '♥' : '♡'}
              </Text>
            </Pressable>
          </SafeAreaView>

          <View style={styles.heroCopy}>
            <Text variant="display" weight="bold" color={color.text.onAction}>
              흰여울문화마을
            </Text>
            <Text variant="body" color={color.text.onAction} style={styles.heroSubtitle}>
              영도 · 골목 산책 · 바다 전망
            </Text>
            {/* Figma 는 별점을 금색(#ffd785)으로 따로 뽑았지만, 히어로 위 글자는 이 화면도
                제목·부제와 같은 흰색으로 통일한다 — 한 화면에 하나 뿐인 색을 위해 토큰을
                새로 만들 만큼 무겁지 않다. */}
            <Text variant="caption" weight="bold" color={color.text.onAction}>
              ★ 4.8 · 리뷰 1,204
            </Text>
          </View>
        </View>

        <View style={styles.body}>
          <View style={styles.tagsRow}>
            {TAGS.map((tag) => (
              <View key={tag.label} style={[styles.tag, { backgroundColor: tag.bg }]}>
                <Text variant="caption" weight="bold" color={tag.fg}>
                  {tag.label}
                </Text>
              </View>
            ))}
          </View>

          <Text variant="title" weight="bold" style={styles.sectionTitle}>
            현지인이 알려주는 포인트
          </Text>
          <Card style={styles.tipCard}>
            <Text variant="body">“오후 4시 이후 절영해안산책로 방향이 가장 예뻐요.”</Text>
            <Text variant="caption" color={color.text.body}>
              영도 주민 · 수진 님
            </Text>
          </Card>

          <Text variant="title" weight="bold" style={styles.sectionTitle}>
            방문 정보
          </Text>
          <View style={styles.infoList}>
            {INFO_ROWS.map((row, index) => (
              <View key={row.label}>
                <View style={styles.infoRow}>
                  <Text variant="caption" weight="bold" color={color.text.body} style={styles.infoLabel}>
                    {row.label}
                  </Text>
                  <Text variant="body" color={color.text.heading} style={styles.infoValue}>
                    {row.value}
                  </Text>
                </View>
                {index < INFO_ROWS.length - 1 && <View style={styles.divider} />}
              </View>
            ))}
          </View>

          {/* 14 동백이 AI 챗봇 화면(app/chat.tsx)이 이미 있어 실제로 잇는다. */}
          <Pressable style={styles.chatCta} onPress={() => router.push('/chat')}>
            <Text variant="body" weight="bold" color={color.action.brand}>
              🌺 동백이에게 이 장소 물어보기
            </Text>
          </Pressable>
        </View>
      </ScrollView>

      {/* TODO: 저장·일정 추가는 실제 여행 데이터 모델이 아직 없어 동작하지 않는다. */}
      <SafeAreaView edges={['bottom']} style={styles.footer}>
        <View style={styles.footerSave}>
          <Button label="저장" variant="ghost" />
        </View>
        <View style={styles.footerAdd}>
          <Button label="15:00 일정에 추가" />
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: color.canvas,
  },
  scrollBody: {
    paddingBottom: spacing[8],
  },
  hero: {
    height: 292,
    backgroundColor: color.action.primary,
    justifyContent: 'space-between',
  },
  heroTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    paddingTop: spacing[2],
  },
  heroButton: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    backgroundColor: color.surface.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroCopy: {
    paddingHorizontal: gutter,
    paddingBottom: spacing[6],
    gap: spacing[1],
  },
  heroSubtitle: {
    opacity: 0.92,
  },
  body: {
    paddingHorizontal: gutter,
    paddingTop: spacing[4],
  },
  tagsRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  tag: {
    flex: 1,
    borderRadius: radius.full,
    paddingVertical: spacing[2],
    alignItems: 'center',
  },
  sectionTitle: {
    marginTop: spacing[8],
    marginBottom: spacing[3],
  },
  tipCard: {
    gap: spacing[2],
  },
  infoList: {
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingHorizontal: spacing[4],
  },
  infoRow: {
    flexDirection: 'row',
    paddingVertical: spacing[3],
    gap: spacing[3],
  },
  infoLabel: {
    width: 60,
  },
  infoValue: {
    flex: 1,
  },
  divider: {
    height: 1,
    backgroundColor: color.surface.field,
  },
  chatCta: {
    alignItems: 'center',
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    paddingVertical: spacing[4],
    marginTop: spacing[8],
  },
  footer: {
    flexDirection: 'row',
    gap: spacing[3],
    paddingHorizontal: gutter,
    paddingTop: spacing[3],
    backgroundColor: color.canvas,
    borderTopWidth: 1,
    borderTopColor: color.surface.field,
  },
  footerSave: {
    flex: 106,
  },
  footerAdd: {
    flex: 224,
  },
});
