// 마이페이지 맨 위 — 커버 사진 위에 프로필을 얹는다. **데스크톱 전용** (시안 01).
//
// 🔴 사진 위에 글자를 그냥 얹지 않는다. 사진이 밝으면 흰 글자가 묻히고, 사진마다
//    읽히다 안 읽히다 한다. 시안이 어두워지는 덧칠을 깔고 그 위에 올린다 —
//    피드 카드에서 이름·⋯ 을 사진 위에 안 얹기로 한 것과 같은 판단이다.
import { type ReactNode } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { Text } from '@/components/Text';
import { color, desktopGutter, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';

/** 사진이 없는 계정도 빈 회색 판이 아니다 — 부산 기본 사진을 깐다. */
const DEFAULT_COVER = require('../../assets/home/web-hero.png');

export const MY_PAGE_COVER_HEIGHT = 420;

/**
 * 커버 위에 서는 단추 — 두 화면이 같은 모양을 쓴다.
 *
 * <p>`/me` 는 「프로필 편집」 하나, `/user/[id]` 는 「팔로우」+「차단하기」 둘이다.
 * 모양을 각 화면에 두면 사진 위에서 읽히게 만드는 그림자·불투명도가 두 벌이 되고,
 * 한쪽만 고쳐져 어느 날 한 화면에서만 안 읽힌다.
 */
export function CoverButton({
  label, onPress, tone = 'light', disabled,
}: {
  label: string;
  onPress: () => void;
  /** `primary` 는 동백 채움 — 「팔로우」처럼 그 화면에서 그다음에 할 일 하나에만. */
  tone?: 'light' | 'primary';
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.coverButton, tone === 'primary' && styles.coverButtonPrimary, (pressed || disabled) && styles.pressed]}
    >
      <Text weight="bold" color={tone === 'primary' ? color.text.onAction : color.brand.navy} numberOfLines={1}>{label}</Text>
    </Pressable>
  );
}

export type CoverCount = { label: string; value: number | null; onPress?: () => void };

export function MyPageCover({
  name,
  email,
  tripCount,
  avatarUri,
  coverUri,
  counts,
  actions,
  eyebrow,
  tx,
}: {
  name: string;
  email: string | null;
  /**
   * 이 사람이 만든 여행 수 (시안 01 의 「부산 여행 3번째」).
   *
   * 🔴 0 이면 줄을 안 그린다. 「부산 여행 0번째」는 말이 안 되고, 아직 아무것도 안 만든
   * 사람에게 굳이 빈손임을 알릴 이유도 없다. 아직 못 받았으면 null 이다 — 0 과 다르다.
   */
  tripCount: number | null;
  avatarUri: string | null;
  /**
   * 사용자가 고른 커버. **없으면 부산 기본 사진**을 깐다.
   *
   * 🔴 서버는 안 고른 사람에게 이 칸을 **아예 안 보낸다** — 기본 사진 주소를 대신 보내지
   * 않는다. 그래야 화면이 「고른 사진」과 「기본 사진」을 가를 수 있고, 프로필에서 「기본으로
   * 되돌리기」를 언제 보여줄지 정할 수 있다. **어느 그림을 기본으로 쓸지는 화면이 정한다.**
   */
  coverUri: string | null;
  counts: CoverCount[];
  /**
   * 커버 오른쪽 아래 단추들. `CoverButton` 으로 만든다.
   *
   * <p>부품이 「프로필 편집」을 스스로 그리지 않는 이유 — `/user/[id]` 는 그 자리에
   * 「팔로우」와 「차단하기」 둘이 선다. 부품이 정하면 두 화면 중 하나는 반드시 억지가 된다.
   */
  actions: ReactNode;
  /** 왼쪽 위 알약. `/me` 는 「내 계정」, `/user/[id]` 는 「‹ 뒤로」. */
  eyebrow: ReactNode;
  tx: (ko: string, en: string) => string;
}) {
  return (
    <View style={styles.cover}>
      <Image source={coverUri ? { uri: coverUri } : DEFAULT_COVER} resizeMode="cover" accessibilityLabel="" style={StyleSheet.absoluteFill} />
      {/* 시안의 세 단계 — 위는 살짝, 아래로 갈수록 진하게. */}
      <LinearGradient
        colors={['rgba(25,25,25,0.10)', 'rgba(25,25,25,0.55)', 'rgba(25,25,25,0.80)']}
        locations={[0, 0.6, 1]}
        style={StyleSheet.absoluteFill}
      />

      <View style={styles.badge}>{eyebrow}</View>

      <View style={styles.bottom}>
        <View style={styles.identity}>
          <View style={styles.avatar}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel="" style={styles.avatarPhoto} />
              : <Text variant="hero" weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>}
          </View>
          <View style={styles.copy}>
            <Text variant="hero" weight="bold" color={color.text.onAction} numberOfLines={1}>{name}</Text>
            {/* 시안은 둘을 가운뎃점으로 한 줄에 잇는다. 한쪽이 없으면 점도 안 찍는다. */}
            {email || (tripCount !== null && tripCount > 0) ? (
              <Text color={color.text.onDarkMuted} numberOfLines={1}>
                {[email, tripCount !== null && tripCount > 0 ? tx(`부산 여행 ${tripCount}번째`, `Busan trip #${tripCount}`) : null]
                  .filter(Boolean).join(' · ')}
              </Text>
            ) : null}
            <View style={styles.pills}>
              {counts.map((count) => (
                <Pressable
                  key={count.label}
                  accessibilityRole="button"
                  accessibilityLabel={txf(tx, '%s 보기', 'View %s', count.label)}
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

        <View style={styles.actions}>{actions}</View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  // zIndex: 0 — react-native-web 의 Image 는 그림을 z-index -1 로 그린다. 쌓임 문맥이 아니면 바탕 뒤로 가서 회색만 보인다(S15P21E201-1381, 로그인 판과 같은 버그).
  cover: { position: 'relative', zIndex: 0, width: '100%', height: MY_PAGE_COVER_HEIGHT, overflow: 'hidden', backgroundColor: color.surface.soft },
  badge: { position: 'absolute', top: spacing[4], left: desktopGutter, paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: 'rgba(255,253,248,0.9)' },

  bottom: {
    position: 'absolute', left: desktopGutter, right: desktopGutter, bottom: spacing[8],
    flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: spacing[4],
  },
  identity: { flexDirection: 'row', alignItems: 'flex-end', gap: spacing[6], flexShrink: 1, minWidth: 0 },
  avatar: {
    width: 152, height: 152, borderRadius: radius.full, overflow: 'hidden',
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.action.secondary, borderWidth: 4, borderColor: color.brand.ivory,
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
  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  coverButton: {
    minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full,
    backgroundColor: color.brand.ivory,
    shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 5 }, elevation: 5,
  },
  coverButtonPrimary: { backgroundColor: color.action.primary },
  pressed: { opacity: 0.82 },
});
