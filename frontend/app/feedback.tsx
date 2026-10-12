// 「의견 보내기」 — 버그·건의·기타를 한 화면에서 받는다(S15P21E201-2049). 들어오는 길은 마이페이지 하나다.
//
// 로그인 없이도 보낼 수 있다 — 로그인이 고장 난 사람이 가장 알리고 싶어 한다. 사진만 로그인한 사람에게 연다
// (올리는 창구가 로그인을 요구한다). 「함께 보내는 정보」는 보내는 값 그대로 보여 준다 — 무엇이 가는지 숨기지 않는다.
import { useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import Constants from 'expo-constants';
import * as ImagePicker from 'expo-image-picker';

import { useAuth } from '@/auth/AuthProvider';
import { AppImage } from '@/components/AppImage';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  CONTENT_MAX, CONTENT_MIN, contentLength, draftProblems, FEEDBACK_TYPES, MAX_SCREENSHOTS, submitFeedback,
  type FeedbackMeta, type FeedbackType,
} from '@/features/feedback/feedbackApi';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { useLayout } from '@/layout/useLayout';
import { resizeForUpload } from '@/social/imageResize';
import { resolveStoryImageUrl, uploadStoryImage } from '@/social/stories';

type Tx = (ko: string, en: string) => string;

function asType(value: unknown): FeedbackType {
  return value === 'IDEA' || value === 'OTHER' ? value : 'BUG';
}

