// 팔로워·팔로잉 본문 — 마이페이지 패널이 쓴다.
//
// 화면(`app/user/[id]/followers.tsx`)은 남의 계정도 열 수 있어 id 를 주소에서 받는다.
// 패널은 **언제나 내 것**이라 로그인한 사람의 id 를 쓴다 — 그래서 두 자리가 나뉜다.
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { RelationList } from '@/social/RelationListScreen';
import { loadFollowers, loadFollowing } from '@/social/stories';

export function RelationBody({ kind }: { kind: 'followers' | 'following' }) {
  const { user, accessToken } = useAuth();
  const { tx } = useI18n();
  const load = kind === 'followers' ? loadFollowers : loadFollowing;
  return (
    <RelationList
      emptyMessage={kind === 'followers'
        ? tx('아직 팔로워가 없어요.', 'No followers yet.')
        : tx('아직 팔로우한 사람이 없어요.', "You're not following anyone yet.")}
      // 로그인 전에는 부를 곳이 없다. 빈 목록으로 두고 화면이 「없어요」를 말한다 —
      // 차단 목록이 이미 같은 방식이다.
      loader={(token, cursor) => (user
        ? load(user.userId, token ?? accessToken, cursor)
        : Promise.resolve({ state: 'success' as const, items: [], nextCursor: null }))}
    />
  );
}
