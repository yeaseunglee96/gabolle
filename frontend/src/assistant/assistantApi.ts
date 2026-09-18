import { apiRequest } from '@/api/client';
import type { AssistantAction } from '@/assistant/intent';

type AssistantMessageResponseDto = {
  kind: string;
  reply: string;
  korean: string | null;
  pronunciation: string | null;
  label: string | null;
  href: string | null;
};

// 백엔드(GeminiAssistantAdapter)가 실제로 허용하는 값과 같아야 한다 —.
const ALLOWED_NAVIGATE_HREFS = ['/plan', '/trips', '/field/translate'] as const;
type NavigateHref = (typeof ALLOWED_NAVIGATE_HREFS)[number];

function isNavigateHref(value: string | null): value is NavigateHref {
  return (ALLOWED_NAVIGATE_HREFS as readonly string[]).includes(value ?? '');
}

function fromDto(dto: AssistantMessageResponseDto): AssistantAction {
  if (dto.kind === 'navigate' && isNavigateHref(dto.href) && dto.label) {
    return { kind: 'navigate', reply: dto.reply, label: dto.label, href: dto.href };
  }
  if (dto.kind === 'phrase' && dto.korean && dto.pronunciation) {
    return { kind: 'phrase', reply: dto.reply, korean: dto.korean, pronunciation: dto.pronunciation };
  }
  // 서버가 지어낼 수 없는 kind/href 를 주거나 필드가 비어 있으면 help 로 낮춘다 — 화면이
  // 모르는 모양을 만나 죽지 않게 한다. 백엔드도 같은 방향(허용 목록 밖 href → HELP)으로 막는다.
  return { kind: 'help', reply: dto.reply };
}

export type AssistantTurn = { role: 'user' | 'assistant'; text: string };

/** 자연어 메시지를 서버(Gemini 기반 AI 도우미)에 물어본다 —. */
export async function askAssistant(
  message: string,
  accessToken: string,
  history: AssistantTurn[] = [],
): Promise<AssistantAction> {
  const dto = await apiRequest<AssistantMessageResponseDto>('/api/v1/assistant/messages', {
    method: 'POST',
    accessToken,
    body: { message, history },
  });
  return fromDto(dto);
}
