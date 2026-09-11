import { Image } from 'react-native';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

// S15P21E201-204 — 가장 긴 변을 1600px로 줄이고 원본 촬영 위치 정보(EXIF)를 뺀다.
// 다시 인코딩하는 것 자체가 EXIF를 떨어뜨린다 — 그래서 이미 1600px 이하인 사진도
// 한 번은 다시 저장한다(치수는 그대로 두고 위치 정보만 제거).
const MAX_DIMENSION = 1600;

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
