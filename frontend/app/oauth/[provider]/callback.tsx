// 소셜 로그인이 끝나고 provider 가 사용자를 되돌려 보내는 착지 화면이다.
// 경로는 /oauth/google/callback · /oauth/naver/callback · /oauth/kakao/callback 셋이고
// [provider] 동적 구간으로 한 파일이 셋을 받는다 (S15P21E201-612, DEC-AUTH-006).
//
// 🔴 이 화면이 없으면 소셜 로그인이 마지막에 실패한다. 2026-09-04 에 실제로 그랬다 —
//    provider 는 code 를 들고 정상으로 되돌려 보내는데 Expo Router 에 이 경로가 없어서
//    "Unmatched Route / Page could not be found" 가 떴다. 세 provider 전부 같았다.
//
// 하는 일은 사실상 하나다. WebBrowser.maybeCompleteAuthSession() 을 부르는 것이다.
// 웹에서 소셜 로그인은 팝업으로 열리고(src/auth/oauth.ts 의 openAuthSessionAsync),
// 그 팝업이 이 주소에 착지했을 때 이 함수가 원래 창으로 결과 URL 을 넘기고 팝업을
// 닫는다. 그 신호가 없으면 원래 창의 await 가 영원히 끝나지 않는다.
//
// src/auth/oauth.ts 도 모듈 맨 위에서 같은 함수를 부르지만 그것만으로는 부족했다 —
// 라우트가 없으면 그 모듈을 아무도 import 하지 않아 코드가 실행되지 않는다.
// 그래서 착지 화면에서 직접 부른다.
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';

import { color, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';

const LABEL: Record<string, string> = {
  google: '구글',
  naver: '네이버',
  kakao: '카카오',
};

export default function OAuthCallback() {
  const { provider } = useLocalSearchParams<{ provider?: string }>();
  const label = (provider && LABEL[provider]) ?? '소셜';

  useEffect(() => {
    // 팝업을 연 원래 창으로 결과를 넘기고 이 창을 닫는다. 팝업이 아닌 상황
    // (사용자가 이 주소를 직접 열었을 때 등)에서는 아무 일도 하지 않는다.
    WebBrowser.maybeCompleteAuthSession();
  }, []);

  return (
    <Screen>
      <View style={styles.body}>
        <Text>{label} 로그인을 처리하고 있어요.</Text>
        <Text>창이 자동으로 닫히지 않으면 닫고 다시 시도해 주세요.</Text>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
    backgroundColor: color.canvas,
  },
});
