export function BrandLogo({ compact = false }: { compact?: boolean }) {
  return <div className={`local-route-logo gabolle-logo ${compact ? "compact" : ""}`} aria-label="GABOLLE 가볼래">
    <svg className="local-route-mark" viewBox="0 0 44 44" aria-hidden="true">
      <path d="M8 30c6-15 13-21 21-18 7 3 8 11 2 16-5 5-13 5-20 0" />
      <path d="M11 28c7-7 14-8 22-2" />
      <circle cx="8" cy="30" r="3" />
      <circle cx="33" cy="26" r="3" />
    </svg>
    {!compact && <span className="local-route-wordmark"><b>GABOLLE</b><small>가볼래</small></span>}
  </div>;
}
