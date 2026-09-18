// 마이페이지 › 여행취향.
//
// 본문은 PreferencesBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { PreferencesBody } from '@/me/panels/PreferencesBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function PreferencesScreen() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('preferences', tx);
  return <MyPageShell tab="preferences" title={title} description={description}><PreferencesBody /></MyPageShell>;
}
