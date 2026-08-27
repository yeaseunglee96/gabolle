import { useEffect, useRef, useState } from "react";
import { loadKakaoSdk } from "./KakaoMap";
import type { RouteStep } from "../types";

interface Waypoint { lat: number; lng: number; seq: number }

interface Props {
  fullPath: [number, number][];
  steps: RouteStep[];
  activeStepIndex: number;
  origin: Waypoint;
  destination: Waypoint;
  userPos: { lat: number; lng: number; heading?: number | null } | null;
  mode: "TRANSIT" | "CAR";
  en: boolean;
}

type StepKind = "WALK" | "BUS" | "SUBWAY" | "DRIVE";

function stepKind(step: RouteStep, mode: "TRANSIT" | "CAR"): StepKind {
  if (mode === "CAR") return "DRIVE";
  const vehicle = step.vehicle ?? "";
  if (!vehicle || vehicle.includes("도보") || /walk/i.test(vehicle)) return "WALK";
  if (vehicle.includes("지하철") || /subway|metro/i.test(vehicle)) return "SUBWAY";
  return "BUS";
}

// strokeWeight를 눈에 띄게 굵게, 색은 채도 높은 원색으로 - "선이 티가 나야 한다"는 요구에 맞춘다.
// strokeLineCap/strokeLineJoin은 카카오맵 API 옵션에 없는 값이라 빼고, 실제 동작이 검증된
// KakaoMap.tsx(메인 일정 지도)가 쓰는 옵션(strokeWeight/strokeColor/strokeOpacity/strokeStyle)만 쓴다.
const KIND_STYLE: Record<StepKind, { color: string; weight: number; dash: string }> = {
  WALK: { color: "#3f3d46", weight: 6, dash: "shortdot" },
  BUS: { color: "#1d5fe0", weight: 7, dash: "solid" },
  SUBWAY: { color: "#9333ea", weight: 7, dash: "solid" },
  DRIVE: { color: "#6f63c7", weight: 7, dash: "solid" },
};

const KIND_LABEL: Record<StepKind, { ko: string; en: string }> = {
  WALK: { ko: "도보", en: "Walk" },
  BUS: { ko: "버스", en: "Bus" },
  SUBWAY: { ko: "지하철", en: "Subway" },
  DRIVE: { ko: "자동차", en: "Drive" },
};

/**
 * 턴바이턴(네비게이션) 화면 전용 지도.
 *
 * KakaoMap.tsx(메인 일정 지도)와 똑같은 방식으로 동작한다 - 데이터가 바뀔 때마다
 * `new kakao.maps.Map(...)`으로 지도를 통째로 새로 만든다. 기존 지도 인스턴스를
 * 재사용하며 오버레이만 갱신하는 방식은 effect 실행 순서·`ready` 상태 경합에 취약해서
 * 실제로 선이 안 보이는 문제가 반복됐다 - 검증된 패턴을 그대로 따른다.
 */
