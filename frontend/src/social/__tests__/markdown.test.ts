import { looksLikeMarkdown, markdownToPlain, parseMarkdown } from '../markdown';

describe('마크다운 해석', () => {
  it('굵게·기울임·링크를 알아본다', () => {
    const [block] = parseMarkdown('**해운대**는 *넓고* [지도](https://example.test)가 있어요');
    expect(block.type).toBe('paragraph');
    const kinds = block.type === 'paragraph' ? block.children.map((c) => c.type) : [];
    expect(kinds).toEqual(expect.arrayContaining(['strong', 'em', 'link']));
  });

  it('제목은 세 단계까지만 쓴다 — 화면에 그 이상은 자리가 없다', () => {
    expect(parseMarkdown('# 하나')[0]).toMatchObject({ type: 'heading', level: 1 });
    expect(parseMarkdown('##### 다섯')[0]).toMatchObject({ type: 'heading', level: 3 });
  });

  it('목록은 번호와 글머리를 가른다', () => {
    const bullet = parseMarkdown('- 하나\n- 둘')[0];
    const ordered = parseMarkdown('1. 하나\n2. 둘')[0];
    expect(bullet).toMatchObject({ type: 'list', ordered: false });
    expect(ordered).toMatchObject({ type: 'list', ordered: true });
    if (bullet.type === 'list') expect(bullet.items).toHaveLength(2);
  });

  it('인용·구분선·코드도 덩어리로 나온다', () => {
    expect(parseMarkdown('> 인용문')[0].type).toBe('quote');
    expect(parseMarkdown('---')[0].type).toBe('rule');
    expect(parseMarkdown('```\nconst a = 1\n```')[0]).toMatchObject({ type: 'code', text: 'const a = 1' });
  });

  it('🔴 글에 HTML 을 심을 수 없다 — 글자 그대로 남는다', () => {
    const blocks = parseMarkdown('<script>alert(1)</script> 안녕');
    const plain = markdownToPlain('<script>alert(1)</script> 안녕');
    // 어떤 덩어리도 «그려지는 HTML» 이 되지 않는다
    expect(JSON.stringify(blocks)).not.toContain('html');
    expect(plain).toContain('alert(1)');
  });

  it('마크다운을 안 쓴 글은 그냥 문단 하나다 — 이미 올라간 글이 안 깨진다', () => {
    const blocks = parseMarkdown('오늘 해운대에 다녀왔어요. 날씨가 좋았습니다.');
    expect(blocks).toHaveLength(1);
    expect(blocks[0]).toMatchObject({ type: 'paragraph' });
  });

  it('목록 카드용 평문은 효과를 벗기고 글자만 남긴다', () => {
    const plain = markdownToPlain('# 제목\n\n**굵게** 와 *기울임*\n\n- 하나\n- 둘');
    expect(plain).toBe('제목 굵게 와 기울임 하나 둘');
    expect(plain).not.toContain('#');
    expect(plain).not.toContain('**');
  });

  it('빈 글이나 공백만 있어도 죽지 않는다', () => {
    expect(parseMarkdown('')).toEqual([]);
    expect(markdownToPlain('   ')).toBe('');
  });

  it('마크다운 표시가 있는지 알아본다 — 미리보기를 권할 때 쓴다', () => {
    expect(looksLikeMarkdown('그냥 글이에요')).toBe(false);
    expect(looksLikeMarkdown('**굵게**')).toBe(true);
    expect(looksLikeMarkdown('- 목록')).toBe(true);
    expect(looksLikeMarkdown('# 제목')).toBe(true);
  });
});
