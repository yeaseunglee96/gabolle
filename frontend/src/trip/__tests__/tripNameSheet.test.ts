import AsyncStorage from '@react-native-async-storage/async-storage';

import { markTripNameAsked, shouldAskTripName, wasTripNameAsked } from '../tripNaming';

// 이름을 언제 물어보나 (S15P21E201-1036). 시안 `design_handoff_trip_name_flow`.
//
// 🔴 이 시험이 지키는 것은 하나다 — **같은 질문을 두 번 하지 않는다.**
//
// 이름 붙이기는 건너뛸 수 있는 일이다. 그런데 건너뛴 사람에게 일정을 열 때마다 다시
// 물으면, 건너뛰기가 「나중에 또 물어볼게요」가 된다. 그건 건너뛸 수 있다고 말해 놓고
// 안 놓아주는 것이다.

beforeEach(async () => {
  await AsyncStorage.clear();
});

describe('이름을 물어볼 자리인가', () => {
  it('이름이 없고 아직 안 물어봤으면 묻는다', () => {
    expect(shouldAskTripName({ title: null, alreadyAsked: false })).toBe(true);
  });

  // 🔴 이미 붙인 사람에게 「이름을 붙일까요?」 라고 물으면, 붙인 이름이 없는 것처럼 들린다.
  it('이름이 이미 있으면 안 묻는다', () => {
    expect(shouldAskTripName({ title: '해운대 이틀', alreadyAsked: false })).toBe(false);
  });

  it('공백만 있는 이름은 이름이 없는 것으로 보고 묻는다', () => {
    expect(shouldAskTripName({ title: '   ', alreadyAsked: false })).toBe(true);
  });

  it('칸 자체가 없어도 묻는다', () => {
    expect(shouldAskTripName({ title: undefined, alreadyAsked: false })).toBe(true);
  });

  // 🔴 여기가 핵심이다. 건너뛴 사람을 놓아준다.
  it('한 번 물어봤으면 이름이 없어도 다시 안 묻는다', () => {
    expect(shouldAskTripName({ title: null, alreadyAsked: true })).toBe(false);
  });
});

describe('물어봤다는 기록', () => {
  it('남기면 다음에 읽힌다', async () => {
    expect(await wasTripNameAsked('trip-a')).toBe(false);
    await markTripNameAsked('trip-a');
    expect(await wasTripNameAsked('trip-a')).toBe(true);
  });

  it('여행마다 따로 센다', async () => {
    await markTripNameAsked('trip-a');
    expect(await wasTripNameAsked('trip-b')).toBe(false);
  });

  // 🔴 못 읽었으면 「물어봤다」로 친다. 반대로 두면 저장소가 막힌 기기에서 일정을 열 때마다
  // 이름을 물어보게 된다 — 고장이 아니라 괴롭힘이 된다.
  it('저장소를 못 읽으면 「물어봤다」로 친다', async () => {
    const original = AsyncStorage.getItem;
    (AsyncStorage as unknown as { getItem: unknown }).getItem = jest.fn(async () => { throw new Error('저장소 없음'); });
    try {
      expect(await wasTripNameAsked('trip-c')).toBe(true);
    } finally {
      (AsyncStorage as unknown as { getItem: unknown }).getItem = original;
    }
  });

  // 못 적는 것은 흐름을 막지 않는다 — 다음에 한 번 더 묻게 될 뿐이다.
  it('저장소에 못 적어도 던지지 않는다', async () => {
    const original = AsyncStorage.setItem;
    (AsyncStorage as unknown as { setItem: unknown }).setItem = jest.fn(async () => { throw new Error('저장소 없음'); });
    try {
      await expect(markTripNameAsked('trip-d')).resolves.toBeUndefined();
    } finally {
      (AsyncStorage as unknown as { setItem: unknown }).setItem = original;
    }
  });
});
