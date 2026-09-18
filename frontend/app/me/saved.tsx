// SavedRecords 화면.
//
// 본문은 SavedRecordsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다.
import { MyPageShell } from '@/me/MyPageShell';
import { SavedRecordsBody } from '@/me/panels/SavedRecordsBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function SavedRecordsScreen() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('saved', tx);
  return <MyPageShell tab="saved" title={title} description={description}><SavedRecordsBody /></MyPageShell>;
}
