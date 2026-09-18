// 마이페이지 › 프로필.
//
// 본문은 ProfileBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { ProfileBody } from '@/me/panels/ProfileBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function ProfileScreen() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('profile', tx);
  return <MyPageShell tab="profile" title={title} description={description}><ProfileBody /></MyPageShell>;
}
