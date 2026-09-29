// 장소 상세 사진을 옆으로 넘겨 본다 — 한 장씩 멈추고, 오른쪽 위에 「1/5」 (S15P21E201-1839).
//
// 여기는 사진 판과 쪽 번호만 그린다. 사진 위 이름·출처 글자는 화면(app/place/[id].tsx)이 그 위에 얹는다
// — 출처는 지금 보이는 사진의 것이어야 해서 onIndexChange 로 몇 번째인지를 올려 준다.
// 🔴 한 장이면 이것을 쓰지 않는다 — 화면이 예전 한 장 그림으로 그린다(쪽 번호 없이).
import { useState } from 'react';
import { ImageBackground, ScrollView, StyleSheet, View, type LayoutChangeEvent, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { Text } from './Text';

export function PlacePhotoGallery({ urls, onFirstLoad, onIndexChange }: { urls: string[]; onFirstLoad?: () => void; onIndexChange?: (index: number) => void }) {
  const { tx } = useI18n();
  const [pageWidth, setPageWidth] = useState(0);
  const [index, setIndex] = useState(0);

  const onLayout = (event: LayoutChangeEvent) => setPageWidth(event.nativeEvent.layout.width);
  // onMomentumScrollEnd 는 웹에서 안 온다 — 굴리는 동안 가장 가까운 쪽으로 센다.
  const onScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (!pageWidth) return;
    const next = Math.max(0, Math.min(urls.length - 1, Math.round(event.nativeEvent.contentOffset.x / pageWidth)));
    if (next !== index) {
      setIndex(next);
      onIndexChange?.(next);
    }
  };

  return (
    <View style={StyleSheet.absoluteFill} onLayout={onLayout}>
      <ScrollView
        testID="place-photo-gallery"
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onScroll={onScroll}
        scrollEventThrottle={16}
        style={StyleSheet.absoluteFill}
      >
        {urls.map((url, i) => (
          <ImageBackground
            key={`${i}-${url}`}
            testID="place-photo-page"
            source={{ uri: url }}
            resizeMode="cover"
            // 폭을 재기 전(0)에도 첫 장은 자리를 차지하게 — 안 그러면 첫 그림에서 사진이 비었다가 나온다.
            style={[styles.page, pageWidth ? { width: pageWidth } : styles.pageUnmeasured]}
            onLoad={i === 0 ? onFirstLoad : undefined}
            onError={i === 0 ? onFirstLoad : undefined}
          />
        ))}
      </ScrollView>
      {/* 몇 장 중 몇 번째인가 — 보이는 글자는 짧게, 화면 낭독에는 「사진 1/5」. */}
      <View pointerEvents="none" style={styles.counter} accessibilityLabel={tx(`사진 ${index + 1}/${urls.length}`, `Photo ${index + 1}/${urls.length}`)}>
        <Text testID="place-photo-counter" variant="caption" weight="bold" color={color.text.onAction}>{`${index + 1}/${urls.length}`}</Text>
      </View>
      {/* 넘길 수 있다는 표시 — 「1/8」 숫자만으로는 옆으로 밀어 볼 생각을 못 했다. 지금 쪽만 길게.
          열 장이 넘으면 점이 사진 폭을 채워 오히려 가린다 — 그때는 숫자만. */}
      {urls.length <= 10 ? (
        <View pointerEvents="none" testID="place-photo-dots" style={styles.dots} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          {urls.map((url, i) => <View key={`${i}-${url}`} style={[styles.dot, i === index && styles.dotActive]} />)}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  page: { height: '100%' },
  pageUnmeasured: { width: '100%' },
  // 사진이 밝아도 숫자가 읽히게 어두운 알약 위에 올린다.
  counter: { position: 'absolute', top: spacing[3], right: spacing[3], paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: 'rgba(8, 27, 53, 0.55)' },
  // 사진 위 글자(이름·출처)는 아래 여백 16 안에서 끝난다 — 점은 그 여백 안, 맨 아래에 둔다.
  dots: { position: 'absolute', left: 0, right: 0, bottom: 6, flexDirection: 'row', justifyContent: 'center', gap: 5 },
  dot: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: 'rgba(255, 255, 255, 0.5)' },
  dotActive: { width: 16, backgroundColor: color.text.onAction },
});
