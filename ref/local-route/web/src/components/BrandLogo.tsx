export function BrandLogo({ compact = false }: { compact?: boolean }) {
  return <div className={`local-route-logo ${compact ? "compact" : ""}`} aria-label="LOCAL ROUTE">
    <svg className="local-route-mark" viewBox="0 0 44 44" aria-hidden="true">
      <path d="M9 8v19c0 5 3 8 8 8h18" />
      <path d="M16 10h9c5 0 8 3 8 7s-3 7-8 7h-9l17 12" />
      <circle cx="9" cy="8" r="3" />
      <circle cx="35" cy="35" r="3" />
    </svg>
    {!compact && <span className="local-route-wordmark"><b>LOCAL</b><b>ROUTE</b></span>}
  </div>;
}
