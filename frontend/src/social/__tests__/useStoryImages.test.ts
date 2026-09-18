// S15P21E201-1121 — 줄이기가 실패해도 사진은 올라가야 한다.
//
// 2026-09-16 iOS·안드로이드 실기기에서 사진 업로드가 100% 실패했는데, 서버 기록에는
// 요청이 한 건도 없었다. 줄이기(resizeForUpload)가 던지면 업로드를 아예 안 했기 때문이다.
// 아래 시험은 그 경로가 다시 막히면 빨개진다.
import { renderHook, act, waitFor } from '@testing-library/react-native';

import { useStoryImages } from '../useStoryImages';

jest.mock('expo-image-picker', () => ({
  launchImageLibraryAsync: jest.fn(),
}));
// 🔴 S15P21E201-1134 — 가짜 모듈에 상수를 빠뜨리면 undefined 가 되고, 크기 비교가
// 언제나 거짓이 되어 **검사가 시험에서만 조용히 꺼진다.** 실제 값과 같이 적어 둔다.
jest.mock('../imageResize', () => ({
  MAX_PICK_BYTES: 30 * 1024 * 1024,
  MAX_UPLOAD_BYTES: 3 * 1024 * 1024,
  MAX_PICK_LABEL: '30MB',
  MAX_UPLOAD_LABEL: '3MB',
  measureBytes: jest.fn(),
  resizeForUpload: jest.fn(),
}));
jest.mock('../stories', () => ({
  uploadStoryImage: jest.fn(),
}));

const picker = jest.requireMock('expo-image-picker') as { launchImageLibraryAsync: jest.Mock };
const resize = jest.requireMock('../imageResize') as { measureBytes: jest.Mock; resizeForUpload: jest.Mock };
const stories = jest.requireMock('../stories') as { uploadStoryImage: jest.Mock };

const tx = (ko: string) => ko;

function pick(asset: { uri: string; fileName?: string; mimeType?: string }) {
  picker.launchImageLibraryAsync.mockResolvedValue({ canceled: false, assets: [asset] });
}

beforeEach(() => {
  jest.clearAllMocks();
  resize.measureBytes.mockResolvedValue(1024);
  stories.uploadStoryImage.mockResolvedValue({ state: 'success', imageUrl: 'https://cdn.example/1.jpg' });
});

describe('useStoryImages — 사진 한 장 올리기', () => {
  it('줄이기가 되면 줄인 것을 올린다', async () => {
    pick({ uri: 'file:///orig.heic', fileName: 'orig.heic', mimeType: 'image/heic' });
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 1600, height: 900 });

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.uploadedUrls).toHaveLength(1));

    expect(stories.uploadStoryImage).toHaveBeenCalledWith(
      { uri: 'file:///small.jpg', fileName: 'story.jpg', mimeType: 'image/jpeg' },
      'token',
    );
    expect(result.current.images[0].error).toBeNull();
  });

  it('줄이기가 실패해도 원본을 그대로 올린다 — 서버가 EXIF 를 지우므로 약속은 지켜진다', async () => {
    pick({ uri: 'file:///orig.heic', fileName: 'orig.heic', mimeType: 'image/heic' });
    resize.resizeForUpload.mockRejectedValue(new Error('manipulate is not a function'));

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.uploadedUrls).toHaveLength(1));

    expect(stories.uploadStoryImage).toHaveBeenCalledWith(
      { uri: 'file:///orig.heic', fileName: 'orig.heic', mimeType: 'image/heic' },
      'token',
    );
  });

  it('줄이기가 실패하고 원본도 3MB 를 넘으면, 왜 못 줄였는지까지 말한다', async () => {
    pick({ uri: 'file:///big.jpg' });
    resize.resizeForUpload.mockRejectedValue(new Error('manipulate is not a function'));
    resize.measureBytes.mockResolvedValue(5 * 1024 * 1024);

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0].uploading).toBe(false));

    expect(stories.uploadStoryImage).not.toHaveBeenCalled();
    expect(result.current.images[0].error).toContain('5.0MB');
    expect(result.current.images[0].error).toContain('manipulate is not a function');
  });

  it('업로드 자체가 실패하면 서버가 준 말을 그대로 보여준다', async () => {
    pick({ uri: 'file:///orig.jpg' });
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 800, height: 600 });
    stories.uploadStoryImage.mockResolvedValue({ state: 'error', message: '사진이 너무 커요. 3MB 이하로 올려주세요.' });

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0].uploading).toBe(false));

    expect(result.current.images[0].error).toBe('사진이 너무 커요. 3MB 이하로 올려주세요.');
    expect(result.current.uploadedUrls).toHaveLength(0);
  });

  it('다시 시도는 줄인 것이 아니라 원본부터 다시 탄다', async () => {
    pick({ uri: 'file:///orig.jpg', fileName: 'orig.jpg', mimeType: 'image/jpeg' });
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 800, height: 600 });
    stories.uploadStoryImage.mockResolvedValue({ state: 'error', message: '잠시 후 다시 시도해 주세요.' });

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0].uploading).toBe(false));

    resize.resizeForUpload.mockClear();
    await act(async () => { result.current.retryImage(0); });
    await waitFor(() => expect(resize.resizeForUpload).toHaveBeenCalledWith('file:///orig.jpg'));
  });
});

