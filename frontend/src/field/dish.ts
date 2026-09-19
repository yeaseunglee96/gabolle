// 음식 하나에 대한 설명과 그림 — S15P21E201-1276 (서버는 -1272).
//
// 🔴 여기서 오는 것은 메뉴판에서 «읽은» 것이 아니다. 전부 모델이 «아는» 것이거나
//    모델이 «만든» 것이다. 화면은 그 둘을 같은 무게로 그리면 안 된다.
import { Platform } from 'react-native';

import { apiRequest, ApiClientError, API_BASE_URL } from '@/api/client';
import type { LanguageCode } from '@/i18n/languages';

/** 서버가 그림이 어디까지 왔는지 말하는 값. */
export type DishImageStatus =
  | 'PENDING'
  | 'READY'
  | 'FAILED'
  /** 그릴 근거가 없다 — 모델이 모르는 음식이다. */
  | 'NONE'
  /**
   * 🔴 지금은 못 만든다 — 그 사람의 그림 한도가 찼다 (S15P21E201-1294).
   *
   * `FAILED` 와 가른 이유는 **다음에 할 일이 다르기 때문**이다. 실패는 그 음식이 원래
   * 안 되는 것일 수 있지만 이것은 잠시 뒤 다시 누르면 된다. 한 문구로 뭉개면 사용자는
   * 기다리면 될 것을 포기한다. **설명은 이 값과 함께 온다.**
   */
  | 'RATE_LIMITED';

export type Dish = {
  name: string;
  /** 🔴 모델이 모르는 음식이면 빈 문자열이다 — 「설명할 것이 없다」가 아니다. */
  description: string;
  /** 언제나 'MODEL_KNOWLEDGE'. 사진에서 읽은 값이 아니라는 뜻이다. */
  descriptionSource: string;
  imageStatus: DishImageStatus;
  imageId: string | null;
};

export type DishResult =
  | { state: 'success'; dish: Dish }
  | { state: 'error'; message: string };

type Translate = (ko: string, en: string) => string;

/**
 * 음식 하나를 물어본다. 설명은 그 자리에서 오고, 그림은 `imageStatus` 로만 알려 준다.
 *
 * @param name 메뉴판에서 읽은 이름(`MenuLine.name`). 🔴 사용자가 손으로 친 값이 아니다
 */
export async function describeDish(
  name: string,
  accessToken: string | null,
  tx: Translate,
  language: LanguageCode,
): Promise<DishResult> {
  if (!accessToken) {
    return { state: 'error', message: tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.') };
  }
  try {
    const dto = await apiRequest<Dish>('/api/v1/dishes', {
      method: 'POST',
      accessToken,
      body: { name, language },
    });
    return { state: 'success', dish: normalizeDish(dto, name) };
  } catch (error) {
    return { state: 'error', message: errorMessage(error, tx) };
  }
}

/** 서버가 보낸 것에서 계약에 있는 칸만 남긴다. */
function normalizeDish(dto: Dish, askedName: string): Dish {
  const status = dto?.imageStatus;
  return {
    name: typeof dto?.name === 'string' && dto.name !== '' ? dto.name : askedName,
    description: typeof dto?.description === 'string' ? dto.description : '',
    descriptionSource: typeof dto?.descriptionSource === 'string' ? dto.descriptionSource : 'MODEL_KNOWLEDGE',
    // 🔴 모르는 값이 오면 「없다」로 떨어뜨린다 — 「만드는 중」으로 떨어뜨리면 화면이
    //    오지 않을 그림을 영원히 기다린다.
    imageStatus: status === 'PENDING' || status === 'READY' || status === 'FAILED'
      || status === 'RATE_LIMITED' ? status : 'NONE',
    imageId: typeof dto?.imageId === 'string' ? dto.imageId : null,
  };
}

function errorMessage(error: unknown, tx: Translate): string {
  if (error instanceof ApiClientError) {
    // 🔴 그림 한도는 메뉴판 읽기 한도와 다른 것이다. 「오늘 메뉴판을 다 썼어요」로
    //    그리면 거짓이다 — 읽기는 아직 남아 있다.
    if (error.code === 'DISH_IMAGE_RATE_LIMITED') {
      return tx('그림은 조금 뒤에 다시 만들 수 있어요. 메뉴판 읽기는 그대로 쓸 수 있어요.',
        'Pictures can be made again in a moment. Menu reading still works.');
    }
    if (error.status === 401) return tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.');
  }
  return tx('이 음식 설명을 가져오지 못했어요.', 'Could not load this dish.');
}

// ── 그림 ─────────────────────────────────────────────────────────────────────

/** 그림을 받아 갈 주소. */
export function dishImageUri(imageId: string): string {
  return `${API_BASE_URL}/api/v1/dishes/images/${imageId}`;
}

export type DishImageLoad =
  /** 다 됐다. `uri` 를 `<Image source={{ uri }}>` 에 그대로 넣는다. */
  | { state: 'ready'; uri: string; revoke: (() => void) | null }
  /** 아직 만드는 중이다. 조금 뒤에 다시 물어본다. */
  | { state: 'pending' }
  /** 그만 물어봐야 한다 — 없거나 못 만들었다. */
  | { state: 'gone' };

/** 받은 바이트를 그대로 그릴 수 있는 주소로 바꾼다. */
async function asDataUri(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error('read failed'));
    reader.onload = () => resolve(String(reader.result));
    // readAsArrayBuffer 는 React Native 에 없다. readAsDataURL 은 웹·앱 양쪽 다 있다.
    reader.readAsDataURL(blob);
  });
}

