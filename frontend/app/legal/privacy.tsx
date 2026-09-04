import { LegalDocumentScreen } from '@/legal/LegalDocumentScreen';
import { PRIVACY_SECTIONS } from '@/legal/legalContent';

export default function Privacy() {
  return <LegalDocumentScreen title={['개인정보 처리방침', 'Privacy Policy']} lead={['실제로 처리하는 정보와 그 목적을 투명하게 안내합니다.', 'How we process your information and why.']} sections={PRIVACY_SECTIONS} />;
}
