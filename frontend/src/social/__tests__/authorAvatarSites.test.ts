// 남의 프로필 사진을 그려야 하는 자리가 첫 글자만 그리지 않는다 — S15P21E201-1821.
// 1804 가 피드 카드·댓글에만 AuthorAvatar 를 붙여 홈 기록 카드와 남의 프로필 머리가 빠졌다.
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

const read = (rel: string) => readFileSync(`${__dirname}/../../../${rel}`, 'utf8') as string;

describe('author avatar sites', () => {
  it('home record card draws AuthorAvatar with the author photo', () => {
    const src = read('src/home/HomeBlocks.tsx');
    expect(src).toContain('<AuthorAvatar name={story.author.displayName} uri={story.author.avatarUrl}');
    expect(src).not.toContain('story.author.displayName.slice(0, 1)');
  });

  it('other user profile header passes the server avatar instead of null', () => {
    const src = read('app/user/[id].tsx');
    expect(src).not.toContain('avatarUri={null}');
    expect(src.match(/avatarUri=\{profile\.avatarUrl \?\? null\}/g)?.length).toBe(2);
  });
});
