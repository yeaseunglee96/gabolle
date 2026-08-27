import { useState } from "react";
import { searchPlaces } from "../api/client";
import type { PlaceRecord } from "../types";

export interface DesiredPlace { placeId: string; name: string; address: string }

interface Props {
  value: DesiredPlace[];
  onChange: (next: DesiredPlace[]) => void;
}

/**
 * 7.1장 "필수 방문 장소" 위저드 입력. 카카오 전체 검색이 아니라 이 앱의 시드 장소 카탈로그
 * 안에서만 찾는다(OriginPicker와 다른 이유: mustVisitPlaceIds는 이 카탈로그의 Place.id를
 * 기대하므로, 임의의 외부 POI id를 저장하면 스케줄러가 절대 찾지 못한다).
 */
export function DesiredPlacesPicker({ value, onChange }: Props) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceRecord[]>([]);
  const [searching, setSearching] = useState(false);
  const [notice, setNotice] = useState("");

  const submit = async (event: React.SyntheticEvent) => {
    event.preventDefault();
    if (query.trim().length < 2) { setNotice("장소명을 2자 이상 입력해 주세요."); return; }
    setSearching(true); setNotice("");
    try {
      const found = await searchPlaces(query.trim());
      setResults(found.filter((place) => !value.some((v) => v.placeId === place.id)));
      if (!found.length) setNotice("검색 결과가 없습니다. 다른 이름으로 찾아보세요.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "장소를 검색하지 못했습니다.");
    } finally {
      setSearching(false);
    }
  };

  const add = (place: PlaceRecord) => {
    onChange([...value, { placeId: place.id, name: place.nameKo ?? place.id, address: place.address ?? "" }]);
    setResults((prev) => prev.filter((p) => p.id !== place.id));
  };
  const remove = (placeId: string) => onChange(value.filter((p) => p.placeId !== placeId));

  return (
    <div className="desired-places">
      <span>가고 싶은 곳 (선택)</span>
      <small>이미 정해둔 장소가 있으면 검색해서 추가해 두세요. 어느 날짜에 넣을지는 추천 모드가 자동으로 정합니다.</small>
      <div className="desired-places-search">
        <input
          aria-label="가고 싶은 장소 검색"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => { if (event.key === "Enter") void submit(event); }}
          placeholder="장소명으로 검색 (예: 감천문화마을)"
        />
        <button type="button" onClick={(event) => void submit(event)} disabled={searching}>{searching ? "검색 중" : "검색"}</button>
      </div>
      {notice && <p className="desired-places-notice" role="status">{notice}</p>}
      {results.length > 0 && (
        <ul className="desired-places-results" aria-label="검색 결과">
          {results.map((place) => (
            <li key={place.id}>
              <button type="button" onClick={() => add(place)}>
                <strong>{place.nameKo}</strong><span>{place.address}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {value.length > 0 && (
        <ul className="desired-places-selected" aria-label="선택한 장소">
          {value.map((place) => (
            <li key={place.placeId}>
              <div><strong>{place.name}</strong><span>{place.address}</span></div>
              <button type="button" onClick={() => remove(place.placeId)} aria-label={`${place.name} 삭제`}>✕</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
