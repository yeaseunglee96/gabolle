package com.gabolle.backend.place.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소 정본 한 건. setter 가 없고 만드는 길은 {@link #imported} 하나뿐이다 — 한 번 넣은 행을
 * 어떻게 갱신할지(폐업·이전·상호 변경)는 아직 정하지 않았다.
 * 영업시간·주차·예약·평점 칸은 이 표에 없다.
 */
@Entity
@Table(name = "place")
public class Place {

	@Id
	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "name_ko", nullable = false, length = 200)
	private String nameKo;

	/** nullable 이다. 영문 검색은 이 칸이 있는 행에만 걸린다. */
	@Column(name = "name_en", length = 200)
	private String nameEn;

	/**
	 * 값 목록이 없는 자유 문자열이라 자바에서 검증하지 않는다. enum 을 두면 수집이 다른 값으로
	 * 들어오는 순간 조회가 전부 빈 결과가 된다.
	 */
	@Column(name = "category", length = 50)
	private String category;

	@Column(name = "address", length = 300)
	private String address;

	/** lat 과 lng 는 함께 있거나 함께 없다 ({@code ck_place_origin_pair}). 거리 조회는 있는 행만 본다. */
	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	/**
	 * 언제 문을 닫았나. {@code null} 은 영업 중이 아니라 모른다는 뜻이다 — 해수욕장·전망대처럼
	 * 인허가 자료와 이어지지 않는 장소가 많다. 추천에서 빼는 것은 값이 실제로 있는 줄뿐이다
	 * ({@code PlaceRepository.findWithinBoundingBox…}).
	 */
	@Column(name = "closed_on")
	private LocalDate closedOn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** 이 장소 행을 어디서 가져왔는가. 값 목록이 없는 자유 문자열이다. */
	@Column(name = "source_type", length = 50)
	private String sourceType;

	@Column(name = "source_id", length = 200)
	private String sourceId;

	/** 우리가 가져온 시각. {@link #observedAt} 과 다르다 — 어제 수집한 지난달 정보가 있을 수 있다. */
	@Column(name = "collected_at")
	private OffsetDateTime collectedAt;

	@Column(name = "observed_at")
	private OffsetDateTime observedAt;

	/** 어느 수집분에서 왔는가. 추천 결과의 datasetVersion 과 맞춰 봐야 어느 데이터로 계산했는지 되짚을 수 있다. */
	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	/**
	 * 영문 주소. 없으면 응답에서 칸 자체를 뺀다 — 빈 문자열로 보내면 화면이 "영문 주소가 없다" 와
	 * "있는데 못 불러왔다" 를 구분할 수 없다.
	 */
	@Column(name = "address_en", length = 300)
	private String addressEn;

	/**
	 * 대표 사진 주소. 관광공사 자료 중 저작권 유형이 {@code Type1}(공공누리 제1유형)인 것만 채운다 —
	 * 대부분인 {@code Type3}(제3자 저작물)는 재사용 전 저작권자 허락이 필요해 비워 둔다.
	 */
	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	/** 사진 출처 표기 문구. 저작권 표기 없이 남의 사진을 쓰지 않기 위해 주소와 짝으로 둔다. */
	@Column(name = "photo_source", length = 100)
	private String photoSource;

	/**
	 * 그 사진이 무엇을 찍은 것인가. {@link #photoSource}(누가 준 사진인가)와 다른 질문이다.
	 * {@code null} 은 모른다는 뜻이며, 안 알아본 것을 {@code SELF} 로 채우지 않는다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "photo_subject", length = 20)
	private PhotoSubject photoSubject;

	/**
	 * 현장 안내용 지하철 출구 번호/이름. 예: "2호선 강남역 3번 출구". 채우는 경로가 아직 없어
	 * 대부분 {@code null} 이다 — 확인 상태를 따로 가질 필요가 없는 안내 문구라
	 * {@code place_feature} 가 아니라 이 칸 하나로 둔다.
	 */
	@Column(name = "subway_exit", length = 100)
	private String subwayExit;

	/**
	 * 추천 후보로 써도 되는가 — S15P21E201-1426. 출처({@link #sourceType})와 다른 질문이다.
	 *
	 * <p>{@code null} 이 아니다. DB 기본값이 {@code CURATED} 라 이관 전에 들어온 행은 전부
	 * 그 값이고, 새 행은 만드는 자리가 정한다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "curation_status", nullable = false, length = 20)
	private CurationStatus curationStatus = CurationStatus.CURATED;

	protected Place() {
	}

	/**
	 * 외부 자료에서 가져온 장소 한 건을 만든다.
	 *
	 * <p>{@code placeId} 를 부르는 쪽이 준다. 무작위로 만들면 같은 자료를 두 번 적재할 때 같은
	 * 가게가 두 행이 되므로, 원천의 식별자에서 결정적으로 만든 값을 넣는다
	 * ({@link com.gabolle.backend.place.loader.SbizPlaceLoader#placeIdOf}).
	 * 좌표는 둘 다 있거나 둘 다 없어야 한다 ({@code ck_place_origin_pair}).
	 *
	 * @param observedAt 원천에서 관측된 시각. 모르면 {@code null} — 지어내지 않는다
	 * @param datasetVersion 없으면 추천 결과가 {@code VERSION_UNRESOLVED} 로 실패한다
	 */
	public static Place imported(UUID placeId, String nameKo, String category, String address,
			Double lat, Double lng, String sourceType, String sourceId,
			OffsetDateTime collectedAt, OffsetDateTime observedAt, String datasetVersion) {
		return imported(placeId, nameKo, category, address, lat, lng, sourceType, sourceId,
				collectedAt, observedAt, datasetVersion, null, null);
	}

	/**
	 * 사진까지 같이 넣는 판.
	 *
	 * @param photoUrl 자유 이용이 확인된 사진만 넣는다. 모르면 {@code null} — 지어내지 않는다
	 * @param photoSource 사진 출처 표기 문구. {@code photoUrl} 이 있으면 이것도 있어야 한다
	 */
	public static Place imported(UUID placeId, String nameKo, String category, String address,
			Double lat, Double lng, String sourceType, String sourceId,
			OffsetDateTime collectedAt, OffsetDateTime observedAt, String datasetVersion,
			String photoUrl, String photoSource) {
		Place place = new Place();
		place.placeId = placeId;
		place.nameKo = nameKo;
		place.category = category;
		place.address = address;
		place.lat = lat;
		place.lng = lng;
		place.createdAt = collectedAt;
		place.sourceType = sourceType;
		place.sourceId = sourceId;
		place.collectedAt = collectedAt;
		place.observedAt = observedAt;
		place.datasetVersion = datasetVersion;
		place.photoUrl = photoUrl;
		place.photoSource = photoSource;
		return place;
	}

	/**
	 * 사용자가 기록에 붙이려고 고른 장소 — S15P21E201-1426.
	 *
	 * <p>{@link #imported} 와 갈라 두는 이유는 {@code curationStatus} 하나 때문이 아니다.
	 * <b>여기서 만든 행은 아무도 안 본 값이다</b> — 이름·주소·좌표가 남의 검색 결과 그대로다.
	 * 같은 공장에서 찍으면 나중에 누가 사진이나 피처를 붙일 때 검증된 행과 구분이 안 된다.
	 *
	 * <p>🔴 {@code datasetVersion} 을 안 채운다. 그 칸은 「어느 수집분에서 왔나」인데 이 행은
	 * 수집분에 속하지 않는다. 지어내면 추천 재현이 그 가짜 값을 따라간다. 추천 후보에
	 * 안 들어가므로 {@code VERSION_UNRESOLVED} 로 실패할 자리도 없다.
	 *
	 * @param sourceId 원천의 식별자. 이것과 {@code sourceType} 이 같으면 같은 장소다
	 *     ({@code uq_place_source})
	 */
	public static Place userSubmitted(UUID placeId, String nameKo, String category, String address,
			Double lat, Double lng, String sourceType, String sourceId, OffsetDateTime createdAt) {
		Place place = new Place();
		place.placeId = placeId;
		place.nameKo = nameKo;
		place.category = category;
		place.address = address;
		place.lat = lat;
		place.lng = lng;
		place.createdAt = createdAt;
		place.sourceType = sourceType;
		place.sourceId = sourceId;
		place.collectedAt = createdAt;
		place.curationStatus = CurationStatus.USER_SUBMITTED;
		return place;
	}

	public CurationStatus getCurationStatus() {
		return curationStatus;
	}

	public UUID getPlaceId() {
		return placeId;
	}

	public String getNameKo() {
		return nameKo;
	}

	public String getNameEn() {
		return nameEn;
	}

	public String getCategory() {
		return category;
	}

	/**
	 * 갈래가 비어 있을 때만 채운다. 이미 값이 있으면 아무것도 안 하고 {@code false} 를 낸다.
	 *
	 * <p>{@link #attachPhoto} 와 같은 이유로 있다 — 장소 적재기는 이미 있는 장소를 건너뛰고
	 * 고치지 않으므로, 적재 규칙이 나중에 바뀌어도 먼저 들어온 행은 영영 안 따라온다.
	 * 2026-09-21 에 숙박 65곳이 정확히 그 상태였다 (S15P21E201-1383) — 갈래를 비우기로 한
	 * 결정을 뒤집었는데 이미 들어와 있던 행은 그대로 비어 있었다.
	 *
	 * <p>🔴 <b>있는 값은 덮지 않는다.</b> 덮게 두면 적재를 다시 돌릴 때마다 손으로 고친 갈래가
	 * 조용히 원래대로 돌아가고, 아무 기록도 안 남는다. 채우는 것은 「모름 → 앎」 한 방향뿐이다.
	 *
	 * @return 실제로 채웠으면 {@code true}. 부르는 쪽이 몇 곳을 채웠는지 세는 데 쓴다
	 */
	public boolean fillMissingCategory(String category) {
		if (category == null || category.isBlank()) {
			throw new IllegalArgumentException("빈 갈래로 채울 수 없다 — 모르면 이 메서드를 부르지 않는다");
		}
		if (this.category != null && !this.category.isBlank()) {
			return false;
		}
		this.category = category;
		return true;
	}

	public String getAddress() {
		return address;
	}

	public Double getLat() {
		return lat;
	}

	public Double getLng() {
		return lng;
	}

	/** 폐업일자. {@code null} 은 「모른다」다 — 「영업 중」이 아니다. */
	public LocalDate getClosedOn() {
		return closedOn;
	}

	/**
	 * 폐업일자를 적는다 — 인허가 자료가 이어졌을 때만 부른다. {@code null} 을 주어 「닫았다」를
	 * 「모른다」로 되돌리는 데도 쓴다. 잘못 이어졌던 것이 풀릴 때 필요하다.
	 */
	public void recordClosedOn(LocalDate closedOn) {
		this.closedOn = closedOn;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public String getSourceType() {
		return sourceType;
	}

	public String getSourceId() {
		return sourceId;
	}

	public OffsetDateTime getCollectedAt() {
		return collectedAt;
	}

	public OffsetDateTime getObservedAt() {
		return observedAt;
	}

	public String getDatasetVersion() {
		return datasetVersion;
	}

	public boolean hasCoordinates() {
		return lat != null && lng != null;
	}

	public String getAddressEn() {
		return this.addressEn;
	}

	public String getPhotoUrl() {
		return this.photoUrl;
	}

	public String getPhotoSource() {
		return this.photoSource;
	}

	public PhotoSubject getPhotoSubject() {
		return this.photoSubject;
	}

	/**
	 * 이미 있는 장소에 사진을 붙인다. 장소 적재기는 이미 있는 장소를 건너뛰고 고치지 않는데,
	 * 사진은 나중에 다른 원천에서 오는 값이라 그 규칙에 묶으면 영영 못 채운다.
	 * 출처 없는 사진이 들어가지 않도록 주소만 받는 메서드는 두지 않는다.
	 *
	 * @param photoSubject 무엇을 찍은 사진인가. 모르면 {@code null} — {@code SELF} 로 채우지 않는다
	 */
	public void attachPhoto(String photoUrl, String photoSource, PhotoSubject photoSubject) {
		if (photoUrl == null || photoUrl.isBlank()) {
			throw new IllegalArgumentException("photoUrl 없이 사진을 붙일 수 없다");
		}
		if (photoSource == null || photoSource.isBlank()) {
			throw new IllegalArgumentException(
					"photoSource 없이 사진을 붙일 수 없다 — 출처 표기 없이 남의 사진을 쓰지 않는다");
		}
		this.photoUrl = photoUrl;
		this.photoSource = photoSource;
		this.photoSubject = photoSubject;
	}

	/** 사진이 무엇을 찍은 것인가. 식당·해수욕장도 같은 칸을 쓰므로 축제에만 맞는 이름을 쓰지 않는다. */
	public enum PhotoSubject {

		/** 이 장소(축제 포함) 자체를 찍은 사진. */
		SELF,

		/** 이 축제가 열리는 곳을 찍은 사진. 축제 모습이 아니다. */
		VENUE
	}

	public String getSubwayExit() {
		return this.subwayExit;
	}

	/**
	 * 이미 있는 장소에 지하철 출구 안내를 붙인다. {@link #attachPhoto} 와 같은 이유로 따로 있다 —
	 * 적재할 때 함께 오는 값이 아니라 나중에 조사해서 채우는 값이다.
	 *
	 * @param subwayExit 예: {@code "2호선 강남역 3번 출구"}. 비우려면 이 메서드를 부르지 않는다 —
	 *     빈 문자열을 넣지 않는다
	 */
	public void assignSubwayExit(String subwayExit) {
		if (subwayExit == null || subwayExit.isBlank()) {
			throw new IllegalArgumentException(
					"subwayExit 없이 지하철 출구를 붙일 수 없다 — 모르면 이 메서드를 부르지 않는다");
		}
		this.subwayExit = subwayExit;
	}
}