export default function FeedbackScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ type?: string; screen?: string; tripId?: string }>();
  const { tx, language } = useI18n();
  const { accessToken, user } = useAuth();
  const { desktop } = useLayout();

  const [type, setType] = useState<FeedbackType>(asType(params.type));
  const [content, setContent] = useState('');
  const [replyEmail, setReplyEmail] = useState(user?.email ?? '');
  const [shots, setShots] = useState<string[]>([]);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const meta: FeedbackMeta = useMemo(() => ({
    appVersion: Constants.expoConfig?.version ?? null,
    platform: Platform.OS,
    osVersion: Platform.OS === 'web' ? (typeof navigator !== 'undefined' ? navigator.userAgent.slice(0, 60) : null) : String(Platform.Version),
    language,
    screen: typeof params.screen === 'string' ? params.screen : 'me',
    tripId: typeof params.tripId === 'string' ? params.tripId : null,
  }), [language, params.screen, params.tripId]);

  const draft = { type, content, replyEmail, screenshotUrls: shots };
  const problems = draftProblems(draft);
  const canSend = problems.length === 0 && !sending && !photoBusy;
  const length = contentLength(content);

  async function pickPhoto() {
    if (!accessToken || photoBusy || shots.length >= MAX_SCREENSHOTS) return;
    setPhotoBusy(true);
    setMessage(null);
    try {
      const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
      if (picked.canceled) return;
      const asset = picked.assets[0];
      const small = await resizeForUpload(asset.uri).catch(() => null);
      const result = await uploadStoryImage(small ? { uri: small.uri, fileName: 'feedback.jpg', mimeType: 'image/jpeg' } : { uri: asset.uri, fileName: asset.fileName, mimeType: asset.mimeType }, accessToken);
      if (result.state === 'success') setShots((current) => [...current, result.imageUrl].slice(0, MAX_SCREENSHOTS));
      else setMessage(result.message);
    } catch {
      setMessage(tx('사진을 올리지 못했어요.', 'Could not upload the photo.'));
    } finally {
      setPhotoBusy(false);
    }
  }

  async function send() {
    if (!canSend) return;
    setSending(true);
    setMessage(null);
    const result = await submitFeedback(draft, meta, accessToken);
    setSending(false);
    if (result.state === 'success') { setSent(true); return; }
    if (result.state === 'limited') setMessage(tx('오늘은 더 보낼 수 없어요. 하루 5건까지예요.', 'You can send up to 5 a day. Please try again tomorrow.'));
    else if (result.state === 'photo') setMessage(tx('사진이 올라가지 않았어요. 다시 골라 주세요.', 'The photo was not uploaded. Please choose it again.'));
    else setMessage(tx('보내지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not send. Please try again shortly.'));
  }

  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/me'));

  if (sent) {
    return (
      <Screen scroll wide={desktop}>
        <Header tx={tx} onBack={goBack} />
        <View style={styles.card}>
          <Text variant="title" weight="bold">{tx('의견을 받았어요', 'Feedback received')}</Text>
          <Text color={color.text.body}>{tx('고맙습니다. 확인한 뒤 처리할게요. 답장 이메일을 적었거나 로그인했다면 처리 결과를 알려 드려요.', 'Thank you. We will review it. If you left an email or are signed in, we will let you know the result.')}</Text>
          <Button label={tx('마이페이지로', 'Back to My page')} variant="tertiary" onPress={goBack} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll wide={desktop}>
      <Header tx={tx} onBack={goBack} />
      <View style={[styles.form, desktop && styles.formWide]}>
        <Field label={tx('종류', 'Type')}>
          <View style={styles.chips}>
            {FEEDBACK_TYPES.map((t) => {
              const on = t.code === type;
              return (
                <Pressable key={t.code} accessibilityRole="button" accessibilityState={{ selected: on }} onPress={() => setType(t.code)} style={[styles.chip, on && styles.chipOn]}>
                  <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading}>{tx(t.ko, t.en)}</Text>
                </Pressable>
              );
            })}
          </View>
        </Field>

        <Field label={tx('내용', 'Details')}>
          <TextInput
            value={content}
            onChangeText={setContent}
            maxLength={CONTENT_MAX + 50}
            multiline
            textAlignVertical="top"
            placeholder={type === 'BUG' ? tx('어느 화면에서 무엇을 했을 때 어떻게 됐는지 적어 주세요.', 'Tell us which screen, what you did, and what happened.') : tx('바라는 점을 자유롭게 적어 주세요.', 'Tell us what you would like.')}
            placeholderTextColor={color.text.muted}
            style={[styles.input, styles.textarea]}
            accessibilityLabel={tx('내용', 'Details')}
          />
          <Text variant="caption" color={length > CONTENT_MAX ? color.state.danger : color.text.muted} style={styles.counter}>{`${length}/${CONTENT_MAX}`}</Text>
          {content.trim().length > 0 && length < CONTENT_MIN ? <Text variant="caption" color={color.state.danger}>{txf(tx, '%s자 이상 %s자 이하로 적어 주세요.', 'Please write %s–%s characters.', String(CONTENT_MIN), String(CONTENT_MAX))}</Text> : null}
        </Field>

        <Field label={tx('화면 사진 (선택)', 'Screenshots (optional)')} hint={user ? txf(tx, '%s장까지 붙일 수 있어요.', 'Up to %s images.', String(MAX_SCREENSHOTS)) : tx('사진은 로그인하면 붙일 수 있어요.', 'Sign in to attach screenshots.')}>
          {shots.length > 0 ? (
            <View style={styles.photoRow}>
              {shots.map((url) => (
                <Pressable key={url} accessibilityRole="button" accessibilityLabel={tx('사진 빼기', 'Remove photo')} onPress={() => setShots((current) => current.filter((u) => u !== url))}>
                  <AppImage source={{ uri: resolveStoryImageUrl(url) }} style={styles.photo} />
                  <Text variant="caption" color={color.text.muted} style={styles.center}>{tx('빼기', 'Remove')}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}
          {user && shots.length < MAX_SCREENSHOTS ? (
            <Button label={photoBusy ? tx('올리는 중…', 'Uploading…') : tx('사진 고르기', 'Choose a photo')} variant="tertiary" compact onPress={() => void pickPhoto()} disabled={photoBusy} />
          ) : null}
        </Field>

        <Field label={tx('답장 받을 이메일 (선택)', 'Reply email (optional)')}>
          <TextInput value={replyEmail} onChangeText={setReplyEmail} maxLength={254} keyboardType="email-address" autoCapitalize="none" autoCorrect={false} placeholder="name@example.com" placeholderTextColor={color.text.muted} style={styles.input} accessibilityLabel={tx('답장 받을 이메일 (선택)', 'Reply email (optional)')} />
          {problems.includes('replyEmail') ? <Text variant="caption" color={color.state.danger}>{tx('이메일 주소를 확인해 주세요.', 'Please check the email address.')}</Text> : null}
        </Field>

        <View style={styles.meta}>
          <Text weight="bold">{tx('함께 보내는 정보', 'Sent along with it')}</Text>
          <MetaLine label={tx('앱 버전', 'App version')} value={meta.appVersion} />
          <MetaLine label={tx('기기·OS', 'Device / OS')} value={[meta.platform, meta.osVersion].filter(Boolean).join(' · ')} />
          <MetaLine label={tx('언어', 'Language')} value={meta.language} />
          <MetaLine label={tx('지금 화면', 'Current screen')} value={meta.screen} />
          {meta.tripId ? <MetaLine label={tx('일정 번호', 'Trip ID')} value={meta.tripId} /> : null}
          <Text variant="caption" color={color.text.muted}>{user ? tx('로그인한 계정도 함께 연결돼요.', 'Your signed-in account is linked too.') : tx('로그인하지 않아도 보낼 수 있어요.', 'You can send this without signing in.')}</Text>
        </View>

        {message ? <Text accessibilityRole="alert" color={color.state.danger}>{message}</Text> : null}
        <Button label={sending ? tx('보내는 중…', 'Sending…') : tx('보내기', 'Send')} onPress={() => void send()} disabled={!canSend} />
        <Text variant="caption" color={color.text.muted}>{tx('보낸 내용은 문의 처리와 오류 수정에만 써요.', 'We use this only to handle your request and fix problems.')}</Text>
      </View>
    </Screen>
  );
}

function Header({ tx, onBack }: { tx: Tx; onBack: () => void }) {
  return (
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={onBack} style={styles.back}>
        <Text variant="title" weight="bold">‹</Text>
      </Pressable>
      <View style={styles.flex}>
        <Text variant="display" weight="bold" color={color.text.heading}>{tx('의견 보내기', 'Send feedback')}</Text>
        <Text color={color.text.body}>{tx('버그나 바라는 점을 알려 주세요.', 'Tell us about a bug or an idea.')}</Text>
      </View>
    </View>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <View style={styles.field}>
      <Text weight="bold">{label}</Text>
      {hint ? <Text variant="caption" color={color.text.body}>{hint}</Text> : null}
      {children}
    </View>
  );
}

function MetaLine({ label, value }: { label: string; value: string | null }) {
  return (
    <View style={styles.metaLine}>
      <Text variant="caption" color={color.text.muted} style={styles.metaLabel}>{label}</Text>
      <Text variant="caption" color={color.text.body} style={styles.flex} numberOfLines={1}>{value || '-'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], marginBottom: spacing[4] },
  back: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  flex: { flex: 1, minWidth: 0 },
  form: { gap: spacing[4] },
  formWide: { maxWidth: 640 },
  field: { gap: spacing[2] },
  input: { minHeight: 48, borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, paddingHorizontal: spacing[3], color: color.text.heading, backgroundColor: color.surface.card },
  textarea: { minHeight: 160, paddingVertical: spacing[3] },
  counter: { alignSelf: 'flex-end' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  chipOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  photoRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] },
  photo: { width: 88, height: 88, borderRadius: radius.md },
  center: { textAlign: 'center' },
  meta: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  metaLine: { flexDirection: 'row', gap: spacing[2] },
  metaLabel: { width: 96 },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
});
