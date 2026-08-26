import { Link } from "react-router-dom";
import { paths } from "../routes/paths";

/** * — 잘못된 경로 */
export function NotFoundPage() {
  return (
    <div className="route-placeholder">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소가 바뀌었거나 만료된 링크일 수 있습니다.</p>
      <Link className="primary-btn" to={paths.home()}>홈으로</Link>
    </div>
  );
}
