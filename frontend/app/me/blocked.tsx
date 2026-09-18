// 마이페이지 › 차단된 계정.
//
// 본문은 BlockedAccountsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { BlockedAccountsBody } from '@/me/panels/BlockedAccountsBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function BlockedAccounts() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('blocked', tx);
  return <MyPageShell tab="blocked" title={title} description={description}><BlockedAccountsBody /></MyPageShell>;
}
