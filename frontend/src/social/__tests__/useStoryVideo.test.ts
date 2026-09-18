// 동영상이 올라가기 전에 무엇을 막는가.
//
// 🔴 이 화면의 핵심은 「얼마나 줄어드는지 몰라도 안전하다」다. 줄인 다음 크기를 다시 재고,
//    그래도 크면 그 자리에서 이유를 말한다. 그 판정이 꺼져 있어도 화면은 멀쩡해 보인다.
import { renderHook, act, waitFor } from '@testing-library/react-native';

import { useStoryVideo } from '../useStoryVideo';

jest.mock('expo-image-picker', () => ({ launchImageLibraryAsync: jest.fn() }));
// 🔴 가짜 모듈에 상수를 빠뜨리면 undefined 가 되고, 크기 비교가 언제나 거짓이 되어
//    검사가 시험에서만 조용히 꺼진다. 실제 값과 같이 적어 둔다.
jest.mock('../videoCompress', () => ({
  MAX_VIDEO_UPLOAD_BYTES: 3 * 1024 * 1024,
  MAX_VIDEO_SECONDS: 30,
  MAX_VIDEO_UPLOAD_LABEL: '3MB',
  MAX_VIDEO_SECONDS_LABEL: '30초',
  measureBytes: jest.fn(),
  compressForUpload: jest.fn(),
}));
jest.mock('../stories', () => ({ uploadStoryVideo: jest.fn() }));

const picker = jest.requireMock('expo-image-picker') as { launchImageLibraryAsync: jest.Mock };
const compress = jest.requireMock('../videoCompress') as { measureBytes: jest.Mock; compressForUpload: jest.Mock };
const stories = jest.requireMock('../stories') as { uploadStoryVideo: jest.Mock };

const tx = (ko: string) => ko;
const MB = 1024 * 1024;

function pick(asset: { uri: string; duration?: number; fileName?: string; mimeType?: string }) {
  picker.launchImageLibraryAsync.mockResolvedValue({ canceled: false, assets: [asset] });
}

async function add() {
  const hook = renderHook(() => useStoryVideo('token', tx));
  await act(async () => { await hook.result.current.addVideo(); });
  return hook;
}

beforeEach(() => {
  jest.clearAllMocks();
  compress.compressForUpload.mockResolvedValue('file://small.mp4');
  compress.measureBytes.mockResolvedValue(1 * MB);
  stories.uploadStoryVideo.mockResolvedValue({ state: 'success', videoUrl: 'https://cdn/v.mp4', byteSize: 1 * MB });
});

describe('동영상 붙이기', () => {
  it('줄여서 상한 아래면 올라간다', async () => {
    pick({ uri: 'file://big.mp4', duration: 10_000 });
    const hook = await add();
    await waitFor(() => expect(hook.result.current.uploadedUrl).toBe('https://cdn/v.mp4'));
    expect(compress.compressForUpload).toHaveBeenCalledWith('file://big.mp4');
  });

  it('🔴 줄여도 상한을 넘으면 안 보낸다 — 실제 크기와 함께 이유를 말한다', async () => {
    compress.measureBytes.mockResolvedValue(5 * MB);
    pick({ uri: 'file://big.mp4', duration: 10_000 });
    const hook = await add();
    await waitFor(() => expect(hook.result.current.video?.error).toBeTruthy());
    expect(hook.result.current.video?.error).toContain('5.0MB');
    expect(stories.uploadStoryVideo).not.toHaveBeenCalled();
  });

  it('🔴 너무 길면 줄이지도 않는다 — 기다린 뒤에 거절하지 않는다', async () => {
    pick({ uri: 'file://long.mp4', duration: 90_000 });
    const hook = await add();
    expect(hook.result.current.video?.error).toContain('90초');
    expect(compress.compressForUpload).not.toHaveBeenCalled();
    expect(stories.uploadStoryVideo).not.toHaveBeenCalled();
  });

  it('🔴 크기를 못 재면 막지 않는다 — 판정은 서버가 한다', async () => {
    compress.measureBytes.mockResolvedValue(null);
    pick({ uri: 'file://unknown.mp4', duration: 10_000 });
    const hook = await add();
    await waitFor(() => expect(stories.uploadStoryVideo).toHaveBeenCalled());
  });

  it('줄이기가 실패해도 원본이 작으면 올라간다', async () => {
    compress.compressForUpload.mockRejectedValue(new Error('코덱 없음'));
    pick({ uri: 'file://small.mp4', duration: 10_000 });
    const hook = await add();
    await waitFor(() => expect(hook.result.current.uploadedUrl).toBe('https://cdn/v.mp4'));
  });

  it('🔴 길이를 못 재면 서버에 안 보낸다 — 0 을 지어내지 않는다', async () => {
    pick({ uri: 'file://noduration.mp4' });
    const hook = await add();
    await waitFor(() => expect(stories.uploadStoryVideo).toHaveBeenCalled());
    expect(stories.uploadStoryVideo.mock.calls[0][0].durationSec).toBeNull();
  });

  it('한 기록에 하나만 붙는다', async () => {
    pick({ uri: 'file://a.mp4', duration: 5_000 });
    const hook = await add();
    await waitFor(() => expect(hook.result.current.uploadedUrl).toBeTruthy());
    expect(hook.result.current.canAdd).toBe(false);
    await act(async () => { await hook.result.current.addVideo(); });
    expect(picker.launchImageLibraryAsync).toHaveBeenCalledTimes(1);
  });

  it('고르기를 취소하면 아무 일도 안 일어난다 — 찾는 방법이 살아 있다', async () => {
    picker.launchImageLibraryAsync.mockResolvedValue({ canceled: true });
    const hook = await add();
    expect(hook.result.current.video).toBeNull();
    expect(compress.compressForUpload).not.toHaveBeenCalled();
  });
});
