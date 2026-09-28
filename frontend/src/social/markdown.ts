// 피드 글의 마크다운 — S15P21E201-1136.
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

// html:false 가 이 파일의 안전장치다. 글에 <script> 를 써도 글자 그대로 남는다.
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

/** 마크다운을 화면이 그릴 수 있는 덩어리로 바꾼다. */
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

/** 효과를 벗기고 글자만 남긴다 — 목록 카드용. */
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

/** 목록 카드 미리보기의 한 조각. heading 이면 제목(#·##·###…) 줄이었던 글자다. */
export type PreviewPiece = { text: string; heading: boolean };

/**
 * 효과를 벗기되 제목 줄이었던 곳만 표시한다 — 목록 카드가 그 줄을 크기 그대로 굵게만 그린다(S15P21E201-1649, 사용자 결정 (다)).
 * 조각을 띄어쓰기 하나로 이어 붙이면 markdownToPlain 과 같은 글이다.
 */
export function markdownToPreview(source: string): PreviewPiece[] {
  const tokens = md.parse(source ?? '', {}) as unknown as MdToken[];
  const out: PreviewPiece[] = [];
  let heading = false;
  const push = (raw: string) => {
    const text = raw.replace(/\s+/g, ' ').trim();
    if (!text) return;
    const last = out[out.length - 1];
    if (last && last.heading === heading) last.text = `${last.text} ${text}`;
    else out.push({ text, heading });
  };
  const walk = (list: MdToken[]) => {
    for (const token of list) {
      if (token.type === 'heading_open') heading = true;
      else if (token.type === 'heading_close') heading = false;
      else if (token.type === 'text' || token.type === 'code_inline' || token.type === 'fence' || token.type === 'code_block') push(token.content);
      else if (token.children?.length) walk(token.children);
    }
  };
  walk(tokens);
  return out;
}

/** 마크다운 표시가 하나라도 있는가 — 「미리보기」를 권할지 정할 때 쓴다. */
export function looksLikeMarkdown(source: string): boolean {
  return /(^|\n)\s{0,3}(#{1,6}\s|[-*+]\s|\d+\.\s|>\s|```)|(\*\*|__|\*[^\s*]|_[^\s_]|\[[^\]]+\]\()/.test(source ?? '');
}
