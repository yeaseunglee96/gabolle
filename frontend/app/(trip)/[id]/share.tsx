// 23 공유·공동 편집 — Figma 23_공유·공동 편집 실측 그대로.
//
// 동행자 초대·읽기 전용 링크·공유본 복제는 실제 초대·권한 시스템이 아직 없어 자리만 둔다.
// "초대 링크 만들기" 버튼만 OS 공유 시트로 목업 링크를 띄운다(네트워크 호출 없음, 새 의존성
// 아닌 react-native 내장 Share).
import { Pressable, Share as RNShare, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

const SHARE_ROWS = [
  { title: '동행자 초대', desc: '카카오톡·링크로 편집 권한 초대' },
  { title: '읽기 전용 링크', desc: '수정 없이 일정만 안전하게 공유' },
  { title: '공유본 복제', desc: '내 일정으로 복사해 자유롭게 수정' },
];

export default function TripShare() {
  function inviteLink() {
    // TODO: 실제 초대 링크 발급 API 미정 — 목업 링크만 공유 시트로 띄운다.
    RNShare.share({ message: '부산 여행 일정에 초대합니다 — https://gabolle.app/invite/demo-trip' });
  }

  return (
    <Screen scroll>
      <Text variant="display" weight="bold">
        동행자와 함께 계획
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        초대부터 실시간 편집, 공유본 복제까지
      </Text>

      <View style={styles.hero}>
        <Text variant="title" weight="bold" color={color.action.secondary}>
          ● ● ● +2
        </Text>
        <Text variant="body" weight="bold" style={styles.heroTitle}>
          5명이 같은 일정을 보고 있어요
        </Text>
        <Text variant="caption" style={styles.heroSub}>
          마지막 편집 · 민준님 1분 전
        </Text>
      </View>

      <View style={styles.list}>
        {SHARE_ROWS.map((row) => (
          <Pressable key={row.title} style={styles.card}>
            <View style={styles.cardBody}>
              <Text variant="body" weight="bold">
                {row.title}
              </Text>
              <Text variant="caption" style={styles.cardDesc}>
                {row.desc}
              </Text>
            </View>
            <Text variant="title" weight="bold" color={color.action.secondary}>
              ›
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.permissionCard}>
        <Text variant="caption" weight="bold" color={color.state.success}>
          ✓ 항목 단위 공동 편집 활성화{'\n'}장소 변경은 실시간으로 모두에게 표시돼요.
        </Text>
      </View>

      <Button label="동행자 초대 링크 만들기" variant="secondary" containerStyle={styles.cta} onPress={inviteLink} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
    color: color.text.body,
  },
  hero: {
    backgroundColor: color.surface.tint,
    borderRadius: radius.lg,
    padding: spacing[4],
    gap: spacing[2],
  },
  heroTitle: {
    marginTop: spacing[1],
  },
  heroSub: {
    color: color.text.body,
  },
  list: {
    marginTop: spacing[4],
    gap: spacing[3],
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[4],
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardDesc: {
    color: color.text.body,
  },
  permissionCard: {
    marginTop: spacing[4],
    backgroundColor: color.state.successBg,
    borderRadius: radius.lg,
    padding: spacing[4],
  },
  cta: {
    marginTop: spacing[6],
  },
});
