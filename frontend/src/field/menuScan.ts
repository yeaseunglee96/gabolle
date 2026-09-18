// 메뉴판 촬영 — 사진을 서버로 보내 글자를 읽는다 (스토리 -86).
import { apiRequest, ApiClientError, ApiUnavailableError } from '@/api/client';
import { singleFileFormData } from '@/api/multipart';
import type { LanguageCode } from '@/i18n/languages';
import { resizeForUpload } from '@/social/imageResize';

export type MenuLine = { text: string; translatedText: string; allergenWords: string[] };

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

/** 무엇이 왜 막혔는지 화면까지 가져간다 —과 같은 이유다. */
function describeCause(error: unknown): string {
  if (error instanceof Error && error.message) return error.message.slice(0, 120);
  if (typeof error === 'string' && error) return error.slice(0, 120);
  return '알 수 없는 오류';
}

/** 사진을 줄여서 보낸다. */
export async function scanMenu(
  uri: string,
  accessToken: string | null,
  tx: Translate,
  language: LanguageCode,
): Promise<MenuScanResult> {
  if (!accessToken) {
    return { state: 'error', message: tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.') };
  }
  try {
    // — 줄이기가 실패해도 여기서 끝내지 않는다.
    let uploadUri = uri;
    let resizeFailure: string | null = null;
    try {
      const resized = await resizeForUpload(uri);
      uploadUri = resized.uri;
    } catch (error) {
      resizeFailure = describeCause(error);
    }

    // — 기록 사진과 같은 자리에서 같은 이유로 막혀 있었다.
    // Expo SDK 57 의 새 fetch 는 RN 의 { uri, name, type } 파트를 모른다 — src/api/multipart.ts.
    // 그래서 1121 에서 고친 「줄이기 실패를 삼키기」로는 증상이 그대로였다.
    const formData = await singleFileFormData('image', { uri: uploadUri, name: 'menu.jpg', type: 'image/jpeg' });
    formData.append('language', language);
    const dto = await apiRequest<MenuScan>('/api/v1/menu-scans', { method: 'POST', accessToken, body: formData });
    return { state: 'success', scan: normalizeScan(dto) };
  } catch (error) {
    return { state: 'error', message: errorMessage(error, tx) };
  }
}

/** 서버가 보낸 것에서 계약에 있는 칸만 남긴다. */
function normalizeScan(dto: MenuScan): MenuScan {
  const lines = Array.isArray(dto?.lines) ? dto.lines : [];
  return {
    lines: lines
      .filter((line): line is MenuLine => typeof line?.text === 'string')
      .map((line) => ({
        text: line.text,
        // 서버가 이 칸을 안 주거나 비우면 원문으로 물러선다 — 화면이 빈 칸을 그리는
        // 것보다 원문이라도 보여주는 것이 낫다. GmsMenuReader.parse 와 같은 물러섬이다.
        translatedText: typeof line.translatedText === 'string' && line.translatedText !== ''
          ? line.translatedText
          : line.text,
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
    // 한도 초과를 조용한 빈 결과로 만들지 않는다. 빈 결과는 「알레르기가 없구나」로 읽힌다.
    if (error.status === 429) return tx('오늘 쓸 수 있는 횟수를 다 썼어요. 잠시 뒤에 다시 해주세요.', "You've used up today's scans. Please try again later.");
    if (error.status === 413) return tx('사진이 너무 커요. 더 작게 찍어 주세요.', 'That photo is too large. Please take a smaller one.');
    if (error.status === 415) return tx('JPEG, PNG 사진만 읽을 수 있어요.', 'Only JPEG and PNG photos can be read.');
    if (error.status === 401) return tx('로그인한 뒤에 쓸 수 있어요.', 'Please sign in to use this.');
  }
  // — 서버에 닿지도 못했으면 그 이유를 붙인다.
  if (error instanceof ApiUnavailableError && error.cause) {
    return `${error.message} (${error.cause})`;
  }
  return tx('사진을 읽지 못했어요. 다시 시도해 주세요.', 'Could not read the photo. Please try again.');
}

// ── 화면이 무슨 말을 할지 여기서 정한다 ───────────────────────────────────────

/** 못 읽은 줄이 있으면 반드시 말한다. 없으면 줄을 만들지 않는다. */
export function unreadNotice(scan: MenuScan, tx: Translate): string | null {
  if (scan.unreadLineCount <= 0) return null;
  return tx(
    `사진이 흐리거나 잘려서 못 읽은 줄이 ${scan.unreadLineCount}개 있어요.`,
    `${scan.unreadLineCount} line(s) could not be read — the photo may be blurry or cropped.`,
  );
}

/** 알레르기 안내. */
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
