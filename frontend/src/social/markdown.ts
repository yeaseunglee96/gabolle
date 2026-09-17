// 피드 글의 마크다운 — S15P21E201-1136.
//
// 🔴 해석만 여기서 하고 그리는 것은 화면(@/components/MarkdownBody)이 한다.
// 남이 만든 렌더러를 통째로 들이지 않은 이유가 둘이다.
//
// 1. **사용자 글이다.** html:false 로 두면 글에 HTML 을 심을 수 없고, 무엇이 그려질지는
//    우리가 만든 부품만 정한다. 남의 렌더러가 무엇을 통과시키는지 추적하지 않아도 된다
// 2. 글자 크기·색·간격을 이 앱의 디자인 토큰에 맞출 수 있다
//
// 꾸러미를 고르며 셋을 실제로 설치해 이 저장소의 시험 실행기로 돌려 봤다.
// react-native-markdown-display 는 변환 안 된 JSX 를 배포해서 못 읽고, marked 는 ESM
// 전용이라 같은 벽에 막힌다. 둘 다 jest.config.js 의 transformIgnorePatterns 를 덮어써야
// 하는데, 그 파일에 팀이 **일부러 안 건드리기로 한 이유**가 적혀 있다(S15P21E201-774).
import MarkdownIt from 'markdown-it';

/** markdown-it 이 내는 토큰 중 우리가 읽는 칸만. 깊은 경로 import 를 피한다. */
type MdToken = {
  type: string;
  tag: string;
  content: string;
  markup: string;
  children?: MdToken[] | null;
  attrs?: [string, string][] | null;
};

export type MdInline =
  | { type: 'text'; text: string }
  | { type: 'strong'; children: MdInline[] }
  | { type: 'em'; children: MdInline[] }
  | { type: 'code'; text: string }
  | { type: 'link'; href: string; children: MdInline[] }
  | { type: 'break' };

export type MdBlock =
  | { type: 'paragraph'; children: MdInline[] }
  | { type: 'heading'; level: 1 | 2 | 3; children: MdInline[] }
  | { type: 'list'; ordered: boolean; items: MdBlock[][] }
  | { type: 'quote'; children: MdBlock[] }
  | { type: 'code'; text: string }
  | { type: 'rule' };

// 🔴 html:false 가 이 파일의 안전장치다. 글에 <script> 를 써도 글자 그대로 남는다.
// linkify 도 끈다 — 글 안의 주소를 자동으로 링크로 바꾸면, 사용자가 의도하지 않은
// 것을 누를 수 있는 것으로 만든다. 링크는 사용자가 [글자](주소) 로 적었을 때만 생긴다.
const md = new MarkdownIt({ html: false, linkify: false, breaks: true });

function inlineFrom(children: MdToken[]): MdInline[] {
  const out: MdInline[] = [];
  let index = 0;

  const until = (closeType: string): MdInline[] => {
    const nested: MdInline[] = [];
    index += 1; // 여는 토큰을 지난다
    while (index < children.length && children[index].type !== closeType) {
      const piece = one();
      if (piece) nested.push(piece);
    }
    index += 1; // 닫는 토큰을 지난다
    return nested;
  };

  const one = (): MdInline | null => {
    const token = children[index];
    if (!token) { index += 1; return null; }
    switch (token.type) {
      case 'text': index += 1; return token.content ? { type: 'text', text: token.content } : null;
      case 'code_inline': index += 1; return { type: 'code', text: token.content };
      case 'softbreak':
      case 'hardbreak': index += 1; return { type: 'break' };
      case 'strong_open': return { type: 'strong', children: until('strong_close') };
      case 'em_open': return { type: 'em', children: until('em_close') };
      case 'link_open': {
        const href = token.attrs?.find(([name]) => name === 'href')?.[1] ?? '';
        return { type: 'link', href, children: until('link_close') };
      }
      // 모르는 것은 건너뛴다 — 지어내서 그리지 않는다.
      default: index += 1; return null;
    }
  };

  while (index < children.length) {
    const piece = one();
    if (piece) out.push(piece);
  }
  return out;
}

