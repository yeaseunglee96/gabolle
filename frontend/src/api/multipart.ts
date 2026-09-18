import { Platform } from 'react-native';

/** 폰에 있는 사진 파일을 `Blob` 으로 바꿔서 돌려준다. */
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

  // 종류(MIME)가 비면 서버가 415 로 되돌린다. 파일에서 못 읽어 오는 경우가 있어
  // (`content://` 가 특히 그렇다) 고른 사진이 알려 준 종류로 채운다.
  return raw.type ? raw : new Blob([raw], { type: mimeType });
}

/** 파일 한 장을 담은 `FormData` 를 만든다. 위 {@link fileUriToBlob} 를 반드시 거친다. */
export async function singleFileFormData(
  field: string,
  file: { uri: string; name: string; type: string },
): Promise<FormData> {
  const formData = new FormData();
  formData.append(field, await fileUriToBlob(file.uri, file.type), file.name);
  return formData;
}
