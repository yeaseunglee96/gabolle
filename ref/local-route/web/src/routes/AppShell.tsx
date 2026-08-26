import { createContext, useContext, useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import { ApiUnavailableError, getLodgings, getPlaceCount } from "../api/client";
import type { PlaceRecord } from "../types";

interface AppShellValue {
  /** 등록된 로컬 장소 수. 백엔드 미연결 시 null */
  placeCount: number | null;
  /** 숙소 후보 목록 (여행 조건 입력에서 사용) */
  lodgings: PlaceRecord[];
  apiOffline: boolean;
}

const AppShellContext = createContext<AppShellValue>({ placeCount: null, lodgings: [], apiOffline: false });

export function useAppShell() {
  return useContext(AppShellContext);
}

/**
 * 모든 라우트의 최상위 레이아웃.
 * 앱 전역에서 한 번만 필요한 조회(장소 수·숙소 목록)와
 * 백엔드 미실행 경고 배너를 여기서 담당한다.
 */
export function AppShell() {
  const [placeCount, setPlaceCount] = useState<number | null>(null);
  const [lodgings, setLodgings] = useState<PlaceRecord[]>([]);
  const [apiOffline, setApiOffline] = useState(false);

  useEffect(() => {
    getPlaceCount()
      .then((count) => { setPlaceCount(count); setApiOffline(false); })
      .catch((error) => { setPlaceCount(null); setApiOffline(error instanceof ApiUnavailableError); });
    getLodgings().then(setLodgings).catch(() => setLodgings([]));
  }, []);

  return (
    <AppShellContext.Provider value={{ placeCount, lodgings, apiOffline }}>
      {apiOffline && (
        <div className="api-offline-banner" role="alert">
          <strong>백엔드 서버에 연결할 수 없습니다</strong>
          <span>
            화면은 표시되지만 장소 조회·일정 생성 등 모든 기능이 동작하지 않습니다.
            프로젝트 루트에서 <code>npm run dev</code> 를 실행하면 서버(:4000)와 웹(:5173)이 함께 실행됩니다.
          </span>
        </div>
      )}
      <Outlet />
    </AppShellContext.Provider>
  );
}
