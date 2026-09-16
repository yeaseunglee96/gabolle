// S15P21E201-1140 — 앱 지도의 안드로이드 키 판정.
//
// 🔴 이 판정이 틀리면 둘 다 나쁘다. 키가 있는데 없다고 하면 멀쩡한 지도 대신 안내문이 뜨고,
//    키가 없는데 있다고 하면 **회색 네모**가 뜬다. 회색 네모는 "앱이 고장났다" 로 읽힌다.
//
// 🔴 특히 **iOS** 를 잰다. 애플 지도는 키가 필요 없는데 플랫폼을 안 가르면 아이폰에서도
//    「키가 없어요」가 떠서, 잘 되는 지도를 안 보여주게 된다.
import { readAndroidMapKey, shouldShowMissingKeyNotice } from '@/map/androidMapKey';

const withKey = (apiKey: unknown) => ({ config: { googleMaps: { apiKey } } });

describe('설정에서 키를 읽는다', () => {
  it('있으면 그대로 준다', () => {
    expect(readAndroidMapKey(withKey('AIza-test'))).toBe('AIza-test');
  });

  it('🔴 공백만 있으면 없는 것이다 — 그 값으로는 지도가 안 그려진다', () => {
    expect(readAndroidMapKey(withKey('   '))).toBeNull();
    expect(readAndroidMapKey(withKey(''))).toBeNull();
  });

  it('앞뒤 공백은 떼고 준다', () => {
    expect(readAndroidMapKey(withKey(' AIza-test '))).toBe('AIza-test');
  });

  it.each([
    ['설정이 없다', undefined],
    ['android 는 있는데 config 가 없다', {}],
    ['config 는 있는데 googleMaps 가 없다', { config: {} }],
    ['googleMaps 는 있는데 apiKey 가 없다', { config: { googleMaps: {} } }],
    ['apiKey 가 문자열이 아니다', { config: { googleMaps: { apiKey: 12345 } } }],
    ['null 이다', null],
  ])('%s → null', (_label, android) => {
    expect(readAndroidMapKey(android)).toBeNull();
  });
});

describe('안내문을 띄울 것인가', () => {
  it('🔴 iOS 는 키가 없어도 안 띄운다 — 애플 지도라 키가 필요 없다', () => {
    expect(shouldShowMissingKeyNotice('ios', undefined)).toBe(false);
    expect(shouldShowMissingKeyNotice('ios', {})).toBe(false);
  });

  it('🔴 웹도 안 띄운다 — 웹은 카카오 지도를 쓰고 이 판정과 무관하다', () => {
    expect(shouldShowMissingKeyNotice('web', undefined)).toBe(false);
  });

  it('안드로이드에서 키가 없으면 띄운다', () => {
    expect(shouldShowMissingKeyNotice('android', undefined)).toBe(true);
    expect(shouldShowMissingKeyNotice('android', withKey('  '))).toBe(true);
  });

  it('안드로이드에서 키가 있으면 안 띄운다 — 지도를 그려야 한다', () => {
    expect(shouldShowMissingKeyNotice('android', withKey('AIza-test'))).toBe(false);
  });
});
