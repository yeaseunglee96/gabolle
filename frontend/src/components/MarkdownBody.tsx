// 마크다운 글을 그린다 —.
import { Fragment } from 'react';
import { Linking, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { parseMarkdown, type MdBlock, type MdInline } from '@/social/markdown';

type Props = {
  /** 사용자가 쓴 글 그대로. 마크다운이 아니어도 된다 — 그냥 문단이 된다. */
  source: string;
  style?: StyleProp<ViewStyle>;
};

function Inline({ nodes, bold, italic }: { nodes: MdInline[]; bold?: boolean; italic?: boolean }) {
  return <>
    {nodes.map((node, index) => {
      const key = `${node.type}-${index}`;
      switch (node.type) {
        case 'text':
          return <Text key={key} variant="body" weight={bold ? 'bold' : undefined} style={italic ? styles.italic : undefined}>{node.text}</Text>;
        case 'strong':
          return <Inline key={key} nodes={node.children} bold italic={italic} />;
        case 'em':
          return <Inline key={key} nodes={node.children} bold={bold} italic />;
        case 'code':
          return <Text key={key} variant="body" style={styles.codeInline}>{node.text}</Text>;
        case 'link':
          // 누르면 바깥으로 나간다. 주소는 사용자가 [글자](주소) 로 적은 것뿐이다
          // 글 안의 주소를 자동으로 링크로 바꾸지 않는다(markdown.ts 의 linkify:false).
          return <Text
            key={key}
            variant="body"
            weight="bold"
            color={color.action.primary}
            onPress={() => { void Linking.openURL(node.href).catch(() => {}); }}
          ><Inline nodes={node.children} bold italic={italic} /></Text>;
        case 'break':
          return <Text key={key} variant="body">{'\n'}</Text>;
        default:
          return null;
      }
    })}
  </>;
}

function Block({ block }: { block: MdBlock }) {
  switch (block.type) {
    case 'paragraph':
      return <Text variant="body" color={color.text.body} style={styles.paragraph}><Inline nodes={block.children} /></Text>;
    case 'heading':
      // 제목은 세 단계까지만 쓴다 — 피드 카드 폭에 그 이상은 구분이 안 된다.
      // 3단계는 크기 대신 색을 낮춰 가른다(더 작게 하면 본문보다 작아진다).
      return <Text
        variant={block.level === 1 ? 'title' : 'body'}
        weight="bold"
        color={block.level === 3 ? color.text.muted : undefined}
        style={styles.heading}
      ><Inline nodes={block.children} bold /></Text>;
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
