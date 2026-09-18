import { Platform } from 'react-native';

/**
 * 폰에 있는 사진 파일을 **`Blob` 으로 바꿔서** 돌려준다.
 *
 * 🔴 S15P21E201-1187 — 이 파일이 생긴 이유. **Expo SDK 57 부터 `fetch` 가 바뀌었다.**
 *
 * React Native 에는 오래된 관례가 하나 있다 — 파일을 보낼 때 `FormData` 에
 * `{ uri, name, type }` 이라는 **객체**를 넣으면, 네트워크 계층이 그 `uri` 를 읽어
 * 파일 내용을 대신 채워 넣어 준다. 웹 표준에는 없는, RN 만의 편의다.
 *
 * SDK 57 의 `expo` 는 앱이 뜰 때 **전역 `fetch` 를 자기 것으로 갈아끼운다**
 * (`expo/src/winter/runtime.native.ts`). 그 `fetch` 는 표준(WinterCG)을 따르므로
 * 위의 RN 관례를 **모른다.** 멀티파트를 만들다 그 객체를 만나면 그 자리에서 던진다:
 *
 * > `Unsupported FormDataPart implementation`
 *
 * 이게 2026-09-17 안드로이드 실기기에서 **사진 업로드가 100% 실패**한 진짜 원인이다.
 * `fetch` 가 요청을 만들기도 전에 던지므로 **요청이 아예 안 나간다** — 그래서 nginx
 * 기록에도, 서버 로그에도 한 줄이 없었다. 「서버에 연결할 수 없어요」는 앱이 그 예외를
 * 네트워크 실패로 잘못 읽은 것이었지, 서버는 내내 멀쩡했다.
 *
 * 고치는 방법은 **보내기 전에 우리가 파일을 읽어 `Blob` 으로 만드는 것**이다.
 * `Blob` 은 표준이라 새 `fetch` 가 그대로 받는다
 * (`expo/src/winter/fetch/convertFormData.ts` 의 `entry instanceof Blob` 가지).
 *
 * ## 왜 `XMLHttpRequest` 로 읽나
 *
 * 전역 `fetch` 는 위에서 말한 대로 갈아끼워졌고, 그 `fetch` 는 `file://` 을 못 연다.
 * 반면 RN 의 `XMLHttpRequest` 는 갈아끼워지지 않았고 `file://` · `content://` 를
 * 예전처럼 읽는다. 새 라이브러리(`expo-file-system`)를 들이면 네이티브 모듈이 늘어
 * 빌드가 달라지는데, 읽기만 하면 되는 일에 그럴 이유가 없다.
 */
export async function fileUriToBlob(uri: string, mimeType: string): Promise<Blob> {
  // 웹에서는 `fetch` 가 갈아끼워지지 않았고 blob:·data: URL 을 그대로 읽는다.
  if (Platform.OS === 'web') {
    const blob = await (await fetch(uri)).blob();
    return blob.type ? blob : new Blob([blob], { type: mimeType });
  }

  const raw = await new Promise<Blob>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.responseType = 'blob';
    xhr.onload = () => {
      const body = xhr.response as Blob | null;
      if (body) resolve(body);
      else reject(new Error(`사진 파일이 비어 있어요 (${uri.slice(0, 40)})`));
    };
    xhr.onerror = () => reject(new Error(`사진 파일을 읽지 못했어요 (${uri.slice(0, 40)})`));
    xhr.onabort = () => reject(new Error('사진 파일 읽기가 중단됐어요'));
    xhr.open('GET', uri, true);
    xhr.send(null);
  });

  // 🔴 종류(MIME)가 비면 서버가 415 로 되돌린다. 파일에서 못 읽어 오는 경우가 있어
  //    (`content://` 가 특히 그렇다) 고른 사진이 알려 준 종류로 채운다.
  return raw.type ? raw : new Blob([raw], { type: mimeType });
}

/**
 * 파일 한 장을 담은 `FormData` 를 만든다. 위 {@link fileUriToBlob} 를 반드시 거친다.
 *
 * 🔴 이 함수를 두는 이유는 **`{ uri, name, type }` 을 직접 `append` 하는 자리를 없애려는 것**이다.
 * 그 한 줄은 예전에는 맞았고 지금은 앱을 조용히 망가뜨린다 — 타입 검사도 시험도 안 잡는다
 * (`as unknown as Blob` 로 타입을 속여야만 쓸 수 있는 형태였다).
 */
export async function singleFileFormData(
  field: string,
  file: { uri: string; name: string; type: string },
): Promise<FormData> {
  const formData = new FormData();
  formData.append(field, await fileUriToBlob(file.uri, file.type), file.name);
  return formData;
}
