import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';

import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';

const PROVIDER_LABEL: Record<string, string> = {
  google: '구글',
  naver: '네이버',
  kakao: '카카오',
};

export default function OAuthCallback() {
  const { provider } = useLocalSearchParams<{ provider?: string }>();
  const label = provider ? PROVIDER_LABEL[provider.toLowerCase()] ?? '소셜' : '소셜';

  useEffect(() => {
    WebBrowser.maybeCompleteAuthSession();
  }, []);

  return (
    <Screen>
      <View accessibilityLiveRegion="polite" style={styles.body}>
        <Text variant="title" weight="bold">{label} 로그인을 처리하고 있어요.</Text>
        <Text color={color.text.muted} style={styles.guide}>창이 자동으로 닫히지 않으면 닫고 다시 시도해 주세요.</Text>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing[2] },
  guide: { textAlign: 'center' },
});
