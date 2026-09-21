// 풍경 배경 영상 — 소리 없이 반복 재생하는 장식 배경. 첫 화면(app/index.tsx)이 쓰는 규칙을 부품으로 뺐다(S15P21E201-1398).
//
// 규칙 셋:
// - 동작 줄이기(OS 접근성 설정)가 켜져 있으면 안 돈다 — 움직이는 배경이 곧 그 설정이 막으려는 것이다
// - 웹에서는 만든 직후의 play() 가 조용히 무시된다(아직 읽는 중). readyToPlay 가 오면 그때 다시 튼다
// - 이 부품은 사진 위에 얹는다. 영상이 늦거나 못 뜨면 밑의 사진이 그대로 보인다 — 부품이 사진을 그리지 않는 이유다
import { useEffect, useState } from 'react';
import { AccessibilityInfo, type StyleProp, type ViewStyle } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';

export function useReduceMotion(): boolean {
  const [reduceMotion, setReduceMotion] = useState(false);
  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => { if (alive) setReduceMotion(enabled); }).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { alive = false; sub.remove(); };
  }, []);
  return reduceMotion;
}

export function ScenicVideo({ source, style }: { source: number; style?: StyleProp<ViewStyle> }) {
  const reduceMotion = useReduceMotion();
  const player = useVideoPlayer(source, (instance) => {
    instance.loop = true;
    instance.muted = true;
  });
  useEffect(() => {
    if (reduceMotion) { player.pause(); return; }
    player.play();
    const sub = player.addListener('statusChange', ({ status }) => { if (status === 'readyToPlay') player.play(); });
    return () => sub.remove();
  }, [player, reduceMotion]);
  if (reduceMotion) return null;
  return <VideoView player={player} contentFit="cover" nativeControls={false} allowsPictureInPicture={false} style={style} />;
}
