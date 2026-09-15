package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소 정본 한 건. 표는 S15P21E201-262 가 만들고 -545 가 출처·데이터 판 칸을 더했다.
 *
 * <p>🔴 <b>거의 읽기 전용이다.</b> setter 는 없고, 만드는 길은 {@link #imported} 하나뿐이다.
 *
 * <p>원래는 공개 생성자도 없었다 — <i>"수집·적재는 다른 파트가 하고 우리는 조회 API 만 만든다.
 * 나중에 쓰기가 필요해지면 그때 그 티켓이 필요한 것만 연다"</i>. <b>그 티켓이 S15P21E201-636 이다</b>:
 * 적재 코드가 저장소 어디에도 없어서 이 표가 통째로 비어 있었고, 그래서 추천 요청이 전부 후보 0건으로
 * 끝났다. 그래서 <b>필요한 것만</b> 연다 — 만들기만 열고 고치기는 안 연다. 한 번 넣은 행을 어떻게
 * 갱신할 것인가(폐업·이전·상호 변경)는 별개의 결정이라 여기서 미리 정하지 않는다.
 *
 * <p>영업시간·주차·예약·평점 칸은 여기 없다. -262 가 일부러 뺐고(담당 티켓이 아직 값을 안 정했다)
 * 그 칸이 필요해지면 -88·-97·-300 계열이 새 마이그레이션으로 더한다.
 */
@Entity
@Table(name = "place")
public class Place {

	@Id
	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "name_ko", nullable = false, length = 200)
	private String nameKo;

	/** 🔴 nullable 이다. 영문 검색(-462)은 이 칸이 있는 행에만 걸린다. */
	@Column(name = "name_en", length = 200)
	private String nameEn;

	/**
	 * 🔴 값 목록이 없는 자유 문자열이다. 마이그레이션 30~31행이 "S15P21E201-88 계열이 정할 자리" 라고
	 * 비워 뒀다. 그래서 자바에서 값을 검증하지 않는다 — 여기서 enum 을 만들면 우리가 그 결정을
	 * 대신 내리는 셈이 되고, 수집이 다른 값으로 들어오는 순간 조회가 전부 빈 결과가 된다.
	 */
	@Column(name = "category", length = 50)
	private String category;

	@Column(name = "address", length = 300)
	private String address;

	/** 🔴 lat 과 lng 는 함께 있거나 함께 없다 ({@code ck_place_origin_pair}). 거리 조회는 있는 행만 본다. */
	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** 이 장소 행을 어디서 가져왔는가. 자유 문자열 — MANUAL·TOURAPI·KAKAO 같은 목록은 아직 미확정이다. */
	@Column(name = "source_type", length = 50)
	private String sourceType;

	@Column(name = "source_id", length = 200)
	private String sourceId;

	/** 우리가 가져온 시각. {@link #observedAt} 과 다르다 — 어제 수집한 지난달 정보가 있을 수 있다. */
	@Column(name = "collected_at")
	private OffsetDateTime collectedAt;

	@Column(name = "observed_at")
	private OffsetDateTime observedAt;

	/**
	 * 어느 수집분에서 왔는가 (FR-REC-12). 🔴 추천 결과의 datasetVersion 과 맞춰 봐야 "그때 어느
	 * 데이터로 계산했나" 를 되짚을 수 있다.
	 */
	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	/**
	 * 영문 주소 (S15P21E201-217 · -430). 🔴 없으면 응답에서 <b>칸 자체를 뺀다</b> — 빈 문자열을
	 * 보내면 화면이 "영문 주소가 없다" 와 "있는데 못 불러왔다" 를 구분할 수 없다.
	 */
	@Column(name = "address_en", length = 300)
	private String addressEn;

	/**
	 * 대표 사진 주소.
	 *
	 * <p>🔴 정정 (2026-09-15, S15P21E201-146) — 위 문단은 더 이상 사실이 아니다. 지우지
	 * 않고 이유를 남긴다: 만들 당시엔 채우는 경로가 없어 항상 {@code null} 이었다("외부 사진
	 * 검색(-146·-480)이 붙어야 값이 생긴다"). 지금은 {@code TourApiPlaceLoader} 가 관광공사
	 * 자료 중 <b>저작권 유형이 {@code Type1}(공공누리 제1유형)인 것만</b> 채운다 — 대부분인
	 * {@code Type3}(제3자 저작물)는 재사용 전 저작권자 허락이 필요해 비워 둔다.
	 */
	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	/** 사진 출처 표기 문구. 저작권 표기 없이 남의 사진을 쓰지 않기 위해 주소와 짝으로 둔다. */
	@Column(name = "photo_source", length = 100)
	private String photoSource;

	/**
	 * 현장 안내용 지하철 출구 번호/이름 (S15P21E201-265). 예: "2호선 강남역 3번 출구".
	 * 채우는 경로가 아직 없어 대부분 {@code null} 이다 — 확인 상태를 따로 가질 필요가 없는
	 * 단순 안내 문구라 {@code place_feature} 가 아니라 이 칸 하나로 둔다.
	 */
	@Column(name = "subway_exit", length = 100)
	private String subwayExit;

	protected Place() {
	}

	/**
	 * 외부 자료에서 가져온 장소 한 건을 만든다 — S15P21E201-636.
	 *
	 * <p>🔴 <b>{@code placeId} 를 부르는 쪽이 준다.</b> 무작위로 만들면 같은 자료를 두 번 적재할 때
	 * 같은 가게가 두 행이 되고, 그러면 후보 수가 부풀고 정답이 한 행에만 붙어 나머지가 오답으로
	 * 학습된다. 원천의 식별자에서 <b>결정적으로</b> 만든 값을 넣는다
	 * ({@link com.gabolle.backend.place.loader.SbizPlaceLoader#placeIdOf}).
	 *
	 * <p>🔴 {@code category}·{@code address} 는 nullable 이지만 {@code nameKo} 는 아니다 —
	 * DB 가 그렇게 강제한다. 좌표는 <b>둘 다 있거나 둘 다 없어야</b> 한다
	 * ({@code ck_place_origin_pair}).
	 *
	 * @param collectedAt 우리가 가져온 시각
	 * @param observedAt 원천에서 관측된 시각. 모르면 {@code null} — 지어내지 않는다
	 * @param datasetVersion 어느 수집분인가. 🔴 이것이 없으면 추천 결과가
	 *     {@code VERSION_UNRESOLVED} 로 실패한다({@code BaselineRecommendationEngine})
	 */
	public static Place imported(UUID placeId, String nameKo, String category, String address,
			Double lat, Double lng, String sourceType, String sourceId,
			OffsetDateTime collectedAt, OffsetDateTime observedAt, String datasetVersion) {
		return imported(placeId, nameKo, category, address, lat, lng, sourceType, sourceId,
				collectedAt, observedAt, datasetVersion, null, null);
	}

	/**
	 * 사진까지 같이 넣는 판 — S15P21E201-146.
	 *
	 * @param photoUrl 대표 사진 주소. 자유 이용이 확인된 사진만 넣는다 — {@code photoSource} 와
	 *     반드시 짝으로 채운다. 모르면 {@code null} — 지어내지 않는다
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

	public String getAddress() {
		return address;
	}

	public Double getLat() {
		return lat;
	}

	public Double getLng() {
		return lng;
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

	/** 좌표가 둘 다 있는가. 거리 계산 전에 이것으로 거른다. */
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

	public String getSubwayExit() {
		return this.subwayExit;
	}
}
