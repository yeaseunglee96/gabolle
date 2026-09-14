import { Platform } from 'react-native';
import { API_BASE_URL, getApiLanguage } from '@/api/client';

/**
 * `GET /api/v1/jobs/{jobId}/progress` (SSE) 를 읽는다 — S15P21E201-69.
 *
 * 백엔드(S15P21E201-193, `JobProgressBroker.JobProgressSnapshot`)가 실제로 보내는 모양은
 * `{jobId, status, stage, percent, code}` 이고, 사건 이름(`progress`·`completed`·`failed`)은
 * `status` 만으로도 이미 구분되므로 여기서는 `data:` 줄만 읽는다.
 *
 * 🔴 **네이티브(iOS·Android)에서는 열지 않는다.** 브라우저의 `EventSource` 는 커스텀 헤더를
 * 못 실어서 `Authorization: Bearer` 를 붙일 수 없다 — 그래서 `fetch` 로 직접 연결을 열고
 * 본문을 `ReadableStream` 으로 읽는다. 그런데 React Native 의 `fetch` 폴리필은 플랫폼마다
 * 스트리밍 본문을 신뢰할 수 없이 준다(전체를 버퍼링한 뒤에야 돌려주는 경우가 흔하다).
 * 웹(Expo Web 이 그대로 브라우저 `fetch`/`ReadableStream` 을 쓴다)에서만 열고, 네이티브는
 * 처음부터 기존 2초 폴링을 쓴다 — 이것은 지름길이 아니라 플랫폼 자체의 한계다. 나중에
 * 네이티브에서도 실시간이 필요해지면 `react-native-sse` 같은 별도 네이티브 라이브러리가
 * 필요하다.
 */
export type JobStreamStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';

export type RecommendationJobStreamSnapshot = {
  jobId: string;
  status: JobStreamStatus;
  stage: string | null;
  percent: number;
  code: string | null;
};

export type JobStreamHandlers = {
  onSnapshot: (snapshot: RecommendationJobStreamSnapshot) => void;
  /** 서버가 끝 상태를 보내고 스스로 연결을 닫았다 — 폴링으로 갈아탈 필요가 없다. */
  onDone: () => void;
  /** 연결이 실패했다 — 부르는 쪽이 폴링으로 조용히 갈아탄다. */
  onError: () => void;
};

export function supportsJobProgressStream(): boolean {
  return Platform.OS === 'web' && typeof fetch !== 'undefined' && typeof ReadableStream !== 'undefined'
    && typeof TextDecoder !== 'undefined';
}

/** @returns 연결을 닫는 함수. 화면이 사라지거나 작업이 끝나면 반드시 부른다. */
export function openJobProgressStream(
  jobId: string,
  accessToken: string | null,
  handlers: JobStreamHandlers,
): () => void {
  const controller = new AbortController();
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    controller.abort();
  };

  (async () => {
    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}/api/v1/jobs/${encodeURIComponent(jobId)}/progress`, {
        headers: {
          Accept: 'text/event-stream',
          'Accept-Language': getApiLanguage(),
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        signal: controller.signal,
      });
    }
    catch {
      if (!closed) handlers.onError();
      return;
    }
    if (!response.ok || !response.body) {
      if (!closed) handlers.onError();
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (!closed) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.indexOf('\n\n');
        while (boundary >= 0) {
          const snapshot = parseEventBlock(buffer.slice(0, boundary));
          buffer = buffer.slice(boundary + 2);
          if (snapshot) handlers.onSnapshot(snapshot);
          boundary = buffer.indexOf('\n\n');
        }
      }
      if (!closed) handlers.onDone();
    }
    catch {
      if (!closed) handlers.onError();
    }
  })();

  return close;
}

function parseEventBlock(block: string): RecommendationJobStreamSnapshot | null {
  const dataLine = block.split('\n').find((line) => line.startsWith('data:'));
  if (!dataLine) return null;
  try {
    return JSON.parse(dataLine.slice('data:'.length).trim()) as RecommendationJobStreamSnapshot;
  }
  catch {
    // 🔴 조용히 버린다 — 진행률 표시 한 건이 깨졌다고 화면 전체를 실패로 만들 이유가 없다.
    //    다음 건이나 폴링이 이어받는다.
    return null;
  }
}
