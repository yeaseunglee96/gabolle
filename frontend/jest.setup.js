// AsyncStorage는 네이티브 모듈이라 Jest 환경(네이티브 브릿지가 없다)에서 그냥 import만
// 해도 죽는다. 이 저장소가 쓰는 값 하나(PlanProvider.EMPTY_PLAN)를 가져오려 해도 같이
// 딸려 들어오므로, 패키지가 공식으로 내놓는 인메모리 목으로 바꿔 끼운다.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);
