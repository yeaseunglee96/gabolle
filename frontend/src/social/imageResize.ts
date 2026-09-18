import { Image } from 'react-native';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

// S15P21E201-204 — 가장 긴 변을 1600px로 줄이고 원본 촬영 위치 정보(EXIF)를 뺀다.
// 다시 인코딩하는 것 자체가 EXIF를 떨어뜨린다 — 그래서 이미 1600px 이하인 사진도
// 한 번은 다시 저장한다(치수는 그대로 두고 위치 정보만 제거).
const MAX_DIMENSION = 1600;

/**
 * 🔴 상한이 둘이다 (S15P21E201-1134). 전에는 하나가 두 일을 겸했다.
 *
 * 하나는 **고른 원본**을 재고, 하나는 **실제로 서버에 보내는 바이트**를 잰다.
 * 그 사이에 {@link resizeForUpload} 가 가장 긴 변을 1600px 로 줄이므로 둘은
 * 완전히 다른 값이다 — 25MB 원본이 줄면 1MB 안팎이 된다.
 *
 * 전에는 이 둘을 3MB 하나로 묶어 두었다. 그래서 요즘 휴대폰 사진(한 장 5~15MB)이
 * **줄이기도 해 보기 전에** 거절당했다. 여행 중에 찍은 사진을 올리는 앱에서 그건
 * 기능이 없는 것과 같다.
 */

/**
 * 고른 원본의 상한. **기기가 죽는 것을 막는 선**이지 서버를 대신하는 선이 아니다.
 * 줄이는 작업은 사진을 통째로 메모리에 올리므로, 상한이 없으면 큰 파일에서 앱이
 * 조용히 죽는다.
 */
export const MAX_PICK_BYTES = 30 * 1024 * 1024;

/**
 * 실제로 서버에 보내는 바이트의 상한 — **서버와 같은 값이어야 한다** (S15P21E201-955).
 * 서버: spring.servlet.multipart.max-file-size=3MB · UploadedImage.MAX_BYTES.
 *
 * 여기서 막는 것은 서버를 대신하려는 게 아니라, 왕복을 기다린 뒤에야 이유를 알게
 * 되는 것을 없애려는 것이다. 마지막 판정은 여전히 서버가 한다.
 *
 * 🔴 보통은 줄인 뒤라 여기 걸릴 일이 없다. 걸리는 것은 **줄이기가 실패해 원본이
 * 그대로 올라가는 경우**다(S15P21E201-1121). 그때는 서버가 받을 수 있는 크기인지가
 * 문제이므로 30MB 가 아니라 이 값으로 잰다.
 */
export const MAX_UPLOAD_BYTES = 3 * 1024 * 1024;

const mb = (bytes: number) => `${Math.round(bytes / (1024 * 1024))}MB`;
/** 사람에게 보여 줄 상한 — 문구와 실제 상한이 어긋나지 않게 여기서 한 번만 만든다. */
export const MAX_PICK_LABEL = mb(MAX_PICK_BYTES);
export const MAX_UPLOAD_LABEL = mb(MAX_UPLOAD_BYTES);

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
