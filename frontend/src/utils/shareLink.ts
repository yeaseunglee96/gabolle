// 공유 창 하나 — 앱은 OS 공유 시트, 웹은 브라우저 공유(navigator.share)나 링크 복사.
import { Platform, Share } from 'react-native';

/**
 * 공유가 어떻게 끝났나.
 * - shared: 공유 창이 열렸고 보냈다(앱은 닫았는지 모르는 판도 있어 여기로 센다)
 * - dismissed: 사용자가 공유 창을 닫았다 — 실패가 아니다, 아무 안내도 하지 않는다
 * - copied: 이 브라우저는 공유를 못 해 링크를 클립보드에 복사했다 — 「복사했어요」를 보인다
 * - failed: 공유도 복사도 안 됐다
 */
export type ShareOutcome = 'shared' | 'dismissed' | 'copied' | 'failed';

export type ShareContent = { title?: string; message: string; url?: string };

/**
 * 🔴 웹에서 Share.share 를 그대로 부르면 안 된다(S15P21E201-1958). react-native-web 은 navigator.share 를 부르는데,
 *    사용자가 공유 창을 닫으면 AbortError 로, navigator.share 가 없는 브라우저(Firefox, 일부 데스크톱)는
 *    「not supported」로 거절한다. 앱은 닫아도 거절하지 않는다 — 그래서 웹에서만 「초대 링크를 만들지 못했습니다」가
 *    성공 카드와 같이 떴다. 앱 동작은 예전 그대로 둔다(던지면 그대로 던진다).
 */
export async function shareLink(content: ShareContent): Promise<ShareOutcome> {
  if (Platform.OS !== 'web') {
    const result = await Share.share({ title: content.title, message: content.message, url: content.url });
    return result.action === Share.dismissedAction ? 'dismissed' : 'shared';
  }
  const nav = (globalThis as { navigator?: Navigator }).navigator;
  if (nav && typeof nav.share === 'function') {
    try {
      await nav.share({ title: content.title, text: content.message, url: content.url });
      return 'shared';
    } catch (cause) {
      if ((cause as { name?: string } | null)?.name === 'AbortError') return 'dismissed';
      // NotAllowedError 등(사용자 동작 없이 불림·권한) — 복사로 물러선다.
    }
  }
  const text = content.url ?? content.message;
  try {
    if (!nav?.clipboard?.writeText) return 'failed';
    await nav.clipboard.writeText(text);
    return 'copied';
  } catch {
    return 'failed';
  }
}
