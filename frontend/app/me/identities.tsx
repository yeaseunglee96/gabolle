// 마이페이지 › 연결된 소셜 계정.
//
// 본문은 IdentitiesBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { IdentitiesBody } from '@/me/panels/IdentitiesBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function MyPageIdentities() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('identities', tx);
  return <MyPageShell tab="identities" title={title} description={description}><IdentitiesBody /></MyPageShell>;
}