/**
 * 마크다운을 화면이 그릴 수 있는 덩어리로 바꾼다.
 *
 * 🔴 **모르는 것은 버리지 않고 글자로 남긴다**가 원칙이지만, 표처럼 우리가 안 그리는
 * 구조는 조용히 건너뛴다. 대신 {@link markdownToPlain} 이 글자는 살려 두므로 목록
 * 카드에서는 내용이 사라지지 않는다.
 */
export function parseMarkdown(source: string): MdBlock[] {
  const tokens = md.parse(source ?? '', {}) as unknown as MdToken[];
  let index = 0;

  const blocksUntil = (closeType: string | null): MdBlock[] => {
    const out: MdBlock[] = [];
    while (index < tokens.length) {
      const token = tokens[index];
      if (closeType && token.type === closeType) break;
      switch (token.type) {
        case 'heading_open': {
          const raw = Number(token.tag.slice(1));
          const level = (raw <= 1 ? 1 : raw === 2 ? 2 : 3) as 1 | 2 | 3;
          index += 1;
          const children = inlineFrom(tokens[index]?.children ?? []);
          index += 2; // inline + heading_close
          out.push({ type: 'heading', level, children });
          break;
        }
        case 'paragraph_open': {
          index += 1;
          const children = inlineFrom(tokens[index]?.children ?? []);
          index += 2; // inline + paragraph_close
          if (children.length) out.push({ type: 'paragraph', children });
          break;
        }
        case 'bullet_list_open':
        case 'ordered_list_open': {
          const ordered = token.type === 'ordered_list_open';
          const closing = ordered ? 'ordered_list_close' : 'bullet_list_close';
          index += 1;
          const items: MdBlock[][] = [];
          while (index < tokens.length && tokens[index].type !== closing) {
            if (tokens[index].type === 'list_item_open') {
              index += 1;
              items.push(blocksUntil('list_item_close'));
              index += 1;
            } else {
              index += 1;
            }
          }
          index += 1;
          out.push({ type: 'list', ordered, items });
          break;
        }
        case 'blockquote_open': {
          index += 1;
          const children = blocksUntil('blockquote_close');
          index += 1;
          out.push({ type: 'quote', children });
          break;
        }
        case 'fence':
        case 'code_block': {
          index += 1;
          out.push({ type: 'code', text: token.content.replace(/\n$/, '') });
          break;
        }
        case 'hr': index += 1; out.push({ type: 'rule' }); break;
        default: index += 1; break;
      }
    }
    return out;
  };

  return blocksUntil(null);
}

/**
 * 효과를 벗기고 글자만 남긴다 — **목록 카드용**.
 *
 * 🔴 목록에서 제목을 크게 그리면 카드 높이가 글마다 들쭉날쭉해진다. 목록은 「무슨
 * 글인지」만 알면 되므로 평문 한 덩어리로 자르고, 온전한 모양은 상세에서만 보여준다.
 */
export function markdownToPlain(source: string): string {
  const tokens = md.parse(source ?? '', {}) as unknown as MdToken[];
  const pieces: string[] = [];
  const walk = (list: MdToken[]) => {
    for (const token of list) {
      if (token.type === 'text' || token.type === 'code_inline') pieces.push(token.content);
      else if (token.type === 'fence' || token.type === 'code_block') pieces.push(token.content);
      else if (token.children?.length) walk(token.children);
    }
  };
  walk(tokens);
  return pieces.join(' ').replace(/\s+/g, ' ').trim();
}

/** 마크다운 표시가 하나라도 있는가 — 「미리보기」를 권할지 정할 때 쓴다. */
export function looksLikeMarkdown(source: string): boolean {
  return /(^|\n)\s{0,3}(#{1,6}\s|[-*+]\s|\d+\.\s|>\s|```)|(\*\*|__|\*[^\s*]|_[^\s_]|\[[^\]]+\]\()/.test(source ?? '');
}
