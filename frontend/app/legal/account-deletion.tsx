import { LegalDocumentScreen } from '@/legal/LegalDocumentScreen';
import { ACCOUNT_DELETION_SECTIONS } from '@/legal/legalContent';

// 앱을 설치하지 않은 사람도, 로그인하지 않고도 열린다 — Google Play 데이터 보안 양식이 요구하는 «웹에서 계정 삭제를 요청하는 방법» 주소.
export default function AccountDeletion() {
  return <LegalDocumentScreen title={['계정 삭제 안내', 'Account deletion']} lead={['계정을 삭제하는 방법과, 무엇이 지워지고 무엇이 남는지 안내합니다.', 'How to delete your account, and what is deleted and what remains.']} sections={ACCOUNT_DELETION_SECTIONS} draftNotice={false} />;
}
