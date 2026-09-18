// 기록에 붙이는 사진 — 고르고, 줄이고, 올리기까지 한 곳에 모은다.
import { useState } from 'react';
import * as ImagePicker from 'expo-image-picker';

import { MAX_PICK_BYTES, MAX_PICK_LABEL, MAX_UPLOAD_BYTES, MAX_UPLOAD_LABEL, measureBytes, resizeForUpload } from '@/social/imageResize';
import { uploadStoryImage } from '@/social/stories';

/** 한 기록에 붙일 수 있는 사진 수. 서버 계약과 같은 값이다. */
export const MAX_STORY_IMAGES = 3;

export type PendingImage = {
  /** 화면에 미리 보여줄 주소. 줄이기가 끝나면 줄인 것으로 바뀐다. */
  localUri: string;
  /** 재시도가 원본부터 다시 타도록 남겨 둔다 — 줄인 것을 또 줄이지 않게. */
  originalUri: string;
  /** 고를 때 받은 원래 파일 이름·형식. 줄이기가 실패해 원본을 그대로 보낼 때 쓴다. */
  originalFileName: string | null;
  originalMimeType: string | null;
  /** 올라간 주소. null 이면 아직 못 올렸다. */
  imageUrl: string | null;
  uploading: boolean;
  error: string | null;
};

type Translate = (ko: string, en: string) => string;

/** 무엇이 왜 막혔는지 화면까지 가져간다 —. */
function describeCause(error: unknown): string {
  if (error instanceof Error && error.message) return error.message.slice(0, 120);
  if (typeof error === 'string' && error) return error.slice(0, 120);
  return '알 수 없는 오류';
}

export function useStoryImages(accessToken: string | null, tx: Translate) {
  const [images, setImages] = useState<PendingImage[]>([]);

  const patch = (index: number, next: Partial<PendingImage>) =>
    setImages((prev) => prev.map((image, position) => (position === index ? { ...image, ...next } : image)));

  // 원본을 그대로 올리지 않는다 — 다시 인코딩해서 가장 긴 변을 1600px로 줄이고 그
  // 과정에서 촬영 위치 정보(EXIF)도 함께 뗀다. 재시도도 이 함수를
  // 다시 타서, 실패했던 것을 원본 그대로 올려버리는 일이 없게 한다.
  const processAndUpload = async (index: number, image: Pick<PendingImage, 'originalUri' | 'originalFileName' | 'originalMimeType'>) => {
    const { originalUri } = image;
    try {
      // — 줄이기가 실패해도 여기서 끝내지 않는다.
      const originalBytes = await measureBytes(originalUri);
      if (originalBytes !== null && originalBytes > MAX_PICK_BYTES) {
        const size = (originalBytes / (1024 * 1024)).toFixed(1);
        patch(index, {
          uploading: false,
          error: tx(`사진이 ${size}MB 라 너무 커요. 한 장은 ${MAX_PICK_LABEL}까지예요.`, `This photo is ${size}MB — each photo must be ${MAX_PICK_LABEL} or less.`),
        });
        return;
      }

      let uploadUri = originalUri;
      let fileName = image.originalFileName ?? 'story.jpg';
      let mimeType = image.originalMimeType ?? 'image/jpeg';
      let resizeFailure: string | null = null;
      try {
        const resized = await resizeForUpload(originalUri);
        uploadUri = resized.uri;
        fileName = 'story.jpg';
        mimeType = 'image/jpeg';
        patch(index, { localUri: resized.uri });
      } catch (error) {
        resizeFailure = describeCause(error);
      }

      // 상한을 넘으면 보내지 않는다 — 올라가기를 기다린 끝에 실패를 보는 대신
      // 여기서 실제 크기와 함께 이유를 말한다
      // 못 재면(null) 막지 않는다. 판정은 서버가 하고 413 처리가 받아 준다.
      const bytes = await measureBytes(uploadUri);
      if (bytes !== null && bytes > MAX_UPLOAD_BYTES) {
        const mb = (bytes / (1024 * 1024)).toFixed(1);
        patch(index, {
          uploading: false,
          error: resizeFailure
            // 숫자를 문구에 박지 않는다 상한을 바꿨는데 문구에
            // 옛 숫자가 남으면 사용자는 틀린 이유를 읽는다 — 이유를 안 보여 주는 것보다 나쁘다.
            // 여기 걸리는 것은 거의 언제나 줄이기가 실패한 경우다. 그때는 서버가 받을
            // 수 있는 크기인지가 문제라 고르기 상한(30MB)이 아니라 전송 상한으로 말한다.
            ? tx(`사진을 줄이지 못했고 원본이 ${mb}MB 라 올릴 수 없어요. 줄이지 못한 사진은 ${MAX_UPLOAD_LABEL}까지만 올릴 수 있어요. (${resizeFailure})`, `Could not resize, and the original is ${mb}MB — un-resized photos must be ${MAX_UPLOAD_LABEL} or less. (${resizeFailure})`)
            : tx(`줄여도 ${mb}MB 라 올릴 수 없어요. 한 장은 ${MAX_UPLOAD_LABEL}까지예요.`, `Still ${mb}MB after resizing — each photo must be ${MAX_UPLOAD_LABEL} or less.`),
        });
        return;
      }

      const outcome = await uploadStoryImage({ uri: uploadUri, fileName, mimeType }, accessToken);
      patch(index, outcome.state === 'success'
        ? { imageUrl: outcome.imageUrl, uploading: false, error: null }
        : { uploading: false, error: resizeFailure ? `${outcome.message} (줄이기 실패: ${resizeFailure})` : outcome.message });
    } catch (error) {
      patch(index, {
        uploading: false,
        error: tx(`사진을 처리하지 못했어요. 다른 사진으로 해보거나, 3MB 이하로 줄여서 올려주세요. (${describeCause(error)})`, `Could not process the photo. Try another one, or resize it to 3MB or less. (${describeCause(error)})`),
      });
    }
  };

  const addImage = async () => {
    if (images.length >= MAX_STORY_IMAGES) return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled) return;
    const asset = result.assets[0];
    const index = images.length;
    const picked = {
      localUri: asset.uri,
      originalUri: asset.uri,
      originalFileName: asset.fileName ?? null,
      originalMimeType: asset.mimeType ?? null,
      imageUrl: null,
      uploading: true,
      error: null,
    };
    setImages((prev) => [...prev, picked]);
    void processAndUpload(index, picked);
  };

  const retryImage = (index: number) => {
    const image = images[index];
    if (!image || image.uploading) return;
    patch(index, { uploading: true, error: null });
    void processAndUpload(index, image);
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
