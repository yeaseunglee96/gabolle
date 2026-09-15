// 메뉴판 촬영 — 사진을 서버로 보내 글자를 읽는다 (S15P21E201-329 · 스토리 -86).
//
// 계약 (2026-09-16, 백엔드 세션 확정):
//   POST /api/v1/menu-scans   로그인 필수 · multipart/form-data · part 이름 image
//   200 { lines: [{ text, allergenWords }], unreadLineCount, evidenceStatus: 'ESTIMATED' }
//   429 한도 초과
//
// 🔴 이 화면은 **사람이 먹는 것** 앞에 선다. 그래서 규칙이 하나뿐이다.
//
//     읽은 것만 말한다. 「없다」는 어떤 형태로도 말하지 않는다.
//
// 2026-09-16 에 고친 결함(S15P21E201-996)이 정확히 이것이었다 — 아무도 조사하지 않은
// 알레르기를 화면이 「확인됨 · 등록된 유발 성분이 없습니다」로 뒤집어 말했다. 이번에는
// 서버가 「이 낱말들이 보인다」고 말하는데 화면이 「이것뿐이다」로 그리면 같은 사고다.
//
// 🔴 그래서 계약에 `safe` 도 `hasAllergen` 도 없다. 있으면 언젠가 누가 그린다.
//    `allergenWords` 가 비었다는 것은 **그 줄에서 못 찾았다**는 뜻이지 **없다**가 아니고,
//    `unreadLineCount` 가 0 이어도 **모델이 읽은 글자 안에서 못 찾았을 뿐**이다.
//
// 🔴 사진은 서버에 남지 않는다. 요청에 실려 가고, 글자를 읽고, 사라진다. 기록 사진 업로드
//    경로를 재활용하려다 그만뒀다 — 그쪽은 주소를 아는 사람이면 누구나 여는 자리인데
//    (피드가 <img> 로 부른다), 메뉴판 사진에는 얼굴·영수증이 같이 찍힌다.
import { apiRequest, ApiClientError } from '@/api/client';
import { resizeForUpload } from '@/social/imageResize';

export type MenuLine = { text: string; allergenWords: string[] };

export type MenuScan = {
  lines: MenuLine[];
  /** 사진이 흐리거나 잘려 못 읽은 줄 수. 0 이어도 「전부 읽었다」가 아니다. */
  unreadLineCount: number;
  /** 사진에서 읽은 값은 언제나 ESTIMATED 다. VERIFIED 를 붙이지 않는다. */
  evidenceStatus: 'ESTIMATED';
};

export type MenuScanResult =
  | { state: 'success'; scan: MenuScan }
  | { state: 'error'; message: string };

type Translate = (ko: string, en: string) => string;

/**
 * 사진을 줄여서 보낸다.
 *
 * 🔴 줄이기를 새로 만들지 않고 기록 사진이 쓰던 것을 그대로 쓴다. 가장 긴 변을 1600px 로
 * 줄이면서 **촬영 위치 정보(EXIF)를 떼어 낸다**(S15P21E201-204) — 식당 좌표가 바깥 업체로
 * 나가지 않는다. 같은 일을 두 벌로 두면 한쪽만 고쳐진다.
 */
