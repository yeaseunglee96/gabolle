import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getStoredAccount, logoutAccount, type AccountUser } from "../api/client";
import { paths } from "../routes/paths";

export function AccountMenu() {
  const navigate = useNavigate();
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const [account, setAccount] = useState<AccountUser | null>(getStoredAccount());

  useEffect(() => {
    const sync = () => setAccount(getStoredAccount());
    window.addEventListener("local-route-account-changed", sync);
    window.addEventListener("storage", sync);
    return () => { window.removeEventListener("local-route-account-changed", sync); window.removeEventListener("storage", sync); };
  }, []);

  if (!account) return <button type="button" className="account-login-btn" onClick={() => navigate(paths.login())}>로그인</button>;
  const move = (path: string) => { detailsRef.current?.removeAttribute("open"); navigate(path); };

  return (
    <details className="account-menu" ref={detailsRef}>
      <summary aria-label="내 계정 메뉴"><span className={`account-avatar avatar-${account.avatarColor?.toLowerCase() ?? "lavender"}`}>{account.avatarImage ? <img src={account.avatarImage} alt="" /> : account.name.slice(0, 1)}</span>{account.name}</summary>
      <div className="account-menu-popover">
        <strong>{account.name}</strong><small>{account.email}</small>
        <button type="button" onClick={() => move(paths.settings())}>프로필·여행 설정</button>
        <button type="button" onClick={() => move(paths.myTrips())}>내 여행</button>
        <button type="button" onClick={async () => { await logoutAccount(); navigate(paths.welcome()); }}>로그아웃</button>
      </div>
    </details>
  );
}
