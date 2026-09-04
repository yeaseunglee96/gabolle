import { Component, type ErrorInfo, type ReactNode } from 'react';
import { router } from 'expo-router';
import { Platform, StyleSheet, View } from 'react-native';

import { color, gutter, radius, spacing } from '@/design/tokens';
import { Button } from './Button';
import { Text } from './Text';

type Props = { children: ReactNode };
type State = { error: Error | null };

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (__DEV__) console.error('[AppErrorBoundary]', error, info.componentStack);
  }

  private retry = () => {
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      window.location.reload();
      return;
    }
    this.setState({ error: null });
  };

  private goHome = () => {
    this.setState({ error: null }, () => router.replace('/'));
  };

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <View style={styles.screen} accessibilityRole="alert">
        <View style={styles.mark} accessibilityElementsHidden>
          <Text variant="display" weight="bold" color={color.brand.orange}>!</Text>
        </View>
        <Text variant="display" weight="bold" color={color.text.heading}>화면을 불러오지 못했어요</Text>
        <Text variant="body" color={color.text.body} style={styles.description}>
          잠시 문제가 생겼어요. 입력한 내용은 가능한 한 유지되며, 다시 시도해도 해결되지 않으면 홈으로 돌아가 주세요.
        </Text>
        <View style={styles.actions}>
          <Button label="다시 시도" onPress={this.retry} />
          <Button label="홈으로 돌아가기" variant="ghost" onPress={this.goHome} />
        </View>
        {__DEV__ ? (
          <View style={styles.debugBox}>
            <Text variant="caption" color={color.state.danger}>{this.state.error.message}</Text>
          </View>
        ) : null}
      </View>
    );
  }
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    minHeight: '100%',
    paddingHorizontal: gutter,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: color.brand.ivory,
  },
  mark: {
    width: 64,
    height: 64,
    marginBottom: spacing[6],
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: color.state.dangerBg,
  },
  description: { maxWidth: 420, marginTop: spacing[3], textAlign: 'center' },
  actions: { width: '100%', maxWidth: 360, marginTop: spacing[8], gap: spacing[3] },
  debugBox: {
    width: '100%',
    maxWidth: 420,
    marginTop: spacing[6],
    padding: spacing[3],
    borderRadius: radius.sm,
    backgroundColor: color.state.dangerBg,
  },
});
