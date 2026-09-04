import { Image, Pressable, StyleSheet, type ImageStyle, type StyleProp } from 'react-native';
import { useRouter } from 'expo-router';
import { radius } from '@/design/tokens';

const logo = require('../../assets/brand/gabolle-logo-figma.png');

export function BrandLogoLink({ imageStyle }: { imageStyle?: StyleProp<ImageStyle> }) {
  const router = useRouter();
  return <Pressable accessibilityRole="link" accessibilityLabel="GABOLLE 시작 화면으로 이동" onPress={() => router.push('/')} style={({ pressed }) => [styles.link, pressed && styles.pressed]}><Image source={logo} resizeMode="contain" accessibilityIgnoresInvertColors style={imageStyle} /></Pressable>;
}
const styles = StyleSheet.create({ link: { borderRadius: radius.sm, minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center' }, pressed: { opacity: .75 } });
