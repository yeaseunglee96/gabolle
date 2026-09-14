import { createElement, type ChangeEvent } from 'react';
import { color, radius, spacing } from '@/design/tokens';

type Props = { label: string; value: string; minimumDate: string; maximumDate?: string; invalid?: boolean; onChange: (value: string) => void; onBlur?: () => void };

export function DateFieldInput({ label, value, minimumDate, maximumDate, invalid, onChange, onBlur }: Props) {
  return createElement('input', { 'aria-label': label, type: 'date', value, min: minimumDate, max: maximumDate, onChange: (event: ChangeEvent<HTMLInputElement>) => onChange(event.currentTarget.value), onBlur, style: { width: '100%', minHeight: 48, boxSizing: 'border-box', borderRadius: radius.md, border: `1px solid ${invalid ? color.state.danger : '#eae6df'}`, backgroundColor: color.surface.subtle, color: color.text.heading, padding: `0 ${spacing[3]}px`, fontSize: 15, cursor: 'pointer' } });
}
