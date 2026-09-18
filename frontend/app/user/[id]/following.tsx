// 팔로잉 목록 —.
import { useLocalSearchParams } from 'expo-router';

import { useI18n } from '@/i18n';
import { RelationListScreen } from '@/social/RelationListScreen';
import { loadFollowing } from '@/social/stories';

export default function Following() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { tx } = useI18n();
  return (
    <RelationListScreen
      title={tx('팔로잉', 'Following')}
      emptyMessage={tx('아직 팔로우한 사람이 없어요.', 'Not following anyone yet.')}
      loader={(accessToken, cursor) => loadFollowing(id, accessToken, cursor)}
    />
  );
}
