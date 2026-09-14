// 언어 스위처를 모든 화면 우측 상단에 상시 노출한다(S15P21E201-261 완료 기준).
// 화면마다 헤더 구조가 달라 각 화면에 손으로 넣는 대신, 루트 레이아웃에 한 번만
// 띄워 둔다 — BuildInfoBadge와 같은 자리·같은 방식(position: absolute, 우측 상단)을
// 쓴다. pointerEvents="box-none"이라 배지가 없는 빈 자리는 아래 화면 터치를 막지 않는다.
//
// ApiAvailabilityBanner는 화면 상단을 가로지르는 넓은 배너라(좌우 spacing[4] 여백만
// 두고 거의 전체 폭), 같은 top 자리에 그대로 두면 배지가 배너 밑에 깔린다. 그 배너와
// 같은 subscribeApiAvailability를 구독해, 배너가 떠 있을 때는 배지를 그 아래로
// 내린다(배너 minHeight 64 + 여백 어림).
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribeApiAvailability } from '@/api/client';
import { LanguageBadge } from './LanguageBadge';

const BANNER_CLEARANCE = 84;

export function GlobalLanguageBadge() {
  const insets = useSafeAreaInsets();
  const [bannerShown, setBannerShown] = useState(false);

  useEffect(() => subscribeApiAvailability(setBannerShown), []);

  const top = insets.top + 8 + (bannerShown ? BANNER_CLEARANCE : 0);
  return (
    <View pointerEvents="box-none" style={[styles.overlay, { top, right: insets.right + 8 }]}>
      <LanguageBadge />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { position: 'absolute', zIndex: 998, alignItems: 'flex-end' },
});
