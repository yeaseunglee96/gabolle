import { useEffect, useRef, useState } from "react";
import { searchLocations } from "../api/client";
import type { LocationSearchResult } from "../types";
import { loadKakaoSdk } from "./KakaoMap";

export function OriginPicker({ value, onChange }: { value: LocationSearchResult; onChange: (location: LocationSearchResult) => void }) {
  const [query, setQuery] = useState(value.name); const [results, setResults] = useState<LocationSearchResult[]>([]);
  const [searching, setSearching] = useState(false); const [notice, setNotice] = useState(""); const mapRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const appKey = import.meta.env.VITE_KAKAO_JS_KEY as string | undefined; if (!appKey || !mapRef.current) return;
    let cancelled = false; loadKakaoSdk(appKey).then(() => { if (cancelled || !mapRef.current) return; const kakao = window.kakao; const position = new kakao.maps.LatLng(value.lat, value.lng); const map = new kakao.maps.Map(mapRef.current, { center: position, level: 4 }); new kakao.maps.Marker({ map, position }); }).catch(() => setNotice("지도 미리보기를 불러오지 못했습니다."));
    return () => { cancelled = true; };
  }, [value]);
  const submit = async (event: React.SyntheticEvent) => {
    event.preventDefault(); if (query.trim().length < 2) { setNotice("주소나 장소명을 2자 이상 입력해 주세요."); return; }
    setSearching(true); setNotice("");
    try {
      const appKey = import.meta.env.VITE_KAKAO_JS_KEY as string | undefined; let found: LocationSearchResult[] = [];
      if (appKey) {
        await loadKakaoSdk(appKey); const kakao = window.kakao;
        if (kakao.maps.services?.Places) found = await new Promise((resolve) => new kakao.maps.services.Places().keywordSearch(query.trim(), (data: any[], status: string) => resolve(status === kakao.maps.services.Status.OK ? data.map((place) => ({ id: place.id, name: place.place_name, address: place.road_address_name || place.address_name, lat: Number(place.y), lng: Number(place.x), category: place.category_name })) : [])));
      }
      if (!found.length) found = await searchLocations(query.trim()); setResults(found);
      if (!found.length) setNotice("검색 결과가 없습니다. 도로명이나 장소명을 다시 확인해 주세요.");
    } catch (error) { setNotice(error instanceof Error ? error.message : "주소를 검색하지 못했습니다."); } finally { setSearching(false); }
  };
  return <div className="origin-picker"><div className="origin-picker-search"><span>출발지</span><div className="origin-search-controls"><input aria-label="카카오맵 출발지 주소" value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") void submit(event); }} placeholder="도로명, 건물명 또는 장소 검색" /><button type="button" onClick={(event) => void submit(event)} disabled={searching}>{searching ? "검색 중" : "주소 검색"}</button></div><small>카카오맵 검색 결과에서 매일 이동을 시작할 위치를 선택합니다.</small></div>
    {results.length > 0 && <ul className="origin-results" aria-label="카카오맵 주소 검색 결과">{results.map((result) => <li key={result.id}><button type="button" className={result.id === value.id ? "selected" : ""} onClick={() => { onChange(result); setQuery(result.name); setResults([]); }}><strong>{result.name}</strong><span>{result.address}</span></button></li>)}</ul>}
    <div className="origin-map" ref={mapRef} aria-label="선택한 출발지 지도" /><div className="origin-selected"><span>선택한 출발지</span><strong>{value.name}</strong><small>{value.address}</small></div>{notice && <p className="origin-notice" role="status">{notice}</p>}
  </div>;
}
