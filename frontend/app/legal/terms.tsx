import { LegalDocumentScreen } from '@/legal/LegalDocumentScreen';
import { TERMS_SECTIONS } from '@/legal/legalContent';

export default function Terms() {
  return <LegalDocumentScreen title={['이용약관', 'Terms of Service']} lead={['가볼래 서비스를 안전하고 편리하게 이용하기 위한 기본 원칙입니다.', 'The basic rules for using GABOLLE safely and comfortably.']} sections={TERMS_SECTIONS} />;
}
