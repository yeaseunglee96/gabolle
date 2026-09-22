// 프로필 사진을 어디서 읽는가 — 화면 둘이 같은 규칙을 쓰게 한 곳에 둔다.
//
// 🔴 2026-09-21 팀원 실기 지적(APK versionCode 29): 「프로필에 커버 사진은 바꿔지는데,
//    프로필은 사진 넣어도 화면 나갔을 때 그대로임. 근데 프로필 편집 누르면 분명 프로필
//    사진 바꿔져 있는데...」
//
//    편집 화면(ProfileBody)은 계정에 붙은 `user.avatarUrl` 을 먼저 보고, 없을 때만 이 기기에
//    남은 옛 사진을 봤다. 그런데 마이페이지(app/(tabs)/me.tsx)는 «기기 저장소만» 읽었다.
//    그래서 사진을 올리면 계정에는 붙는데 마이페이지는 끝까지 첫 글자 동그라미였다.
//    한쪽만 고치면 다음에 또 어긋나므로, 규칙을 한 벌만 두고 둘이 이것을 부른다.
import AsyncStorage from '@react-native-async-storage/async-storage';

/** 기기에만 사진을 두던 시절의 자리. 계정 사진이 생기기 전에 고른 것이 여기 남아 있다. */
export function deviceAvatarKey(userId: string): string {
  return `gabolle:profile-avatar:${userId}`;
}

/**
 * 이 사람의 프로필 사진 주소.
 *
 * 계정에 붙은 사진이 «먼저»다. 없을 때만 이 기기에 남은 옛 사진을 쓴다 — 기기에만 있던
 * 시절에 고른 사진이 갑자기 사라지면 사용자는 지워진 줄 안다.
 */
export async function loadProfileAvatar(userId: string | null, accountAvatarUrl: string | null | undefined): Promise<string | null> {
  if (!userId) return null;
  if (accountAvatarUrl) return accountAvatarUrl;
  try {
    return await AsyncStorage.getItem(deviceAvatarKey(userId));
  } catch {
    // 기기 저장소를 못 읽는 것은 「사진이 없다」와 같게 다룬다 — 첫 글자 동그라미가 뜬다.
    return null;
  }
}
