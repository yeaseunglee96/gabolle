// 카드에 코스 공유 링크 주소가 보이던 것 — S15P21E201-1906.
jest.mock('@/api/client', () => ({ APP_WEB_BASE_URL: 'https://j15e201.p.ssafy.io' }));
import { appendCourseLink, storyBodyText } from '@/social/courseLink';

it('🔴 붙인 링크 주소는 카드 본문에서 빠진다', () => {
  const body = appendCourseLink('해운대 좋았어요', 'https://j15e201.p.ssafy.io/s/abc_123');
  expect(storyBodyText(body)).toBe('해운대 좋았어요');
});
it('링크만 있는 글은 빈 글이다', () => {
  expect(storyBodyText('https://j15e201.p.ssafy.io/s/abc')).toBe('');
});
it('다른 사이트 주소는 그대로 둔다', () => {
  expect(storyBodyText('참고 https://example.com/s/x')).toBe('참고 https://example.com/s/x');
});
