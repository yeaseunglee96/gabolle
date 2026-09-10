package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 갈래를 연 기록 한 건 (S15P21E201-475).
 *
 * <p>여덟 갈래에 같은 힘을 들일 수 없다. 아무도 안 여는 갈래에 데이터를 채우는 동안 사람들이
 * 매번 여는 갈래가 비어 있을 수 있다. 기록이 없으면 그 판단을 감으로 하게 되고, 감으로 정한
 * 우선순위는 나중에 되짚을 수 없다.
 *
 * <h2>이 기록은 익명이 아니라 가명이다</h2>
 * 이름·이메일 같은 값은 담지 않지만 {@code tripId} 로 여행에 이어지고, 여행은 사람에게
 * 이어진다. 그 사실을 감추지 않는다. 여행 단위로 봐야 "한 사람이 여덟 번 연 것" 과 "여덟
 * 사람이 한 번씩 연 것" 이 구분되고, 그 구분이 없으면 집계가 우선순위 판단에 못 쓰인다.
 *
 * <p>갈래는 코드 문자열로 담는다. 갈래가 늘거나 이름이 바뀌어도 옛 기록은 그때의 값으로
 * 남아야 한다 — 열거형으로 매핑하면 지워진 값을 되읽을 때 기동이 실패한다.
 */
@Entity
@Table(name = "place_facet_view")
public class PlaceFacetView {

	@Id
	@Column(name = "place_facet_view_id", nullable = false, updatable = false)
	private UUID placeFacetViewId;

	@Column(name = "facet_key", nullable = false, length = 40)
	private String facetKey;

	@Column(name = "trip_id", nullable = false)
	private UUID tripId;

	@Column(name = "viewed_at", nullable = false)
	private OffsetDateTime viewedAt;

	protected PlaceFacetView() {
	}

	private PlaceFacetView(UUID placeFacetViewId, String facetKey, UUID tripId, OffsetDateTime viewedAt) {
		this.placeFacetViewId = placeFacetViewId;
		this.facetKey = facetKey;
		this.tripId = tripId;
		this.viewedAt = viewedAt;
	}

	/**
	 * 기록 한 건을 만든다.
	 *
	 * <p>값 검사를 여기서 한다 — 빈 갈래 코드나 여행 없는 기록은 아무 질문에도 답하지 못하므로
	 * 쌓아 둘 이유가 없다.
	 */
	public static PlaceFacetView of(String facetKey, UUID tripId, OffsetDateTime viewedAt) {
		if (facetKey == null || facetKey.isBlank()) {
			throw new IllegalArgumentException("갈래 코드 없이 열람 기록을 만들 수 없다");
		}
		if (tripId == null) {
			throw new IllegalArgumentException("어느 여행인지 없이 열람 기록을 만들 수 없다");
		}
		if (viewedAt == null) {
			throw new IllegalArgumentException("언제 열었는지 없이 열람 기록을 만들 수 없다");
		}
		return new PlaceFacetView(UUID.randomUUID(), facetKey, tripId, viewedAt);
	}

	public UUID placeFacetViewId() {
		return this.placeFacetViewId;
	}

	public String facetKey() {
		return this.facetKey;
	}

	public UUID tripId() {
		return this.tripId;
	}

	public OffsetDateTime viewedAt() {
		return this.viewedAt;
	}
}
