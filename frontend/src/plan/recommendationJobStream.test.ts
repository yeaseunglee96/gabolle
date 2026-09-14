import { openJobProgressStream, supportsJobProgressStream } from './recommendationJobStream';

/** `data: {...}\n\n` 블록 여러 개를 한 번에, 혹은 청크로 잘려 오는 것을 흉내 낸다. */
function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let index = 0;
  return new ReadableStream({
    pull(controller) {
      if (index >= chunks.length) {
        controller.close();
        return;
      }
      controller.enqueue(encoder.encode(chunks[index]));
      index += 1;
    },
  });
}

describe('supportsJobProgressStream', () => {
  it('테스트 환경(jest-expo, Platform.OS=ios)에서는 지원하지 않는다 — 네이티브라서다', () => {
    expect(supportsJobProgressStream()).toBe(false);
  });
});

describe('openJobProgressStream', () => {
  const jobId = '02ae7ea5-0688-4d8c-a310-b42031ff47a2';
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    jest.restoreAllMocks();
  });

  it('완결된 블록마다 onSnapshot을 부르고, 서버가 스스로 닫으면 onDone을 부른다', async () => {
    const events: unknown[] = [];
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      body: streamOf([
        'event: progress\ndata: {"jobId":"' + jobId + '","status":"RUNNING","stage":"RANKING","percent":40,"code":null}\n\n',
        'event: completed\ndata: {"jobId":"' + jobId + '","status":"SUCCEEDED","stage":"COMPLETED","percent":100,"code":null}\n\n',
      ]),
    }) as unknown as typeof fetch;

    let done = false;
    await new Promise<void>((resolve) => {
      openJobProgressStream(jobId, 'token', {
        onSnapshot: (snapshot) => {
          events.push(snapshot);
          if (snapshot.status === 'SUCCEEDED') resolve();
        },
        onDone: () => { done = true; },
        onError: () => { throw new Error('onError는 불리면 안 된다'); },
      });
    });
    // onDone은 reader가 done:true를 받은 다음 마이크로태스크에서 불리므로 한 틱 기다린다.
    await new Promise((r) => setTimeout(r, 0));

    expect(events).toEqual([
      { jobId, status: 'RUNNING', stage: 'RANKING', percent: 40, code: null },
      { jobId, status: 'SUCCEEDED', stage: 'COMPLETED', percent: 100, code: null },
    ]);
    expect(done).toBe(true);
  });

  it('🔴 블록이 청크 경계에서 잘려도 하나로 이어붙여 읽는다', async () => {
    const events: unknown[] = [];
    const fullBlock = `data: {"jobId":"${jobId}","status":"RUNNING","stage":"CANDIDATE_GENERATION","percent":10,"code":null}\n\n`;
    const cutPoint = fullBlock.indexOf('CANDIDATE');
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      body: streamOf([fullBlock.slice(0, cutPoint), fullBlock.slice(cutPoint)]),
    }) as unknown as typeof fetch;

    await new Promise<void>((resolve) => {
      openJobProgressStream(jobId, null, {
        onSnapshot: (snapshot) => { events.push(snapshot); resolve(); },
        onDone: () => {},
        onError: () => { throw new Error('onError는 불리면 안 된다'); },
      });
    });

    expect(events).toEqual([
      { jobId, status: 'RUNNING', stage: 'CANDIDATE_GENERATION', percent: 10, code: null },
    ]);
  });

  it('응답이 실패(!ok)면 onError를 부른다 — 화면이 폴링으로 갈아탈 신호다', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: false, body: null }) as unknown as typeof fetch;

    await new Promise<void>((resolve) => {
      openJobProgressStream(jobId, null, {
        onSnapshot: () => { throw new Error('onSnapshot은 불리면 안 된다'); },
        onDone: () => { throw new Error('onDone은 불리면 안 된다'); },
        onError: resolve,
      });
    });
  });

  it('fetch 자체가 실패해도(네트워크 오류) onError를 부른다', async () => {
    globalThis.fetch = jest.fn().mockRejectedValue(new Error('network down')) as unknown as typeof fetch;

    await new Promise<void>((resolve) => {
      openJobProgressStream(jobId, null, {
        onSnapshot: () => { throw new Error('onSnapshot은 불리면 안 된다'); },
        onDone: () => { throw new Error('onDone은 불리면 안 된다'); },
        onError: resolve,
      });
    });
  });

  it('close()를 부른 뒤에는 onError·onDone 어느 것도 부르지 않는다 — 화면이 스스로 정리한 연결이다', async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, body: streamOf([]) }) as unknown as typeof fetch;
    const close = openJobProgressStream(jobId, null, {
      onSnapshot: () => { throw new Error('onSnapshot은 불리면 안 된다'); },
      onDone: () => { throw new Error('onDone은 불리면 안 된다'); },
      onError: () => { throw new Error('onError는 불리면 안 된다'); },
    });
    close();
    await new Promise((r) => setTimeout(r, 10));
  });
});
