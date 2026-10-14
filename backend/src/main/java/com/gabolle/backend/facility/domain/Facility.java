package com.gabolle.backend.facility.domain;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 근처 편의시설 한 곳 = 한 행 (S15P21E201-2052). 주차장·약국·공중화장실·지하철 물품보관함이 한 표에 든다.
 *
 * <p>장소({@code place}) 표와 섞지 않는다 — 추천·탐색·일정이 읽는 후보 목록에 화장실이 끼면 안 된다. 그래서 {@code place_feature} 도
 * 쓰지 않고, 종류마다 다른 칸은 {@link #detail}(JSON 객체)에 둔다. 모양은 {@code FacilityReader} 설명에 있다.
 *
 * <p>같은 {@code (kind, sourceId)} 는 한 행이다. 적재기를 다시 돌리면 {@link #refresh} 로 갱신하고 행은 늘지 않는다. 행 번호는
 * {@code (kind, sourceId)} 에서 언제나 같게 만든다({@link #idOf}) — 지웠다가 다시 넣어도 앱이 들고 있는 번호가 그대로다.
 */
@Entity
@Table(name = "facility")
public class Facility {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	@Id
	@Column(name = "facility_id", nullable = false, updatable = false)
	private UUID facilityId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false, length = 20)
	private FacilityKind kind;

	@Column(name = "source_id", nullable = false, updatable = false, length = 200)
	private String sourceId;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	/** 주소를 모르면 {@code null}. 지하철 환승주차장처럼 주소 대신 위치 설명만 있는 곳도 비어 있을 수 있다. */
	@Column(name = "address", length = 300)
	private String address;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	/** 종류별 칸. JSON 객체이고 값이 없는 칸은 {@code null} 로 둔다 — 모른다는 것을 0 이나 빈 글자로 바꿔 적지 않는다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "detail", nullable = false)
	private Map<String, Object> detail;

	@Column(name = "dataset_version", nullable = false, length = 100)
	private String datasetVersion;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	protected Facility() {
	}

	public static Facility create(FacilityKind kind, String sourceId, String name, String address, double lat, double lng,
			Map<String, Object> detail, String datasetVersion, OffsetDateTime collectedAt) {
		Facility facility = new Facility();
		facility.facilityId = idOf(kind, sourceId);
		facility.kind = Objects.requireNonNull(kind);
		facility.sourceId = Objects.requireNonNull(sourceId);
		facility.name = Objects.requireNonNull(name);
		facility.address = address;
		facility.lat = lat;
		facility.lng = lng;
		facility.detail = new LinkedHashMap<>(detail);
		facility.datasetVersion = Objects.requireNonNull(datasetVersion);
		facility.collectedAt = Objects.requireNonNull(collectedAt);
		return facility;
	}

	/** {@code (종류, 원천 번호)} 에서 언제나 같은 행 번호 — 앞머리가 다른 표의 번호와 겹치지 않게 한다. */
	public static UUID idOf(FacilityKind kind, String sourceId) {
		return UUID.nameUUIDFromBytes(("gabolle:facility:" + kind.name() + ":" + sourceId)
				.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 같은 행을 새 수집분의 값으로 고친다. <b>값이 하나라도 바뀌었으면 {@code true}</b> — 아무것도 안 바뀌었으면 수집분 이름도 시각도
	 * 건드리지 않아 갱신(UPDATE)이 한 줄도 나가지 않는다. 그래서 같은 파일을 다시 돌려도 DB 에는 아무 일도 일어나지 않는다.
	 */
	public boolean refresh(String newName, String newAddress, double newLat, double newLng, Map<String, Object> newDetail,
			String newDatasetVersion, OffsetDateTime newCollectedAt) {
		boolean same = this.name.equals(newName) && Objects.equals(this.address, newAddress)
				&& Double.compare(this.lat, newLat) == 0 && Double.compare(this.lng, newLng) == 0
				&& this.datasetVersion.equals(newDatasetVersion) && sameJson(this.detail, newDetail);
		if (same) {
			return false;
		}
		this.name = newName;
		this.address = newAddress;
		this.lat = newLat;
		this.lng = newLng;
		this.detail = new LinkedHashMap<>(newDetail);
		this.datasetVersion = newDatasetVersion;
		this.collectedAt = newCollectedAt;
		return true;
	}

	/**
	 * 두 JSON 객체가 같은가. 키 순서는 보지 않는다 — DB(JSONB)는 키를 자기 순서로 되돌려 주므로 자바 쪽 순서와 다르다. 숫자 종류
	 * (Integer 와 Long)가 달라도 값이 같으면 같다.
	 */
	static boolean sameJson(Object left, Object right) {
		return MAPPER.valueToTree(left).equals(MAPPER.valueToTree(right));
	}

	public UUID getFacilityId() {
		return this.facilityId;
	}

	public FacilityKind getKind() {
		return this.kind;
	}

	public String getSourceId() {
		return this.sourceId;
	}

	public String getName() {
		return this.name;
	}

	public String getAddress() {
		return this.address;
	}

	public double getLat() {
		return this.lat;
	}

	public double getLng() {
		return this.lng;
	}

	public Map<String, Object> getDetail() {
		return this.detail;
	}

	public String getDatasetVersion() {
		return this.datasetVersion;
	}

	public OffsetDateTime getCollectedAt() {
		return this.collectedAt;
	}
}
