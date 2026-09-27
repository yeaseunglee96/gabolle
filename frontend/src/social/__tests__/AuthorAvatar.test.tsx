// 피드·댓글 작성자 동그라미 — 남의 프로필 사진이 첫 글자로만 보이던 것 (S15P21E201-1804).
import { fireEvent, render, screen } from '@testing-library/react-native';

import { AuthorAvatar } from '@/social/AuthorAvatar';

declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

describe('작성자 동그라미', () => {
  it('사진 주소가 있으면 그 사진을 그린다', () => {
    render(<AuthorAvatar name="장효준" uri="https://example.com/a.jpg" style={{ width: 24, height: 24 }} />);
    expect(screen.getByTestId('author-avatar-image').props.source).toEqual({ uri: 'https://example.com/a.jpg' });
    expect(screen.queryByText('장')).toBeNull();
  });

  it('서버가 칸을 안 보내거나(배포 전) null 이면 첫 글자', () => {
    const { rerender } = render(<AuthorAvatar name="장효준" style={{}} />);
    expect(screen.getByText('장')).toBeTruthy();
    rerender(<AuthorAvatar name="장효준" uri={null} style={{}} />);
    expect(screen.getByText('장')).toBeTruthy();
    expect(screen.queryByTestId('author-avatar-image')).toBeNull();
  });

  it('사진을 못 불러오면 첫 글자로 되돌아간다', () => {
    render(<AuthorAvatar name="장효준" uri="https://example.com/broken.jpg" style={{}} />);
    fireEvent(screen.getByTestId('author-avatar-image'), 'error');
    expect(screen.getByText('장')).toBeTruthy();
  });

  it('피드 카드와 댓글이 이 동그라미에 작성자 사진을 넘긴다', () => {
    const feed = readFileSync(`${__dirname}/../../../app/(tabs)/feed.tsx`, 'utf8');
    const detail = readFileSync(`${__dirname}/../../../app/feed/[id].tsx`, 'utf8');
    expect(feed).toContain('uri={story.author.avatarUrl}');
    expect(detail).toContain('uri={reply.author.avatarUrl}');
  });
});
