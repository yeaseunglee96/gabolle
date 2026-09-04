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
 * <p>🔴 <b>읽기 전용이다.</b> 이 서비스는 장소를 만들지도 고치지도 않는다 — 수집·적재는 다른 파트가
 * 하고 우리는 조회 API 만 만든다. 그래서 setter 도 공개 생성자도 두지 않는다. 나중에 쓰기가 필요해지면
 * 그때 그 티켓이 필요한 것만 연다. 지금 열어 두면 "누가 place 를 쓰는가" 에 답이 둘이 된다.
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

	protected Place() {
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
}
