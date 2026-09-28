// 목록 카드 미리보기 — 효과를 벗긴 글자에서 제목(#·##·###) 줄이었던 곳만 굵게 남긴다(S15P21E201-1649, 사용자 결정 (다)).
//
// 🔴 크기는 바꾸지 않는다. 한 글자 상자 안에 굵기만 섞어서, 줄 수 자르기(numberOfLines)가 바깥 상자 하나에 그대로 걸린다.
// 🔴 본문은 문자열 그대로 자식으로 넘긴다(Fragment 로 싸지 않는다). 안드로이드에서 한글 낱말이 중간에 끊기지 않게 하는
//    처리(Text 의 joinHangulChildren)는 문자열 자식에만 걸린다.
import type { ReactNode } from 'react';

import { Text, type TextProps } from '@/components/Text';
import { markdownToPreview } from '@/social/markdown';

export function MarkdownPreview({ source, ...props }: TextProps & { source: string }) {
  const children: ReactNode[] = [];
  markdownToPreview(source).forEach((piece, index) => {
    const lead = index > 0 ? ' ' : '';
    if (!piece.heading) {
      children.push(`${lead}${piece.text}`);
      return;
    }
    if (lead) children.push(lead);
    children.push(<Text key={index} variant={props.variant} color={props.color} weight="bold">{piece.text}</Text>);
  });
  return <Text {...props}>{children}</Text>;
}
