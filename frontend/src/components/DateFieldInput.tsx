import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { Text } from './Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

type Props = { label: string; value: string; minimumDate: string; maximumDate?: string; invalid?: boolean; onChange: (value: string) => void; onBlur?: () => void };
const parseDate = (value: string, fallback: string) => new Date(`${(/^\d{4}-\d{2}-\d{2}$/.test(value) ? value : fallback)}T12:00:00`);
const formatDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function DateFieldInput({ label, value, minimumDate, maximumDate, invalid, onChange, onBlur }: Props) {
  const { tx } = useI18n();
  const [open, setOpen] = useState(false);
  const handleChange = (event: DateTimePickerEvent, date?: Date) => {
    setOpen(false);
    if (event.type === 'set' && date) onChange(formatDate(date));
    onBlur?.();
  };
  return <View>
    <Pressable accessibilityRole="button" accessibilityLabel={tx(`${label} 달력 열기`, `Open calendar for ${label}`)} onPress={() => setOpen(true)} style={[styles.field, invalid && styles.invalid]}>
      <Text color={value ? color.text.heading : color.text.muted}>{value || tx('날짜 선택', 'Select date')}</Text><Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('달력', 'Calendar')}</Text>
    </Pressable>
    {open && <DateTimePicker value={parseDate(value, minimumDate)} mode="date" minimumDate={parseDate(minimumDate, minimumDate)} maximumDate={maximumDate ? parseDate(maximumDate, maximumDate) : undefined} onChange={handleChange} />}
  </View>;
}
const styles = StyleSheet.create({ field: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, paddingHorizontal: spacing[3], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, invalid: { borderColor: color.state.danger } });
