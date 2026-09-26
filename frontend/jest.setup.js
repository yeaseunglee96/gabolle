// AsyncStorage는 네이티브 모듈이라 Jest 환경(네이티브 브릿지가 없다)에서 그냥 import만
// 해도 죽는다. 이 저장소가 쓰는 값 하나(PlanProvider.EMPTY_PLAN)를 가져오려 해도 같이
// 딸려 들어오므로, 패키지가 공식으로 내놓는 인메모리 목으로 바꿔 끼운다.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// Reanimated 도 Jest 에서는 불러오기만 해도 죽는다 — 네이티브 worklets 가 없다. 라이브러리가 주는 가짜
// (react-native-reanimated/mock)도 worklets 를 불러 똑같이 죽는다(2026-09-23 실측, signUpConsentBlockers.test.tsx).
// 여행 화면(폰)이 탭바 = 창 움직임에 쓰면서(S15P21E201-1756) 그 화면을 그리는 시험이 전부 죽어, 앱이 쓰는 것만 흉내 낸다.
// 움직임은 곧바로 끝값이 된다 — 시험이 보는 것은 무엇이 그려지는지이지 몇 초 걸리는지가 아니다.
// 파일 안에서 따로 jest.mock 한 시험(회원가입)은 그쪽이 이긴다.
jest.mock('react-native-reanimated', () => {
  const { View, Text, Image, ScrollView } = require('react-native');
  const chain = { duration: () => chain, delay: () => chain, reduceMotion: () => chain };
  const interpolate = (value, input, output) => {
    const [a, b] = input;
    const [c, d] = output;
    const t = b === a ? 0 : Math.min(1, Math.max(0, (value - a) / (b - a)));
    return c + (d - c) * t;
  };
  const easing = () => (t) => t;
  return {
    __esModule: true,
    default: { View, Text, Image, ScrollView, createAnimatedComponent: (component) => component },
    useSharedValue: (value) => ({ value }),
    useAnimatedStyle: (fn) => fn(),
    useReducedMotion: () => false,
    withTiming: (value) => value,
    withRepeat: (value) => value,
    withSequence: (...values) => values[values.length - 1],
    cancelAnimation: () => {},
    interpolate,
    interpolateColor: (value, input, output) => (value <= input[0] ? output[0] : output[output.length - 1]),
    Extrapolation: { CLAMP: 'clamp', EXTEND: 'extend', IDENTITY: 'identity' },
    Easing: { bezier: easing, linear: (t) => t, ease: (t) => t, inOut: () => (t) => t, out: () => (t) => t, in: () => (t) => t },
    FadeInRight: chain,
    FadeOutLeft: chain,
    ReduceMotion: { System: 'system', Always: 'always', Never: 'never' },
  };
});
