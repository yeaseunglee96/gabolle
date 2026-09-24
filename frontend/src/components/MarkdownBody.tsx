// 마크다운 글을 그린다 — S15P21E201-1136.
import { Fragment } from 'react';
import { Linking, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text, type TextProps } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { parseMarkdown, type MdBlock, type MdInline } from '@/social/markdown';

type Props = {
  /** 사용자가 쓴 글 그대로. 마크다운이 아니어도 된다 — 그냥 문단이 된다. */
  source: string;
  style?: StyleProp<ViewStyle>;
};

type Variant = NonNullable<TextProps['variant']>;

type InlineProps = {
  nodes: MdInline[];
  bold?: boolean;
  italic?: boolean;
  /** 조각의 크기·색. 🔴 Text 는 조각마다 크기·색을 스스로 박아서 바깥 제목의 크기를 덮는다 —
   *  제목 안의 조각은 제목의 크기를 물려받아야 한다(S15P21E201-1597). */
  variant?: Variant;
  tint?: string;
};

function Inline({ nodes, bold, italic, variant = 'body', tint }: InlineProps) {
  return <>
    {nodes.map((node, index) => {
      const key = `${node.type}-${index}`;
      switch (node.type) {
        case 'text':
          return <Text key={key} variant={variant} color={tint} weight={bold ? 'bold' : undefined} style={italic ? styles.italic : undefined}>{node.text}</Text>;
        case 'strong':
          return <Inline key={key} nodes={node.children} bold italic={italic} variant={variant} tint={tint} />;
        case 'em':
          return <Inline key={key} nodes={node.children} bold={bold} italic variant={variant} tint={tint} />;
        case 'code':
          return <Text key={key} variant={variant} color={tint} style={styles.codeInline}>{node.text}</Text>;
        case 'link':
          // 누르면 바깥으로 나간다. 주소는 사용자가 [글자](주소) 로 적은 것뿐이다
          // 글 안의 주소를 자동으로 링크로 바꾸지 않는다(markdown.ts 의 linkify:false).
          return <Text
            key={key}
            variant={variant}
            weight="bold"
            color={color.action.primary}
            onPress={() => { void Linking.openURL(node.href).catch(() => {}); }}
          ><Inline nodes={node.children} bold italic={italic} variant={variant} tint={color.action.primary} /></Text>;
        case 'break':
          return <Text key={key} variant={variant}>{'\n'}</Text>;
        default:
          return null;
      }
    })}
  </>;
}

// 🔴 제목은 크기로 가른다(S15P21E201-1597). 본문(15)보다 큰 기존 크기는 title 18 · display 26 · hero 34
//    셋뿐이라 세 단계에 하나씩 쓴다.
const HEADING_VARIANT: Record<1 | 2 | 3, Variant> = { 1: 'hero', 2: 'display', 3: 'title' };

function Block({ block }: { block: MdBlock }) {
  switch (block.type) {
    case 'paragraph':
      return <Text variant="body" color={color.text.body} style={styles.paragraph}><Inline nodes={block.children} /></Text>;
    case 'heading':
      // 제목은 세 단계까지만 쓴다 — 피드 카드 폭에 그 이상은 구분이 안 된다.
      // hero 의 기본 색은 버튼 위 흰 글자라 제목 색을 따로 준다.
      return <Text variant={HEADING_VARIANT[block.level]} weight="bold" color={color.text.heading} style={styles.heading}>
        <Inline nodes={block.children} bold variant={HEADING_VARIANT[block.level]} tint={color.text.heading} />
      </Text>;
    case 'rule':
      return <View style={styles.rule} />;
    case 'code':
      return <View style={styles.codeBlock}><Text variant="caption" style={styles.codeText}>{block.text}</Text></View>;
    case 'quote':
      return <View style={styles.quote}>{block.children.map((child, index) => <Block key={`q-${index}`} block={child} />)}</View>;
    case 'list':
      return <View style={styles.list}>
        {block.items.map((item, index) => <View key={`li-${index}`} style={styles.listItem}>
          {/* 글머리는 글자로 그린다 — 화면 낭독기가 「하나, 둘」을 읽을 수 있다. */}
          <Text variant="body" color={color.text.muted} style={styles.bullet}>{block.ordered ? `${index + 1}.` : '•'}</Text>
          <View style={styles.listBody}>
            {item.map((child, childIndex) => <Block key={`li-${index}-${childIndex}`} block={child} />)}
          </View>
        </View>)}
      </View>;
    default:
      return null;
  }
}

export function MarkdownBody({ source, style }: Props) {
  const blocks = parseMarkdown(source);
  if (!blocks.length) return null;
  return <View style={style}>
    {blocks.map((block, index) => <Fragment key={`b-${index}`}><Block block={block} /></Fragment>)}
  </View>;
}

const styles = StyleSheet.create({
  paragraph: { marginTop: spacing[2] },
  heading: { marginTop: spacing[4] },
  italic: { fontStyle: 'italic' },
  rule: { height: 1, marginVertical: spacing[4], backgroundColor: color.surface.border },
  quote: {
    marginTop: spacing[3],
    paddingLeft: spacing[3],
    borderLeftWidth: 3,
    borderLeftColor: color.surface.border,
  },
  list: { marginTop: spacing[2], gap: spacing[1] },
  listItem: { flexDirection: 'row', gap: spacing[2] },
  bullet: { minWidth: 20 },
  listBody: { flex: 1 },
  codeInline: { backgroundColor: color.surface.soft, borderRadius: radius.sm },
  codeBlock: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  codeText: { color: color.text.body },
});
