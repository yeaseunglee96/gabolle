import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";

interface Props { children: ReactNode }
interface State { error: Error | null }

/**
 * 렌더 중 오류가 나도 화면 전체가 사라지지 않게 막는다.
 *
 * React 는 렌더 오류를 잡아주는 상위 컴포넌트가 없으면 트리 전체를
 * 언마운트한다. 그러면 사용자에게는 아무 안내 없이 흰 화면만 남고,
 * 원인은 개발자 도구 콘솔을 열어야만 보인다.
 * 이 경계가 오류를 붙잡아 무엇이 잘못됐는지 화면에 그대로 보여준다.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[LOCAL ROUTE] 화면 렌더 중 오류", error, info.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div className="app-crash" role="alert">
        <span className="app-crash-eyebrow">화면을 그리는 중 오류가 발생했습니다</span>
        <h1>{error.message}</h1>
        <p>
          이 화면만 실패했고 앱은 아직 살아 있습니다. 아래 버튼으로 다시 시도하거나,
          문제가 계속되면 이 메시지를 그대로 개발팀에 전달해 주세요.
        </p>
        {error.stack && <pre className="app-crash-stack">{error.stack.split("\n").slice(0, 12).join("\n")}</pre>}
        <div className="app-crash-actions">
          <button type="button" className="primary-btn" onClick={() => this.setState({ error: null })}>다시 시도</button>
          <button type="button" className="secondary-btn" onClick={() => { window.location.href = "/"; }}>처음 화면으로</button>
        </div>
      </div>
    );
  }
}
