import { Link } from "react-router-dom";
import { ROUTE_TREE, flattenRoutes } from "../routes/routeTree";
import type { Milestone, Owner } from "../routes/routeTree";

const MILESTONE_LABEL: Record<Milestone, string> = {
  M1_WEB: "9/1 웹 MVP",
  M2_APP: "9/9 앱 빌드",
  M3_REVIEW: "스토어 심사 필수",
  M4_LATER: "심사 이후",
};

const OWNER_LABEL: Record<Owner, string> = {
  FE: "FE",
  BE: "BE",
  DB: "DB",
  INFRA: "INFRA",
};

/**
 * 아직 구현되지 않은 라우트를 위한 공통 자리표시 화면.
 *
 * 페이지 트리(routeTree.ts)에서 해당 노드의 메타데이터를 읽어와
 * "무엇을 만들어야 하는지 · 누가 · 어떤 API 로 · 언제까지"를 화면에 직접 띄운다.
 * 팀원이 이 화면을 열면 그대로 작업 명세가 된다.
 */
export function RoutePlaceholder({ routeId }: { routeId: string }) {
  const node = flattenRoutes(ROUTE_TREE).find((candidate) => candidate.id === routeId);

  if (!node) {
    return (
      <div className="route-placeholder">
        <h1>정의되지 않은 화면</h1>
        <p><code>{routeId}</code> 는 페이지 트리에 없습니다. <code>web/src/routes/routeTree.ts</code> 에 먼저 추가하세요.</p>
      </div>
    );
  }

  return (
    <div className="route-placeholder">
      <span className="route-placeholder-eyebrow">{MILESTONE_LABEL[node.milestone]} · 구현 예정</span>
      <h1>{node.titleKo}</h1>
      <p className="route-placeholder-purpose">{node.purpose}</p>

      <dl className="route-placeholder-meta">
        <div><dt>경로</dt><dd><code>{node.fullPath}</code></dd></div>
        <div><dt>앱 파일</dt><dd><code>{node.expoPath}</code></dd></div>
        <div><dt>담당</dt><dd>{node.owners.map((owner) => OWNER_LABEL[owner]).join(" · ")}</dd></div>
        <div><dt>권한</dt><dd>{node.access}</dd></div>
        <div><dt>플랫폼</dt><dd>{node.platform === "BOTH" ? "웹 · 앱" : node.platform === "WEB" ? "웹 전용" : "앱 전용"}</dd></div>
      </dl>

      {node.apis.length > 0 && (
        <section className="route-placeholder-apis">
          <h2>연결할 API</h2>
          <ul>{node.apis.map((api) => <li key={api}><code>{api}</code></li>)}</ul>
        </section>
      )}

      <Link className="secondary-btn" to="/">홈으로</Link>
    </div>
  );
}
