// 홈 코치마크 — 시안 5 의 01c (docs/design_handoff_brand_first_run 의 HomeCoach). 한 장뿐이다.
//
// 앱 소개 세 장을 막 넘긴 사람에게 홈에서 딱 두 가지만 짚는다 — 「여기서 시작한다」(시작 바)와
// 「궁금하면 동백이」(오른쪽 아래 단추). 나머지는 보면 안다. 장수가 늘면 아무도 안 읽는다.
// 한 장이라 「건너뛰기」가 따로 없다 — 닫는 길은 「먼저 둘러볼게요」 하나다. 닫는 단추가 둘이면 둘 중 무엇이 다른지 사람이 고민한다.
//
// 어두운 막에 두 구멍을 뚫는다(SVG 마스크). 구멍 자리는 홈이 실제로 잰 값(measureInWindow)이라
// 화면 폭이 바뀌어도 맞다 — 좌표를 박아 두면 폴드 같은 기기에서 엉뚱한 곳을 가리킨다.
import { useState } from 'react';
import { Modal, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, Mask, Path, Rect } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

export type CoachHole = { x: number; y: number; width: number; height: number; radius: number };

/**
 * 설명 덩이를 시작 바 «아래»에 둘지 «위»에 둘지.
 *
 * 🔴 아래에 두는 것이 기본이다 — 읽는 순서가 「바 → 설명」이라 자연스럽다. 그런데 1080x2280
 *    같은 폰에서는 바 아래 남는 높이보다 내용이 길어서, 단추 둘과 마지막 줄이 화면 밖으로 밀렸다.
 *    닫는 길이 「먼저 둘러볼게요」 하나뿐인데 그것이 안 보이면 «뒤로가기 말고는 닫을 방법이 없다»
 *    (S15P21E201-1448, 실기 빌드 29).
 *
 * 위로 올리면 구멍(시작 바)도 안 가리고 위쪽 빈 자리를 쓴다. 위도 모자라면 아래로 둔다 —
 * 그때는 어차피 어느 쪽도 안 들어가므로, 읽는 순서라도 지키는 쪽이 낫다.
 *
 * 높이를 아직 못 쟀으면(0) 아래에 둔다. 첫 그림 한 장만 아래였다가 제자리를 찾는다.
 */
export function coachCopyPlacement({ screenHeight, insetTop, insetBottom, startBarBottom, startBarTop, copyHeight, gap }: {
  screenHeight: number;
  insetTop: number;
  insetBottom: number;
  startBarBottom: number;
  startBarTop: number;
  copyHeight: number;
  gap: number;
}): 'below' | 'above' {
  if (copyHeight <= 0) return 'below';
  const fitsBelow = startBarBottom + gap + copyHeight + gap <= screenHeight - insetBottom;
  if (fitsBelow) return 'below';
  const fitsAbove = startBarTop - gap - copyHeight >= insetTop + gap;
  return fitsAbove ? 'above' : 'below';
}

const SCRIM = 'rgba(25,25,25,0.9)';
const RING = color.action.primary;

