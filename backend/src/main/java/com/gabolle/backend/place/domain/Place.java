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

	/**
	 * 언제 문을 닫았나 — S15P21E201-1341.
	 *
	 * <p>🔴 <b>{@code null} 은 「영업 중」이 아니라 「모른다」다.</b> 인허가 자료와 안 이어진
	 * 장소가 많다 — 해수욕장·전망대는 애초에 음식·주류 인허가가 없다. 그것을 폐업으로
	 * 떨어뜨리면 멀쩡한 곳이 통째로 사라진다.
	 *
	 * <p>그래서 추천에서 빼는 것은 <b>값이 실제로 있는 줄뿐</b>이다
	 * ({@code PlaceRepository.findWithinBoundingBox…}).
	 */
	@Column(name = "closed_on")
	private LocalDate closedOn;

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
	 * 그 사진이 <b>무엇을 찍은 것인가</b> — S15P21E201-1006.
	 *
	 * <p>🔴 {@link #photoSource} 와 <b>다른 질문</b>이다. 그쪽은 «누가 준 사진인가»(출처),
	 * 이쪽은 «무엇을 찍은 사진인가»(피사체)다. 한 칸에 담으면 둘 중 하나는 반드시 거짓이 되고,
	 * 자유 문장으로 적으면 화면이 그것을 읽어 판단할 수 없다.
	 *
	 * <p>🔴 {@code null} 은 <b>모른다</b>는 뜻이다. 지금 있는 사진이 전부 여기다 —
	 * 그것을 {@code SELF} 로 채우지 않는다. «안 알아본 것»을 «확인했더니 맞더라»로 뒤집는
	 * 것이 이 저장소가 알레르기 표시에서 겪은 바로 그 사고다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "photo_subject", length = 20)
	private PhotoSubject photoSubject;

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

	/** 폐업일자. {@code null} 은 「모른다」다 — 「영업 중」이 아니다. */
	public LocalDate getClosedOn() {
		return closedOn;
	}

	/**
	 * 폐업일자를 적는다 — 인허가 자료가 이어졌을 때만 부른다.
	 *
	 * <p>🔴 <b>모르는 것을 {@code null} 로 되돌리는 데도 쓸 수 있다.</b> 잘못 이어졌던 것이
	 * 풀리면 「닫았다」를 「모른다」로 되돌려야 한다 — 한번 적은 폐업이 영영 안 지워지면,
	 * 잘못 붙은 가게 하나가 영영 추천에서 사라진다.
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

	public PhotoSubject getPhotoSubject() {
		return this.photoSubject;
	}

	/**
	 * 이미 있는 장소에 사진을 붙인다 — S15P21E201-1006.
	 *
	 * <p>🔴 <b>장소 적재기는 이미 있는 장소를 건드리지 않는다.</b>(«이미 있는 장소는 건너뛴다 —
	 * 고치지 않는다») 그 규칙은 그대로 두고, 사진만 따로 갱신할 길을 연다. 사진은 장소 본문과
	 * 달리 <b>나중에 다른 원천에서 오는 값</b>이라 같은 규칙으로 묶으면 영영 못 채운다.
	 *
	 * <p>🔴 출처를 함께 받는다. 주소만 받는 메서드를 두지 않는 이유는, 그러면 언젠가
	 * <b>출처 없는 사진</b>이 들어가기 때문이다 — {@link #photoSource} 가 있는 이유가 그것이다.
	 *
	 * @param photoSubject 무엇을 찍은 사진인가. <b>모르면 {@code null}</b> 을 준다 —
	 *     모르는 것을 {@code SELF} 로 채우지 않는다
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

	/**
	 * 사진이 무엇을 찍은 것인가 — S15P21E201-1006.
	 *
	 * <p>{@code EVENT} 가 아니라 {@code SELF} 인 이유: 이 값은 {@code place} 에 살고 식당·
	 * 해수욕장도 같은 칸을 쓴다. «행사»는 축제에만 맞는 말이라 뜻이 안 통한다.
	 */
	public enum PhotoSubject {

		/** 이 장소(축제 포함) 자체를 찍은 사진. */
		SELF,

		/** 이 축제가 <b>열리는 곳</b>을 찍은 사진. 축제 모습이 아니다. */
		VENUE
	}

	public String getSubwayExit() {
		return this.subwayExit;
	}

	/**
	 * 이미 있는 장소에 지하철 출구 안내를 붙인다 — S15P21E201-479.
	 *
	 * <p>{@link #attachPhoto} 와 같은 이유로 따로 있다. 지하철 출구는 장소를 적재할 때 함께
	 * 오는 값이 아니라 <b>나중에 조사해서 채우는 값</b>이라, 장소 적재기의 «이미 있는 장소는
	 * 건너뛴다» 규칙에 묶으면 영영 못 채운다.
	 *
	 * @param subwayExit 예: {@code "2호선 강남역 3번 출구"}. 비우려면(확인해 보니 지하철로
	 *     못 가는 곳이었다 등) 이 메서드를 부르지 않는다 — 빈 문자열을 넣지 않는다
	 */
	public void assignSubwayExit(String subwayExit) {
		if (subwayExit == null || subwayExit.isBlank()) {
			throw new IllegalArgumentException(
					"subwayExit 없이 지하철 출구를 붙일 수 없다 — 모르면 이 메서드를 부르지 않는다");
		}
		this.subwayExit = subwayExit;
	}
}
