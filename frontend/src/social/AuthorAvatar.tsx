// 글·댓글 작성자의 동그라미 — 프로필 사진이 있으면 사진, 없으면 이름 첫 글자 (S15P21E201-1804).
//
// 🔴 사진이 없는 경우가 셋이다. 모두 첫 글자로 되돌아간다.
//    ① 서버가 아직 avatarUrl 칸을 안 보낸다(배포 전 — 칸 자체가 없다)
//    ② 사진을 안 골랐다(null)
//    ③ 주소는 있는데 불러오기가 실패했다(onError) — 빈 동그라미만 남기지 않는다
import { useEffect, useState } from 'react';
import { Image, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color } from '@/design/tokens';

type AuthorAvatarProps = {
  name: string;
  uri?: string | null;
  /** 크기·모양·배경색은 부르는 자리의 것을 그대로 쓴다 — 사진은 그 칸을 꽉 채운다. */
  style: StyleProp<ViewStyle>;
  variant?: 'caption' | 'body';
};

export function AuthorAvatar({ name, uri, style, variant = 'caption' }: AuthorAvatarProps) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [uri]);
  const showPhoto = !!uri && !failed;
  return (
    <View style={[style, styles.clip]}>
      {showPhoto
        ? <Image
            testID="author-avatar-image"
            source={{ uri }}
            accessibilityIgnoresInvertColors
            onError={() => setFailed(true)}
            style={StyleSheet.absoluteFill}
          />
        : <Text variant={variant} weight="bold" color={color.text.onAction}>{name.slice(0, 1)}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({ clip: { overflow: 'hidden' } });
