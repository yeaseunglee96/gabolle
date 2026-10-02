// 비회원으로 만든 여행을 로그인한 계정으로 넘긴다 — S15P21E201-317.
//
// 로그인 방법(이메일·카카오·네이버·애플·계정 연결)마다 서버에 승계를 끼우지 않고, 로그인을 마친 직후
// 여기 한 곳에서 부른다. 서버는 넘긴 출입증을 지우므로 성공하면 이쪽도 출입증을 버리고 새로 받는다.
//
// 🔴 실패하면 출입증을 버리지 않는다. 버리면 그 출입증에 묶인 여행을 다시 찾을 길이 없다. 남겨 두면
//    다음 로그인에서 다시 넘긴다.
import { resetAnonymousSession } from '@/api/client';
import { claimAnonymousTrips } from './authApi';

export async function handOverGuestTrips(accessToken: string): Promise<number> {
  try {
    const { claimedTrips } = await claimAnonymousTrips(accessToken);
    await resetAnonymousSession();
    return claimedTrips;
  } catch {
    return 0;
  }
}
