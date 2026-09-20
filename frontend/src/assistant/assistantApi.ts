import { apiRequest } from '@/api/client';
import type { AssistantAction, AssistantNavigateHref, AssistantNavigatePath } from '@/assistant/intent';

type AssistantMessageResponseDto = {
  kind: string;
  reply: string;
  korean: string | null;
  pronunciation: string | null;
  label: string | null;
  href: string | null;
};

/**
 * 🔴 백엔드 `GeminiAssistantAdapter.ALLOWED_HREFS` 가 실제로 내주는 값과 같아야 한다.
 *
 * 여기 없는 주소가 오면 아래 `fromDto` 가 `help`(= 버튼 없는 안내문)로 낮춘다. 즉 목록이
 * 서버보다 짧으면 **버튼이 조용히 사라지고** 사용자는 갈 방법이 없다. 실제로 그랬다 —
 * 2026-09-18 운영 실측에서 다섯 중 셋('/plan/basic?days=2' · '/field/transit' ·
 * '/field/exchange-rate')이 버튼 없이 글만 남았다 (S15P21E201-1273).
 *
 * '/plan' 과 '/plan/basic' 은 **둘 다** 받는다. 서버가 어느 쪽을 보내든 이미 깔린 앱이
 * 안 깨지게 하기 위해서다 ('/plan/basic' 은 '/plan' 으로 보내는 리다이렉트 화면이다).
 */
export const ALLOWED_NAVIGATE_HREFS: readonly AssistantNavigatePath[] = [
  '/plan',
  '/plan/basic',
  '/trips',
  '/field/translate',
  '/field/transit',
  '/field/exchange-rate',
];

/**
 * 서버는 경로 뒤에 쿼리를 붙여 보낸다 — '/plan/basic?days=2'. 대조는 물음표(와 #) 앞까지만
 * 하고, 이동할 때는 쿼리가 붙은 원래 주소를 그대로 넘긴다.
 */
export function isAllowedNavigateHref(value: string | null | undefined): value is AssistantNavigateHref {
  if (!value) return false;
  const cut = value.search(/[?#]/);
  const path = cut < 0 ? value : value.slice(0, cut);
  return (ALLOWED_NAVIGATE_HREFS as readonly string[]).includes(path);
}

function fromDto(dto: AssistantMessageResponseDto): AssistantAction {
  if (dto.kind === 'navigate' && isAllowedNavigateHref(dto.href) && dto.label) {
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

/** 자연어 메시지를 서버(Gemini 기반 AI 도우미)에 물어본다 — S15P21E201-802. */
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