// S15P21E201-1134 — 고르기 상한과 전송 상한은 다른 것을 잰다.
//
// 전에는 하나(3MB)가 둘을 겸해서, 요즘 휴대폰 사진(5~15MB)이 **줄이기도 해 보기 전에**
// 거절당했다. 고른 원본은 30MB 까지 받고, 실제로 서버에 가는 바이트만 3MB 로 잰다.
describe('useStoryImages — 고르기 상한(30MB)과 전송 상한(3MB)', () => {
  it('🔴 25MB 원본을 골라도 거절하지 않는다 — 줄이면 서버 상한 안에 들어온다', async () => {
    pick({ uri: 'file:///big.jpg', fileName: 'big.jpg', mimeType: 'image/jpeg' });
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg' });
    // 원본은 25MB, 줄인 뒤는 0.9MB
    resize.measureBytes
      .mockResolvedValueOnce(25 * 1024 * 1024)
      .mockResolvedValueOnce(Math.round(0.9 * 1024 * 1024));

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0]?.uploading).toBe(false));

    expect(result.current.images[0]?.error).toBeNull();
    expect(stories.uploadStoryImage).toHaveBeenCalledTimes(1);
  });

  it('40MB 원본은 줄이기를 시도하지도 않고 거절한다 — 통째로 메모리에 올리면 앱이 죽는다', async () => {
    pick({ uri: 'file:///huge.jpg', fileName: 'huge.jpg', mimeType: 'image/jpeg' });
    resize.measureBytes.mockResolvedValueOnce(40 * 1024 * 1024);

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0]?.uploading).toBe(false));

    expect(result.current.images[0]?.error).toContain('30MB');
    expect(resize.resizeForUpload).not.toHaveBeenCalled();
    expect(stories.uploadStoryImage).not.toHaveBeenCalled();
  });

  it('줄이기가 실패하고 원본이 서버 상한을 넘으면 전송 상한으로 말한다', async () => {
    pick({ uri: 'file:///mid.jpg', fileName: 'mid.jpg', mimeType: 'image/jpeg' });
    resize.resizeForUpload.mockRejectedValue(new Error('decoder failed'));
    // 원본 10MB — 고르기 상한(30MB)은 통과하지만 전송 상한(3MB)은 넘는다
    resize.measureBytes
      .mockResolvedValueOnce(10 * 1024 * 1024)
      .mockResolvedValueOnce(10 * 1024 * 1024);

    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.images[0]?.uploading).toBe(false));

    expect(result.current.images[0]?.error).toContain('3MB');
    expect(stories.uploadStoryImage).not.toHaveBeenCalled();
  });
});
