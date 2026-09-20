// 마이페이지 프로필 카드 — **폰 전용** (시안 02).
//
// 🔴 넓은 화면은 이 카드를 안 쓴다. 커버 사진이 화면 전폭으로 깔리고 그 위에 프로필이
//    얹힌다(MyPageCover). 그래서 이 파일에 있던 넓은 화면 가지는 전부 죽은 코드였고
//    걷어냈다 — 남겨 두면 다음 사람이 고치고서 「왜 안 바뀌지」를 한참 찾는다.
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';

/** 사진이 없는 계정도 빈 회색 판이 아니다 — 넓은 화면(MyPageCover)과 같은 부산 기본 사진. S15P21E201-1375 */
const DEFAULT_COVER = require('../../assets/home/web-hero.png');

export type ProfileCardCount = { label: string; value: number | null; onPress?: () => void };

export type ProfileCardProps = {
  name: string;
  /** 로그인 전이면 null. 그때는 이메일 줄 대신 안내를 쓴다. */
  email: string | null;
  avatarUri: string | null;
  coverUri: string | null;
  counts: ProfileCardCount[];
  onEdit: () => void;
  tx: (ko: string, en: string) => string;
};

export function ProfileCard({ name, email, avatarUri, coverUri, counts, onEdit, tx }: ProfileCardProps) {
  const initial = name.trim().slice(0, 1) || '·';
  return (
    <View style={styles.card}>
      <View style={styles.cover}>
        <Image source={coverUri ? { uri: coverUri } : DEFAULT_COVER} resizeMode="cover" style={styles.coverPhoto} accessibilityLabel={coverUri ? tx('배경 사진', 'Cover photo') : ''} />
      </View>

      <View style={styles.body}>
        <View style={styles.topRow}>
          <View style={styles.avatar}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" style={styles.avatarPhoto} accessibilityLabel={tx('프로필 사진', 'Profile photo')} />
              : <Text variant="display" weight="bold" color={color.text.onAction}>{initial}</Text>}
          </View>
          <Pressable accessibilityRole="button" onPress={onEdit} style={styles.editButton}>
            <Text weight="bold" color={color.action.outline} numberOfLines={1}>{tx('프로필 편집', 'Edit profile')}</Text>
          </Pressable>
        </View>

        <Text variant="display" weight="bold" color={color.text.heading} numberOfLines={1}>{name}</Text>
        <Text variant="caption" color={color.text.muted} numberOfLines={1}>
          {email || tx('로그인 없이 앱을 둘러보는 중이에요', 'Browsing without an account')}
        </Text>

        {/* 🔴 숫자가 버튼이 된다(시안 02). 전에는 글자라 눌러도 되는지 안 보였다 —
            눌리는 것은 눌리게 생겨야 한다. */}
        <View style={styles.counts}>
          {counts.map((count) => (
            <Pressable
              key={count.label}
              accessibilityRole={count.onPress ? 'button' : undefined}
              accessibilityLabel={count.onPress ? txf(tx, '%s 보기', 'View %s', count.label) : undefined}
              disabled={!count.onPress}
              onPress={count.onPress}
              style={({ pressed }) => [styles.count, pressed && styles.pressed]}
            >
              {/* 아직 못 받은 수를 0 으로 그리지 않는다. 0 은 「없다」는 뜻이고
                  못 받은 것은 「모른다」인데, 0 이 찍히면 사람은 앞의 뜻으로 읽는다. */}
              <Text variant="title" weight="bold" numberOfLines={1}>{count.value === null ? '–' : count.value}</Text>
              <Text variant="caption" color={color.text.muted} numberOfLines={1}>{count.label}</Text>
            </Pressable>
          ))}
        </View>
      </View>
    </View>
  );
}

/** 시안 02 의 값. 아바타가 걸쳐 앉을 만큼만 있으면 된다. */
const COVER_HEIGHT = 120;
const AVATAR = 88;

const styles = StyleSheet.create({
  // 폰에도 테두리와 둥근 모서리를 준다(시안 02). 전에는 띠처럼 화면 폭을 꽉 채웠다.
  card: { borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, overflow: 'hidden' },
  cover: { height: COVER_HEIGHT, backgroundColor: color.surface.soft },
  coverPhoto: { width: '100%', height: '100%' },
  body: { gap: spacing[2], paddingHorizontal: spacing[4], paddingBottom: spacing[4], marginTop: -44 },
  topRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: spacing[3] },
  avatar: {
    width: AVATAR, height: AVATAR, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.action.secondary, borderWidth: 4, borderColor: color.surface.card, overflow: 'hidden',
  },
  avatarPhoto: { width: '100%', height: '100%' },
  editButton: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.action.outline },

  counts: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] },
  count: {
    flex: 1, minHeight: 56, alignItems: 'center', justifyContent: 'center', gap: 2,
    borderRadius: radius.md, backgroundColor: color.surface.soft,
  },
  pressed: { opacity: 0.72 },
});
