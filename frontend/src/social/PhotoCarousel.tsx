// 여러 장 사진을 옆으로 넘기는 줄 — 피드 목록 커버와 기록 상세가 같이 쓴다(S15P21E201-1787, QA).
//
// 전에는 목록 커버가 첫 사진 한 장만 그리고 아래 점은 «몇 장인지»만 알렸다(S15P21E201-1177 때 넘기기를 미뤘다).
// 상세는 사진을 격자로 한꺼번에 깔았다. 이제 둘 다 한 장씩 넘기고, 점이 몇 장 중 몇 번째인지 따라간다.
//
// 🔴 웹은 마우스로 끌어 넘기기가 안 된다 — 웹에서만 좌우 화살표를 둔다. 폰은 손가락으로 넘긴다.
// 읽기 이름은 카탈로그에 이미 있는 문구(「이전」·「다음」·「사진 %d/%d」)를 쓴다 — 새 문구는 일본어·중국어로 떨어진다.
import { useRef, useState } from 'react';
import { Image, Platform, Pressable, ScrollView, StyleSheet, View, type ImageResizeMode, type LayoutChangeEvent, type NativeScrollEvent, type NativeSyntheticEvent, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

/** 가로 위치(x)와 한 장 폭(width)으로 지금 보이는 사진 번호를 구한다. 끝을 넘겨 튕겨도 범위 안에 둔다. */
export function photoIndexAt(x: number, width: number, count: number): number {
  if (width <= 0 || count <= 0) return 0;
  return Math.min(count - 1, Math.max(0, Math.round(x / width)));
}

export function PhotoCarousel({
  urls,
  resizeMode = 'cover',
  onPressPhoto,
  pressLabel,
  style,
}: {
  urls: string[];
  /** 목록 커버는 칸을 채우고(cover), 상세는 사진 전체를 보인다(contain). */
  resizeMode?: ImageResizeMode;
  /** 사진을 누르면 할 일 — 목록은 상세로 간다. 없으면 사진은 누르는 자리가 아니다. */
  onPressPhoto?: () => void;
  /** 사진을 누르는 자리의 읽기 이름. */
  pressLabel?: string;
  /** 크기(높이 또는 비율)는 부르는 쪽이 정한다. */
  style?: StyleProp<ViewStyle>;
}) {
  const { tx } = useI18n();
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
  const scroller = useRef<ScrollView>(null);
  const count = urls.length;

  const onLayout = (event: LayoutChangeEvent) => setWidth(Math.round(event.nativeEvent.layout.width));
  const onScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const next = photoIndexAt(event.nativeEvent.contentOffset.x, width, count);
    if (next !== index) setIndex(next);
  };
  const goTo = (next: number) => {
    const target = Math.min(count - 1, Math.max(0, next));
    scroller.current?.scrollTo({ x: target * width, animated: true });
    setIndex(target);
  };

  const photo = (url: string, at: number) => {
    const image = <Image source={{ uri: url }} resizeMode={resizeMode} accessibilityIgnoresInvertColors style={styles.image} />;
    const size = { width: width || '100%', height: '100%' } as const;
    return onPressPhoto
      ? <Pressable key={`${at}:${url}`} accessibilityRole="link" accessibilityLabel={pressLabel} onPress={onPressPhoto} style={size}>{image}</Pressable>
      : <View key={`${at}:${url}`} style={size}>{image}</View>;
  };

  return (
    <View onLayout={onLayout} style={[styles.frame, style]}>
      {count > 1 ? (
        <ScrollView
          ref={scroller}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          onScroll={onScroll}
          scrollEventThrottle={16}
          style={StyleSheet.absoluteFill}
        >
          {urls.map(photo)}
        </ScrollView>
      ) : count === 1 ? photo(urls[0], 0) : null}

      {count > 1 ? (
        <View
          pointerEvents="none"
          accessible
          accessibilityLabel={tx(`사진 ${index + 1}/${count}`, `Photo ${index + 1}/${count}`)}
          style={styles.dots}
        >
          {urls.map((url, at) => <View key={`${at}:${url}`} style={[styles.dot, at === index && styles.dotOn]} />)}
        </View>
      ) : null}

      {Platform.OS === 'web' && count > 1 ? (
        <>
          {index > 0 ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('이전', 'Previous')} onPress={() => goTo(index - 1)} style={[styles.arrow, styles.arrowLeft]}>
              <Text weight="bold" color={color.text.heading}>‹</Text>
            </Pressable>
          ) : null}
          {index < count - 1 ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('다음', 'Next')} onPress={() => goTo(index + 1)} style={[styles.arrow, styles.arrowRight]}>
              <Text weight="bold" color={color.text.heading}>›</Text>
            </Pressable>
          ) : null}
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { position: 'relative', width: '100%', overflow: 'hidden' },
  image: { width: '100%', height: '100%' },
  dots: { position: 'absolute', left: 0, right: 0, bottom: spacing[3], flexDirection: 'row', justifyContent: 'center', gap: spacing[1] },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: color.surface.card, opacity: 0.5 },
  dotOn: { opacity: 1 },
  arrow: {
    position: 'absolute', top: '50%', marginTop: -18, width: 36, height: 36, borderRadius: radius.full,
    alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,253,248,0.92)',
  },
  arrowLeft: { left: spacing[2] },
  arrowRight: { right: spacing[2] },
});
