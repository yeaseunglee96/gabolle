import { LegalDocumentScreen } from '@/legal/LegalDocumentScreen';
import { DATA_SOURCES_SECTIONS } from '@/legal/legalContent';

export default function DataSources() {
  return <LegalDocumentScreen title={['공공데이터 출처', 'Public data sources']} lead={['가볼래가 활용하는 공공데이터와 그 이용 조건입니다.', 'Public datasets GABOLLE uses, and the terms under which we use them.']} sections={DATA_SOURCES_SECTIONS} />;
}
