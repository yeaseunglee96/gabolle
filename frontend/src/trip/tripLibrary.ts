import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = 'gabolle:my-trips:v1';

export async function clearSavedTrips(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}
