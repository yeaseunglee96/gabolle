// 아직 내용을 채우지 않은 화면의 공통 자리표시자.
// 화면 번호·한글 이름·대응 명세 ID 만 보여줘서, 나중에 이 자리부터 채우면 되게 한다.
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { color, fontFamily, spacing, type } from '@/design/tokens';

type ScreenStubProps = {
  /** Figma APP 페이지 화면 번호. 신규 화면은 '신규'. */
  number: string;
  /** 한글 화면 이름 */
  title: string;
  /** 대응 명세 ID */
  specId: string;
};

export function ScreenStub({ number, title, specId }: ScreenStubProps) {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.number}>{number}</Text>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.specId}>{specId}</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: color.canvas,
  },
  card: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
    paddingHorizontal: spacing[6],
  },
  number: {
    color: color.text.muted,
    fontFamily: fontFamily.regular,
    fontSize: type.caption.size,
    lineHeight: type.caption.lineHeight,
    letterSpacing: type.caption.letterSpacing,
  },
  title: {
    color: color.text.heading,
    fontFamily: fontFamily.bold,
    fontSize: type.title.size,
    lineHeight: type.title.lineHeight,
    letterSpacing: type.title.letterSpacing,
  },
  specId: {
    color: color.text.muted,
    fontFamily: fontFamily.regular,
    fontSize: type.caption.size,
    lineHeight: type.caption.lineHeight,
    letterSpacing: type.caption.letterSpacing,
  },
});
