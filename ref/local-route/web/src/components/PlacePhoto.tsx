import { useEffect, useRef, useState } from "react";
import { getPlaceImage } from "../api/client";
import type { PlaceImageMatch } from "../types";

interface Props {
  placeId: string;
  imageUrl: string | null;
  alt: string;
  category?: string;
  className?: string;
  showSource?: boolean;
}

const CATEGORY_LABEL: Record<string, string> = {
  TOURIST: "BUSAN VIEW", RESTAURANT: "LOCAL FOOD", CAFE: "BUSAN CAFE",
  LODGING: "STAY", FESTIVAL: "FESTIVAL", SOUVENIR: "LOCAL GOODS",
};

/** 저장 사진 → 검색 사진 → 로컬 시각 폴백 순으로 끊김 없이 보여주는 공통 장소 사진. */
export function PlacePhoto({ placeId, imageUrl, alt, category = "TOURIST", className, showSource = false }: Props) {
  const [match, setMatch] = useState<PlaceImageMatch | null>(imageUrl ? { imageUrl, sourceUrl: imageUrl, provider: "DATABASE", title: alt } : null);
  const [failed, setFailed] = useState(false);
  const fallbackRequested = useRef(false);

  useEffect(() => {
    let cancelled = false;
    fallbackRequested.current = false;
    setFailed(false);
    setMatch(imageUrl ? { imageUrl, sourceUrl: imageUrl, provider: "DATABASE", title: alt } : null);
    if (!imageUrl) getPlaceImage(placeId).then((result) => { if (!cancelled) setMatch(result); }).catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [placeId, imageUrl, alt]);

  const recover = () => {
    if (fallbackRequested.current) { setFailed(true); return; }
    fallbackRequested.current = true;
    getPlaceImage(placeId, true).then((result) => {
      if (result.imageUrl && result.imageUrl !== match?.imageUrl) { setMatch(result); setFailed(false); }
      else setFailed(true);
    }).catch(() => setFailed(true));
  };

  const provider = match?.provider === "NAVER" ? "네이버 이미지" : match?.provider === "GOOGLE" ? "Google 이미지" : null;
  if (!match?.imageUrl || failed) return <div className={`place-photo-fallback category-${category.toLowerCase()} ${className ?? ""}`} aria-label={`${alt} 대체 이미지`}><span>{CATEGORY_LABEL[category] ?? "BUSAN"}</span></div>;

  return <><img className={className} src={match.imageUrl} alt={alt} loading="lazy" decoding="async" onError={recover} />{showSource && provider && match.sourceUrl && <a className="image-source-badge" href={match.sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>{provider}</a>}</>;
}
