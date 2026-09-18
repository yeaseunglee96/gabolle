// 서버에서 받아온 데이터를 화면 밖에 보관한다 —.
import { QueryClient } from '@tanstack/react-query';

/** 기본값을 일부러 보수적으로 잡았다. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      // 창을 다시 포커스할 때마다 부르지 않는다 — 웹에서 탭을 오갈 때마다
      // 요청이 나가면 이 티켓이 고치려는 증상이 그대로 돌아온다.
      refetchOnWindowFocus: false,
    },
  },
});
