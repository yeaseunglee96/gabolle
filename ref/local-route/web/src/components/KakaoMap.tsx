import { useEffect, useRef } from "react";
import type { ItineraryDayOutput, SouvenirShop } from "../types";

declare global {
  interface Window {
    kakao: any;
  }
}

const DAY_COLORS = ["#2563eb", "#16a34a", "#f97316", "#dc2626", "#7c3aed", "#0891b2"];

let loaderPromise: Promise<void> | null = null;

export function loadKakaoSdk(appKey: string): Promise<void> {
  if (window.kakao?.maps) return Promise.resolve();
  if (loaderPromise) return loaderPromise;

  loaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`;
    script.onload = () => window.kakao.maps.load(() => resolve());
    script.onerror = () => reject(new Error("카카오맵 SDK 로드 실패"));
    document.head.appendChild(script);
  });
  return loaderPromise;
}

interface Props {
  days: ItineraryDayOutput[];
  originLat: number;
  originLng: number;
  selectedPlaceId?: string | null;
  souvenirShops?: SouvenirShop[];
  routePath?: [number, number][];
  routeMode?: "TRANSIT" | "CAR";
}

export function KakaoMap({ days, originLat, originLng, selectedPlaceId, souvenirShops = [], routePath = [], routeMode = "TRANSIT" }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const resizeObserverRef = useRef<ResizeObserver | null>(null);

  useEffect(() => {
    const appKey = import.meta.env.VITE_KAKAO_JS_KEY as string | undefined;
    if (!appKey) {
      if (errorRef.current) {
        errorRef.current.textContent =
          "카카오 JS 키가 설정되지 않았습니다 (web/.env의 VITE_KAKAO_JS_KEY). 지도 없이 아래 일정만 표시합니다.";
      }
      return;
    }
    if (!containerRef.current) return;

    let cancelled = false;
    loadKakaoSdk(appKey)
      .then(() => {
        if (cancelled || !containerRef.current) return;
        const kakao = window.kakao;
        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(originLat, originLng),
          level: 8,
        });

        const bounds = new kakao.maps.LatLngBounds();

        days.forEach((day, dayIdx) => {
          const color = DAY_COLORS[dayIdx % DAY_COLORS.length];
          const path: any[] = [];

          day.items.forEach((item, i) => {
            const pos = new kakao.maps.LatLng(item.lat, item.lng);
            path.push(pos);
            bounds.extend(pos);

            const content = document.createElement("div");
            const selected = item.placeId === selectedPlaceId;
            const placeTitle = item.nameEn ? `${item.nameKo} (${item.nameEn})` : item.nameKo;
            content.title = `${day.dayIndex}일차 ${i + 1}. ${placeTitle}`;
            content.style.cssText = `
              background:${color};color:#fff;border-radius:50%;
              width:${selected ? 36 : 26}px;height:${selected ? 36 : 26}px;display:flex;align-items:center;justify-content:center;
              font-size:${selected ? 14 : 12}px;font-weight:700;border:${selected ? 4 : 2}px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.35);
            `;
            content.textContent = String(i + 1);

            new kakao.maps.CustomOverlay({
              map,
              position: pos,
              content,
              yAnchor: 0.5,
            });
          });

          if (path.length > 1) {
            new kakao.maps.Polyline({
              map,
              path,
              strokeWeight: 4,
              strokeColor: color,
              strokeOpacity: 0.8,
              strokeStyle: "solid",
            });
          }
        });
        if (routePath.length > 1) {
          const detailedPath = routePath.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
          detailedPath.forEach((point) => bounds.extend(point));
          new kakao.maps.Polyline({ map, path: detailedPath, strokeWeight: 7, strokeColor: "#ffffff", strokeOpacity: .92, strokeStyle: "solid" });
          new kakao.maps.Polyline({ map, path: detailedPath, strokeWeight: 4, strokeColor: routeMode === "CAR" ? "#6f63c7" : "#3a96ce", strokeOpacity: .95, strokeStyle: "solid" });
        }
        souvenirShops.forEach((shop) => {
          const pos = new kakao.maps.LatLng(shop.lat, shop.lng); bounds.extend(pos);
          const content = document.createElement("button");
          content.type = "button"; content.title = `${shop.nameKo} · ${shop.items.join(", ")}`;
          content.style.cssText = "background:#fff7ed;border:2px solid #ea580c;border-radius:50%;width:32px;height:32px;display:flex;align-items:center;justify-content:center;box-shadow:0 2px 8px rgba(0,0,0,.25);font-size:15px;cursor:pointer";
          content.textContent = "🎁";
          new kakao.maps.CustomOverlay({ map, position: pos, content, yAnchor: .5 });
        });

        if (!bounds.isEmpty()) {
          map.setBounds(bounds);
        }

        // 지도 패널 크기가 바뀌어도(전체 지도 보기 토글 등) 타일이 올바르게 다시 그려지도록 감시
        const ro = new ResizeObserver(() => {
          map.relayout();
          if (!bounds.isEmpty()) map.setBounds(bounds);
        });
        ro.observe(containerRef.current);
        resizeObserverRef.current = ro;
      })
      .catch((err) => {
        if (errorRef.current) errorRef.current.textContent = err.message;
      });

    return () => {
      cancelled = true;
      resizeObserverRef.current?.disconnect();
    };
  }, [days, originLat, originLng, selectedPlaceId, souvenirShops, routePath, routeMode]);

  return (
    <div className="map-wrap">
      <div ref={containerRef} className="map-canvas" />
      <div ref={errorRef} className="map-error" />
    </div>
  );
}
