// — 줄이기가 실패해도 사진은 올라가야 한다.
import { renderHook, act, waitFor } from '@testing-library/react-native';

import { useStoryImages } from '../useStoryImages';

jest.mock('expo-image-picker', () => ({
  launchImageLibraryAsync: jest.fn(),
}));
// — 가짜 모듈에 상수를 빠뜨리면 undefined 가 되고, 크기 비교가
// 언제나 거짓이 되어 검사가 시험에서만 조용히 꺼진다. 실제 값과 같이 적어 둔다.
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

// — 고르기 상한과 전송 상한은 다른 것을 잰다.
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

// 🔴 글 쓰다 나갔다 와도 **이미 올라간** 사진은 남아야 한다 (S15P21E201-1312).
//
//    올라간 사진은 로컬 주소가 아니라 서버가 준 주소를 갖고 있다. 그 주소는 새로고침해도
//    다른 기기에서도 그대로 쓸 수 있는데, 전에는 안 올라간 사진과 같이 버리고 있었다.
describe('useStoryImages — 올라간 사진 되살리기', () => {
  it('주소만으로 되살리고, 되살린 것은 처음부터 올라간 상태다', () => {
    const { result } = renderHook(() => useStoryImages('token', tx));

    act(() => { result.current.restoreUploaded(['https://cdn.example/a.jpg', 'https://cdn.example/b.jpg']); });

    expect(result.current.uploadedUrls).toEqual(['https://cdn.example/a.jpg', 'https://cdn.example/b.jpg']);
    // 다시 올리지 않는다 — 올라가는 중으로 두면 글 보내기가 영영 안 열린다.
    expect(result.current.anyUploading).toBe(false);
    expect(result.current.images.every((image) => image.error === null)).toBe(true);
  });

  it('상한을 넘겨 되살리지 않는다', () => {
    const { result } = renderHook(() => useStoryImages('token', tx));

    act(() => { result.current.restoreUploaded(['1', '2', '3', '4', '5']); });

    expect(result.current.images).toHaveLength(3);
  });

  it('🔴 이미 사진이 있으면 덮지 않는다 — 되살리기가 사용자가 방금 고른 것을 지우면 안 된다', async () => {
    pick({ uri: 'file:///orig.jpg' });
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 1600, height: 900 });
    const { result } = renderHook(() => useStoryImages('token', tx));
    await act(async () => { await result.current.addImage(); });
    await waitFor(() => expect(result.current.anyUploading).toBe(false));

    act(() => { result.current.restoreUploaded(['https://cdn.example/old.jpg']); });

    expect(result.current.uploadedUrls).toEqual(['https://cdn.example/1.jpg']);
  });

  it('🔴 되살린 사진은 재시도하지 않는다 — 원본이 이 기기에 없다', () => {
    const { result } = renderHook(() => useStoryImages('token', tx));
    act(() => { result.current.restoreUploaded(['https://cdn.example/a.jpg']); });

    act(() => { result.current.retryImage(0); });

    expect(stories.uploadStoryImage).not.toHaveBeenCalled();
    expect(result.current.anyUploading).toBe(false);
  });

  it('🔴 사진이 안 바뀌면 주소 목록도 그대로다 — 이 값이 임시 저장의 조건이라 매번 바뀌면 그릴 때마다 저장한다', () => {
    const { result, rerender } = renderHook(() => useStoryImages('token', tx));
    act(() => { result.current.restoreUploaded(['https://cdn.example/a.jpg']); });
    const first = result.current.uploadedUrls;

    rerender({});

    expect(result.current.uploadedUrls).toBe(first);
  });
});
