package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 갈래를 연 기록 한 건. 어느 갈래에 데이터를 채울지 정하는 근거로 쓴다.
 *
 * <p>익명이 아니라 가명이다 — 이름·이메일은 담지 않지만 {@code tripId} 로 여행에, 여행은 사람에게
 * 이어진다. 여행 단위로 봐야 "한 사람이 여덟 번 연 것" 과 "여덟 사람이 한 번씩 연 것" 이 구분된다.
 * {@code tripId} 가 비어 있는 기록은 여행에 속하지 않는 전역 탐색 화면에서 연 것으로, 그 구분을
 * 못 하는 대신 사람에게 이어지지도 않는다.
 *
 * <p>갈래는 코드 문자열로 담는다. 열거형으로 매핑하면 갈래가 지워졌을 때 옛 기록을 되읽다가
 * 기동이 실패한다.
 */
@Entity
@Table(name = "place_facet_view")
public class PlaceFacetView {

	@Id
	@Column(name = "place_facet_view_id", nullable = false, updatable = false)
	private UUID placeFacetViewId;

	@Column(name = "facet_key", nullable = false, length = 40)
	private String facetKey;

	/** 여행 밖(전역 탐색)에서 연 기록은 비어 있다. */
	@Column(name = "trip_id")
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
	 * 여행 안에서 연 기록 한 건. 여행 없는 기록은 여기로 만들 수 없고 {@link #ofGlobal} 로 들어온다 —
	 * 여기서 {@code null} 을 받아 주면 여행 번호를 실수로 빠뜨린 호출과 전역 열람이 같은 모양이 된다.
	 */
	public static PlaceFacetView of(String facetKey, UUID tripId, OffsetDateTime viewedAt) {
		if (tripId == null) {
			throw new IllegalArgumentException("어느 여행인지 없이 열람 기록을 만들 수 없다 — 전역 열람은 ofGlobal 을 쓴다");
		}
		return create(facetKey, tripId, viewedAt);
	}

	/** 여행에 안 묶인 전역 탐색에서 연 기록 한 건. */
	public static PlaceFacetView ofGlobal(String facetKey, OffsetDateTime viewedAt) {
		return create(facetKey, null, viewedAt);
	}

	private static PlaceFacetView create(String facetKey, UUID tripId, OffsetDateTime viewedAt) {
		if (facetKey == null || facetKey.isBlank()) {
			throw new IllegalArgumentException("갈래 코드 없이 열람 기록을 만들 수 없다");
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
