// 사진 업로드 — 12초 기본 제한에 걸려 「서버 응답이 늦어」로 실패하던 것(S15P21E201-1904).
jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: jest.fn(async () => ({ imageId: 'i', imageUrl: 'https://x/i.jpg', contentType: 'image/jpeg', byteSize: 1 })) };
});
jest.mock('@/api/multipart', () => ({ singleFileFormData: jest.fn(async (_f: string, file: { uri: string }) => ({ file })) }));
import { UPLOAD_TIMEOUT_MS, uploadStoryImage } from '@/social/stories';
const mockApiRequest = (jest.requireMock('@/api/client') as { apiRequest: jest.Mock }).apiRequest;

it('🔴 사진을 60초 제한으로 올린다', async () => {
  const out = await uploadStoryImage({ uri: 'file://small.jpg', fileName: 'small.jpg', mimeType: 'image/jpeg' } as never, 't');
  expect(out.state).toBe('success');
  const [, options] = mockApiRequest.mock.calls[0] as unknown as [string, { timeoutMs: number; body: { file: { uri: string } } }];
  expect(options.timeoutMs).toBe(UPLOAD_TIMEOUT_MS);
  expect(UPLOAD_TIMEOUT_MS).toBeGreaterThanOrEqual(60_000);
  expect(options.body.file.uri).toBe('file://small.jpg');
});

it('🔴 영상은 사진처럼 줄이지 않는다 — 그대로 올리고 60초 제한만 준다', async () => {
  mockApiRequest.mockClear();
  mockApiRequest.mockResolvedValueOnce({ videoId: 'v', videoUrl: 'https://x/v.mp4', contentType: 'video/mp4', byteSize: 1 } as never);
  const { uploadStoryVideo } = jest.requireActual('@/social/stories') as typeof import('@/social/stories');
  await uploadStoryVideo({ uri: 'file://clip.mp4', fileName: 'clip.mp4', mimeType: 'video/mp4' }, 't');
  const [, options] = mockApiRequest.mock.calls[0] as unknown as [string, { timeoutMs: number; body: { file: { uri: string } } }];
  expect(options.body.file.uri).toBe('file://clip.mp4');
  expect(options.timeoutMs).toBe(UPLOAD_TIMEOUT_MS);
});

