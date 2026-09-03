import { Image, Platform, Pressable, StyleSheet, type ImageStyle, type StyleProp } from 'react-native';
import { useRouter } from 'expo-router';
import { radius } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

const logo = require('../../assets/brand/gabolle-logo-figma.png');

export function BrandLogoLink({ imageStyle }: { imageStyle?: StyleProp<ImageStyle> }) {
  const router = useRouter();
  const { width } = useLayout();
  const webWide = Platform.OS === 'web' && width >= 1120;
  const destination = webWide ? '/home' : '/';
  return <Pressable accessibilityRole="button" accessibilityLabel={webWide ? 'GABOLLE 홈으로 이동' : 'GABOLLE 시작 화면으로 이동'} onPress={() => router.push(destination)} style={({ pressed }) => [styles.link, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" accessibilityIgnoresInvertColors style={imageStyle} /></Pressable>;
}
const styles = StyleSheet.create({ link: { borderRadius: radius.sm, minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center' }, pressed: { opacity: .75 } });
