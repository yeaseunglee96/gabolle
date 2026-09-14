import { Image } from 'react-native';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

// S15P21E201-204 — 가장 긴 변을 1600px로 줄이고 원본 촬영 위치 정보(EXIF)를 뺀다.
// 다시 인코딩하는 것 자체가 EXIF를 떨어뜨린다 — 그래서 이미 1600px 이하인 사진도
// 한 번은 다시 저장한다(치수는 그대로 두고 위치 정보만 제거).
const MAX_DIMENSION = 1600;

/**
 * 사진 한 장의 상한 — 서버와 같은 값이어야 한다 (S15P21E201-955).
 * 서버: spring.servlet.multipart.max-file-size=3MB · UploadedImage.MAX_BYTES.
 * 여기서 막는 것은 서버를 대신하려는 게 아니라, 왕복을 기다린 뒤에야 이유를
 * 알게 되는 것을 없애려는 것이다. 마지막 판정은 여전히 서버가 한다.
 */
export const MAX_UPLOAD_BYTES = 3 * 1024 * 1024;

/**
 * 파일 크기를 바이트로 잰다. 못 재면 null 을 준다 — 그때는 막지 않고 보낸다.
 * 🔴 못 쟀다고 막으면, 멀쩡한 사진이 플랫폼 사정으로 올라가지 않게 된다.
 * 재기는 거들 뿐이고 실제 문은 서버다.
 */
export async function measureBytes(uri: string): Promise<number | null> {
  try {
    const response = await fetch(uri);
    const blob = await response.blob();
    return typeof blob.size === 'number' && blob.size > 0 ? blob.size : null;
  } catch {
    return null;
  }
}

function getImageSize(uri: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    Image.getSize(uri, (width, height) => resolve({ width, height }), reject);
  });
}

export async function resizeForUpload(uri: string): Promise<{ uri: string; width: number; height: number }> {
  const { width, height } = await getImageSize(uri);
  const context = ImageManipulator.manipulate(uri);
  if (width >= height) context.resize({ width: Math.min(width, MAX_DIMENSION) });
  else context.resize({ height: Math.min(height, MAX_DIMENSION) });
  const rendered = await context.renderAsync();
  return rendered.saveAsync({ compress: 0.85, format: SaveFormat.JPEG });
}
