// 기록에 붙이는 동영상 — 고르고, 줄이고, 올리기까지 한 곳에 모은다.
// 사진의 useStoryImages 와 같은 모양이다. 다른 점은 **한 개만** 붙는다는 것뿐이다.
import { useState } from 'react';
import * as ImagePicker from 'expo-image-picker';

import {
  compressForUpload,
  MAX_VIDEO_SECONDS,
  MAX_VIDEO_SECONDS_LABEL,
  MAX_VIDEO_UPLOAD_BYTES,
  MAX_VIDEO_UPLOAD_LABEL,
  measureBytes,
} from '@/social/videoCompress';
import { uploadStoryVideo } from '@/social/stories';

/** 한 기록에 붙일 수 있는 동영상 수. 서버 계약과 같은 값이다. */
export const MAX_STORY_VIDEOS = 1;

export type PendingVideo = {
  /** 화면에 미리 보여줄 주소. 줄이기가 끝나면 줄인 것으로 바뀐다. */
  localUri: string;
  /** 재시도가 원본부터 다시 타도록 남겨 둔다 — 줄인 것을 또 줄이지 않게. */
  originalUri: string;
  originalFileName: string | null;
  originalMimeType: string | null;
  /** 앱이 잰 길이(초). 못 쟀으면 null — 서버에 안 보낸다. */
  durationSec: number | null;
  /** 올라간 주소. null 이면 아직 못 올렸다. */
  videoUrl: string | null;
  uploading: boolean;
  error: string | null;
};

type Translate = (ko: string, en: string) => string;

function describeCause(error: unknown): string {
  if (error instanceof Error && error.message) return error.message.slice(0, 120);
  if (typeof error === 'string' && error) return error.slice(0, 120);
  return '알 수 없는 오류';
}

const mb = (bytes: number) => (bytes / (1024 * 1024)).toFixed(1);

export function useStoryVideo(accessToken: string | null, tx: Translate) {
  const [video, setVideo] = useState<PendingVideo | null>(null);

  const patch = (next: Partial<PendingVideo>) => setVideo((prev) => (prev ? { ...prev, ...next } : prev));

  const processAndUpload = async (picked: PendingVideo) => {
    try {
      // 🔴 줄이기가 실패해도 여기서 끝내지 않는다. 원본이 이미 작을 수 있다.
      let uploadUri = picked.originalUri;
      let compressFailure: string | null = null;
      try {
        uploadUri = await compressForUpload(picked.originalUri);
        patch({ localUri: uploadUri });
      } catch (error) {
        compressFailure = describeCause(error);
      }

      // 상한을 넘으면 보내지 않는다 — 올라가기를 기다린 끝에 실패를 보는 대신
      // 여기서 실제 크기와 함께 이유를 말한다.
      // 🔴 못 재면(null) 막지 않는다. 판정은 서버가 하고 413 처리가 받아 준다.
      const bytes = await measureBytes(uploadUri);
      if (bytes !== null && bytes > MAX_VIDEO_UPLOAD_BYTES) {
        patch({
          uploading: false,
          // 숫자를 문구에 박지 않는다. 상한을 바꿨는데 문구에 옛 숫자가 남으면
          // 사용자는 틀린 이유를 읽는다 — 이유를 안 보여 주는 것보다 나쁘다.
          error: compressFailure
            ? tx(
                `동영상을 줄이지 못했고 ${mb(bytes)}MB 라 올릴 수 없어요. 더 짧은 영상으로 해보세요. (${compressFailure})`,
                `Could not compress, and it is ${mb(bytes)}MB — try a shorter clip. (${compressFailure})`,
              )
            : tx(
                `줄여도 ${mb(bytes)}MB 라 올릴 수 없어요. 동영상은 ${MAX_VIDEO_UPLOAD_LABEL}까지예요. 더 짧은 영상으로 해보세요.`,
                `Still ${mb(bytes)}MB after compressing — a video must be ${MAX_VIDEO_UPLOAD_LABEL} or less. Try a shorter clip.`,
              ),
        });
        return;
      }

      const outcome = await uploadStoryVideo(
        { uri: uploadUri, fileName: 'story.mp4', mimeType: 'video/mp4', durationSec: picked.durationSec },
        accessToken,
      );
      patch(outcome.state === 'success'
        ? { videoUrl: outcome.videoUrl, uploading: false, error: null }
        : { uploading: false, error: compressFailure ? `${outcome.message} (줄이기 실패: ${compressFailure})` : outcome.message });
    } catch (error) {
      patch({
        uploading: false,
        error: tx(
          `동영상을 처리하지 못했어요. 더 짧은 영상으로 해보세요. (${describeCause(error)})`,
          `Could not process the video. Try a shorter clip. (${describeCause(error)})`,
        ),
      });
    }
  };

  const addVideo = async () => {
    if (video) return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['videos'] });
    if (result.canceled) return;
    const asset = result.assets[0];
    // expo-image-picker 는 밀리초로 준다. 못 재면 null 로 둔다 — 0 을 지어내면
    // 「0초짜리 영상」이 되어 서버에 그대로 실린다.
    const durationSec = typeof asset.duration === 'number' ? asset.duration / 1000 : null;

    // 🔴 길이를 여기서 막는다. 크기는 대략 화질 × 길이라, 안 막으면 3분짜리를 골라 놓고
    //    몇 분을 기다린 뒤에 거절당한다. 줄이는 일은 기기에서 도는 실제 연산이다.
    if (durationSec !== null && durationSec > MAX_VIDEO_SECONDS) {
      setVideo({
        localUri: asset.uri,
        originalUri: asset.uri,
        originalFileName: asset.fileName ?? null,
        originalMimeType: asset.mimeType ?? null,
        durationSec,
        videoUrl: null,
        uploading: false,
        error: tx(
          `${Math.round(durationSec)}초짜리라 너무 길어요. 동영상은 ${MAX_VIDEO_SECONDS_LABEL}까지예요.`,
          `This clip is ${Math.round(durationSec)}s — a video must be ${MAX_VIDEO_SECONDS}s or less.`,
        ),
      });
      return;
    }

    const picked: PendingVideo = {
      localUri: asset.uri,
      originalUri: asset.uri,
      originalFileName: asset.fileName ?? null,
      originalMimeType: asset.mimeType ?? null,
      durationSec,
      videoUrl: null,
      uploading: true,
      error: null,
    };
    setVideo(picked);
    void processAndUpload(picked);
  };

  const retryVideo = () => {
    if (!video || video.uploading) return;
    patch({ uploading: true, error: null });
    void processAndUpload({ ...video, uploading: true, error: null });
  };

  const removeVideo = () => setVideo(null);

  return {
    video,
    addVideo,
    retryVideo,
    removeVideo,
    clearVideo: removeVideo,
    /** 올라가는 중이면 글을 보내지 않는다 — 주소가 아직 없어서 빠진다. */
    uploading: video?.uploading ?? false,
    /** 실제로 올라간 것만. 실패한 동영상은 글에 안 붙는다. */
    uploadedUrl: video?.videoUrl ?? null,
    canAdd: video === null,
  };
}