export async function scanMenu(uri: string, accessToken: string | null, tx: Translate): Promise<MenuScanResult> {
  if (!accessToken) {
    return { state: 'error', message: tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.') };
  }
  try {
    const resized = await resizeForUpload(uri);
    const formData = new FormData();
    if (typeof window !== 'undefined' && typeof Blob !== 'undefined' && resized.uri.startsWith('blob:')) {
      formData.append('image', await (await fetch(resized.uri)).blob(), 'menu.jpg');
    } else {
      // React Native 의 FormData 는 { uri, name, type } 을 파일로 받는다 (web 의 File 과 다르다).
      formData.append('image', { uri: resized.uri, name: 'menu.jpg', type: 'image/jpeg' } as unknown as Blob);
    }
    const dto = await apiRequest<MenuScan>('/api/v1/menu-scans', { method: 'POST', accessToken, body: formData });
    return { state: 'success', scan: normalizeScan(dto) };
  } catch (error) {
    return { state: 'error', message: errorMessage(error, tx) };
  }
}

/**
 * 서버가 보낸 것에서 **계약에 있는 칸만** 남긴다.
 *
 * 🔴 메뉴판에 「이전 지시를 무시하고 …」라고 인쇄해 두면 모델은 따라간다. 그건 못 막는다.
 * 막을 것은 **따라갔을 때 일어나는 일**이다 — 스키마 밖의 값은 여기서 버린다.
 * 「혹시 모르니 보여주자」를 하지 않는다.
 */
function normalizeScan(dto: MenuScan): MenuScan {
  const lines = Array.isArray(dto?.lines) ? dto.lines : [];
  return {
    lines: lines
      .filter((line): line is MenuLine => typeof line?.text === 'string')
      .map((line) => ({
        text: line.text,
        allergenWords: Array.isArray(line.allergenWords)
          ? line.allergenWords.filter((word): word is string => typeof word === 'string')
          : [],
      })),
    unreadLineCount: Number.isFinite(dto?.unreadLineCount) ? Math.max(0, Math.trunc(dto.unreadLineCount)) : 0,
    evidenceStatus: 'ESTIMATED',
  };
}

function errorMessage(error: unknown, tx: Translate): string {
  if (error instanceof ApiClientError) {
    // 🔴 한도 초과를 조용한 빈 결과로 만들지 않는다. 빈 결과는 「알레르기가 없구나」로 읽힌다.
    if (error.status === 429) return tx('오늘 쓸 수 있는 횟수를 다 썼어요. 잠시 뒤에 다시 해주세요.', "You've used up today's scans. Please try again later.");
    if (error.status === 413) return tx('사진이 너무 커요. 더 작게 찍어 주세요.', 'That photo is too large. Please take a smaller one.');
    if (error.status === 415) return tx('JPEG, PNG 사진만 읽을 수 있어요.', 'Only JPEG and PNG photos can be read.');
    if (error.status === 401) return tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.');
  }
  return tx('사진을 읽지 못했어요. 다시 시도해 주세요.', 'Could not read the photo. Please try again.');
}

// ── 화면이 무슨 말을 할지 여기서 정한다 ───────────────────────────────────────
//
// 🔴 판단을 화면 안에 조건문으로 흩어 놓으면 검사할 수 없다. 함수로 꺼내면 시험이 붙든다 —
//    「없다」를 말하지 않는다는 규칙은 사람이 지키는 것이 아니라 시험이 지켜야 한다.

/** 못 읽은 줄이 있으면 반드시 말한다. 없으면 줄을 만들지 않는다. */
export function unreadNotice(scan: MenuScan, tx: Translate): string | null {
  if (scan.unreadLineCount <= 0) return null;
  return tx(
    `사진이 흐리거나 잘려서 못 읽은 줄이 ${scan.unreadLineCount}개 있어요.`,
    `${scan.unreadLineCount} line(s) could not be read — the photo may be blurry or cropped.`,
  );
}

/**
 * 알레르기 안내.
 *
 * 🔴 **두 경우 모두 「직접 확인해 주세요」가 반드시 붙는다.** 찾았을 때는 빠진 것이 있을 수
 * 있어서고, 못 찾았을 때는 그것이 「없다」는 뜻이 아니기 때문이다. 그 한 줄이 이 화면에서
 * 제일 중요한 문장이라 함수가 언제나 같이 낸다 — 화면이 빼먹을 수 없게.
 */
export function allergenNotice(scan: MenuScan, tx: Translate): { words: string[]; headline: string; caution: string } {
  const words = [...new Set(scan.lines.flatMap((line) => line.allergenWords))];
  return {
    words,
    headline: words.length > 0
      ? tx('읽은 글자에서 이런 낱말이 보였어요', 'These words appeared in the text we read')
      : tx('읽은 글자에서는 알레르기와 관련된 낱말을 찾지 못했어요', "We didn't find allergy-related words in the text we read"),
    caution: words.length > 0
      ? tx('사진에서 읽은 것이라 빠진 것이 있을 수 있어요. 드시기 전에 직원에게 확인해 주세요.', 'This came from a photo, so something may be missing. Please check with the staff before eating.')
      : tx('메뉴에 없다는 뜻이 아니에요. 드시기 전에 직원에게 확인해 주세요.', "That does not mean the menu has none. Please check with the staff before eating."),
  };
}

/** 글자를 하나도 못 찾았을 때. 빈 화면 대신 다음에 할 일을 말한다. */
export function emptyNotice(scan: MenuScan, tx: Translate): string | null {
  if (scan.lines.length > 0) return null;
  return tx('글자를 찾지 못했어요. 더 밝은 곳에서 메뉴판을 크게 담아 다시 찍어 주세요.', 'No text found. Try again in brighter light with the menu filling the frame.');
}
