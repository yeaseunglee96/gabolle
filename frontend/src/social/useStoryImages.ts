// 기록에 붙이는 사진 — 고르고, 줄이고, 올리기까지 한 곳에 모은다.
//
// S15P21E201-958 2단계에서 compose.tsx 밖으로 뺐다. 피드 화면의 인라인 글쓰기가
// 같은 일을 해야 하는데, 🔴 복사해 두면 3MB 판정이나 리사이즈 규칙을 고칠 때
// 한쪽만 고치게 된다. 이 저장소가 여러 번 겪은 고장이라 처음부터 한 벌로 둔다.
//
// 담는 것은 "아직 못 올린 사진의 상태" 다. 올라간 주소(imageUrl)는 글을 보낼 때
// createStory 의 imageUrls 로 들어간다.
import { useState } from 'react';
import * as ImagePicker from 'expo-image-picker';

import { MAX_UPLOAD_BYTES, measureBytes, resizeForUpload } from '@/social/imageResize';
import { uploadStoryImage } from '@/social/stories';

/** 한 기록에 붙일 수 있는 사진 수. 서버 계약과 같은 값이다. */
export const MAX_STORY_IMAGES = 3;

export type PendingImage = {
  /** 화면에 미리 보여줄 주소. 줄이기가 끝나면 줄인 것으로 바뀐다. */
  localUri: string;
  /** 🔴 재시도가 원본부터 다시 타도록 남겨 둔다 — 줄인 것을 또 줄이지 않게. */
  originalUri: string;
  /** 올라간 주소. null 이면 아직 못 올렸다. */
  imageUrl: string | null;
  uploading: boolean;
  error: string | null;
};

type Translate = (ko: string, en: string) => string;

export function useStoryImages(accessToken: string | null, tx: Translate) {
  const [images, setImages] = useState<PendingImage[]>([]);

  const patch = (index: number, next: Partial<PendingImage>) =>
    setImages((prev) => prev.map((image, position) => (position === index ? { ...image, ...next } : image)));

  // 원본을 그대로 올리지 않는다 — 다시 인코딩해서 가장 긴 변을 1600px로 줄이고 그
  // 과정에서 촬영 위치 정보(EXIF)도 함께 뗀다(S15P21E201-204). 재시도도 이 함수를
  // 다시 타서, 실패했던 것을 원본 그대로 올려버리는 일이 없게 한다.
  const processAndUpload = async (index: number, originalUri: string) => {
    try {
      const resized = await resizeForUpload(originalUri);
      patch(index, { localUri: resized.uri });

      // 줄인 뒤에도 상한을 넘으면 보내지 않는다 — 올라가기를 기다린 끝에 실패를
      // 보는 대신, 여기서 실제 크기와 함께 이유를 말한다 (S15P21E201-955).
      // 못 재면(null) 막지 않는다. 판정은 서버가 하고 413 처리가 받아 준다.
      const bytes = await measureBytes(resized.uri);
      if (bytes !== null && bytes > MAX_UPLOAD_BYTES) {
        const mb = (bytes / (1024 * 1024)).toFixed(1);
        patch(index, {
          uploading: false,
          error: tx(`줄여도 ${mb}MB 라 올릴 수 없어요. 한 장은 3MB까지예요.`, `Still ${mb}MB after resizing — each photo must be 3MB or less.`),
        });
        return;
      }

      const outcome = await uploadStoryImage({ uri: resized.uri, fileName: 'story.jpg', mimeType: 'image/jpeg' }, accessToken);
      patch(index, outcome.state === 'success'
        ? { imageUrl: outcome.imageUrl, uploading: false, error: null }
        : { uploading: false, error: outcome.message });
    } catch {
      patch(index, {
        uploading: false,
        error: tx('사진을 처리하지 못했어요. 다른 사진으로 해보거나, 3MB 이하로 줄여서 올려주세요.', 'Could not process the photo. Try another one, or resize it to 3MB or less.'),
      });
    }
  };

  const addImage = async () => {
    if (images.length >= MAX_STORY_IMAGES) return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled) return;
    const asset = result.assets[0];
    const index = images.length;
    setImages((prev) => [...prev, { localUri: asset.uri, originalUri: asset.uri, imageUrl: null, uploading: true, error: null }]);
    void processAndUpload(index, asset.uri);
  };

  const retryImage = (index: number) => {
    const image = images[index];
    if (!image || image.uploading) return;
    patch(index, { uploading: true, error: null });
    void processAndUpload(index, image.originalUri);
  };

  const removeImage = (index: number) => setImages((prev) => prev.filter((_, position) => position !== index));

  const clearImages = () => setImages([]);

  return {
    images,
    addImage,
    retryImage,
    removeImage,
    clearImages,
    /** 하나라도 올라가는 중이면 글을 보내지 않는다 — 주소가 아직 없어서 빠진다. */
    anyUploading: images.some((image) => image.uploading),
    /** 실제로 올라간 것만. 실패한 사진은 글에 안 붙는다. */
    uploadedUrls: images.filter((image) => image.imageUrl).map((image) => image.imageUrl as string),
    canAddMore: images.length < MAX_STORY_IMAGES,
  };
}
