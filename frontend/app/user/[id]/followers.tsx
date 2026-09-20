// 팔로워 목록 — S15P21E201-1180. 화면 본문은 RelationListScreen 하나를 세 목록이 같이 쓴다.
import { useLocalSearchParams } from 'expo-router';

import { useI18n } from '@/i18n';
import { RelationListScreen } from '@/social/RelationListScreen';
import { loadFollowers } from '@/social/stories';

export default function Followers() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { tx } = useI18n();
  return (
    <RelationListScreen
      title={tx('팔로워', 'Followers')}
      emptyMessage={tx('아직 팔로워가 없어요.', 'No followers yet.')}
      loader={(accessToken, cursor) => loadFollowers(id, accessToken, cursor)}
    />
  );
}
