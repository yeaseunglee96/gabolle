// 기록 본문 속 코스 링크 — S15P21E201-1593.
//
// 글쓰기의 「코스 링크 함께 올리기」가 읽기 전용 공유 링크(`<웹 주소>/s/<토큰>`)를 본문 끝에 붙이고,
// 피드 카드·기록 상세는 그 링크를 찾아 코스 카드로 그린다. 서버에 새 칸은 없다 — 링크는 본문의 글자다.
import { APP_WEB_BASE_URL } from '@/api/client';

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// 🔴 우리 웹 주소의 /s/ 만 코스로 본다. 다른 사이트의 /s/ 는 그냥 글자다.
//    식은 쓸 때 만든다 — 파일을 불러오는 순간 만들면, 웹 주소를 안 주는 환경(시험의 가짜 client 등)에서 불러오기만 해도 터진다.
function courseLinkPattern(): RegExp | null {
  return APP_WEB_BASE_URL ? new RegExp(`${escapeRegExp(APP_WEB_BASE_URL)}/s/([A-Za-z0-9_-]+)`) : null;
}

export type CourseLink = { token: string; url: string };

/** 본문에서 우리 공유 링크를 찾는다 — 첫 번째 하나만. 없으면 null. */
export function findCourseLink(body: string): CourseLink | null {
  const match = courseLinkPattern()?.exec(body);
  return match ? { token: match[1], url: match[0] } : null;
}

/** 본문 끝에 코스 링크를 붙인다 — 빈 줄 하나를 두고. */
export function appendCourseLink(body: string, shareUrl: string): string {
  const trimmed = body.trim();
  return trimmed ? `${trimmed}\n\n${shareUrl}` : shareUrl;
}

/**
 * 화면에 그릴 본문 — 코스 카드로 그리는 링크를 뺀다. 같은 것을 카드와 글자로 두 번 그리지 않는다.
 * 링크만 있던 줄은 줄째 사라지고, 글 사이에 있던 링크는 그 자리만 비운다.
 */
export function withoutCourseLink(body: string, link: CourseLink | null): string {
  if (!link) return body;
  return body.split(link.url).join('').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}
