import { EmptyTabScreen } from '@/components/EmptyTabScreen';
import { useI18n } from '@/i18n';

export default function Saved() {
  const { tx } = useI18n();
  return <EmptyTabScreen active="saved" eyebrow="SAVED" title={tx('저장한 장소', 'Saved places')} description={tx('마음에 드는 장소와 추천 코스를 저장하면 이곳에서 다시 볼 수 있어요.', 'Save places and recommended routes you like to find them here again.')} actionLabel={tx('부산 둘러보기', 'Explore Busan')} actionPath="/home" />;
}