export function HomeCoach({ visible, startBar, assistant, onStart, onClose }: {
  visible: boolean;
  /** 시작 바의 자리. 못 쟀으면 null — 그때는 구멍 없이 글만 보여 준다. */
  startBar: CoachHole | null;
  /** 동백이 단추의 자리. */
  assistant: CoachHole | null;
  onStart: () => void;
  onClose: () => void;
}) {
  const { tx } = useI18n();
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  // 🔴 두 설명 덩이는 각각 absolute 라 서로의 자리를 모른다 — 작은 폰(1080×2280)에서는 시작 바 설명이 동백이 설명 위로
  //    100px 쯤 겹쳐 글자가 두 겹으로 읽혔다(실기 빌드 27, S15P21E201-1403). 시작 바 설명의 높이를 재서, 동백이 설명 자리를
  //    침범하면 동백이 설명을 시작 바 설명 «안의 한 줄»로 접는다. 화살표는 그대로 단추를 가리킨다.
  const [startCopyHeight, setStartCopyHeight] = useState(0);
  if (!visible) return null;
  const holes = [startBar, assistant].filter((hole): hole is CoachHole => Boolean(hole));
  // 시작 바 설명은 바 아래에, 동백이 설명은 단추 왼쪽 위에 — 구멍을 가리지 않는 자리.
  const startCopyTop = startBar ? startBar.y + startBar.height + spacing[4] : height * 0.28;
  // 설명은 단추 위 64 — 그 사이 56px 이 화살표 자리다. 전에는 12 위에 두고 화살표를 46 위에서 시작해 글자를 가로질렀다.
  const ASSISTANT_COPY_GAP = 64;
  const assistantCopyBottom = assistant ? height - assistant.y + ASSISTANT_COPY_GAP : height * 0.2;
  const assistantCopyRight = assistant ? width - assistant.x - assistant.width : spacing[6];
  const ASSISTANT_COPY_HEIGHT = 96;
  const assistantCopyTop = height - assistantCopyBottom - ASSISTANT_COPY_HEIGHT;
  const placement = startBar ? coachCopyPlacement({
    screenHeight: height,
    insetTop: insets.top,
    insetBottom: insets.bottom,
    startBarBottom: startBar.y + startBar.height,
    startBarTop: startBar.y,
    copyHeight: startCopyHeight,
    gap: spacing[4],
  }) : 'below';
  // 위로 올리면 오른쪽 아래 동백이 설명과는 아예 안 만난다 — 접을 이유가 없다.
  const folded = placement === 'below' && startCopyHeight > 0 && startCopyTop + startCopyHeight + spacing[3] > assistantCopyTop;
  const copyPosition = placement === 'above' && startBar
    ? { bottom: height - startBar.y + spacing[4] }
    : { top: startCopyTop };

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <View style={styles.root} accessibilityViewIsModal>
        <Svg width={width} height={height} style={StyleSheet.absoluteFill} pointerEvents="none">
          <Defs>
            {/* 마스크의 흰·검정은 색이 아니라 「보임·가림」이다(밝기 마스크) — 배색 토큰과 무관하다. */}
            <Mask id="coach-holes">
              <Rect x={0} y={0} width={width} height={height} fill="white" />
              {holes.map((hole, index) => (
                <Rect key={index} x={hole.x} y={hole.y} width={hole.width} height={hole.height} rx={hole.radius} fill="black" />
              ))}
            </Mask>
          </Defs>
          <Rect x={0} y={0} width={width} height={height} fill={SCRIM} mask="url(#coach-holes)" />
          {holes.map((hole, index) => (
            <Rect key={`ring-${index}`} x={hole.x - 3} y={hole.y - 3} width={hole.width + 6} height={hole.height + 6} rx={hole.radius + 3} fill="none" stroke={RING} strokeWidth={2} />
          ))}
          {assistant ? (
            // 동백이 설명에서 단추로 흐르는 화살표 — 「저 단추」를 손가락 대신 가리킨다.
            <>
              <Path d={`M${assistant.x + assistant.width * 0.15} ${assistant.y - ASSISTANT_COPY_GAP + 10} Q${assistant.x + assistant.width * 0.2} ${assistant.y - 16} ${assistant.x + assistant.width * 0.45} ${assistant.y - 10}`} stroke={color.text.onAction} strokeWidth={2} fill="none" strokeLinecap="round" />
              <Circle cx={assistant.x + assistant.width * 0.45} cy={assistant.y - 10} r={3.5} fill={color.text.onAction} />
            </>
          ) : null}
        </Svg>

        <View style={[styles.copy, copyPosition]} onLayout={(event) => setStartCopyHeight(Math.round(event.nativeEvent.layout.height))}>
          <Text variant="micro" weight="bold" color={RING} style={styles.eyebrow}>{tx('GABOLLE · 부산 전용 AI 여행 가이드', 'GABOLLE · AI travel guide just for Busan')}</Text>
          <Text variant="title" weight="bold" color={color.text.onAction} style={styles.center}>{tx('여기서 시작해요', 'Start right here')}</Text>
          <Text variant="body" color="rgba(255,255,255,0.85)" style={styles.center}>{tx('출발지·날짜·인원만 고르면 돼요. 나머지는 물어보는 만큼만 답하면 되고, 절반은 건너뛰어도 괜찮아요.', 'Just pick where you start, your dates and who is coming. Answer the rest only as much as you like — skipping half is fine.')}</Text>
          <View style={styles.actions}>
            <Pressable accessibilityRole="button" onPress={onStart} style={({ pressed }) => [styles.primary, pressed && styles.pressed]}>
              <Text variant="body" weight="bold" color={color.text.heading}>{tx('바로 여행 만들기', 'Plan a trip now')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.ghost, pressed && styles.pressed]}>
              <Text variant="util" weight="bold" color="rgba(255,255,255,0.85)">{tx('먼저 둘러볼게요', 'Let me look around first')}</Text>
            </Pressable>
          </View>
          {folded ? <Text variant="util" color="rgba(255,255,255,0.85)" style={styles.center}>{tx('궁금한 건 오른쪽 아래 동백이에게 — 어느 언어로 물어도 답해요', 'Ask Dongbaek at the bottom right — in any language')}</Text> : null}
          <Text variant="caption" color="rgba(255,255,255,0.6)" style={styles.center}>{tx('이 안내는 한 번만 떠요 · 마이페이지 › 도움말에서 다시 볼 수 있어요', 'You see this once · replay it from My page › Help')}</Text>
        </View>

        {folded ? null : <View style={[styles.assistantCopy, { bottom: assistantCopyBottom, right: assistantCopyRight + spacing[2] }]}>
          <Text variant="title" weight="bold" color={color.text.onAction} style={styles.right}>{tx('궁금한 건 동백이에게', 'Ask Dongbaek anything')}</Text>
          <Text variant="util" color="rgba(255,255,255,0.85)" style={styles.right}>{tx('화면 어디서든 오른쪽 아래. 어느 언어로 물어도 답하고, 메뉴판 번역·통역도 여기서 열려요.', 'Always at the bottom right. Ask in any language — menu translation and interpreting open from here too.')}</Text>
        </View>}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  copy: { position: 'absolute', left: spacing[6], right: spacing[6], gap: spacing[2], alignItems: 'center' },
  eyebrow: { letterSpacing: 1 },
  center: { textAlign: 'center' },
  right: { textAlign: 'right' },
  actions: { alignItems: 'center', gap: spacing[1], marginTop: spacing[2] },
  primary: { minHeight: 48, paddingHorizontal: spacing[8], borderRadius: radius.md, backgroundColor: color.surface.card, justifyContent: 'center' },
  ghost: { minHeight: 40, paddingHorizontal: spacing[3], justifyContent: 'center' },
  assistantCopy: { position: 'absolute', maxWidth: 250, gap: spacing[1] },
  pressed: { opacity: 0.8 },
});
