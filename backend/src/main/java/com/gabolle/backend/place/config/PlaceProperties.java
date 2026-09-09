package com.gabolle.backend.place.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 근처 조회(S15P21E201-469)가 쓰는 설정.
 *
 * <h2>🔴 "기념품샵" 판별을 여기로 뺀 이유</h2>
 *
 * {@code purpose=SOUVENIR} 를 {@link com.gabolle.backend.place.domain.Place#getCategory()} 로 볼지
 * {@code place_feature} 표식으로 볼지 아직 팀이 정하지 않았다. 자바 코드에 하나를 못박으면 나중에
 * 다른 쪽으로 정해질 때 배포를 다시 해야 한다. {@link PurposeSpec} 으로 빼 두면 그때는 설정값만
 * 바뀐다.
 *
 * <h2>🔴 필드마다 기본값을 자바에 넣는 이유</h2>
 *
 * 이 티켓은 {@code application*.properties} 를 고치지 않는 것이 경계다. 그런데 그 파일에 아무
 * 설정이 없어도 {@code no-db} 프로필의 {@code contextLoads} 는 깨지면 안 된다 — 이 클래스 자체는
 * {@code db}·{@code dev} 프로필에서만 뜨지만({@link PlaceConfiguration}), 설정이 통째로 없는
 * 환경에서도 바인딩이 실패하지 않도록 각 필드에 기본값을 직접 준다. {@code purposes} 의 기본은
 * 빈 맵이다 — 모르는 목적을 아는 척 처리하기보다는
 * {@link com.gabolle.backend.place.service.PlaceRequestException}(코드 {@code UNKNOWN_PURPOSE})로
 * 명시적으로 거부한다.
 */
@ConfigurationProperties(prefix = "gabolle.place")
public class PlaceProperties {

	/**
	 * 목적 코드(예: {@code "SOUVENIR"}) → 그 목적을 판별하는 방법. 기본은 빈 맵.
	 *
	 * <p>🔴 빈 맵이라도 근처 조회 엔드포인트 자체는 막히지 않는다. {@code purpose} 요청 파라미터는
	 * 선택값이라 안 보내면 목적 필터 없이 반경 안 장소를 거리순으로 돌려준다
	 * ({@code NearbyPlaceService} 참고). {@code purpose} 를 보냈는데 여기 없으면 그때만
	 * {@code UNKNOWN_PURPOSE} 로 거부한다. 어떤 카테고리 값이 "기념품샵" 같은 목적에 해당하는지는
	 * 아직 아무도 정하지 않았고, 여기서 값을 지어내면 그 값이 곧 계약이 되어 나중에 실제 값으로
	 * 바꿀 때 다른 팀의 코드까지 깨진다. 그래서 기본값을 채우지 않고 빈 채로 둔다.
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
	 * <p>🔴 <b>근처 조회({@link #nearbyMaxScanned})와 값이 다른 이유</b> (S15P21E201-724).
	 * 근처 조회는 사람이 화면에서 보는 목록이라 몇십 곳이면 되지만, 추천 후보는 <b>채점 대상
	 * 전체</b>다. 부산은 반경 5km 안에 음식점이 평균 9,422곳이라 2,000 에서 자르면 채점기가
	 * 보는 것은 그중 일부이고, <b>잘려 나간 장소는 아무리 좋아도 점수를 매길 기회조차 없다.</b>
	 * 값이 2,000 이던 동안 실효 반경이 중앙값 304m 였다 — 반경을 5km 로 잡아 놓고 실제로는
	 * 300m 를 봤다.
	 *
	 * <p>이 값을 올리면 조회·채점 시간이 같이 는다. 느려지면 여기를 내린다 —
	 * 코드를 고칠 일이 아니다.
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
