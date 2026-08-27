import { createContext, useContext } from "react";
import type { ItineraryItemOutput, ItineraryOutput } from "../../types";

/**
 * /trips/:tripId 하위 모든 페이지가 공유하는 상태.
 * TripLayout 이 한 번만 일정을 불러오고, 자식 페이지는 이 컨텍스트만 읽는다.
 * 페이지를 추가할 때 API 를 다시 호출할 필요가 없다.
 */
export interface TripContextValue {
  tripId: string;
  itinerary: ItineraryOutput;
  /** 등록된 로컬 장소 총 개수 (사이드바 표시용) */
  placeCount: number | null;
  /** 재계산·재최적화가 진행 중인지 */
  regenerating: boolean;
  /** 내 권한이 OWNER 또는 EDITOR 인지 */
  canEdit: boolean;
  myRole: string;
  pinnedPlaceIds: string[];
  excludedPlaceIds: string[];
  /** 일정 전체 재생성 */
  regenerate: () => Promise<void>;
  /** 여행 조건 수정 화면으로 이동 */
  editConditions: () => void;
  /** 장소 고정/해제 */
  togglePin: (item: ItineraryItemOutput) => Promise<void>;
  /** 제외 확인 모달 열기 */
  requestExclude: (item: ItineraryItemOutput) => void;
  /** 대체 장소 모달 열기 */
  requestReplace: (item: ItineraryItemOutput) => Promise<void>;
  /** 최근 변경 되돌리기 */
  undo: () => Promise<void>;
  /** 서버에서 일정을 다시 읽어온다(재생성이 아니라 단순 갱신). 페이스 러닝이 실측 기록 후 호출한다. */
  reloadItinerary: () => Promise<void>;
  /** 지도에서 선택된 장소 */
  selectedPlaceId: string | null;
  selectPlace: (placeId: string | null) => void;
  /** 목록 컴포넌트(ItemCard 계열)에 그대로 펼쳐 넘기는 공통 props */
  itemProps: {
    hasCar: boolean;
    pinnedPlaceIds: string[];
    busy: boolean;
    language: "KO" | "EN";
    onTogglePin: (item: ItineraryItemOutput) => void;
    onExclude: (item: ItineraryItemOutput) => void;
    onReplace: (item: ItineraryItemOutput) => void;
    onSelect: (item: ItineraryItemOutput) => void;
    /** 같은 날짜 안에서 방문 순서 변경(드래그·위아래 버튼) */
    onReorder: (dayIndex: number, itemIds: string[]) => Promise<void>;
  };
}

export const TripContext = createContext<TripContextValue | null>(null);

export function useTrip(): TripContextValue {
  const value = useContext(TripContext);
  if (!value) throw new Error("useTrip 은 /trips/:tripId 라우트 안에서만 사용할 수 있습니다.");
  return value;
}
