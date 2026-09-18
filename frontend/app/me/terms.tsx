// 마이페이지 › 약관·고지.
//
// 본문은 TermsBody 가 그린다 — 마이페이지에서 패널로 열 때도 같은 것을 쓴다(myPanels).
// 두 벌이 되면 한쪽만 고쳐지고, 그 차이는 두 길로 들어가 나란히 봐야만 보인다.
import { MyPageShell } from '@/me/MyPageShell';
import { TermsBody } from '@/me/panels/TermsBody';
import { panelTitle } from '@/me/myPanels';
import { useI18n } from '@/i18n';

export default function MyPageTerms() {
  const { tx } = useI18n();
  const { title, description } = panelTitle('terms', tx);
  return <MyPageShell tab="terms" title={title} description={description}><TermsBody /></MyPageShell>;
}
