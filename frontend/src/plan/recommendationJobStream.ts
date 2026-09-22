import { Platform } from 'react-native';
import { API_BASE_URL, getApiLanguage } from '@/api/client';

/** `GET /api/v1/jobs/{jobId}/progress` (SSE) 를 읽는다 — S15P21E201-69. */
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
    // 조용히 버린다 — 진행률 표시 한 건이 깨졌다고 화면 전체를 실패로 만들 이유가 없다.
    // 다음 건이나 폴링이 이어받는다.
    return null;
  }
}
