package com.gabolle.backend.place.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 근처 조회가 쓰는 설정.
 *
 * <p>목적 판별({@link PurposeSpec})을 설정으로 뺀 것은 {@code purpose=SOUVENIR} 를
 * {@code place.category} 로 볼지 {@code place_feature} 표식으로 볼지 팀이 아직 정하지 않았기
 * 때문이다. 자바에 못박으면 정해질 때 배포를 다시 해야 한다.
 *
 * <p>필드마다 기본값을 자바에 둔다. {@code application*.properties} 에 아무 설정이 없는 환경에서도
 * 바인딩이 실패하지 않아야 한다.
 */
@ConfigurationProperties(prefix = "gabolle.place")
public class PlaceProperties {

	/**
	 * 목적 코드(예: {@code "SOUVENIR"}) → 그 목적을 판별하는 방법. 기본은 빈 맵이지만 근처 조회
	 * 자체는 막히지 않는다 — {@code purpose} 는 선택값이라 안 보내면 목적 필터 없이 돌려주고,
	 * 보냈는데 여기 없을 때만 {@code UNKNOWN_PURPOSE} 로 거부한다. 여기서 값을 지어내면 그것이 곧
	 * 계약이 되어 실제 값으로 바꿀 때 다른 팀의 코드까지 깨진다.
	 */
	private Map<String, PurposeSpec> purposes = Map.of();

	/** 근처 조회의 반경 사다리(m). 오름차순이어야 한다 — 아니어도 서비스가 정렬해서 쓴다. */
	private List<Integer> nearbyRadiusLadderMeters = List.of(1000, 2000, 5000);

	/** 이 개수를 못 채우면 사다리의 다음 반경으로 넓힌다. */
	private int nearbyMinimumCount = 5;

	/** 경계상자 후보를 이 개수까지만 자바에서 거리 계산한다. 넘으면 응답에 {@code scanTruncated}. */
	private int nearbyMaxScanned = 2000;

	/**
	 * 추천 후보 사전 필터({@code PlaceCandidateQueryService})가 경계상자에서 읽어 올 최대 행 수.
	 * 넘으면 자르되 응답에 {@code scanTruncated} 로 적어 내보낸다.
	 *
	 * <p>{@link #nearbyMaxScanned} 보다 훨씬 큰 이유: 근처 조회는 사람이 화면에서 보는 목록이지만
	 * 추천 후보는 채점 대상 전체이고, 잘려 나간 장소는 아무리 좋아도 점수를 매길 기회조차 없다.
	 * 부산은 반경 5km 안에 음식점이 평균 9,422곳이라 2,000 에서 자르면 실효 반경이 중앙값 304m 로
	 * 줄었다. 올리면 조회·채점 시간이 같이 는다 — 느려지면 코드가 아니라 여기를 내린다.
	 */
	private int candidateMaxScanned = 20000;

	public Map<String, PurposeSpec> getPurposes() {
		return this.purposes;
	}

	public void setPurposes(Map<String, PurposeSpec> purposes) {
		this.purposes = purposes;
	}

	public List<Integer> getNearbyRadiusLadderMeters() {
		return this.nearbyRadiusLadderMeters;
	}

	public void setNearbyRadiusLadderMeters(List<Integer> nearbyRadiusLadderMeters) {
		this.nearbyRadiusLadderMeters = nearbyRadiusLadderMeters;
	}

	public int getNearbyMinimumCount() {
		return this.nearbyMinimumCount;
	}

	public void setNearbyMinimumCount(int nearbyMinimumCount) {
		this.nearbyMinimumCount = nearbyMinimumCount;
	}

	public int getNearbyMaxScanned() {
		return this.nearbyMaxScanned;
	}

	public void setNearbyMaxScanned(int nearbyMaxScanned) {
		this.nearbyMaxScanned = nearbyMaxScanned;
	}

	public int getCandidateMaxScanned() {
		return this.candidateMaxScanned;
	}

	public void setCandidateMaxScanned(int candidateMaxScanned) {
		this.candidateMaxScanned = candidateMaxScanned;
	}

	/**
	 * 목적 코드 하나의 판별 방법.
	 *
	 * <p>{@code categories} 가 있으면 {@code place.category} 로 거른다. {@code featureType} 이
	 * 있으면 {@code place_feature} 표식으로 조회한다({@code featureKey} 는 태그형일 때만 준다).
	 * 둘 다 있으면 표식으로 먼저 좁힌 뒤 카테고리로 한 번 더 거른다 — 자세한 순서는
	 * {@link com.gabolle.backend.place.service.NearbyPlaceService} 참고.
	 */
	public record PurposeSpec(List<String> categories, String featureType, String featureKey) {
	}
}
