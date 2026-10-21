// 의견 보내기 — S15P21E201-2049. 서버 계약은 backend/.../feedback/presentation/FeedbackController 그대로다.
//
// 로그인 없이도 보낼 수 있다(로그인했으면 서버가 계정을 묶는다). 사진은 로그인한 사람만 — 올리는 창구가 로그인을 요구한다.
import { apiRequest, ApiClientError, ApiUnavailableError } from '@/api/client';

export type FeedbackType = 'BUG' | 'IDEA' | 'OTHER';

/** 서버 FeedbackService 의 CONTENT_MIN·CONTENT_MAX·MAX_SCREENSHOTS 와 같다. */
export const CONTENT_MIN = 10;
export const CONTENT_MAX = 1000;
export const MAX_SCREENSHOTS = 3;

export const FEEDBACK_TYPES: ReadonlyArray<{ code: FeedbackType; ko: string; en: string }> = [
  { code: 'BUG', ko: '버그', en: 'Bug' },
  { code: 'IDEA', ko: '건의', en: 'Suggestion' },
  { code: 'OTHER', ko: '기타', en: 'Other' },
];

export type FeedbackDraft = { type: FeedbackType; content: string; replyEmail: string; screenshotUrls: string[] };

/** 「함께 보내는 정보」 — 화면에 그대로 보여 주고 그대로 보낸다. */
export type FeedbackMeta = { appVersion: string | null; platform: string; osVersion: string | null; language: string; screen: string | null; tripId: string | null };

export type FeedbackSubmitResult =
  | { state: 'success'; feedbackId: string }
  | { state: 'invalid' }
  | { state: 'limited' }
  | { state: 'photo' }
  | { state: 'error'; message: string };

const EMAIL = /^[^@\s]{1,64}@[^@\s]+\.[^@\s]{2,}$/;

export function contentLength(content: string): number {
  return [...content.trim()].length;
}

/** 보내기 전에 화면이 막을 것 — 서버도 같은 것을 다시 본다. 비어 있으면 보낼 수 있다. */
export function draftProblems(draft: FeedbackDraft): Array<'content' | 'replyEmail' | 'screenshotUrls'> {
  const problems: Array<'content' | 'replyEmail' | 'screenshotUrls'> = [];
  const length = contentLength(draft.content);
  if (length < CONTENT_MIN || length > CONTENT_MAX) problems.push('content');
  const email = draft.replyEmail.trim();
  if (email && !EMAIL.test(email)) problems.push('replyEmail');
  if (draft.screenshotUrls.length > MAX_SCREENSHOTS) problems.push('screenshotUrls');
  return problems;
}

export async function submitFeedback(draft: FeedbackDraft, meta: FeedbackMeta, accessToken: string | null): Promise<FeedbackSubmitResult> {
  try {
    const saved = await apiRequest<{ feedbackId: string }>('/api/v1/feedback', {
      method: 'POST',
      accessToken,
      body: {
        type: draft.type,
        content: draft.content.trim(),
        replyEmail: draft.replyEmail.trim() || null,
        screenshotUrls: draft.screenshotUrls,
        ...meta,
      },
    });
    return { state: 'success', feedbackId: saved.feedbackId };
  } catch (error) {
    if (error instanceof ApiClientError && !(error instanceof ApiUnavailableError)) {
      switch (error.code) {
        case 'FEEDBACK_RATE_LIMITED': return { state: 'limited' };
        case 'FEEDBACK_SCREENSHOT_INVALID': return { state: 'photo' };
        case 'FEEDBACK_INVALID': return { state: 'invalid' };
        default: break;
      }
    }
    return { state: 'error', message: error instanceof Error ? error.message : '' };
  }
}