/**
 * 그림이 다 됐는지 한 번 물어보고, 됐으면 그릴 수 있는 주소까지 만들어 낸다.
 *
 * 🔴 **202 와 404 를 같게 다루지 않는다.** 202 는 「아직」이고 404 는 「그만 물어봐」다.
 * 둘을 같게 보면 화면이 영원히 다시 묻거나, 10초만 더 기다리면 올 그림을 영영 안 받는다.
 *
 * <h2>🔴 주소를 건네지 않고 «바이트를» 건넨다 — S15P21E201-1335</h2>
 *
 * 예전에는 앱에서 주소를 그대로 주고 `<Image source={{ uri, headers }}>` 가 인증 헤더를
 * 붙여 다시 받아 오게 했다. **안드로이드 실기에서 그림 칸이 흰색으로만 떴다**
 * (2026-09-19, 운영 빌드 versionCode 22). 서버는 멀쩡했다 — 같은 주소를 같은 토큰으로
 * curl 하면 512×512 JPEG 가 그대로 온다. 화면에서도 그림 칸은 제 크기대로 자리를
 * 차지하고 「AI 가 그린 그림이에요」 안내까지 떴다. **비트맵만 안 왔다.**
 *
 * 그래서 **그림 칸이 직접 통신하지 않게** 한다. 여기서 이미 받은 바이트를 data URI 로
 * 바꿔 넘기면, 그리는 쪽은 네트워크도 인증도 몰라도 된다. 웹에서 이미 그렇게 하고
 * 있었고(웹의 `<img src>` 는 헤더를 못 보낸다), 앱만 다른 길로 가던 것을 없앴다.
 *
 * 🔴 **바이트를 두 번 받던 것도 함께 없어졌다.** 예전 주석은 「주소를 그대로 주면 두 번
 * 안 받는다」고 적어 둔 자리인데 사실은 반대였다 — 이 `fetch` 가 이미 몸통을 다 받아
 * 놓고 버렸고, `<Image>` 가 같은 그림을 한 번 더 받았다.
 */
export async function loadDishImage(imageId: string, accessToken: string, signal?: AbortSignal)
  : Promise<DishImageLoad> {
  const uri = dishImageUri(imageId);
  try {
    const response = await fetch(uri, {
      method: 'GET',
      signal,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (response.status === 202) return { state: 'pending' };
    if (!response.ok) return { state: 'gone' };

    const blob = await response.blob();
    if (Platform.OS === 'web') {
      const objectUrl = URL.createObjectURL(blob);
      // 🔴 다 쓰면 돌려줘야 한다. 안 돌려주면 메뉴 줄을 열 때마다 브라우저 메모리에
      //    그림이 쌓인다 — 화면을 떠나도 안 사라진다.
      return { state: 'ready', uri: objectUrl, revoke: () => URL.revokeObjectURL(objectUrl) };
    }
    // 앱은 data URI 로 그린다. 브라우저 밖이라 돌려줄 것이 없어 revoke 는 null 이다 —
    // 그 줄을 접으면 문자열도 함께 사라진다.
    return { state: 'ready', uri: await asDataUri(blob), revoke: null };
  } catch {
    // 통신이 한 번 끊긴 것과 그림이 없는 것은 다르다. 여기서 'gone' 으로 떨어뜨리면
    // 지하철에서 한 번 끊긴 사람이 다시는 그림을 못 본다.
    return { state: 'pending' };
  }
}

/** 그림을 얼마나 자주·얼마나 오래 물어볼 것인가. */
export const DISH_IMAGE_POLL = {
  /** 그림 한 장이 10.9초 걸린다(실측). 2초마다 물으면 대여섯 번 만에 온다. */
  everyMs: 2000,
  /**
   * 🔴 영원히 묻지 않는다. 46초가 걸린 적도 있어서 넉넉히 두되, 끝은 반드시 있다 —
   * 끝이 없으면 화면을 켜 둔 사람의 배터리와 통신이 계속 나간다.
   */
  giveUpAfterMs: 90000,
} as const;
