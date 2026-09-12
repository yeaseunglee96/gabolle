// 언어 스위처를 모든 화면 우측 상단에 상시 노출한다(S15P21E201-261 완료 기준).
// 화면마다 헤더 구조가 달라 각 화면에 손으로 넣는 대신, 루트 레이아웃에 한 번만
// 띄워 둔다 — BuildInfoBadge와 같은 자리·같은 방식(position: absolute, 우측 상단)을
// 쓴다. pointerEvents="box-none"이라 배지가 없는 빈 자리는 아래 화면 터치를 막지 않는다.
//
// ApiAvailabilityBanner는 화면 상단을 가로지르는 넓은 배너라(좌우 spacing[4] 여백만
// 두고 거의 전체 폭), 같은 top 자리에 그대로 두면 배지가 배너 밑에 깔린다. 그 배너와
// 같은 subscribeApiAvailability를 구독해, 배너가 떠 있을 때는 배지를 그 아래로
// 내린다(배너 minHeight 64 + 여백 어림).
//
// 🔴 이 배지는 절대좌표라 화면의 일반 레이아웃 흐름에 안 잡힌다 — 화면이 우측 상단에
//    자기 버튼(건너뛰기·뒤로가기 옆 아이콘 등)을 두면 이 배지와 그냥 겹친다. 실사용
//    리포트로 최소 열두 화면에서 이 결함이 나왔다(2026-09-12 — home.tsx 가 처음,
//    app-intro·spend-profile·taste·sign-up·oauth-signup·oauth-link·basics·feed·
//    collaborate·share·coauthors·festivals·explore·place/[id] 가 뒤이어 발견됨).
//    Screen(컴포넌트) 자체의 기본 paddingTop(24)만으로는 안 가려진다 — 새 화면에서
//    우측 상단에 뭔가를 둘 때는 그 헤더 행 스타일에 marginTop: spacing[6] 를 반드시
//    더한다. (Screen 자체를 고쳐 전역으로 미는 방법도 검토했으나, 헤더가 없는 화면
//    전부에 불필요한 여백이 생기는 것이 더 위험하다고 판단해 화면별로 고쳤다.)
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
