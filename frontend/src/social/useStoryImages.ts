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
  /** 고를 때 받은 원래 파일 이름·형식. 줄이기가 실패해 원본을 그대로 보낼 때 쓴다. */
  originalFileName: string | null;
  originalMimeType: string | null;
  /** 올라간 주소. null 이면 아직 못 올렸다. */
  imageUrl: string | null;
  uploading: boolean;
  error: string | null;
};

type Translate = (ko: string, en: string) => string;

/**
 * 무엇이 왜 막혔는지 화면까지 가져간다 — S15P21E201-1121.
 *
 * <p>🔴 예전에는 catch 가 error 를 통째로 버리고 「사진을 처리하지 못했어요」만 남겼다.
 * 릴리스 빌드에는 JS 콘솔이 없어서 기기 로그에도 아무것도 안 남는다. 그래서 실기기에서
 * 100% 실패하는데도 어느 단계가 깨졌는지 아무도 알 수 없었다(2026-09-16 iOS·안드로이드
 * 양쪽에서 재현). 사람에게 보여 줄 말 뒤에 기술적인 한 줄을 붙여 둔다.
 */
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
  // 과정에서 촬영 위치 정보(EXIF)도 함께 뗀다(S15P21E201-204). 재시도도 이 함수를
  // 다시 타서, 실패했던 것을 원본 그대로 올려버리는 일이 없게 한다.
  const processAndUpload = async (index: number, image: Pick<PendingImage, 'originalUri' | 'originalFileName' | 'originalMimeType'>) => {
    const { originalUri } = image;
    try {
      // 🔴 S15P21E201-1121 — 줄이기가 실패해도 여기서 끝내지 않는다.
      //
      //    예전에는 resizeForUpload 가 던지면 곧장 catch 로 떨어져 업로드 자체를
      //    안 했다. 2026-09-16 iOS·안드로이드 실기기에서 사진 업로드가 100% 실패했고,
      //    서버 기록에는 요청이 한 건도 남지 않았다 — 네트워크까지 가지도 못한 것이다.
      //
      //    줄이기는 두 가지를 한다: (1) 가장 긴 변을 1600px로 줄여 전송량을 아끼고
      //    (2) 다시 인코딩하면서 촬영 위치 정보(EXIF)를 뗀다(S15P21E201-204).
      //    그런데 (2)는 서버가 이미 자기 몫으로 한다 — ImageUploadService 가
      //    ImageSanitizer.strip 으로 EXIF 를 지우고 저장한다. 즉 줄이기는 편의이지
      //    개인정보 약속의 근거가 아니다. 그래서 실패하면 원본으로 넘겨도 약속은
      //    그대로 지켜진다. 상한을 넘으면 아래 3MB 검사와 서버의 413 이 받아 준다.
      //
      //    원본이라도 올라가는 것이, 아무것도 안 올라가는 것보다 낫다.
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

      // 상한을 넘으면 보내지 않는다 — 올라가기를 기다린 끝에 실패를 보는 대신,
      // 여기서 실제 크기와 함께 이유를 말한다 (S15P21E201-955).
      // 못 재면(null) 막지 않는다. 판정은 서버가 하고 413 처리가 받아 준다.
      const bytes = await measureBytes(uploadUri);
      if (bytes !== null && bytes > MAX_UPLOAD_BYTES) {
        const mb = (bytes / (1024 * 1024)).toFixed(1);
        patch(index, {
          uploading: false,
          error: resizeFailure
            ? tx(`사진을 줄이지 못했고 원본이 ${mb}MB 라 올릴 수 없어요. 한 장은 3MB까지예요. (${resizeFailure})`, `Could not resize, and the original is ${mb}MB — each photo must be 3MB or less. (${resizeFailure})`)
            : tx(`줄여도 ${mb}MB 라 올릴 수 없어요. 한 장은 3MB까지예요.`, `Still ${mb}MB after resizing — each photo must be 3MB or less.`),
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
