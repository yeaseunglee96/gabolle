// 15 내 정보 — Figma 15_내 정보 실측을 기본 골격으로 쓰되, 계정 관련 항목은 명세를 따른다.
//
// 🔴 Figma 에는 이메일·비밀번호 관련 항목이 원래 없다(확인 결과 그렇다 — 뺄 것이 없었다).
// 대신 로그인 수단이 Google OAuth 하나로 확정되면서 명세(FR-ACC-03~09)가 요구하는
// 항목 중 Figma 에 없는 네 가지를 아래 "계정 관리" 구역에 새로 더한다:
//   언어 전환(기존 "언어 및 번역" 행이 커버) · 개인화 끄기/초기화 · 연결 계정 · 계정 삭제.
// 계정 삭제는 스토어 심사 필수 항목이라 동작은 없어도 자리는 반드시 있어야 한다.
import { type ReactNode, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { color, gutter, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { ProgressBar } from '@/components/ProgressBar';
import { Toggle } from '@/components/Toggle';
import { TabBar } from '@/components/TabBar';

const STATS = [
  { icon: '🧳', value: '4', label: '완료 여행' },
  { icon: '📍', value: '12', label: '저장 장소' },
  { icon: '✍️', value: '3', label: '작성 리뷰' },
];

const TASTE_CHIPS = [
  { icon: '🌊', label: '바다', tinted: true },
  { icon: '🏘', label: '골목', tinted: true },
  { icon: '🍜', label: '미식', tinted: false },
  { icon: '🌶️', label: '맵기 보통', tinted: false },
];

function SettingsRow({
  title,
  value,
  titleColor,
  right,
}: {
  title: string;
  value?: string;
  titleColor?: string;
  right?: ReactNode;
}) {
  return (
    <Pressable style={styles.settingsRow}>
      <Text variant="body" weight="bold" color={titleColor ?? color.text.heading}>
        {title}
      </Text>
      <View style={styles.settingsRowRight}>
        {value && <Text variant="caption">{value}</Text>}
        {right ?? (
          <Text variant="title" color={color.text.muted}>
            ›
          </Text>
        )}
      </View>
    </Pressable>
  );
}

export default function Me() {
  const [personalizationOff, setPersonalizationOff] = useState(false);

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <Text variant="display" weight="bold">
          내 정보
        </Text>
        {/* 설정 화면이 따로 없어 지금은 장식만 한다. */}
        <View style={styles.settingsButton}>
          <Text variant="title">⚙</Text>
        </View>
      </View>

      <Card style={styles.profileCard}>
        <View style={styles.profileTopRow}>
          {/* TODO: 실제 프로필 아바타 자산. 자산이 오기 전까지 이모지 원형으로 대체한다. */}
          <View style={styles.avatar}>
            <Text variant="display">🐷</Text>
            <View style={styles.avatarBadge}>
              <Text variant="caption" weight="bold" color={color.text.onAction}>
                기본
              </Text>
            </View>
          </View>
          <View style={styles.profileInfo}>
            <Text variant="title" weight="bold">
              진미리
            </Text>
            <Text variant="caption" style={styles.profileBio}>
              부산 바다와 골목을 좋아하는 여행자
            </Text>
            <View style={styles.levelBadge}>
              <Text variant="caption" weight="bold" color={color.text.accent}>
                LOCAL Lv.2
              </Text>
            </View>
          </View>
        </View>

        <View style={styles.completionRow}>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            프로필 완성도 80%
          </Text>
          <ProgressBar progress={0.8} />
        </View>

        <Pressable style={styles.avatarChangeButton}>
          <Text variant="body" weight="bold" color={color.text.accent}>
            프로필 이미지 변경
          </Text>
        </Pressable>
      </Card>

      <Text variant="caption" style={styles.note}>
        가입 시 부산 음식 프로필이 자동 배정돼요 · 언제든 변경 가능
      </Text>

      <View style={styles.statsRow}>
        {STATS.map((stat) => (
          <Card key={stat.label} style={styles.statCard}>
            <Text variant="title">{stat.icon}</Text>
            <Text variant="title" weight="bold" color={color.text.accent}>
              {stat.value}
            </Text>
            <Text variant="caption">{stat.label}</Text>
          </Card>
        ))}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        나의 여행 취향
      </Text>
      <View style={styles.tasteGrid}>
        {TASTE_CHIPS.map((chip) => (
          <View
            key={chip.label}
            style={[styles.tasteChip, { backgroundColor: chip.tinted ? color.surface.soft : color.surface.field }]}
          >
            <Text
              variant="caption"
              weight="bold"
              color={chip.tinted ? color.text.accent : color.text.muted}
            >
              {chip.icon} {chip.label}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.settingsGroup}>
        <SettingsRow title="여행 조건 관리" value="예산·접근성·알레르기" />
        <SettingsRow title="언어 및 번역" value="한국어" />
        <SettingsRow title="오프라인 저장" value="부산 지도 256MB" />
      </View>

      {/* Figma 에 없는 구역 — 위 파일 머리말 참고. */}
      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        계정 관리
      </Text>
      <View style={styles.settingsGroup}>
        <SettingsRow title="연결 계정" value="Google · miri@gmail.com" right={<Text variant="caption" weight="bold" color={color.state.success}>연결됨</Text>} />
        <SettingsRow
          title="개인화 추천 끄기"
          right={<Toggle value={personalizationOff} onValueChange={setPersonalizationOff} />}
        />
        {/* TODO: 개인화 데이터 초기화 확인 플로우 미정 — 자리만 둔다. */}
        <SettingsRow title="개인화 데이터 초기화" />
        {/* TODO: 계정 삭제 확인 플로우·화면 미정. 스토어 심사 필수 항목이라 자리만 먼저 둔다. */}
        <SettingsRow title="계정 삭제" titleColor={color.state.danger} />
      </View>

      <View style={styles.tabBarWrap}>
        <TabBar active="me" />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  settingsButton: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    backgroundColor: color.surface.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  profileCard: {
    marginTop: spacing[4],
    gap: spacing[4],
  },
  profileTopRow: {
    flexDirection: 'row',
    gap: spacing[4],
  },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: radius.full,
    backgroundColor: color.state.warningBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarBadge: {
    position: 'absolute',
    bottom: -spacing[1],
    alignSelf: 'center',
    backgroundColor: color.action.brand,
    borderRadius: radius.full,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  profileInfo: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[1],
  },
  profileBio: {
    color: color.text.body,
  },
  levelBadge: {
    alignSelf: 'flex-start',
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
    marginTop: spacing[1],
  },
  completionRow: {
    gap: spacing[2],
  },
  avatarChangeButton: {
    alignItems: 'center',
    backgroundColor: color.canvas,
    borderRadius: radius.md,
    paddingVertical: spacing[2],
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  note: {
    marginTop: spacing[3],
  },
  statsRow: {
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[4],
  },
  statCard: {
    flex: 1,
    alignItems: 'flex-start',
    gap: spacing[1],
  },
  sectionTitle: {
    marginTop: spacing[8],
    marginBottom: spacing[3],
  },
  tasteGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
  },
  tasteChip: {
    width: '48%',
    borderRadius: radius.md,
    paddingVertical: spacing[3],
    alignItems: 'center',
  },
  settingsGroup: {
    gap: spacing[2],
  },
  settingsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  settingsRowRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  tabBarWrap: {
    marginTop: spacing[6],
    marginHorizontal: -gutter,
  },
});
