// 03 온보딩·권한 안내 — Figma 03_온보딩·권한 안내 실측 그대로.
//
// 🔴 권한을 요청하기 전에 왜 필요한지 먼저 설명하는 화면이다(스토어 심사 항목).
// 실제 OS 권한 요청 API 는 아직 붙이지 않는다 — 아래 토글은 사용자가 "무엇을 허용할지"
// 미리 골라두는 화면 안 로컬 상태일 뿐, 누른다고 실제 권한 팝업이 뜨지 않는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';
import { Toggle } from '@/components/Toggle';

const PERMISSION_PREFERENCES_KEY = '@gabolle/permission-preferences';

type PermissionKey = 'location' | 'camera' | 'notification';

type Permission = {
  key: PermissionKey;
  icon: string;
  title: string;
  description: string;
  note: string;
  recommended?: boolean;
};

const PERMISSIONS: Permission[] = [
  {
    key: 'location',
    icon: '📍',
    title: '위치',
    description: '정확한 경로와 주변 장소 추천',
    note: '앱 사용 중에만 위치를 확인해요',
    recommended: true,
  },
  {
    key: 'camera',
    icon: '📷',
    title: '카메라·번역',
    description: '메뉴와 안내문을 바로 번역',
    note: '촬영한 이미지는 저장하지 않아요',
  },
  {
    key: 'notification',
    icon: '🔔',
    title: '알림',
    description: '일정 변경과 혼잡도 알림',
    note: '중요한 여행 알림만 보내드려요',
  },
];

export default function Permissions() {
  const router = useRouter();
  const [values, setValues] = useState<Record<PermissionKey, boolean>>({ location: true, camera: false, notification: false });
  async function continueTo(path: string, preferences = values) {
    await AsyncStorage.setItem(PERMISSION_PREFERENCES_KEY, JSON.stringify(preferences));
    router.replace({ pathname: '/sign-in', params: { returnTo: path } });
  }

  return (
    <Screen style={styles.screen}>
      <Eyebrow>앱 권한 안내</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        부산 여행에 꼭 필요한{'\n'}기능을 준비할게요
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        허용하지 않아도 둘러볼 수 있고, 설정에서 언제든 바꿀 수 있어요.
      </Text>

      <View style={styles.cards}>
        {PERMISSIONS.map((perm) => (
          <View key={perm.key} style={styles.card}>
            <View style={styles.cardIcon}>
              <Text variant="title">{perm.icon}</Text>
            </View>
            <View style={styles.cardBody}>
              <View style={styles.cardTopRow}>
                <Text variant="body" weight="bold">
                  {perm.title}
                </Text>
                <Toggle value={values[perm.key]} onValueChange={(next) => setValues((current) => ({ ...current, [perm.key]: next }))} />
              </View>
              <Text variant="caption" color={color.text.body}>
                {perm.description}
              </Text>
              <View style={styles.cardBottomRow}>
                <Text variant="caption" color={color.text.muted} style={styles.cardNote}>
                  {perm.note}
                </Text>
              </View>
            </View>
          </View>
        ))}
      </View>

      <Pressable accessibilityRole="link" accessibilityLabel="개인정보 처리 안내 보기" onPress={() => router.push('/privacy')} style={({ pressed }) => [styles.privacyBox, pressed && styles.privacyPressed]}>
        <Text variant="title">🔒</Text>
        <View style={styles.privacyCopy}>
          <Text variant="caption" weight="bold" color={color.text.heading}>
            개인정보는 추천 기능에만 사용하며 제3자에게 제공하지 않아요.
          </Text>
          <Text variant="caption" weight="medium" color={color.text.accent}>
            자세한 개인정보 처리방침 보기 ›
          </Text>
        </View>
      </Pressable>

      <Pressable accessibilityRole="button" onPress={() => void continueTo('/home', { location: false, camera: false, notification: false })}>
        <Text variant="caption" weight="bold" color={color.text.muted} style={styles.laterLink}>
          나중에 설정
        </Text>
      </Pressable>

      <Button label="선택하고 로그인·회원가입으로" containerStyle={styles.cta} onPress={() => void continueTo('/home')} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  title: {
    marginTop: spacing[1],
  },
  subtitle: {
    marginTop: spacing[3],
    color: color.text.body,
  },
  cards: {
    flex: 1,
    justifyContent: 'center',
    gap: spacing[2],
  },
  card: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
    borderWidth: 1,
    borderColor: '#eee5da',
  },
  cardIcon: {
    width: 48,
    height: 48,
    borderRadius: radius.md,
    backgroundColor: '#fff1e8',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardBottomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[1],
  },
  cardNote: {
    flex: 1,
  },
  badge: {
    borderRadius: radius.full,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  recommendedBadge: { backgroundColor: '#fff1e8' },
  privacyBox: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: '#fff1e8',
    borderRadius: radius.md,
    padding: spacing[4],
    marginTop: spacing[3],
  },
  privacyCopy: {
    flex: 1,
    gap: spacing[1],
  },
  privacyPressed: { opacity: 0.72, transform: [{ scale: 0.99 }] },
  laterLink: {
    marginTop: spacing[3],
  },
  cta: {
    marginTop: spacing[3],
    minHeight: 54,
    borderRadius: radius.full,
    backgroundColor: color.brand.orange,
  },
});
