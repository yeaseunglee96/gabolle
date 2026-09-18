// 마이페이지 프로필 카드 — 커버 사진 위에 아바타가 걸쳐 앉는다
// 시안: docs/design_handoff_mypage/MyPage.dc.html
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export type ProfileCardCount = { label: string; value: number | null; onPress?: () => void };

export type ProfileCardProps = {
  name: string;
  /** 로그인 전이면 null. 그때는 이메일 줄 대신 안내를 쓴다. */
  email: string | null;
  avatarUri: string | null;
  coverUri: string | null;
  /** 한 줄 소개. 서버에 칸이 없으면 null 이고 줄을 안 그린다. */
  bio: string | null;
  /** 거주지. 위와 같다. */
  homeCity: string | null;
  counts: ProfileCardCount[];
  onEdit: () => void;
  wide: boolean;
  tx: (ko: string, en: string) => string;
};

export function ProfileCard({ name, email, avatarUri, coverUri, bio, homeCity, counts, onEdit, wide, tx }: ProfileCardProps) {
  const initial = name.trim().slice(0, 1) || '·';
  return (
    <View style={[styles.card, wide && styles.cardWide]}>
      <View style={[styles.cover, wide && styles.coverWide, !coverUri && styles.coverEmpty]}>
        {coverUri ? (
          <Image source={{ uri: coverUri }} resizeMode="cover" style={styles.coverPhoto} accessibilityLabel={tx('배경 사진', 'Cover photo')} />
        ) : null}
 {/* 커버 아래를 흰색으로 녹이는 것은 사진이 있을 때만 한다.
            사진은 사용자가 고르는 것이라 어떤 색이 올지 알 수 없어서, 안 녹이면 그 위에
            얹힌 이름이 묻힌다. 반대로 사진이 없으면 녹일 것이 없다 — 그때도 녹이면
            평평한 색 위에 **빈 흰 띠**가 생겨서 화면이 덜 만들어진 것처럼 보인다.
            (2026-09-18 실제로 띄워서 찾았다.) */}
        {coverUri ? <View style={styles.fade} /> : null}
        <View style={styles.eyebrowBadge}>
          <Text variant="caption" weight="bold">{tx('내 계정', 'Account')}</Text>
        </View>
      </View>

      <View style={[styles.body, wide && styles.bodyWide]}>
        <View style={styles.topRow}>
          <View style={[styles.avatar, wide && styles.avatarWide]}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" style={styles.avatarPhoto} accessibilityLabel={tx('프로필 사진', 'Profile photo')} />
              : <Text variant="display" weight="bold" color={color.text.onAction}>{initial}</Text>}
          </View>
          <Pressable accessibilityRole="button" onPress={onEdit} style={styles.editButton}>
            <Text weight="bold" color={color.text.onAction}>{tx('프로필 편집', 'Edit profile')}</Text>
          </Pressable>
        </View>

 {/* `hero` 는 기본 글자색이 흰색이다(tokens 의 defaults). 히어로 사진 위에
            얹으라고 만든 변형이라 그렇다. 여기는 흰 바탕이라 색을 안 주면 **이름이 통째로
            안 보인다** — 시험도 타입도 안 잡는다. 2026-09-18 에 띄워 보고 찾았다. */}
        <Text variant={wide ? 'hero' : 'display'} weight="bold" color={color.text.heading} numberOfLines={1}>{name}</Text>
        {bio ? <Text>{bio}</Text> : null}

        <Text variant="caption" color={color.text.muted} numberOfLines={1}>
          {[email, homeCity].filter(Boolean).join(' · ') || tx('로그인 없이 앱을 둘러보는 중이에요', 'Browsing without an account')}
        </Text>

        <View style={styles.counts}>
          {counts.map((count) => (
            <Pressable
              key={count.label}
              accessibilityRole={count.onPress ? 'button' : undefined}
              disabled={!count.onPress}
              onPress={count.onPress}
              style={styles.count}
            >
              {/* 아직 못 받은 수를 0 으로 그리지 않는다. 0 은 「없다」는 뜻이고
                  못 받은 것은 「모른다」인데, 0 이 찍히면 사람은 앞의 뜻으로 읽는다.
              */}
              <Text weight="bold">{count.value === null ? '–' : count.value}</Text>
              <Text variant="caption" color={color.text.muted}>{count.label}</Text>
            </Pressable>
          ))}
        </View>
      </View>
    </View>
  );
}

const COVER_HEIGHT = 240;
const COVER_HEIGHT_WIDE = 300;
/** 사진이 없을 때는 낮게. 빈 색 띠를 300px 씩 둘 이유가 없다. */
const COVER_HEIGHT_EMPTY = 168;
const AVATAR = 88;
const AVATAR_WIDE = 112;

const styles = StyleSheet.create({
  card: { backgroundColor: color.surface.card, borderBottomWidth: 1, borderColor: color.surface.border, overflow: 'hidden' },
  cardWide: { borderRadius: radius.lg, borderWidth: 1 },
  cover: { height: COVER_HEIGHT, backgroundColor: color.surface.soft },
  coverWide: { height: COVER_HEIGHT_WIDE },
  coverEmpty: { height: COVER_HEIGHT_EMPTY },
  coverPhoto: { width: '100%', height: '100%' },
  // 시안의 흰색 그라데이션을 RN 에서 흉내 낸다 — 아래 절반을 반투명 흰색으로 덮는다.
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '45%', backgroundColor: 'rgba(255,255,255,0.72)' },
  eyebrowBadge: { position: 'absolute', top: spacing[4], left: spacing[4], paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,253,248,0.9)' },
  body: { gap: spacing[2], paddingHorizontal: spacing[6], paddingBottom: spacing[6], marginTop: -56 },
  bodyWide: { paddingHorizontal: spacing[8], marginTop: -72 },
  topRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  avatar: {
    width: AVATAR, height: AVATAR, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.brand.navy, borderWidth: 4, borderColor: color.surface.card, overflow: 'hidden',
  },
  avatarWide: { width: AVATAR_WIDE, height: AVATAR_WIDE, borderWidth: 5 },
  avatarPhoto: { width: '100%', height: '100%' },
  editButton: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.brand.navy },
  counts: { flexDirection: 'row', gap: spacing[6], marginTop: spacing[2] },
  count: { alignItems: 'center', minHeight: 44, justifyContent: 'center' },
});
