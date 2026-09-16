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
jest.mock('../imageResize', () => ({
  MAX_UPLOAD_BYTES: 3 * 1024 * 1024,
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
