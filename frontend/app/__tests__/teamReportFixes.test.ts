// 팀원 실기 지적 넷 — S15P21E201-1456.
//
// (다섯 가운데 「하루 여행 시간 칸」은 S15P21E201-1452 가 먼저 고쳤다.)
//
// 🔴 2026-09-21, APK versionCode 29. 넷 다 «화면은 그대로 그려지고» 값이나
//    자리만 틀리는 종류라, 되돌려도 타입 검사·스냅샷이 전부 초록이다. 그래서 각각의
//    원인이 실제로 사라졌는지를 잰다.
import { SPEND_QUESTIONS } from '@/onboarding/spendProfile';
import { PREFERENCE_TOTAL } from '@/preferences/accountPreferences';
import { TASTE_KEYS } from '@/preferences/tasteProfile';

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/homeHeaderBell.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

// ① 하루 여행 시간 칸은 S15P21E201-1452 가 먼저 고쳤다 — 시험도 그쪽(src/plan/dayWindow.test.ts)에 있다.

// ── ② 동행 초대 두 단추 ─────────────────────────────────────────────────────
describe('동행 초대의 두 단추가 안 붙어 있다', () => {
  it('「참여자 목록·역할 관리」 아래에 여백이 있다', () => {
    const source = read('src/trip/TripInvitePanel.tsx');
    expect(source).toContain('manageButton: { marginTop: spacing[3], marginBottom: spacing[3] }');
  });
});

// ── ③ 「첫 기록 남기기」가 가운데 ───────────────────────────────────────────
//
// 🔴 Button 안쪽 Pressable 은 width:'100%' 다. 껍데기 폭이 «자동»이면 그 백분율이 안 풀려
//    글자 폭으로 줄고 왼쪽에 붙는다. 그래서 minWidth 만으로는 가운데로 안 간다 —
//    실기에서 껍데기 251..829(가운데 540), 단추 251..508(가운데 379)이었다.
describe('빈 기록 화면의 단추가 가운데다', () => {
  it.each([
    ['app/(tabs)/me.tsx', 'recordsEmptyCta'],
    ['src/me/panels/MyPostsBody.tsx', 'emptyCta'],
  ])('%s 의 %s 는 폭이 확정돼 있다', (file, key) => {
    const source = read(file);
    const line = source.split('\n').find((row: string) => row.includes(`${key}: {`)) ?? '';
    expect(line).toContain("width: '100%'");
    expect(line).toContain('maxWidth: 320');
    // minWidth 만 두면 껍데기 폭이 자동으로 남아 같은 결함이 돌아온다.
    expect(line).not.toContain('minWidth: 220');
  });
});

// ── ④ 프로필 사진 ───────────────────────────────────────────────────────────
describe('두 화면이 같은 규칙으로 프로필 사진을 읽는다', () => {
  it.each(['app/(tabs)/me.tsx', 'src/me/panels/ProfileBody.tsx'])('%s 가 loadProfileAvatar 를 쓴다', (file) => {
    const source = read(file);
    expect(source).toContain('loadProfileAvatar(');
    // 열쇠를 직접 조립하면 규칙이 또 두 벌이 된다.
    expect(source).not.toContain('`gabolle:profile-avatar:${');
  });
});

// ── ⑤ 취향은 일곱이다 ───────────────────────────────────────────────────────
describe('취향 문항 수를 세어서 정한다', () => {
  it('세 질문 + 취향 넷 = 일곱', () => {
    expect(SPEND_QUESTIONS.length).toBe(3);
    expect(TASTE_KEYS.length).toBe(4);
    expect(PREFERENCE_TOTAL).toBe(7);
  });

  it('문항이 줄면 총계도 같이 준다 — 숫자를 박아 두어 8 로 남았던 것이 원인이다', () => {
    const source = read('src/preferences/accountPreferences.ts');
    expect(source).toContain('PREFERENCE_TOTAL = SPEND_QUESTIONS.length + TASTE_KEYS.length');
    expect(source).not.toContain('PREFERENCE_TOTAL = 8');
  });

  // 🔴 새 숫자를 적는 대신 «숫자를 아예 안 적는다» — 적으면 문항이 바뀔 때마다 또 낡는다.
  it('화면이 취향 개수를 글로 안 적는다 — 넷만 그리면서 「다섯」이라 적혀 있었다', () => {
    const source = read('src/me/panels/PreferencesBody.tsx');
    expect(source).toContain("tx('취향', 'Travel tastes')");
    expect(source).not.toContain("tx('취향 다섯'");
  });

  it('지우기 안내문도 개수를 안 적는다 — 「여덟 답」이 남아 있었다', () => {
    const source = read('src/me/panels/PreferencesBody.tsx');
    expect(source).toContain('기억해 둔 답을 모두 지워요');
    expect(source).not.toContain('여덟 답을 모두 지워요');
  });
});
