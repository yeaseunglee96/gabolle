// 마이페이지 맨 위 — 커버 사진 위에 프로필을 얹는다. **데스크톱 전용** (시안 01).
//
// 🔴 사진 위에 글자를 그냥 얹지 않는다. 사진이 밝으면 흰 글자가 묻히고, 사진마다
//    읽히다 안 읽히다 한다. 시안이 어두워지는 덧칠을 깔고 그 위에 올린다 —
//    피드 카드에서 이름·⋯ 을 사진 위에 안 얹기로 한 것과 같은 판단이다.
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';

/** 사진이 없는 계정도 빈 회색 판이 아니다 — 부산 기본 사진을 깐다. */
const DEFAULT_COVER = require('../../assets/home/web-hero.png');

export const MY_PAGE_COVER_HEIGHT = 360;

export type CoverCount = { label: string; value: number | null; onPress?: () => void };

export function MyPageCover({
  name,
  email,
  avatarUri,
  coverUri,
  counts,
  onEdit,
  tx,
}: {
  name: string;
  email: string | null;
  avatarUri: string | null;
  /** 사용자가 고른 커버. 없으면 부산 기본 사진 — 서버에 이 칸이 생기기 전까지는 늘 null 이다. */
  coverUri: string | null;
  counts: CoverCount[];
  onEdit: () => void;
  tx: (ko: string, en: string) => string;
}) {
  return (
    <View style={styles.cover}>
      <Image source={coverUri ? { uri: coverUri } : DEFAULT_COVER} resizeMode="cover" accessibilityLabel="" style={StyleSheet.absoluteFill} />
      {/* 시안의 세 단계 — 위는 살짝, 아래로 갈수록 진하게. */}
      <LinearGradient
        colors={['rgba(11,29,58,0.10)', 'rgba(11,29,58,0.55)', 'rgba(11,29,58,0.80)']}
        locations={[0, 0.6, 1]}
        style={StyleSheet.absoluteFill}
      />

      <View style={styles.badge}>
        <Eyebrow>{tx('내 계정', 'Account')}</Eyebrow>
      </View>

      <View style={styles.bottom}>
        <View style={styles.identity}>
          <View style={styles.avatar}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel="" style={styles.avatarPhoto} />
              : <Text variant="hero" weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>}
          </View>
          <View style={styles.copy}>
            <Text variant="hero" weight="bold" color={color.text.onAction} numberOfLines={1}>{name}</Text>
            {email ? <Text color={color.text.onDarkMuted} numberOfLines={1}>{email}</Text> : null}
            <View style={styles.pills}>
              {counts.map((count) => (
                <Pressable
                  key={count.label}
                  accessibilityRole="button"
                  accessibilityLabel={tx(`${count.label} 보기`, `View ${count.label}`)}
                  disabled={!count.onPress}
                  onPress={count.onPress}
                  style={({ pressed }) => [styles.pill, pressed && styles.pressed]}
                >
                  {/* 🔴 한 줄로 묶는다. 시안 캡처에서 이미 「3 기 / 록」으로 꺾인 적이 있다. */}
                  <Text weight="bold" color={color.text.onAction} numberOfLines={1}>
                    {/* 숫자를 못 받았으면 지어내지 않는다 — 0 은 「아무것도 없다」이고 모르는 것과 다르다. */}
                    {count.value === null ? '–' : count.value}
                  </Text>
                  <Text variant="caption" weight="bold" color={color.text.onDarkMuted} numberOfLines={1}>{count.label}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        </View>

        <Pressable accessibilityRole="button" onPress={onEdit} style={({ pressed }) => [styles.edit, pressed && styles.pressed]}>
          <Text weight="bold" color={color.brand.navy} numberOfLines={1}>{tx('프로필 편집', 'Edit profile')}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  cover: { position: 'relative', width: '100%', height: MY_PAGE_COVER_HEIGHT, overflow: 'hidden', backgroundColor: color.surface.soft },
  badge: { position: 'absolute', top: spacing[4], left: desktopGutter, paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,253,248,0.9)' },

  bottom: {
    position: 'absolute', left: desktopGutter, right: desktopGutter, bottom: spacing[8],
    flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: spacing[4],
  },
  identity: { flexDirection: 'row', alignItems: 'flex-end', gap: spacing[6], flexShrink: 1, minWidth: 0 },
  avatar: {
    width: 128, height: 128, borderRadius: radius.full, overflow: 'hidden',
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.brand.navy, borderWidth: 5, borderColor: color.brand.ivory,
  },
  avatarPhoto: { width: '100%', height: '100%' },
  copy: { gap: spacing[2], flexShrink: 1, minWidth: 0 },

  pills: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[1] },
  pill: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    // 시안은 좌우 14 인데 토큰에 없다. 여백은 토큰만 쓴다(인계 규칙 4) — 12 로 간다.
    minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full,
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.35)', backgroundColor: 'rgba(255,253,248,0.14)',
  },
  edit: {
    minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full,
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 5 }, elevation: 5,
  },
  pressed: { opacity: 0.82 },
});
