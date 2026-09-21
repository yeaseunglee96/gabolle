// 웹에서는 동영상을 못 줄인다 — 이 파일이 그 사실을 담는다.
//
// 🔴 **이 파일이 없으면 웹 앱 전체가 안 뜬다.** 줄이는 꾸러미는 기기 전용이라 웹 번들에
//    들어가는 순간 「react-native 를 직접 들여왔다」로 빌드가 깨진다. 시험도 타입도
//    안 잡는다 — 브라우저에서 열어 봐야 안다.
//
// 저장소가 이미 같은 방식을 쓴다(RouteMap.tsx / RouteMap.native.tsx). 폰·태블릿은
// videoCompress.native.ts 가, 웹은 이 파일이 쓰인다.
import { measureBytes } from '@/social/imageResize';

export {
  MAX_VIDEO_UPLOAD_BYTES,
  MAX_VIDEO_SECONDS,
  MAX_VIDEO_UPLOAD_LABEL,
  MAX_VIDEO_SECONDS_LABEL,
} from '@/social/videoLimits';

export { measureBytes };

/**
 * 웹에서는 줄이지 못한다.
 *
 * 🔴 조용히 원본을 돌려주지 않는다. 그러면 안 줄인 파일이 「줄였다」로 올라가고, 서버가
 *    거절할 때까지 아무도 모른다. 여기서 실패로 알리면 부르는 쪽이 크기를 재서
 *    사람에게 이유를 말한다 — 그 길이 이미 있다.
 */
export async function compressForUpload(_uri: string): Promise<string> {
  throw new Error('웹에서는 동영상을 줄일 수 없어요');
}
