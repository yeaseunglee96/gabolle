// 로그인 직후 비회원 여행을 넘기는 순서 — S15P21E201-317.
// 넘긴 뒤에만 출입증을 버린다. 실패했는데 버리면 그 출입증에 묶인 여행을 다시 찾을 길이 없다.
const mockClaim = jest.fn();
const mockReset = jest.fn(async () => undefined);
jest.mock('../authApi', () => ({ claimAnonymousTrips: (...args: unknown[]) => mockClaim(...args) }));
jest.mock('@/api/client', () => ({ resetAnonymousSession: () => mockReset() }));

import { handOverGuestTrips } from '../guestHandover';

beforeEach(() => { mockClaim.mockReset(); mockReset.mockClear(); });

describe('handOverGuestTrips', () => {
  it('넘긴 여행 수를 돌려주고 출입증을 버린다', async () => {
    mockClaim.mockResolvedValue({ claimedTrips: 2 });

    await expect(handOverGuestTrips('member-token')).resolves.toBe(2);
    expect(mockClaim).toHaveBeenCalledWith('member-token');
    expect(mockReset).toHaveBeenCalledTimes(1);
  });

  it('넘기지 못하면 출입증을 남기고 로그인은 막지 않는다', async () => {
    mockClaim.mockRejectedValue(new Error('network'));

    await expect(handOverGuestTrips('member-token')).resolves.toBe(0);
    expect(mockReset).not.toHaveBeenCalled();
  });
});
