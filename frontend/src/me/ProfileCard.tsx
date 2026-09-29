// 마이페이지 프로필 카드 — **폰 전용** (시안 02).
//
// 🔴 넓은 화면은 이 카드를 안 쓴다. 커버 사진이 화면 전폭으로 깔리고 그 위에 프로필이
//    얹힌다(MyPageCover). 그래서 이 파일에 있던 넓은 화면 가지는 전부 죽은 코드였고
//    걷어냈다 — 남겨 두면 다음 사람이 고치고서 「왜 안 바뀌지」를 한참 찾는다.
import { useState, type ReactNode } from 'react';
import { Image, Pressable, StyleSheet, View } from 'react-native';

import { PhotoViewer } from '@/components/PhotoViewer';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { txf } from '@/i18n/format';

/** 사진이 없는 계정도 빈 회색 판이 아니다 — 넓은 화면(MyPageCover)과 같은 부산 기본 사진. S15P21E201-1375 */
const DEFAULT_COVER = require('../../assets/home/web-hero.png');

export type ProfileCardCount = { label: string; value: number | null; onPress?: () => void };

export type ProfileCardProps = {
  name: string;
  /** 이메일 줄. 없으면(로그인 전 · 다른 사람 프로필) null. */
  email: string | null;
  /**
   * 로그인 안 한 내 마이페이지인가 — 그때만 이메일 줄 자리에 「로그인 없이 앱을 둘러보는 중이에요」를 쓴다(S15P21E201-1684).
   * 🔴 전에는 이메일이 없으면 무조건 그 말을 써서, 이메일을 비워 넘기는 다른 사람 프로필에도 떴다(iOS 심사 공지의 알려진 문제).
   */
  guest?: boolean;
  avatarUri: string | null;
  coverUri: string | null;
  counts: ProfileCardCount[];
  /** 커버 오른쪽 단추들. `/me` 는 「프로필 편집」, `/user/[id]` 는 「팔로우」+「차단하기」. */
  actions: ReactNode;
  tx: (ko: string, en: string) => string;
};

export function ProfileCard({ name, email, guest = false, avatarUri, coverUri, counts, actions, tx }: ProfileCardProps) {
  const initial = name.trim().slice(0, 1) || '·';
  // 🔴 가로로 눕히면 커버를 낮춘다 (2026-09-22, build 41 실기기).
  //
  // 세로 874 에서는 커버 120 을 써도 이름·칩이 탭바(하단 고정) 위에 넉넉히 들어간다.
  // 그런데 가로는 높이가 **402** 뿐이라, 같은 120 을 쓰면 첫 화면에 이름까지밖에 안 들어오고
  // 「기록·팔로워·팔로잉」 칩이 탭바 뒤로 밀린다(실측: 칩 y=353~385 · 탭바 y=323).
  // 스크롤하면 닿기는 하지만, 첫 화면에서 자기 계정 숫자가 안 보이는 것은 어색하다.
  //
  // 폭이 아니라 **높이**로 가른다 — 폴드처럼 켜진 채 비율이 바뀌는 기기도 useWindowDimensions
  // 기반이라 그 자리에서 따라온다(frontend/CLAUDE.md 의 「폭 분기는 useLayout」).
  const { isLandscape } = useLayout();
  const coverHeight = isLandscape ? COVER_HEIGHT_LANDSCAPE : COVER_HEIGHT;
  // 커버를 눌러 크게 보는 창 (S15P21E201-1802). 올린 사진이 있을 때만 연다 —
  // 기본 부산 사진은 「내 사진」이 아니라 빈자리를 채우는 그림이라 크게 볼 것이 없다.
  const [viewingCover, setViewingCover] = useState(false);
  const cover = <Image source={coverUri ? { uri: coverUri } : DEFAULT_COVER} resizeMode="cover" style={styles.coverPhoto} accessibilityLabel={coverUri ? tx('배경 사진', 'Cover photo') : ''} />;
  return (
    <View style={styles.card}>
      <View style={[styles.cover, { height: coverHeight }]}>
        {coverUri
          ? <Pressable accessibilityRole="imagebutton" accessibilityLabel={tx('배경 사진 크게 보기', 'View cover photo')} onPress={() => setViewingCover(true)} style={styles.coverPress}>{cover}</Pressable>
          : cover}
      </View>
      <PhotoViewer visible={viewingCover} uri={coverUri} label={tx('배경 사진', 'Cover photo')} closeLabel={tx('사진 닫기', 'Close photo')} onClose={() => setViewingCover(false)} />

      <View style={styles.body}>
        <View style={styles.topRow}>
          <View style={styles.avatar}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" style={styles.avatarPhoto} accessibilityLabel={tx('프로필 사진', 'Profile photo')} />
              : <Text variant="display" weight="bold" color={color.text.onAction}>{initial}</Text>}
          </View>
          <View style={styles.actions}>{actions}</View>
        </View>

        <Text variant="display" weight="bold" color={color.text.heading} numberOfLines={1}>{name}</Text>
        {email || guest ? (
          <Text variant="caption" color={color.text.muted} numberOfLines={1}>
            {email || tx('로그인 없이 앱을 둘러보는 중이에요', 'Browsing without an account')}
          </Text>
        ) : null}

        {/* 🔴 숫자가 버튼이 된다(시안 02). 전에는 글자라 눌러도 되는지 안 보였다 —
            눌리는 것은 눌리게 생겨야 한다. */}
        {/* 🔴 손님에게는 숫자 칸을 안 그린다 — 「– 기록 · – 팔로워 · – 팔로잉」 대시 줄은 고장처럼 보였다.
            로그인은 커버 오른쪽 「로그인」 단추가 맡는다. */}
        {guest ? null : <View style={styles.counts}>
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
        </View>}
      </View>
    </View>
  );
}

