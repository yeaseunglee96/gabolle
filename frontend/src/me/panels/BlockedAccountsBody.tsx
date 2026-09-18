// 차단된 계정 본문 — 화면(`app/me/blocked.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
import { useState } from 'react';

import { Button } from '@/components/Button';
import { useAuth } from '@/auth/AuthProvider';
import { useI18n } from '@/i18n';
import { RelationList } from '@/social/RelationListScreen';
import { loadMyBlocks, setBlocked, type RelationItem } from '@/social/stories';

function UnblockButton({ item, refresh }: { item: RelationItem; refresh: () => void }) {
  const { accessToken } = useAuth();
  const { tx } = useI18n();
  const [busy, setBusy] = useState(false);
  const unblock = async () => {
    if (busy) return;
    setBusy(true);
    await setBlocked(item.userId, false, accessToken);
    setBusy(false);
    refresh();
  };
  return <Button compact variant="ghost" label={busy ? tx('처리 중…', 'Working…') : tx('차단 해제', 'Unblock')} disabled={busy} onPress={() => void unblock()} />;
}

export function BlockedAccountsBody() {
  const { user, accessToken } = useAuth();
  const { tx } = useI18n();
  return (
    <RelationList
      emptyMessage={tx('차단한 계정이 없어요.', "You haven't blocked anyone.")}
      loader={(token, cursor) => (user ? loadMyBlocks(user.userId, token ?? accessToken, cursor) : Promise.resolve({ state: 'success' as const, items: [], nextCursor: null }))}
      renderAction={(item, refresh) => <UnblockButton item={item} refresh={refresh} />}
    />
  );
}