export function NavigateMap({ fullPath, steps, activeStepIndex, origin, destination, userPos, mode, en }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const highlightLinesRef = useRef<any[]>([]);
  const userMarkerRef = useRef<any>(null);
  const resizeObserverRef = useRef<ResizeObserver | null>(null);
  const [ready, setReady] = useState(false);
  const [follow, setFollow] = useState(true);
  const [kindsUsed, setKindsUsed] = useState<StepKind[]>([]);

  // 지도 생성 + 전체 경로선 + 순서번호 마커. KakaoMap.tsx와 동일하게, 경로가 바뀔 때마다 전부 새로 그린다.
  useEffect(() => {
    const appKey = import.meta.env.VITE_KAKAO_JS_KEY as string | undefined;
    if (!appKey) {
      if (errorRef.current) errorRef.current.textContent = en ? "Kakao map key is not set." : "카카오 JS 키가 설정되지 않았습니다.";
      return;
    }
    if (!containerRef.current) return;
    let cancelled = false;

    loadKakaoSdk(appKey).then(() => {
      if (cancelled || !containerRef.current) return;
      try {
        const kakao = window.kakao;
        const map = new kakao.maps.Map(containerRef.current, { center: new kakao.maps.LatLng(origin.lat, origin.lng), level: 5 });
        mapRef.current = map;
        if (errorRef.current) errorRef.current.textContent = "";

        const bounds = new kakao.maps.LatLngBounds();
        const accent = mode === "CAR" ? "#6f63c7" : "#1d5fe0";

        if (fullPath.length > 1) {
          const casing = fullPath.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
          casing.forEach((point: any) => bounds.extend(point));
          new kakao.maps.Polyline({ map, path: casing, strokeWeight: 11, strokeColor: "#ffffff", strokeOpacity: 1, strokeStyle: "solid" });
        }

        const seenKinds = new Set<StepKind>();
        let drewAnySegment = false;
        steps.forEach((step) => {
          if (step.path.length < 2) return;
          const kind = stepKind(step, mode);
          seenKinds.add(kind);
          drewAnySegment = true;
          const style = KIND_STYLE[kind];
          const path = step.path.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
          new kakao.maps.Polyline({ map, path, strokeWeight: style.weight, strokeColor: style.color, strokeOpacity: 1, strokeStyle: style.dash });
        });
        if (!drewAnySegment && fullPath.length > 1) {
          const path = fullPath.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
          new kakao.maps.Polyline({ map, path, strokeWeight: 7, strokeColor: accent, strokeOpacity: 1, strokeStyle: "solid" });
        }
        setKindsUsed(Array.from(seenKinds));

        const pin = (pos: Waypoint, bg: string) => {
          const content = document.createElement("div");
          content.style.cssText = `background:${bg};color:#fff;border-radius:50%;width:30px;height:30px;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:800;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.35);`;
          content.textContent = String(pos.seq);
          const latlng = new kakao.maps.LatLng(pos.lat, pos.lng);
          bounds.extend(latlng);
          new kakao.maps.CustomOverlay({ map, position: latlng, content, yAnchor: 0.5, zIndex: 20 });
        };
        pin(origin, "#8a8d97");
        pin(destination, accent);

        if (!bounds.isEmpty()) map.setBounds(bounds);
        map.relayout();
        if (!bounds.isEmpty()) map.setBounds(bounds);

        kakao.maps.event.addListener(map, "dragstart", () => setFollow(false));

        resizeObserverRef.current?.disconnect();
        const ro = new ResizeObserver(() => {
          map.relayout();
          if (!bounds.isEmpty()) map.setBounds(bounds);
        });
        ro.observe(containerRef.current);
        resizeObserverRef.current = ro;

        setReady(true);
      } catch (err) {
        if (errorRef.current) errorRef.current.textContent = err instanceof Error ? `지도를 그리지 못했습니다: ${err.message}` : String(err);
      }
    }).catch((err) => { if (errorRef.current) errorRef.current.textContent = err.message; });

    return () => {
      cancelled = true;
      resizeObserverRef.current?.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fullPath, steps, mode, origin.seq, destination.seq]);

  // 현재 구간 스포트라이트 - activeStepIndex만 바뀔 때는 지도를 다시 만들지 않고 강조선만 갱신한다
  useEffect(() => {
    if (!ready || !mapRef.current) return;
    const kakao = window.kakao;
    highlightLinesRef.current.forEach((line) => line.setMap(null));
    highlightLinesRef.current = [];
    const step = steps[activeStepIndex];
    if (!step || step.path.length < 2) return;
    const path = step.path.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
    highlightLinesRef.current.push(new kakao.maps.Polyline({ map: mapRef.current, path, strokeWeight: 9, strokeColor: "#f59e0b", strokeOpacity: 1, strokeStyle: "shortdash" }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, activeStepIndex, steps]);

  // 사용자 실시간 위치 마커 - "따라가기"가 켜져 있으면 지도를 사용자 위치로 계속 이동시킨다
  useEffect(() => {
    if (!ready || !mapRef.current) return;
    const kakao = window.kakao;
    const map = mapRef.current;
    if (!userPos) {
      userMarkerRef.current?.setMap(null);
      userMarkerRef.current = null;
      return;
    }
    const latlng = new kakao.maps.LatLng(userPos.lat, userPos.lng);
    if (!userMarkerRef.current) {
      const content = document.createElement("div");
      content.className = "nav-user-dot";
      content.innerHTML = `<span class="nav-user-dot-pulse"></span><span class="nav-user-dot-core"></span>`;
      userMarkerRef.current = new kakao.maps.CustomOverlay({ map, position: latlng, content, yAnchor: 0.5, zIndex: 50 });
    } else {
      userMarkerRef.current.setPosition(latlng);
    }
    if (follow) map.panTo(latlng);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, userPos, follow]);

  return (
    <div className="nav-map-block">
      <div className="map-wrap nav-map-wrap">
        <div ref={containerRef} className="map-canvas" />
        <div ref={errorRef} className="map-error" />
        {userPos && !follow && (
          <button type="button" className="nav-recenter-btn" onClick={() => setFollow(true)}>
            {en ? "Recenter" : "내 위치로"}
          </button>
        )}
      </div>
      {kindsUsed.length > 0 && (
        <div className="map-legend nav-map-legend">
          {kindsUsed.map((kind) => (
            <span key={kind}>
              <i className={`legend-line nav-legend-line-${kind.toLowerCase()}`} />
              {en ? KIND_LABEL[kind].en : KIND_LABEL[kind].ko}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
