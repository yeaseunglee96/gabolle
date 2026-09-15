import { API_BASE_URL } from '@/api/client';
import { resolveStoryImageUrl } from '../stories';

describe('resolveStoryImageUrl', () => {
  it('turns the backend local-storage path into a native-loadable absolute URL', () => {
    expect(resolveStoryImageUrl('/api/v1/uploads/images/story/photo.jpg'))
      .toBe(`${API_BASE_URL}/api/v1/uploads/images/story/photo.jpg`);
  });

  it('keeps already absolute object-storage URLs unchanged', () => {
    expect(resolveStoryImageUrl('https://photos.example.com/story/photo.webp'))
      .toBe('https://photos.example.com/story/photo.webp');
  });
});
