// 팔로워·팔로잉 줄의 기록 수 —-1329.
//
// 🔴 이 시험이 지키는 것은 **「안 셌다」와 「0 개」가 안 합쳐지는가**이다. 차단 목록은 서버가
//    세지 않고 null 로 보내는데, 그것을 0 으로 그리면 화면이 「기록 0개」라고 **단언**하게
//    된다. 그 줄은 사실이 아니고, 화면 어디에도 오류로 안 보인다.
import { adaptRelationItem } from '@/social/stories';

const base = { userId: 'u1', displayName: '여행자', following: false };

describe('목록 줄의 기록 수', () => {
  it('서버가 센 수를 그대로 들고 온다', () => {
    expect(adaptRelationItem({ ...base, storyCount: 7 }).storyCount).toBe(7);
  });

  it('🔴 0 개는 0 이다 — 「없다」도 그리는 값이다', () => {
    expect(adaptRelationItem({ ...base, storyCount: 0 }).storyCount).toBe(0);
  });

  it.each([undefined, null])('🔴 안 센 것(%s)은 null 로 남는다 — 0 으로 떨어뜨리지 않는다', (value) => {
    expect(adaptRelationItem({ ...base, storyCount: value }).storyCount).toBeNull();
  });

  it.each([-3, Number.NaN, Infinity])('모르는 값(%s)도 「안 셌다」로 본다 — 지어내지 않는다', (value) => {
    expect(adaptRelationItem({ ...base, storyCount: value }).storyCount).toBeNull();
  });

  it('나머지 칸은 손대지 않는다', () => {
    const item = adaptRelationItem({ ...base, avatarUrl: 'https://x/y.png', following: true, storyCount: 2 });

    expect(item).toEqual({ ...base, avatarUrl: 'https://x/y.png', following: true, storyCount: 2 });
  });
});
