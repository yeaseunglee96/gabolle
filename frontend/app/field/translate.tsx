// 22 현장 도구·번역 — Figma 22_현장 도구·번역 실측 그대로.
//
// 번역·음성 API 업체가 아직 안 정해졌다(Jira S15P21E201-77). "메뉴판 카메라 번역"·
// "양방향 음성 통역" 은 그 업체가 정해져야 만들 수 있어 지금은 눌러도 이동하지 않는다.
// "장소별 한국어" 는 이미 만든 17 현장 말하기 화면(phrase 카드)과 같은 기능이라 그리로 잇고,
// "날씨·준비물" 은 16 여행 준비 화면으로 잇는다.
import { useEffect, useState } from 'react';
import { AppState, Image, Platform, Pressable, StyleSheet, View, type ImageSourcePropType } from 'react-native';
import { Camera } from 'expo-camera';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { PermissionRationale } from '@/components/PermissionRationale';
import { useI18n } from '@/i18n';

const DEMO_TRIP_ID = 'demo-trip';
const sunIcon = require('../../assets/icons/common/sun.png');
const cameraIcon = require('../../assets/icons/common/camera.png');

type Tool = {
  key: string;
  icon: string | ImageSourcePropType;
  title: string;
  desc: string;
  tinted?: boolean;
  onPress?: () => void;
  pending?: boolean;
};

export default function Translate() {
  const router = useRouter();
  const { tx } = useI18n();
  const [cameraPermission, setCameraPermission] = useState<'checking' | 'undetermined' | 'granted' | 'denied'>(Platform.OS === 'web' ? 'granted' : 'checking');
  const [requestingCamera, setRequestingCamera] = useState(false);

  useEffect(() => {
    if (Platform.OS === 'web') return;
    const refreshPermission = () => void Camera.getCameraPermissionsAsync()
      .then((result) => setCameraPermission(result.granted ? 'granted' : result.status === 'denied' ? 'denied' : 'undetermined'))
      .catch(() => setCameraPermission('undetermined'));
    refreshPermission();
    const subscription = AppState.addEventListener('change', (state) => { if (state === 'active') refreshPermission(); });
    return () => subscription.remove();
  }, []);

  async function requestCamera() {
    setRequestingCamera(true);
    try {
      const result = await Camera.requestCameraPermissionsAsync();
      setCameraPermission(result.granted ? 'granted' : 'denied');
    } catch {
      setCameraPermission('denied');
    } finally {
      setRequestingCamera(false);
    }
  }

  // 지금 실제로 눌리는 기능(장소별 한국어·날씨)을 위로, 아직 못 쓰는 기능(음성 통역·
  // 메뉴판 번역)을 아래로 둔다 — 카드 순서만으로 "이건 지금 되는 기능이다"가 보이게.
  const tools: Tool[] = [
    {
      key: 'phrase',
      icon: '말',
      title: tx('장소별 한국어', 'Korean phrases by situation'),
      desc: tx('택시·식당에서 바로 보여주는 문장', 'Sentences to show right away at taxis and restaurants'),
      onPress: () => router.push('/field/speak'),
    },
    {
      key: 'weather',
      icon: sunIcon,
      title: tx('날씨·준비물', 'Weather & what to bring'),
      desc: tx('기상청 예보 기반 우산·옷차림 안내', 'Umbrella and clothing tips based on the weather forecast'),
      onPress: () => router.push(`/${DEMO_TRIP_ID}/prepare`),
    },
    {
      key: 'voice',
      icon: '◉',
      title: tx('양방향 음성 통역', 'Two-way voice interpretation'),
      desc: tx('한국어 ↔ English 실시간 대화', 'Real-time conversation, Korean ↔ English'),
      pending: true,
    },
    {
      key: 'menu-camera',
      icon: '▣',
      title: tx('메뉴판 카메라 번역', 'Menu camera translation'),
      desc: tx('사진을 찍으면 음식명·가격·알레르기를 번역', 'Take a photo to translate dish names, prices, and allergens'),
      tinted: true,
      // 번역 업체가 아직 안 정해져 이 기능 자체가 못 켜져 있다 — 카메라 권한을 미리
      // 받아두는 것과는 별개라, 목록 카드에서는 권한을 요청하지 않는다. 권한 사전 요청은
      // 위 PermissionRationale 배너로만 한다.
      pending: true,
    },
  ];

  return (
    <Screen scroll>
      <Text variant="display" weight="bold">
        {tx('현장 도구', 'On-the-go tools')}
      </Text>
      <Text variant="caption" style={styles.subtitle}>
        {tx('여행 중 필요한 기능을 한곳에서 바로 사용해요', 'Everything you need on your trip, in one place')}
      </Text>

      {cameraPermission !== 'granted' && (
        <PermissionRationale
          icon={cameraIcon}
          title={tx('메뉴판을 촬영해 번역할까요?', 'Photograph a menu to translate it?')}
          description={tx('카메라는 메뉴와 안내문을 읽을 때만 사용해요. 촬영한 이미지는 사진첩에 저장하지 않아요.', 'The camera is only used to read menus and signs. Photos are not saved to your camera roll.')}
          denied={cameraPermission === 'denied'}
          busy={cameraPermission === 'checking' || requestingCamera}
          actionLabel={tx('카메라 사용', 'Use camera')}
          onRequest={() => void requestCamera()}
        />
      )}

      <View style={styles.list}>
        {tools.map((tool) => (
          <Pressable
            key={tool.key}
            disabled={!tool.onPress}
            accessibilityRole={tool.onPress ? 'button' : undefined}
            accessibilityState={{ disabled: !tool.onPress }}
            onPress={tool.onPress}
            style={[styles.card, tool.tinted && styles.cardTinted, tool.pending && styles.cardPending]}
          >
            <View style={[styles.iconBox, tool.tinted ? styles.iconBoxDark : styles.iconBoxLight]}>
              {typeof tool.icon === 'string' ? (
                <Text variant="title" weight="bold" color={tool.tinted ? color.text.onAction : color.action.secondary}>
                  {tool.icon}
                </Text>
              ) : (
                <Image source={tool.icon} resizeMode="contain" style={styles.toolIconImage} />
              )}
            </View>
            <View style={styles.cardBody}>
              <Text variant="body" weight="bold">
                {tool.title}
              </Text>
              <Text variant="caption" style={styles.cardDesc}>
                {tool.desc}
              </Text>
            </View>
            {tool.pending ? <Text variant="caption" weight="bold" color={color.text.muted}>{tx('준비 중', 'Coming soon')}</Text> : <Text variant="title" weight="bold" color={color.action.secondary}>›</Text>}
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
    color: color.text.body,
  },
  list: {
    gap: spacing[3],
    marginTop: spacing[4],
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
  },
  cardTinted: {
    backgroundColor: color.surface.tint,
  },
  cardPending: { opacity: 0.64 },
  iconBox: {
    width: 50,
    height: 50,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBoxLight: {
    backgroundColor: color.surface.tint,
  },
  iconBoxDark: {
    backgroundColor: color.action.secondary,
  },
  toolIconImage: { width: 24, height: 24 },
  cardBody: {
    flex: 1,
    gap: spacing[1],
  },
  cardDesc: {
    color: color.text.body,
  },
});
