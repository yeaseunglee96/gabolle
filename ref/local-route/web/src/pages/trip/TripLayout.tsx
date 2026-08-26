import { useCallback, useEffect, useMemo, useState } from "react";
import { Outlet, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { DashboardShell } from "../../components/DashboardShell";
import { getAlternatives, getCollaboration, getItinerary, generateItinerary, reoptimizeDay, reorderDay, undoItineraryChange } from "../../api/client";
import type { ItineraryItemOutput, ItineraryOutput, PlaceAlternative } from "../../types";
import { applyTripLanguage, getUiLanguage, subscribeUiLanguage } from "../../i18n";
import { useAppShell } from "../../routes/AppShell";
import { TAB_TO_PATH, paths, tabFromPathname } from "../../routes/paths";
import { setLastVisitedTripId } from "../../utils/visitor";
import { TripContext } from "./TripContext";
import type { TripContextValue } from "./TripContext";

/**
 * /trips/:tripId 하위 전체의 레이아웃.
 *
 * 책임
 *  - 일정을 한 번만 불러와 자식 페이지에 컨텍스트로 내려준다
 *  - 재계산·부분 재최적화·되돌리기 등 일정을 바꾸는 모든 동작을 소유한다
 *  - 사이드바 탭을 URL 에서 역산해 표시하고, 탭 클릭을 라우팅으로 연결한다
 *  - 제외 확인·대체 장소 모달처럼 어느 탭에서든 뜰 수 있는 UI 를 관리한다
 */
export function TripLayout() {
  const { tripId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { placeCount } = useAppShell();

  const [itinerary, setItinerary] = useState<ItineraryOutput | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [regenerating, setRegenerating] = useState(false);
  const [pinnedPlaceIds, setPinnedPlaceIds] = useState<string[]>([]);
  const [excludedPlaceIds, setExcludedPlaceIds] = useState<string[]>([]);
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingExclude, setPendingExclude] = useState<ItineraryItemOutput | null>(null);
  const [pendingReplace, setPendingReplace] = useState<ItineraryItemOutput | null>(null);
  const [alternatives, setAlternatives] = useState<PlaceAlternative[]>([]);
  const [myRole, setMyRole] = useState<string>("VIEWER");
  const [uiLang, setUiLang] = useState<"KO" | "EN">(getUiLanguage());

  const mode = searchParams.get("mode") ?? undefined;

  useEffect(() => {
    return subscribeUiLanguage((lang: "KO" | "EN") => setUiLang(lang));
  }, []);

  useEffect(() => {
    if (!tripId) return;
    let cancelled = false;
    getItinerary(tripId, mode)
      .then((next) => { if (!cancelled) { setItinerary(next); setLoadError(null); } })
      .catch((error) => { if (!cancelled) setLoadError(error instanceof Error ? error.message : "일정을 불러오지 못했습니다."); });
    return () => { cancelled = true; };
  }, [tripId, mode]);

  useEffect(() => {
    if (!itinerary) return;
    applyTripLanguage(itinerary.trip.language);
    document.documentElement.dataset.allergies = JSON.stringify(itinerary.trip.allergies);
    document.documentElement.dataset.diet = itinerary.trip.dietType;
    setLastVisitedTripId(itinerary.tripId);
  }, [itinerary]);

  useEffect(() => {
    if (!itinerary) return;
    getCollaboration(itinerary.itineraryId).then((state) => setMyRole(state.myRole)).catch(() => setMyRole("VIEWER"));
  }, [itinerary?.itineraryId]);

  const canEdit = myRole === "OWNER" || myRole === "EDITOR";

  const refresh = useCallback(async () => {
    const next = await getItinerary(tripId);
    setItinerary(next);
    return next;
  }, [tripId]);

  const dayOf = useCallback((item: ItineraryItemOutput) => (
    itinerary?.days.find((day) => day.items.some((candidate) => candidate.placeId === item.placeId))?.dayIndex ?? 1
  ), [itinerary]);

  const partialReoptimize = useCallback(async (
    item: ItineraryItemOutput,
    action: "REMOVE" | "PIN" | "UNPIN" | "REPLACE",
    replacementPlaceId?: string,
  ) => {
    if (!itinerary) return;
    setRegenerating(true);
    try {
      await reoptimizeDay(itinerary.itineraryId, dayOf(item), { action, itemId: item.itemId, replacementPlaceId });
      if (action === "PIN") setPinnedPlaceIds((ids) => [...new Set([...ids, item.placeId])]);
      if (action === "UNPIN") setPinnedPlaceIds((ids) => ids.filter((id) => id !== item.placeId));
      if (action === "REMOVE") setExcludedPlaceIds((ids) => [...new Set([...ids, item.placeId])]);
      await refresh();
    } finally {
      setRegenerating(false);
    }
  }, [itinerary, dayOf, refresh]);

  const value = useMemo<TripContextValue | null>(() => {
    if (!itinerary) return null;

    const togglePin = async (item: ItineraryItemOutput) => {
      const action = pinnedPlaceIds.includes(item.placeId) ? "UNPIN" : "PIN";
      await partialReoptimize(item, action);
      setNotice(action === "PIN"
        ? `${item.nameKo}을(를) 고정하고 해당 날짜만 다시 계산했습니다.`
        : `${item.nameKo} 고정을 해제했습니다.`);
    };

    const requestReplace = async (item: ItineraryItemOutput) => {
      if (!item.itemId) return;
      setPendingReplace(item);
      setAlternatives(await getAlternatives(itinerary.itineraryId, item.itemId));
    };

    const reorder = async (dayIndex: number, itemIds: string[]) => {
      setRegenerating(true);
      try {
        const result = await reorderDay(itinerary.itineraryId, dayIndex, itemIds);
        await refresh();
        setNotice(result.warnings.length ? result.warnings.join(" ") : "방문 순서를 변경했습니다.");
      } finally {
        setRegenerating(false);
      }
    };

    return {
      tripId,
      itinerary,
      placeCount,
      regenerating,
      canEdit,
      myRole,
      pinnedPlaceIds,
      excludedPlaceIds,
      selectedPlaceId,
      selectPlace: setSelectedPlaceId,
      regenerate: async () => {
        setRegenerating(true);
        try { setItinerary(await generateItinerary(tripId, undefined, itinerary.mode)); }
        catch { /* 재생성 실패 시 기존 일정을 유지한다. 사용자가 다시 시도할 수 있다. */ }
        finally { setRegenerating(false); }
      },
      editConditions: () => navigate(paths.plan("basic"), { state: { fromTripId: tripId } }),
      togglePin,
      requestExclude: setPendingExclude,
      requestReplace,
      undo: async () => {
        setRegenerating(true);
        try { await undoItineraryChange(itinerary.itineraryId); await refresh(); }
        finally { setRegenerating(false); }
      },
      reloadItinerary: async () => { await refresh(); },
      itemProps: {
        hasCar: itinerary.trip.hasCar,
        pinnedPlaceIds,
        busy: regenerating || !canEdit,
        language: uiLang,
        onTogglePin: (item) => void togglePin(item),
        onExclude: setPendingExclude,
        onReplace: (item) => void requestReplace(item),
        onSelect: (item) => setSelectedPlaceId(item.placeId),
        onReorder: reorder,
      },
    };
  }, [itinerary, tripId, placeCount, regenerating, canEdit, myRole, pinnedPlaceIds, excludedPlaceIds, selectedPlaceId, partialReoptimize, navigate, refresh, uiLang]);

  if (loadError) {
    return (
      <div className="route-placeholder">
        <h1>일정을 불러오지 못했습니다</h1>
        <p>{loadError}</p>
        <button type="button" className="primary-btn" onClick={() => navigate(paths.plan())}>새 일정 만들기</button>
      </div>
    );
  }

  if (!value) {
    return <div className="route-placeholder"><h1>일정을 불러오는 중입니다…</h1></div>;
  }

  const confirmExclude = async () => {
    if (!pendingExclude) return;
    await partialReoptimize(pendingExclude, "REMOVE");
    setNotice(`${pendingExclude.nameKo}을(를) 제외하고 해당 날짜만 다시 계산했습니다.`);
    setPendingExclude(null);
  };

  const replaceWith = async (alternative: PlaceAlternative) => {
    if (!pendingReplace) return;
    await partialReoptimize(pendingReplace, "REPLACE", alternative.placeId);
    setNotice(`${pendingReplace.nameKo}을(를) ${alternative.nameKo}(으)로 교체하고 해당 날짜만 다시 계산했습니다.`);
    setPendingReplace(null);
    setAlternatives([]);
  };

  const activeTab = tabFromPathname(location.pathname);

  return (
    <TripContext.Provider value={value}>
      <DashboardShell
        placeCount={placeCount}
        language={uiLang}
        activeTab={activeTab}
        onTabChange={(tab) => { navigate(TAB_TO_PATH[tab](tripId)); window.scrollTo({ top: 0, behavior: "smooth" }); }}
      >
        {notice && (
          <div className="action-notice" role="status">
            <span>{notice}</span>
            <button type="button" onClick={() => setNotice(null)} aria-label="알림 닫기">×</button>
          </div>
        )}

        <Outlet />

        {pendingExclude && (
          <div className="modal-backdrop" role="presentation" onMouseDown={() => !regenerating && setPendingExclude(null)}>
            <div className="decision-modal" role="dialog" aria-modal="true" aria-labelledby="exclude-title" onKeyDown={(event) => { if (event.key === "Escape" && !regenerating) setPendingExclude(null); }} onMouseDown={(event) => event.stopPropagation()}>
              <span className="modal-icon">↻</span>
              <h2 id="exclude-title">{pendingExclude.nameKo}을(를) 제외할까요?</h2>
              <p>이 장소를 제외 목록에 저장하고, 고정 장소와 다른 날짜는 유지한 채 <strong>해당 날짜만 다시 계산</strong>합니다.</p>
              <div className="impact-preview"><span>유지</span><b>고정 장소·다른 날짜</b><span>변경 가능</span><b>현재 날짜의 방문지·시간·이동거리</b></div>
              <div className="form-actions">
                <button autoFocus type="button" className="secondary-btn" disabled={regenerating} onClick={() => setPendingExclude(null)}>취소</button>
                <button type="button" className="primary-btn" disabled={regenerating} onClick={confirmExclude}>{regenerating ? "다시 계산 중…" : "제외하고 다시 계산"}</button>
              </div>
            </div>
          </div>
        )}

        {pendingReplace && (
          <div className="modal-backdrop" role="presentation">
            <div className="decision-modal alternative-modal" role="dialog" aria-modal="true" aria-labelledby="replace-title" onKeyDown={(event) => { if (event.key === "Escape") { setPendingReplace(null); setAlternatives([]); } }}>
              <h2 id="replace-title">{pendingReplace.nameKo} 대체 장소</h2>
              <p>같은 카테고리에서 현재 조건을 통과한 후보입니다. 교체하면 해당 날짜만 다시 계산됩니다.</p>
              <div className="alternative-list">
                {alternatives.map((alternative, index) => (
                  <button autoFocus={index === 0} type="button" key={alternative.placeId} disabled={regenerating} onClick={() => replaceWith(alternative)}>
                    <strong>{alternative.nameKo}</strong>
                    <span>로컬 {(alternative.localScore * 5).toFixed(1)}/5 · 예상 {alternative.estCost.toLocaleString()}원</span>
                    <small>{[alternative.hasEnglishMenu && "영어 메뉴", alternative.foreignCardPayment && "해외카드"].filter(Boolean).join(" · ") || "추가 편의정보 없음"}</small>
                  </button>
                ))}
              </div>
              <button type="button" className="secondary-btn" onClick={() => { setPendingReplace(null); setAlternatives([]); }}>취소</button>
            </div>
          </div>
        )}
      </DashboardShell>
    </TripContext.Provider>
  );
}
