// MyPosts 화면.
//
// 본문은 MyPostsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { MyPostsBody } from '@/me/panels/MyPostsBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function MyPostsScreen() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('posts', tx);
  return <MyPageShell tab="posts" title={title} description={description}><MyPostsBody /></MyPageShell>;
}
