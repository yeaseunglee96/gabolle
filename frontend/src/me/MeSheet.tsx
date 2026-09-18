// 마이페이지 모달 껍데기 — 데스크톱은 센터 모달, 폰은 바텀시트 (S15P21E201-1238).
// 시안: docs/design_handoff_mypage/MyPage.dc.html
//
// 🔴 시안이 메뉴 행을 **라우트 이동이 아니라 모달**로 정했다. 이동으로 두면 마이페이지를
//    벗어났다가 뒤로 와야 하고, 넓은 화면에서는 옛 좌측 사이드바 배치로 떨어진다 —
//    2026-09-18 에 실제로 그렇게 보였다.
//
// 🔴 **딥링크 라우트는 남긴다.** `/me/posts` 같은 주소로 들어오는 길이 밖에 있을 수
//    있고, 지우면 그 링크가 「화면을 찾을 수 없어요」가 된다. 모달은 마이페이지 **안에서**
//    누를 때의 길이다.
import type { ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';

/** 행 오른쪽 칩의 성격. 파괴적인 것은 색으로 구분한다 — 되돌릴 수 없는 것을 같은 색으로 두지 않는다. */
export type MeSheetTone = 'plain' | 'primary' | 'danger';

export type MeSheetRow = {
  label: string;
  /** 없으면 줄을 안 그린다. 🔴 「미입력」으로 채우지 않는다. */
  caption?: string | null;
  /** 없으면 칩을 안 그린다 — 누를 것이 없는 행도 있다. */
  action?: string | null;
  tone?: MeSheetTone;
  onPress?: () => void;
};

export type MeSheetProps = {
  visible: boolean;
  title: string;
  /** 모달이 무엇을 다루는지 한 줄. 없으면 안 그린다. */
  description?: string | null;
  rows?: MeSheetRow[];
  /** 행 대신 직접 그릴 것이 있으면. */
  children?: ReactNode;
  onClose: () => void;
};

export function MeSheet({ visible, title, description, rows, children, onClose }: MeSheetProps) {
  const { tx } = useI18n();
  const { kind } = useLayout();
  const phone = kind === 'phone';

  return (
    <Modal visible={visible} transparent animationType={phone ? 'slide' : 'fade'} onRequestClose={onClose}>
      {/* 🔴 바깥을 눌러 닫되 **단추 역할을 주지 않는다.** 안쪽에도 단추가 있어서,
          단추 안에 단추가 들어가면 웹에서 잘못된 마크업이 되고 콘솔이 경고한다
          (2026-09-18 실제로 났다). 읽어 주는 이름은 안쪽 ✕ 가 갖는다. */}
      <Pressable onPress={onClose} style={[styles.backdrop, phone && styles.backdropPhone]}>
        {/* 안쪽 누름이 바깥으로 새면, 행을 누르려다 모달이 닫힌다. */}
        <View onStartShouldSetResponder={() => true} style={[styles.sheet, phone ? styles.sheetPhone : styles.sheetWide]}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{title}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={styles.close}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.body} keyboardShouldPersistTaps="handled">
            {description ? <Text color={color.text.body} style={styles.description}>{description}</Text> : null}

            {rows?.map((row) => (
              <View key={row.label} style={styles.row}>
                <View style={styles.rowCopy}>
                  <Text weight="bold" numberOfLines={1}>{row.label}</Text>
                  {row.caption ? <Text variant="caption" color={color.text.muted} numberOfLines={2}>{row.caption}</Text> : null}
                </View>
                {row.action ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`${row.label} · ${row.action}`}
                    disabled={!row.onPress}
                    onPress={row.onPress}
                    style={[
                      styles.chip,
                      row.tone === 'primary' && styles.chipPrimary,
                      row.tone === 'danger' && styles.chipDanger,
                      !row.onPress && styles.chipOff,
                    ]}
                  >
                    <Text
                      variant="caption"
                      weight="bold"
                      color={row.tone === 'primary' ? color.text.onAction : row.tone === 'danger' ? color.state.danger : color.text.heading}
                    >
                      {row.action}
                    </Text>
                  </Pressable>
                ) : null}
              </View>
            ))}

            {children}
          </ScrollView>
        </View>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(11,29,58,0.45)', alignItems: 'center', justifyContent: 'center', padding: spacing[4] },
  backdropPhone: { justifyContent: 'flex-end', padding: 0 },
  sheet: { backgroundColor: color.brand.ivory, overflow: 'hidden' },
  sheetWide: { width: '100%', maxWidth: 520, maxHeight: '88%', borderRadius: radius.lg },
  sheetPhone: { width: '100%', maxHeight: '92%', borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg },
  header: { minHeight: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[4], borderBottomWidth: 1, borderColor: color.surface.border },
  close: { position: 'absolute', right: spacing[2], width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  body: { paddingHorizontal: spacing[6], paddingTop: spacing[4], paddingBottom: spacing[8] },
  description: { marginBottom: spacing[4] },
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginBottom: spacing[2], paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  rowCopy: { flex: 1, gap: 2 },
  chip: { minHeight: 36, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.soft },
  chipPrimary: { backgroundColor: color.brand.navy },
  chipDanger: { backgroundColor: color.state.dangerBg },
  chipOff: { opacity: 0.55 },
});