/**
 * 폰 프로필 카드용 단추 — 시안은 동백 채움이다.
 *
 * <p>🔴 이 화면에는 동백 채움이 둘이 된다(이 단추 + 활성 탭). tokens.ts 규칙 1
 * 「채움은 화면당 하나」 위반이고, 사용자가 시안대로 가기로 정했다(2026-09-21).
 * 되돌릴 때는 tone 을 'outline' 으로 바꾸면 된다 — 붉은 선 단추로 내려간다.
 */
export function ProfileCardButton({
  label, onPress, tone = 'primary', disabled,
}: {
  label: string;
  onPress: () => void;
  tone?: 'primary' | 'outline';
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.actionButton, tone === 'primary' ? styles.actionPrimary : styles.actionOutline, (pressed || disabled) && styles.pressed]}
    >
      <Text weight="bold" color={tone === 'primary' ? color.text.onAction : color.action.outline} numberOfLines={1}>{label}</Text>
    </Pressable>
  );
}

/**
 * 세로에서의 커버 높이.
 *
 * 🔴 120 → 180 (S15P21E201-1802, 사용자 요청). 120 은 「아바타(80)가 걸쳐 앉을 만큼」으로
 *    잡은 최소값이라 사진이 띠처럼 얇게 눌려 무엇을 올렸는지 잘 안 보였다. 넓은 화면은
 *    같은 사진을 420(MyPageCover)으로 쓰는데 폰만 유독 작았다.
 *
 *    180 을 고른 근거 — 아이폰 세로 874 에서 커버 180 + 몸통(아바타 줄·이름·이메일·칩)
 *    약 250 = 430 이라, 탭바(64+여백)를 빼도 첫 화면에 칩까지 다 들어온다. 실제로 재 보고
 *    정했다. 더 키우면 칩이 접히기 시작한다.
 */
const COVER_HEIGHT = 180;
/** 가로는 화면 높이가 402 뿐이라 커버를 낮춘다 — 아바타(80)가 여전히 걸쳐 앉는다. */
const COVER_HEIGHT_LANDSCAPE = 72;
const AVATAR = 80;

const styles = StyleSheet.create({
  // 폰에도 테두리와 둥근 모서리를 준다(시안 02). 전에는 띠처럼 화면 폭을 꽉 채웠다.
  card: { borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, overflow: 'hidden' },
  cover: { height: COVER_HEIGHT, backgroundColor: color.surface.soft },
  coverPress: { width: '100%', height: '100%' },
  coverPhoto: { width: '100%', height: '100%' },
  body: { gap: spacing[2], paddingHorizontal: spacing[4], paddingBottom: spacing[4], marginTop: -40 },
  // 🔴 아바타 줄을 커버 «위»로 올린다. 안 올리면 커버의 overflow: hidden 이 겹친 부분을
  //    잘라 먹어서, 아바타가 반달처럼 보인다.
  // 🔴 alignItems: 'center' — 줄이 -40 올라가 있고 아바타가 80 이라 줄의 세로 가운데가 곧 커버와
  //    흰 카드의 경계선이다. 단추도 가운데에 두면 둘 다 경계선 위에 앉는다(시안 design_handoff_mypage_v2
  //    변경점 1, S15P21E201-1526). 전에는 flex-end 라 단추가 아바타 아랫단에 붙어 경계선 아래로 처졌다.
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], zIndex: 1 },
  avatar: {
    width: AVATAR, height: AVATAR, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.action.secondary, borderWidth: 4, borderColor: color.surface.card, overflow: 'hidden',
  },
  avatarPhoto: { width: '100%', height: '100%' },
  actions: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  actionButton: { minHeight: 44, paddingHorizontal: spacing[4], justifyContent: 'center', borderRadius: radius.full },
  actionPrimary: { backgroundColor: color.action.primary },
  actionOutline: { backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.action.outline },

  counts: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[2] },
  // 시안 2b — 셋이 가로 한 줄에 서는 작은 알약. 전에는 화면 폭을 삼등분한 큰 상자라
  // 아래 세그먼트와 무게가 비슷해서 어느 것이 탭인지 헷갈렸다.
  count: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2],
    minHeight: 32, paddingHorizontal: spacing[3],
    borderRadius: radius.full, backgroundColor: color.surface.tint,
  },
  pressed: { opacity: 0.72 },
});
