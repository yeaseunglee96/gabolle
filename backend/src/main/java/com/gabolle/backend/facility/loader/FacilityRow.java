package com.gabolle.backend.facility.loader;

import java.util.Map;

import com.gabolle.backend.facility.domain.FacilityKind;

/**
 * 파일 한 줄을 읽고 다듬은 결과 — DB 에 넣을 모양 그대로다 (S15P21E201-2052).
 *
 * @param kind 어느 파일에서 왔나
 * @param sourceId 원천 번호. 종류 안에서 유일하다
 * @param name 화면에 보일 이름 (다듬은 글)
 * @param address 주소. 모르면 {@code null}
 * @param lat 위도 (역에 붙은 시설은 역 자료로 바로잡은 값)
 * @param lng 경도
 * @param detail 종류별 칸. 모르는 값은 {@code null} 로 비워 둔다
 */
public record FacilityRow(FacilityKind kind, String sourceId, String name, String address, double lat, double lng,
		Map<String, Object> detail) {
}
