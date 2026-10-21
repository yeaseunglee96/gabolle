// 칩·알약 안에 «이름 하나»를 넣는 글자 — S15P21E201-2054.
//
// 🔴 안드로이드(갤럭시)에서 앱 언어 칩의 「繁體中文」이 「繁體中」으로 끝 글자가 잘려 보였다. 탭(2026-10-05)에서 먼저 나왔고,
//    S15P21E201-1988 이 «한 줄 · 넘치면 글자 줄이기»(numberOfLines + adjustsFontSizeToFit)로 막으려 했지만, 그 처방이 들어간
//    앱 1.0.3 을 갤럭시 S22 에서 열어도 그대로였다(2026-10-14). 잘림을 가리는 처방은 듣지 않았다.
//
// 🔴 원인(RN 0.86 안드로이드 소스를 읽어 얻은 추정 — 실기기 확인이 필요하다).
//
//   ① 여유가 없다. 칩은 alignItems: center 라 글자가 «잰 폭 그대로»만 차지한다. 안드로이드는 글을 한 번 잰 뒤(폭 = 글 길이),
//      화면에 그리는 기본 부품(TextView)이 같은 글을 그 폭 안에서 다시 줄 나눈다. 두 계산이 한 점이라도 어긋나면
//      끝 글자(한자는 글자 사이에서 끊긴다)가 둘째 줄로 밀리는데, 둘째 줄은 부품 높이(한 줄) 밖이라 안 보인다.
//      이 팀이 세 번 겪은 증상과 모양이 같다 — 1966·1972·1988(자리는 차지하는데 끝이 안 보임).
//      첫 화면의 언어 시트 줄(WelcomeLanguageSheet)은 같은 이름을 같은 굵기로 그리는데 글이 줄 폭 전체로 펴져 있다 — 이 칩과 다른 점은
//      여유뿐이고, 그 줄에서 잘렸다는 보고는 아직 없다.
//   ② 줄임표가 꺼진다. adjustsFontSizeToFit 이 켜지면 RN 이 줄임표(…)를 꺼 버려서(ReactTextView.updateView), 잘려도 흔적이 없다.
//      그리는 순간에는 글자 크기를 다시 맞추는 반복도 돈다. 잘림을 «고치는» 장치가 아니라 «숨기는» 장치였다.
//   ③ 굵기 700. Text 가 가나·한자 글은 fontFamily 를 비워 기기 글꼴로 그리게 한다(S15P21E201-1942). 기기의 한자 대체 글꼴에 굵은 면이
//      없으면 안드로이드가 «가짜 굵게»(굵은 면이 없을 때 보통 면을 덧칠해 굵어 보이게 하는 것)를 입힌다 — 中·日 칩만 굵어 보이는 이유.
//      안드로이드가 가짜 굵게를 고르는 기준은 «요청 굵기가 600 이상이고 글꼴 굵기보다 200 이상 높을 때»로 알려져 있어 500 은 입히지 않는다
//      (안드로이드 글꼴 매칭 규칙에 대한 기억이다 — 이 PC 에서 기기로 확인하지 못했다).
//
// 그래서 이 부품이 하는 일 셋.
//   · 칩 폭 전체로 펴고(alignSelf: stretch) 가운데 맞춘다(textAlign: center) — 잰 폭과 그려지는 폭 사이에 여유가 생긴다.
//   · 굵기를 medium(500) 으로 — 가짜 굵게 문턱 아래. 한글·영문은 Pretendard-Medium, 한자·가나는 기기 글꼴.
//   · numberOfLines · adjustsFontSizeToFit 을 받지 않는다 — 타입이 막는다. 넘치면 줄이 바뀐다(칩이 커진다), 잘리지 않는다.
//
// 칩 말고 «이름 하나»를 알약 안에 넣는 곳(필터 칩·선택 칩)이 생기면 Text 대신 이것을 쓴다.
import { StyleSheet } from 'react-native';

import { Text, type TextProps } from '@/components/Text';

/** 줄을 자르거나 글자를 줄이는 속성과 굵기는 받지 않는다 — 이 부품이 정하는 것들이다. */
export type ChipLabelProps = Omit<TextProps, 'numberOfLines' | 'adjustsFontSizeToFit' | 'minimumFontScale' | 'ellipsizeMode' | 'weight'>;

export function ChipLabel({ style, ...rest }: ChipLabelProps) {
  return <Text weight="medium" {...rest} style={[styles.label, style]} />;
}

const styles = StyleSheet.create({
  label: { alignSelf: 'stretch', textAlign: 'center' },
});
