// 동영상을 올릴 수 있는 크기로 줄인다 — 사진의 imageResize 와 같은 자리. **기기 전용.**
//
// 🔴 웹에서는 이 파일이 안 쓰인다(videoCompress.ts 가 대신 쓰인다). 줄이는 꾸러미가
//    네이티브 전용이라, 웹 번들에 들어가면 앱 전체가 안 뜬다 — 저장소가 지도 부품에서
//    쓰는 것과 같은 갈라치기다(RouteMap.tsx / RouteMap.native.tsx).
//
// 🔴 서버에는 영상을 다시 인코딩할 장치가 없다. 폰과 데스크톱에는 있다. 그래서 올리기
//    전에 기기에서 줄인다. 새 의존성 하나가 늘어나는 것을 감수한 결정이다.
import { Video } from 'react-native-compressor';

import { measureBytes } from '@/social/imageResize';

export {
  MAX_VIDEO_UPLOAD_BYTES,
  MAX_VIDEO_SECONDS,
  MAX_VIDEO_UPLOAD_LABEL,
  MAX_VIDEO_SECONDS_LABEL,
} from '@/social/videoLimits';

export { measureBytes };

/**
 * 기기에서 줄인다. 줄인 파일의 주소를 준다.
 *
 * 🔴 얼마나 줄어드는지는 **미리 알 수 없다.** 원본의 화질·길이·코덱에 따라 다르고,
 *    기기 성능도 탄다. 그래서 이 함수는 「작아진다」를 약속하지 않는다 — 부르는 쪽이
 *    **줄인 뒤에 크기를 다시 재서** 판단한다. 사진이 이미 그렇게 한다.
 */
export async function compressForUpload(uri: string): Promise<string> {
  return Video.compress(uri, { compressionMethod: 'auto' });
}
