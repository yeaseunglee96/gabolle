// 04 로그인 — 레이아웃·여백·타이포는 Figma 04_로그인 실측을 그대로 따르지만,
// 인증 수단은 다르다.
//
// 🔴 Figma 는 이메일·비밀번호 입력칸과 "카카오로 계속하기" 버튼을 보여주는데,
// Figma 가 결정보다 오래된 화면이라 그렇다. 확정된 내용:
//   - 로그인은 Google OAuth 하나뿐이다. 카카오·네이버·이메일 전부 안 쓴다.
//   - 카카오는 지도·리뷰 API 로만 쓰고 로그인 수단이 아니다.
//   - 05 회원가입 화면은 폐기됐다. 비밀번호 찾기도 없다.
// 그래서 입력칸과 카카오 버튼을 빼고 Continue with Google 하나만 남긴다.
// 다음 사람이 Figma 를 보고 "빠뜨렸네" 하며 되돌리지 않도록 이 주석을 남긴다.
import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';

export default function SignIn() {
  const router = useRouter();

  return (
    <Screen scroll>
      <Eyebrow>04 · 계정</Eyebrow>
      <Text variant="display" weight="bold" style={styles.title}>
        로그인
      </Text>
      <Text variant="body" style={styles.subtitle}>
        여행을 저장하고 어디서든 이어보세요.
      </Text>

      <View style={styles.hero}>
        {/* TODO: 실제 부산 사진. 자산이 오기 전까지 브랜드 색 면으로 대체한다. */}
        <Text variant="title" weight="bold" color={color.text.onAction}>
          부산 여행, 이어서 시작해요
        </Text>
      </View>

      {/* Figma 에는 이 버튼 다음 화면이 없어서, 계획 만들기 흐름(06 기본 조건 설정)으로 잇는다. */}
      <Button label="Continue with Google" containerStyle={styles.cta} onPress={() => router.push('/basics')} />

      <Card tinted style={styles.security}>
        <Text variant="body" weight="bold" color={color.text.eyebrow}>
          ✓ 안전하게 보호돼요
        </Text>
        <Text variant="caption" style={styles.securityBody}>
          개인정보와 여행 조건은 암호화해 저장합니다.
        </Text>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
  },
  subtitle: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  hero: {
    height: 126,
    borderRadius: radius.lg,
    backgroundColor: color.action.primary,
    alignItems: 'flex-start',
    justifyContent: 'flex-end',
    padding: spacing[4],
    marginBottom: spacing[6],
  },
  cta: {
    marginBottom: spacing[6],
  },
  security: {
    gap: spacing[1],
  },
  securityBody: {
    color: color.text.body,
  },
});
