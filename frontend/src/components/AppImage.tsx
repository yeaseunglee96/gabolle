// 사진 공용 부품 — 네트워크·번들 사진은 이것으로 그린다(S15P21E201-1975).
//
// 🔴 왜 React Native 기본 Image 가 아닌가: 기본 Image 는 1600×1200 사진을 카드 크기와 상관없이 원본 크기로
//    메모리에 풀어 놓는다. 화면을 쌓아 가며 오래 쓰면(탭에서 화면 18개) 앱 메모리가 199MB → 903MB 로 불어나고,
//    그 뒤로는 새 사진을 못 그려 회색 판만 남았다. 앱을 껐다 켜야 다시 보였다.
//    expo-image 는 그리는 자리 크기로 줄여서 올리고(안드로이드 Glide 의 기본 동작), 메모리·디스크 캐시를 스스로 비운다.
//
// 실패하면 한 번 다시 불러 보고(retries), 그래도 실패면 회색 대신 연한 빈 판을 그린다 — 이름을 주면 그 이름을 띄운다.
import { useState, type ReactNode } from 'react';
import { StyleSheet, View, type ImageResizeMode, type ImageSourcePropType, type ImageStyle, type StyleProp } from 'react-native';
import { Image as ExpoImage, type ImageContentFit } from 'expo-image';

import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';

type Props = {
  source: ImageSourcePropType | null | undefined;
  style?: StyleProp<ImageStyle>;
  resizeMode?: ImageResizeMode;
  accessibilityLabel?: string;
  accessibilityIgnoresInvertColors?: boolean;
  testID?: string;
  /** 다시 불러 볼 횟수. 부르는 쪽이 따로 재시도하면 0. */
  retries?: number;
  /** 끝내 못 불러왔을 때 빈 판 가운데 띄울 이름. */
  fallbackLabel?: string;
  /** 끝내 못 불러왔을 때 빈 판 대신 그릴 것. */
  fallback?: ReactNode;
  /** 끝내 못 불러왔을 때 한 번 부른다(재시도 중 실패는 알리지 않는다). */
  onError?: () => void;
  onLoad?: () => void;
};

export function contentFitFor(mode: ImageResizeMode | undefined): ImageContentFit {
  switch (mode) {
    case 'contain': return 'contain';
    case 'stretch': return 'fill';
    case 'center': return 'scale-down';
    case 'repeat': return 'cover';
    default: return 'cover';
  }
}

function keyOf(source: ImageSourcePropType | null | undefined): string | undefined {
  if (source == null) return undefined;
  if (typeof source === 'number') return String(source);
  if (Array.isArray(source)) return source[0]?.uri;
  return source.uri;
}

export function AppImage({ source, style, resizeMode, accessibilityLabel, accessibilityIgnoresInvertColors, testID, retries = 1, fallbackLabel, fallback, onError, onLoad }: Props) {
  const key = keyOf(source);
  const [failed, setFailed] = useState<{ key: string | undefined; count: number }>({ key, count: 0 });
  // 사진 주소가 바뀌면 실패 횟수를 새로 센다.
  const count = failed.key === key ? failed.count : 0;

  if (!source || count > retries) {
    if (fallback !== undefined) return <>{fallback}</>;
    return (
      <View testID="app-image-fallback" accessibilityLabel={accessibilityLabel} style={[style as object, styles.blank]}>
        {fallbackLabel ? <Text variant="caption" weight="bold" color={color.text.muted} numberOfLines={2} style={styles.label}>{fallbackLabel}</Text> : null}
      </View>
    );
  }

  return (
    <ExpoImage
      // 다시 부를 때는 새로 그리게 한다 — 같은 부품을 그대로 두면 실패한 요청을 다시 보내지 않는다.
      key={`${key ?? ''}#${count}`}
      testID={testID}
      source={source as never}
      style={style as never}
      contentFit={contentFitFor(resizeMode)}
      cachePolicy="memory-disk"
      recyclingKey={key}
      transition={120}
      accessibilityLabel={accessibilityLabel}
      accessibilityIgnoresInvertColors={accessibilityIgnoresInvertColors}
      onLoad={onLoad}
      onError={() => {
        const next = count + 1;
        setFailed({ key, count: next });
        if (next > retries) onError?.();
      }}
    />
  );
}

const styles = StyleSheet.create({
  blank: { backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  label: { textAlign: 'center', paddingHorizontal: spacing[2] },
});
