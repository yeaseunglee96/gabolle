// 신규 사용자 안내의 «켜짐 표시» — S15P21E201-1361.
//
// 홈 코치마크 · 동백이 첫 여행 체크리스트 · 일정 첫 힌트는 **온보딩(앱 소개)을 거친
// 사람에게만** 뜬다. 이미 로그인된 채로 앱을 켜는 기존 회원은 앱 소개를 다시 안 보므로
// 여기 표시가 켜질 일이 없다 — 「처음 온 사람인가」를 서버에 묻지 않고, 그 사람이
// 지나온 길(앱 소개의 마지막 단추)로 판단한다.
//
// 🔴 전부 기기에 남는다(AsyncStorage). 기기를 바꾸면 한 번 더 보게 되는데, 그것은
//    받아들인다 — 반대(서버에 저장)는 로그인 전에는 알 수 없고, 코치마크는 로그인 전에도 뜬다.
import AsyncStorage from '@react-native-async-storage/async-storage';

const COACH_PENDING = 'gabolle:home-coach-pending';
const COACH_SEEN = 'gabolle:home-coach-seen';
const CHECKLIST = 'gabolle:first-trip-checklist';
const ITINERARY_HINT_SEEN = 'gabolle:itinerary-hint-seen';

export type ChecklistStep = 'trip' | 'invite' | 'story';
export type ChecklistState = { active: boolean; dismissed: boolean; done: Record<ChecklistStep, boolean> };

const EMPTY: ChecklistState = { active: false, dismissed: false, done: { trip: false, invite: false, story: false } };

const read = async (key: string) => { try { return await AsyncStorage.getItem(key); } catch { return null; } };
const write = async (key: string, value: string) => { try { await AsyncStorage.setItem(key, value); } catch { /* 기기 저장이 안 되면 안내가 한 번 더 뜰 뿐이다 */ } };

/** 앱 소개를 끝낸 순간 — 홈 코치와 체크리스트를 켠다. 기존 회원은 여기를 지나지 않는다. */
export async function markOnboardingFinished(): Promise<void> {
  await write(COACH_PENDING, 'true');
  const current = await loadChecklist();
  if (!current.active) await write(CHECKLIST, JSON.stringify({ ...EMPTY, active: true }));
}

/** 도움말의 「처음 안내 다시 보기」 — 코치만 다시 켠다. */
export async function requestHomeCoachAgain(): Promise<void> {
  await write(COACH_PENDING, 'true');
}

/** 홈이 코치를 띄워야 하나 — 켜져 있고 아직 안 봤을 때만. 띄우면서 표시를 끈다. */
export async function takeHomeCoach(): Promise<boolean> {
  const pending = (await read(COACH_PENDING)) === 'true';
  if (!pending) return false;
  await write(COACH_PENDING, 'false');
  await write(COACH_SEEN, 'true');
  return true;
}

export async function loadChecklist(): Promise<ChecklistState> {
  const raw = await read(CHECKLIST);
  if (!raw) return EMPTY;
  try {
    const parsed = JSON.parse(raw) as Partial<ChecklistState>;
    return { active: Boolean(parsed.active), dismissed: Boolean(parsed.dismissed), done: { ...EMPTY.done, ...(parsed.done ?? {}) } };
  } catch { return EMPTY; }
}

/** 한 칸을 했다고 적는다 — 체크리스트가 안 켜져 있어도 적어 둔다(나중에 켜지면 그대로 ✓). */
export async function markChecklistStep(step: ChecklistStep): Promise<void> {
  const current = await loadChecklist();
  if (current.done[step]) return;
  await write(CHECKLIST, JSON.stringify({ ...current, done: { ...current.done, [step]: true } }));
}

export async function dismissChecklist(): Promise<void> {
  const current = await loadChecklist();
  await write(CHECKLIST, JSON.stringify({ ...current, dismissed: true }));
}

/**
 * 로그아웃·탈퇴 때 이 기기의 안내 표시를 비운다 — S15P21E201-1804.
 *
 * 🔴 체크리스트는 기기에 남고 «계정을 안 가린다». 그래서 한 번이라도 여행을 만든 기기에서는
 *    로그아웃하고 새 계정으로 가입해도 「첫 여행 만들기」가 이미 ✓ 로 보였다.
 *
 *    AuthProvider 의 signOut 은 바로 이런 것을 정리하라고 clearSavedTrips()·preferences.reset()
 *    을 부르고 있었고, 그 옆 주석도 「다음 사람이 앞사람의 것을 보면 안 된다」고 적어 뒀다.
 *    체크리스트만 그 목록에서 빠져 있었다.
 *
 *    코치마크 표시도 같이 비운다 — 새 계정은 앱 소개를 다시 지나며 markOnboardingFinished()
 *    가 다시 켜 주므로, 여기서 비워도 「봐야 할 사람이 못 보는」 일은 없다.
 */
export async function clearFirstRunMarks(): Promise<void> {
  for (const key of [CHECKLIST, COACH_PENDING, COACH_SEEN, ITINERARY_HINT_SEEN]) {
    try { await AsyncStorage.removeItem(key); } catch { /* 못 지워도 안내가 한 번 더 뜰 뿐이다 */ }
  }
}

/** 일정 첫 힌트 — 처음 한 번만. 읽으면서 끈다. */
export async function takeItineraryHint(): Promise<boolean> {
  if ((await read(ITINERARY_HINT_SEEN)) === 'true') return false;
  // 온보딩을 거친 사람에게만 — 코치를 본 적이 없으면 기존 회원이다.
  if ((await read(COACH_SEEN)) !== 'true') return false;
  await write(ITINERARY_HINT_SEEN, 'true');
  return true;
}
