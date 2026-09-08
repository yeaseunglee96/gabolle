import AsyncStorage from '@react-native-async-storage/async-storage';

// "내 여행" 목록은 이제 서버가 준다(GET /api/v1/trips, src/trip/trips.ts, S15P21E201-738) —
// 예전에는 일정을 열 때마다 이 기기에 목록을 따로 적어 뒀었다. 이 함수는 그 예전 방식으로
// 기기에 남아 있을 수 있는 값을 지우는 용도로만 남긴다(로그아웃·계정 삭제 때, S15P21E201-740).
const STORAGE_KEY = 'gabolle:my-trips:v1';

export async function clearSavedTrips(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}
