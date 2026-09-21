// 홈 머리의 알림 종이 화면 밖으로 밀려나던 것 — S15P21E201-1402 (1390 후속).
//
// 🔴 2026-09-21 실기(SM-G973N, 1080×2280, versionCode 27, 한국어, 로그인 상태).
//    머리줄은 [로고][언어 알약 · 날씨 칩 · 종] 이다. 로그인하면 날씨 칩이 붙는데 한국어 날씨
//    「구름 조금 21° / 28°」가 길어 줄이 화면보다 넓어졌고, 종의 경계가
//
//        bounds=[1076,154][1080,270]     ← 화면에 걸친 4px
//
//    였다. 손님일 때는 날씨 칩이 없어 멀쩡했다 — 그래서 로그인해야만 보인다.
//
// 🔴 원인. React Native(Yoga)와 react-native-web 은 flexShrink 기본값이 «0» 이다(CSS 의 1 과
//    다르다 — node_modules/react-native/ReactCommon/yoga/yoga/style/Style.h 의
//    DefaultFlexShrink = 0.0f). 그래서 아무것도 줄지 않고 줄이 넘쳐, 맨 끝의 종이 밖으로
//    밀려났다. 종이 «줄어든» 것이 아니다.
//
//    처음 고칠 때(f7b1bac9) 이것을 「기본값이 1 이라 종이 줄었다」로 잘못 읽고 날씨 칩에만
//    flexShrink 를 줬다. Expo 웹에서 재 보니 종은 여전히 x=392~436 — 화면 밖이었다. 부모
//    (headerRight)가 줄지 않으면 안의 칩도 줄 이유가 없다.
//
// 🔴 그래도 폭이 모자랐다(Expo 웹, 360·390·411 실측 — 로고 ~138 · 언어 알약 ~81 · 날씨 ~126 ·
//    종 44px). 그래서 폰 홈 머리에서 언어 알약을 뺐다(S15P21E201-1408 과 합침). 시안 5 Home 도
//    로고·날씨·종 셋이다. 언어는 첫 화면과 마이페이지 설정 「앱 언어」(AppLanguageSetting)에서 바꾼다.
//
// 🔴 이 시험이 파일 내용을 보는 이유. jsdom 에는 flexbox 가 없어 렌더러 시험으로는 폭이 늘 0 이고
//    넘침이 재현되지 않는다. 실제로 이 결함은 타입·시험 검사가 전부 초록인 채로 스토어 빌드까지
//    올라갔다. 눈으로 보는 확인은 Expo 웹(`npx expo start --web`)에서 폭을 바꿔 가며 한다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const HOME = readFileSync(join(__dirname, '..', '(tabs)', 'home.tsx'), 'utf8') as string;

/** `<이름>: { ... }` 스타일 한 줄을 통째로 집어 온다. */
function styleLine(name: string): string {
  const found = HOME.split('\n').find((line) => line.trim().startsWith(`${name}: {`));
  if (!found) throw new Error(`${name} 스타일을 못 찾았습니다 — 이름이 바뀌었으면 이 시험도 같이 고치십시오`);
  return found;
}

describe('홈 머리 — 줄이 넘쳐도 종은 화면 안에 있다', () => {
  it('🔴 머리 오른쪽 묶음(headerRight)이 로고 옆 남은 폭에 맞춰 준다 — 이것이 없으면 안의 무엇도 안 준다', () => {
    const right = styleLine('headerRight');
    expect(right).toContain('flexShrink: 1');
    expect(right).toContain('minWidth: 0');
  });

  it('🔴 줄어들 쪽은 날씨 칩이고, 칩 안에서는 날씨 낱말이 말줄임되고 기온은 남는다', () => {
    const chip = styleLine('weatherChip');
    expect(chip).toContain('flexShrink: 1');
    expect(chip).toContain('minWidth: 0');
    expect(styleLine('weatherWord')).toContain('flexShrink: 1');
    expect(styleLine('weatherTemp')).toContain('flexShrink: 0');
    expect(HOME).toContain('numberOfLines={1} style={styles.weatherWord}');
  });

  it('🔴 종은 줄지 않고 손가락이 닿는 크기(44)를 지킨다', () => {
    const bell = styleLine('bell');
    expect(bell).toContain('flexShrink: 0');
    expect(bell).toContain('width: 44');
    expect(bell).toContain('height: 44');
  });
});

describe('폰 홈 머리에는 언어 알약이 없다', () => {
  it('🔴 언어 알약을 다시 넣지 않는다 — 넣으면 411 폭에서 종이 다시 밀려난다', () => {
    expect(HOME).not.toContain('styles.langPill');
    expect(HOME).not.toContain('WelcomeLanguageSheet');
  });

  it('언어를 바꿀 자리는 설정에 남아 있다 — 홈에서 뺐다고 길이 끊기지 않는다', () => {
    const setting = readFileSync(join(__dirname, '..', '..', 'src', 'me', 'AppLanguageSetting.tsx'), 'utf8') as string;
    expect(setting).toContain('앱 언어');
  });
});
