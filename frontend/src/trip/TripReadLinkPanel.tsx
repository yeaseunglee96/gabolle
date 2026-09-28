// 읽기 전용 링크로 공유 — 동행 초대 창 맨 아래에 있던 것을 떼어 왔다(S15P21E201-1593).
// 여행 화면 머리의 「공유」가 이것만 담은 창을 연다. 동행 초대 창도 그대로 이것을 쓴다 — 같은 일을 두 벌로 두지 않는다.
// 새 API 는 없다 — 전과 같은 issueShareLink(POST /trips/{id}/share-links) 한 번이다.
import { useState } from 'react';
import { Share as NativeShare, StyleSheet, View } from 'react-native';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDateTime } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { issueShareLink, type ShareLinkIssued } from '@/share/sharedItinerary';

export function TripReadLinkPanel({ tripId }: { tripId: string }) {
  const { tx, locale } = useI18n();
  const { accessToken } = useAuth();
  const [readLink, setReadLink] = useState<ShareLinkIssued | null>(null);
  const [issuingReadLink, setIssuingReadLink] = useState(false);
  const [readLinkError, setReadLinkError] = useState('');

  async function createReadLink() {
    if (!accessToken || issuingReadLink) return;
    setIssuingReadLink(true);
    setReadLinkError('');
    try {
      setReadLink(await issueShareLink(tripId, accessToken));
    } catch (cause) {
      setReadLinkError(cause instanceof ApiClientError ? cause.message : tx('공유 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not create the share link. Please try again shortly.'));
    } finally {
      setIssuingReadLink(false);
    }
  }

  return <View>
    <View style={styles.heading}><Eyebrow>{tx('구경만 시키기', 'Just show it off')}</Eyebrow><Text variant="title" weight="bold">{tx('읽기 전용 링크로 공유해요', 'Share a read-only link')}</Text><Text color={color.text.body}>{tx('가볼래 계정이 없어도 볼 수 있어요. 날짜별 일정만 보이고 출발지·연락처·예산·인원은 공유되지 않아요. 30일 뒤 만료돼요.', "Viewable without a GABOLLE account. Only the day-by-day itinerary is shown — starting point, contact info, budget, and party size aren't shared. Expires in 30 days.")}</Text></View>
    <Button label={issuingReadLink ? tx('링크 만드는 중…', 'Creating link…') : tx('읽기 전용 링크 만들기', 'Create read-only link')} variant="tertiary" disabled={issuingReadLink} onPress={() => void createReadLink()} />
    {/* 빈 글자('')를 && 로 이으면 웹에서 「View 안의 텍스트」 오류가 난다 — 삼항으로 null 을 준다. */}
    {readLinkError ? <View accessibilityRole="alert" style={styles.errorCard}><Text weight="bold" color={color.state.danger}>{tx('링크를 만들지 못했습니다', 'Could not create the link')}</Text><Text color={color.text.body}>{readLinkError}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void createReadLink()} /></View> : null}
    {readLink && <View accessibilityLiveRegion="polite" style={styles.successCard}><Text weight="bold" color={color.state.success}>{tx('읽기 전용 링크를 만들었어요', 'Read-only link created')}</Text><Text selectable color={color.text.body}>{readLink.shareUrl}</Text><Text variant="caption" color={color.text.muted}>{txf(tx, '만료: %s', 'Expires: %s', formatDateTime(readLink.expiresAt, locale))}</Text><Button label={tx('공유 창 열기', 'Open share sheet')} variant="tertiary" onPress={() => void NativeShare.share({ message: readLink.shareUrl, url: readLink.shareUrl })} /></View>}
  </View>;
}

const styles = StyleSheet.create({
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] },
  errorCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  successCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
});
