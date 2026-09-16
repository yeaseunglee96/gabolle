// 추천 목록의 저장·제외가 화면을 다시 열어도 남는가 — S15P21E201-975.
//
// 그전에는 화면 상태만 바꿔서, 버튼을 눌러 "저장됨" 이 돼도 다시 들어오면 "저장" 이었다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { loadRecommendationActions, saveRecommendationAction } from '@/plan/recommendationActions';

describe('추천 저장·제외 기억', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it('저장한 판단이 다시 읽힌다', async () => {
    await saveRecommendationAction('trip-1', 'place-a', 'saved');
    await saveRecommendationAction('trip-1', 'place-b', 'excluded');

    expect(await loadRecommendationActions('trip-1')).toEqual({
      'place-a': 'saved',
      'place-b': 'excluded',
    });
  });

  // 저장·제외는 그 여행의 후보에 대한 판단이지 장소 자체에 대한 판단이 아니다.
  it('여행이 다르면 서로 안 보인다', async () => {
    await saveRecommendationAction('trip-1', 'place-a', 'excluded');

    expect(await loadRecommendationActions('trip-2')).toEqual({});
  });

  it('되돌리면 그 장소의 판단만 지워진다', async () => {
    await saveRecommendationAction('trip-1', 'place-a', 'saved');
    await saveRecommendationAction('trip-1', 'place-b', 'saved');

    await saveRecommendationAction('trip-1', 'place-a', null);

    expect(await loadRecommendationActions('trip-1')).toEqual({ 'place-b': 'saved' });
  });

  // 이 값 때문에 추천 화면이 안 열리는 일이 있으면 안 된다.
  it('저장된 값이 깨져 있으면 빈 것으로 본다', async () => {
    await AsyncStorage.setItem('@gabolle/recommendation-actions:trip-1', '{ not json');

    expect(await loadRecommendationActions('trip-1')).toEqual({});
  });
});
