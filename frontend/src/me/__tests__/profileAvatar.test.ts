// 프로필 사진을 어디서 읽는가 — S15P21E201-1456 ④.
//
// 🔴 2026-09-21 팀원 실기 지적(APK versionCode 29): 사진을 바꿔도 마이페이지는 그대로인데,
//    프로필 편집을 열면 바뀌어 있었다. 편집 화면은 계정 사진(user.avatarUrl)을 먼저 보고
//    마이페이지는 «기기 저장소만» 읽고 있었다. 규칙이 두 벌이라 어긋난 것이므로,
//    한 벌로 모으고 그 한 벌을 여기서 잰다.
import AsyncStorage from '@react-native-async-storage/async-storage';

import { deviceAvatarKey, loadProfileAvatar } from '@/me/profileAvatar';

jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: { getItem: jest.fn(), setItem: jest.fn(), removeItem: jest.fn() },
}));

const storage = AsyncStorage as unknown as { getItem: jest.Mock };

beforeEach(() => { storage.getItem.mockReset(); });

describe('loadProfileAvatar', () => {
  it('계정에 붙은 사진이 먼저다', async () => {
    storage.getItem.mockResolvedValue('file:///old-on-this-phone.jpg');

    await expect(loadProfileAvatar('u1', 'https://cdn.test/new.jpg')).resolves.toBe('https://cdn.test/new.jpg');
    // 계정 사진이 있으면 기기 저장소는 «묻지도 않는다».
    expect(storage.getItem).not.toHaveBeenCalled();
  });

  it('계정에 없으면 이 기기에 남은 옛 사진을 쓴다 — 갑자기 사라지면 지워진 줄 안다', async () => {
    storage.getItem.mockResolvedValue('file:///old-on-this-phone.jpg');

    await expect(loadProfileAvatar('u1', null)).resolves.toBe('file:///old-on-this-phone.jpg');
    expect(storage.getItem).toHaveBeenCalledWith(deviceAvatarKey('u1'));
  });

  it('둘 다 없으면 null — 첫 글자 동그라미가 뜬다', async () => {
    storage.getItem.mockResolvedValue(null);

    await expect(loadProfileAvatar('u1', undefined)).resolves.toBeNull();
  });

  it('로그인하지 않았으면 기기 저장소를 안 뒤진다', async () => {
    await expect(loadProfileAvatar(null, null)).resolves.toBeNull();
    expect(storage.getItem).not.toHaveBeenCalled();
  });

  it('기기 저장소를 못 읽어도 화면은 뜬다', async () => {
    storage.getItem.mockRejectedValue(new Error('storage unavailable'));

    await expect(loadProfileAvatar('u1', null)).resolves.toBeNull();
  });

  it('사람마다 자리가 다르다 — 계정을 바꾸면 남의 사진이 보이면 안 된다', () => {
    expect(deviceAvatarKey('u1')).not.toBe(deviceAvatarKey('u2'));
  });
});
