// 사진 한 장을 화면 가득 보는 창 — S15P21E201-1802.
//
// 🔴 이 앱에는 사진을 크게 보는 길이 «한 곳도» 없었다. PhotoGrid 에 `onPressPhoto` 라는
//    자리는 있는데 그것을 넘기는 화면이 하나도 없었다(2026-09-27 확인). 그래서 피드 사진도
//    프로필 커버도 눌러야 할 것처럼 생겼는데 눌러도 아무 일이 없었다.
//
// 🔴 확대(핀치)는 넣지 않는다. react-native 기본 부품만으로는 두 손가락 확대를 제대로
//    만들 수 없고, 어설프게 넣으면 「확대는 되는데 원래대로 못 돌아가는」 창이 된다.
//    지금 필요한 것은 「화면 가득 보기」다 — 그것만 정확히 한다.
//    나중에 확대가 필요해지면 그때 전용 라이브러리를 들인다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { AppImage } from '@/components/AppImage';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';

export type PhotoViewerProps = {
  visible: boolean;
  /** 볼 사진. null 이면 창을 열지 않는다 — 부르는 쪽에서 굳이 안 가려도 되게. */
  uri: string | null;
  /** 화면 낭독기가 읽을 말. 사진이 무엇인지는 부르는 쪽이 안다. */
  label: string;
  /**
   * 닫기 단추를 읽어 줄 말.
   *
   * 🔴 이 부품은 `useI18n()` 을 안 쓴다 — 말을 «받는다». ProfileCard 처럼 tx 를 prop 으로
   *    받는 부품 안에 들어가는데, 여기서 훅을 부르면 그 부품을 쓰는 시험이 전부
   *    Provider 를 감싸야 한다(실제로 시험 4개가 깨졌다). 부르는 쪽은 이미 tx 를 갖고 있다.
   */
  closeLabel: string;
  onClose: () => void;
};

export function PhotoViewer({ visible, uri, label, closeLabel, onClose }: PhotoViewerProps) {
  // 🔴 안 열렸으면 «아무것도 부르지 않고» 빠진다 — 훅도 마찬가지다.
  //    useSafeAreaInsets() 를 위에 두면 SafeAreaProvider 없이 ProfileCard 를 그리는 시험이
  //    전부 깨진다(실제로 4개가 깨졌다). 창을 안 여는 화면에까지 provider 를 요구할 이유가 없다.
  if (!visible || !uri) return null;
  return <PhotoViewerBody uri={uri} label={label} closeLabel={closeLabel} onClose={onClose} />;
}

function PhotoViewerBody({ uri, label, closeLabel, onClose }: { uri: string; label: string; closeLabel: string; onClose: () => void }) {
  const insets = useSafeAreaInsets();

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      {/* 바깥을 눌러도 닫힌다. 사진 자체도 눌러서 닫는다 — 크게 본 뒤에 하고 싶은 일은
          「닫기」 하나뿐이라, 닫을 자리를 찾게 만들지 않는다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={closeLabel}
        onPress={onClose}
        style={styles.backdrop}
      >
        {/* 🔴 contain 이다. cover 로 하면 화면 비율에 맞춰 «잘린다» — 크게 보려고 눌렀는데
            가장자리가 사라지면 작게 볼 때보다 못하다. */}
        <AppImage
          source={{ uri }}
          resizeMode="contain"
          accessibilityLabel={label}
          style={styles.photo}
        />
      </Pressable>

      {/* 닫기 단추는 눌림을 따로 받는다 — 위 Pressable 안에 두면 단추 속 단추가 된다. */}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={closeLabel}
        hitSlop={10}
        onPress={onClose}
        style={({ pressed }) => [styles.close, { top: insets.top + spacing[3] }, pressed && styles.pressed]}
      >
        <Text variant="title" weight="bold" color={color.text.onAction}>✕</Text>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  // 사진만 보이게 바탕은 거의 검게. 토큰의 어둠막보다 진하다 — 사진 가장자리와 배경이
  // 구분되어야 「이게 사진의 끝」이 보인다.
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.92)', alignItems: 'center', justifyContent: 'center' },
  photo: { width: '100%', height: '100%' },
  close: {
    position: 'absolute', right: spacing[4],
    width: 44, height: 44, borderRadius: radius.full,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  pressed: { opacity: 0.72 },
});
