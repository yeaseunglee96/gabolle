import { useLocalSearchParams, useRouter } from 'expo-router';

import { StoryComposeForm } from '@/social/StoryComposeForm';

// 글쓰기 화면 — 피드·마이페이지·참여자 화면에서 온다. 본문은 StoryComposeForm 한 벌이고,
// 여행 화면은 같은 것을 창 안에 연다(S15P21E201-1760).
export default function ComposeStory() {
  const router = useRouter();
  // 여행 화면(참여자 탭)에서 「기록 남기기」로 왔으면 그 여행에 글이 달린다(S15P21E201-418). 없으면 지금까지처럼 여행 없는 글.
  const { tripId: tripIdParam } = useLocalSearchParams<{ tripId?: string }>();
  const tripId = typeof tripIdParam === 'string' && tripIdParam ? tripIdParam : undefined;
  // 갈 곳이 없으면 피드로 — S15P21E201-1292.
  return <StoryComposeForm tripId={tripId} onClose={() => (router.canGoBack() ? router.back() : router.replace('/feed'))} />;
}
