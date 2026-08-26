import { useEffect, useState } from "react";
import type { ItineraryItemOutput, PlaceImageMatch } from "../types";
import { getPlaceImage } from "../api/client";
import { SpeakModal } from "./SpeakModal";

const CATEGORY_META: Record<string, { label: string; color: string }> = {
  TOURIST: { label: "관광지", color: "#b9d9f4" }, RESTAURANT: { label: "식당", color: "#d8cff4" },
  CAFE: { label: "카페", color: "#cce9ee" }, LODGING: { label: "숙소", color: "#ddd7ea" },
};

interface Props {
  item: ItineraryItemOutput;
  pinned?: boolean;
  busy?: boolean;
  onTogglePin?: (item: ItineraryItemOutput) => void;
  onExclude?: (item: ItineraryItemOutput) => void;
  onReplace?: (item: ItineraryItemOutput) => void;
  onSelect?: (item: ItineraryItemOutput) => void;
  language?: "KO" | "EN";
  userAllergies?: string[];
  dietType?: string;
}

export function ItemCard({ item, pinned, busy, onTogglePin, onExclude, onReplace, onSelect, language, userAllergies = [], dietType = "NONE" }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [showSpeakModal, setShowSpeakModal] = useState(false);
  const [searchedImage, setSearchedImage] = useState<PlaceImageMatch | null>(null);
  const [imageFailed, setImageFailed] = useState(false);
  const meta = CATEGORY_META[item.category] ?? { label: item.category, color: "#d8dde5" };
  const sourceLabel = item.dataSource === "TOURAPI" ? "한국관광공사 기초 데이터" : "LOCAL ROUTE 초기 조사 데이터";
  const en = language === "EN" || (!language && document.documentElement.lang === "en");
  const mapUrl = `https://map.kakao.com/link/to/${encodeURIComponent(item.nameKo)},${item.lat},${item.lng}`;
  const hasKakaoReviews = item.category === "RESTAURANT" && item.kakaoRating !== null && item.kakaoReviewCount !== null && !!item.kakaoReviewSource;
  const kakaoMapUrl = item.kakaoPlaceUrl ?? mapUrl;
  const reviewDate = item.kakaoReviewCollectedAt ? new Intl.DateTimeFormat(en ? "en-US" : "ko-KR", { year: "numeric", month: "short", day: "numeric" }).format(new Date(item.kakaoReviewCollectedAt)) : null;
  const taxiText = `${item.nameKo}로 가주세요. ${item.address}`;
  const categoryLabel = en ? ({ TOURIST: "Attraction", RESTAURANT: "Restaurant", CAFE: "Cafe", LODGING: "Lodging" }[item.category] ?? item.category) : meta.label;
  const reason = en ? item.recommendReason.replace(/카카오 평점 ([\d.]+) · 후기 ([\d,]+)개/, "Kakao rating $1 · $2 reviews").replace(/후기 키워드:/, "review highlights:").replace(/로컬점수 ([\d.]+)/, "Local score $1").replace(/취향 태그:/, "tags:").replace(/다음 장소까지 약 (\d+)분/, "about $1 min to next stop") : item.recommendReason;
  const effectiveAllergies = userAllergies.length ? userAllergies : JSON.parse(document.documentElement.dataset.allergies ?? "[]");
  const effectiveDiet = dietType !== "NONE" ? dietType : document.documentElement.dataset.diet ?? "NONE";
  const needsFoodCheck = ["RESTAURANT", "CAFE"].includes(item.category) && (effectiveAllergies.length > 0 || effectiveDiet !== "NONE") && item.allergens.length === 0 && item.dietOptions.length === 0;
  useEffect(() => {
    setSearchedImage(null); setImageFailed(false);
    if (item.imageUrl || !["TOURIST", "RESTAURANT"].includes(item.category)) return;
    let cancelled = false;
    getPlaceImage(item.placeId).then((match) => { if (!cancelled && match.imageUrl) setSearchedImage(match); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [item.placeId, item.imageUrl, item.category]);
  const displayImage = imageFailed ? null : item.imageUrl ?? searchedImage?.imageUrl ?? null;
  const imageProvider = searchedImage?.provider === "NAVER" ? "네이버 이미지" : searchedImage?.provider === "GOOGLE" ? "Google 이미지" : null;

  return <article className={`place-card ${pinned ? "pinned" : ""}`} onClick={() => onSelect?.(item)}>
    <div className="place-sequence"><span>{item.seqOrder}</span><time>{item.plannedArrival}</time></div>
    <div className="place-visual" style={{ backgroundColor: meta.color }}>{displayImage ? <img src={displayImage} alt={en && item.nameEn ? item.nameEn : item.nameKo} onError={() => setImageFailed(true)} /> : <span className="place-placeholder" aria-hidden="true">{categoryLabel.slice(0, 1)}</span>}{imageProvider && searchedImage?.sourceUrl && displayImage && <a className="image-source-badge" href={searchedImage.sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()} aria-label={`${item.nameKo} 사진 출처 열기`}>{imageProvider}</a>}</div>
    <div className="place-content">
      <div className="place-kicker"><span>{categoryLabel}</span><span>{en ? `Stay ${item.stayMinutes} min` : `체류 ${item.stayMinutes}분`}</span>{pinned && <span className="pin-badge">{en ? "Pinned" : "고정됨"}</span>}</div>
      <h3>{en && item.nameEn ? `${item.nameKo} (${item.nameEn})` : item.nameKo}</h3>
      <p className="place-address">{en ? item.addressEn ?? `${item.address} · English address unavailable` : item.address}</p>
      <div className="evidence-row"><span className="evidence primary">{en ? `Local score ${(item.localScore * 5).toFixed(1)}/5` : `로컬 점수 ${(item.localScore * 5).toFixed(1)}/5`}</span><span className="evidence">{en ? `Open ${item.openTime}–${item.closeTime}` : `영업 ${item.openTime}–${item.closeTime}`}</span><span className="evidence">{en ? `Est. ₩${item.estCost.toLocaleString()}` : `예상 ${item.estCost.toLocaleString()}원`}</span></div>
      {hasKakaoReviews ? (
        <div className="kakao-review-card">
          <div className="kakao-review-score">
            <span aria-hidden="true">★</span>
            <strong>{item.kakaoRating!.toFixed(1)}</strong>
            <small>{en ? `${item.kakaoReviewCount!.toLocaleString()} Kakao reviews` : `카카오 후기 ${item.kakaoReviewCount!.toLocaleString()}개`}</small>
          </div>
          {item.kakaoReviewKeywords.length > 0 && <div className="review-keywords">{item.kakaoReviewKeywords.slice(0, 3).map((keyword) => <span key={keyword}>“{keyword}”</span>)}</div>}
          <a href={kakaoMapUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}>{en ? "See on Kakao Map" : "카카오맵에서 후기 보기"}</a>
        </div>
      ) : (
        <div className="kakao-review-card simple-link">
          <span className="evidence-badge-sm">Kakao Map</span>
          <a href={kakaoMapUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}>{en ? "Search on Kakao Map ↗" : "카카오맵 장소 검색 ↗"}</a>
        </div>
      )}
      <p className="recommend-reason"><strong>{en ? "Why recommended" : "추천 근거"}</strong>{reason}</p>
      <div className="capability-row">
        {item.hasEnglishMenu && <span>{en ? "English Menu" : "영어 메뉴"}</span>}
        {item.foreignCardPayment && <span>{en ? "International Cards" : "해외카드"}</span>}
        {item.soloFriendly && <span className="badge-blue">{en ? "Solo Friendly" : "혼밥 가능"}</span>}
        {item.takeoutAvailable && <span className="badge-amber">{en ? "Takeout Available" : "포장 가능"}</span>}
      </div>
      {needsFoodCheck && <div className="allergy-warning">{en ? "Allergen/diet data unavailable. Please confirm before ordering." : "알레르기·식단 정보가 없어 주문 전 확인이 필요합니다."}</div>}
      {expanded && <div className="place-details">
        <dl><div><dt>{en ? "Data Source" : "데이터 출처"}</dt><dd>{sourceLabel}</dd></div>{item.category === "RESTAURANT" && <div><dt>{en ? "Kakao Reviews" : "카카오 후기"}</dt><dd>{hasKakaoReviews ? `${item.kakaoReviewSource === "LICENSED_IMPORT" ? "승인된 집계 데이터" : "수동 검증"}${reviewDate ? ` · ${reviewDate} 기준` : ""}` : "공식 API 미제공 · 검증된 집계 연결 전에는 추천 점수에 미반영"}</dd></div>}<div><dt>{en ? "Cost Note" : "비용 성격"}</dt><dd>{en ? "Price-tier estimate · actual price may differ" : "가격대 기반 추정 · 실제 결제 금액과 다를 수 있음"}</dd></div></dl>
        <div className="taxi-card"><span>{en ? "Show this to driver" : "기사님께 보여주세요"}</span><b>{taxiText}</b><button type="button" onClick={(e) => { e.stopPropagation(); navigator.clipboard.writeText(taxiText); }}>{en ? "Copy address" : "주소 복사"}</button></div>
      </div>}
      <div className="place-actions">
        <button type="button" className="speak-action-btn" onClick={(e) => { e.stopPropagation(); setShowSpeakModal(true); }}>
          🗣️ {en ? "Speak Korean" : "한국어 말하기"}
        </button>
        <button type="button" onClick={(e) => { e.stopPropagation(); setExpanded((value) => !value); }} aria-expanded={expanded}>{en ? (expanded ? "Hide Details" : "Evidence & Policy") : (expanded ? "정보 접기" : "근거·정책 보기")}</button>
        <button type="button" onClick={(event) => { event.stopPropagation(); onSelect?.(item); window.setTimeout(() => document.getElementById("route-map")?.scrollIntoView({ behavior: "smooth", block: "center" }), 0); }}>{en ? "Route on Map" : "지도에서 경로 보기"}</button>
        {onTogglePin && <button type="button" disabled={busy} onClick={(e) => { e.stopPropagation(); onTogglePin(item); }}>{pinned ? (en ? "Unpin" : "고정 해제") : (en ? "Pin Place" : "장소 고정")}</button>}
        {onReplace && <button type="button" disabled={busy || pinned} onClick={(e) => { e.stopPropagation(); onReplace(item); }}>{en ? "Replace Place" : "비슷한 장소로 교체"}</button>}
        {onExclude && <button type="button" className="danger-action" disabled={busy || pinned} onClick={(e) => { e.stopPropagation(); onExclude(item); }}>{en ? "Exclude & Recalculate" : "제외 후 재계산"}</button>}
      </div>

      <SpeakModal
        isOpen={showSpeakModal}
        onClose={() => setShowSpeakModal(false)}
        targetCategory={item.category}
        targetAddress={item.address}
        targetName={item.nameKo}
        targetNameEn={item.nameEn}
        language={language}
      />
    </div>
  </article>;
}

export function TravelSegment({ item, hasCar, language = "KO" }: { item: ItineraryItemOutput; hasCar: boolean; language?: "KO" | "EN" }) {
  if (item.travelMinToNext === null) return null;
  const en = language === "EN";
  const modeIcon = hasCar ? "🚗" : "🚌";
  const modeLabel = en ? (hasCar ? "Drive" : "Public transit") : (hasCar ? "자차" : "대중교통");
  return (
    <div className="travel-segment">
      <div className="travel-track" aria-hidden="true">
        <span className="travel-track-line" />
        <span className="travel-track-icon">{modeIcon}</span>
        <span className="travel-track-line" />
      </div>
      <div className="travel-info">
        <div className="travel-info-main">
          <strong>{item.travelMinToNext}{en ? " min" : "분"}</strong>
          <span className="tag-chip-sm travel-mode-chip">{modeLabel}</span>
          <span className="tag-chip-sm">{item.distanceToNextM ? `${(item.distanceToNextM / 1000).toFixed(1)}km` : (en ? "distance pending" : "거리 확인 중")}</span>
        </div>
        <small>{en ? (item.travelIsEstimate ? "Estimated travel time · actual route may differ" : "Kakao route") : (item.travelIsEstimate ? "예상 이동시간 · 실제 경로와 다를 수 있음" : "카카오 경로 기준")}</small>
      </div>
    </div>
  );
}
