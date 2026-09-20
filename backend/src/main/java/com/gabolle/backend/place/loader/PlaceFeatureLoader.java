package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.repository.PlaceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@link PlaceFeatureNdjsonReader} 가 읽은 사실을 {@code place_feature} 에 넣는다.
 *
 * <p>{@code place} 에 그 열쇠로 만든 행이 있어야만 넣을 수 있다({@code fk_place_feature_place}).
 * 장소가 없는 열쇠는 건너뛰되 세어서 돌려준다 — 예외로 멈추면 나머지가 통째로 안 들어가고,
 * 조용히 넘기면 다 넣은 줄 아는데 일부만 들어간 상태가 된다.
 *
 * <p>이미 있는 사실은 건너뛰고 고치지 않는다. 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는
 * 것이 여기서 지키는 전부고, 새 판으로 갱신할지는 별개의 결정이다.
 *
 * <p>증거 등급은 {@code ESTIMATED} 다. 조사원이 적은 것이지 가게에 확인한 것이 아니다.
 *
 * <p>열쇠 체계는 {@code source_type} 과 다른 것이다 — 가격대는 {@code source_type} 이
 * {@code RESEARCH_PRICEBAND} 인데 열쇠는 상가업소번호다. 그래서
 * {@link PlaceFeatureNdjsonReader.Fact} 가 {@code keySource} 를 따로 들고 다니고, 읽는 쪽이 그
 * 값을 정한다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureLoader {

	/**
	 * {@code ON CONFLICT} 에 대상을 적지 않는다. 이 문장이 맞설 수 있는 유일 제약이 둘이기
	 * 때문이다 — 기본키와 부분 색인 {@code uq_place_feature_unkeyed}
	 * ({@code (place_id, feature_type) WHERE feature_key IS NULL}, 같은 장소에 열쇠가 여럿 걸릴
	 * 때 충돌한다). 대상을 적으면 PostgreSQL 은 그 색인의 충돌만 흡수하고 다른 색인의 충돌은
	 * 예외로 던진다.
	 *
	 * <p>{@code ?5::jsonb} 처럼 순번 파라미터 바로 뒤에 {@code ::} 캐스트를 붙이면 Hibernate 가
	 * {@code 5::jsonb} 를 파라미터 번호로 읽으려다 {@code ParameterLabelException} 을 던진다 —
	 * {@code CAST(... AS jsonb)} 로 쓴다.
	 */
	private static final String INSERT_IF_ABSENT = """
			INSERT INTO place_feature
			    (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
			     source_type, source_id, observed_at, source_version, created_at)
			VALUES (?1, ?2, ?3, ?4, CAST(?5 AS jsonb), ?6, ?7, ?8, ?9, ?10, ?11)
			ON CONFLICT DO NOTHING
			""";

	private final PlaceRepository placeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PlaceFeatureLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * {@code missingPlace} 는 그 열쇠로 만든 장소가 표에 없어서 못 넣은 것,
	 * {@code alreadyPresent} 는 같은 장소에 같은 종류가 이미 있어서 건너뛴 것이다.
	 */
	public record Saved(int inserted, int missingPlace, int alreadyPresent) {

		public Saved plus(Saved other) {
			return new Saved(this.inserted + other.inserted, this.missingPlace + other.missingPlace,
					this.alreadyPresent + other.alreadyPresent);
		}

		@Override
		public String toString() {
			return "넣음 " + this.inserted + " · 장소가 없어 못 넣음 " + this.missingPlace + " · 이미 있어 건너뜀 "
					+ this.alreadyPresent;
		}
	}

	/**
	 * {@code sourceType} 은 {@code place_feature.source_type}, {@code datasetVersion} 은
	 * {@code source_version} 에 들어간다. 후자가 비면 이 값으로 만든 추천을 되짚을 수 없어
	 * 부르는 쪽이 미리 검사한다.
	 */
	@Transactional
	public Saved saveChunk(List<PlaceFeatureNdjsonReader.Fact> facts, String sourceType, String datasetVersion,
			OffsetDateTime collectedAt) {
		List<UUID> placeIds = facts.stream().map(fact -> placeIdOf(fact.keySource(), fact.storeId())).toList();
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository.findAllById(placeIds).forEach(place -> knownPlaces.add(place.getPlaceId()));

		int inserted = 0;
		int missingPlace = 0;
		int alreadyPresent = 0;
		for (PlaceFeatureNdjsonReader.Fact fact : facts) {
			UUID placeId = placeIdOf(fact.keySource(), fact.storeId());
			if (!knownPlaces.contains(placeId)) {
				missingPlace++;
				continue;
			}
			UUID featureId = featureIdOf(fact.keySource(), fact.storeId(), fact.featureType(), fact.featureKey());
			int affected = this.entityManager.createNativeQuery(INSERT_IF_ABSENT)
					.setParameter(1, featureId)
					.setParameter(2, placeId)
					.setParameter(3, fact.featureType())
					// 태그형은 이 칸이 있어야 하고 나머지는 비어 있어야 한다
					// (ck_place_feature_key_shape).
					.setParameter(4, fact.featureKey())
					.setParameter(5, fact.value())
					.setParameter(6, PlaceEvidenceStatus.ESTIMATED.name())
					.setParameter(7, sourceType)
					.setParameter(8, fact.storeId())
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					.setParameter(9, (OffsetDateTime) null)
					.setParameter(10, datasetVersion)
					.setParameter(11, collectedAt)
					.executeUpdate();
			if (affected == 1) {
				inserted++;
			}
			else {
				// 같은 덩어리 안의 중복도 여기서 걸린다 — 앞선 행이 같은 트랜잭션 안에 이미
				// 들어가 있어 뒤이은 INSERT 가 그 행과 충돌한다.
				alreadyPresent++;
			}
		}
		return new Saved(inserted, missingPlace, alreadyPresent);
	}

	/**
	 * 산출물의 열쇠({@link PlaceFeatureNdjsonReader.Fact#keySource()})로 장소 아이디를 만든다.
	 * 원천마다 앞에 붙는 말이 달라({@code gabolle:place:SBIZ:} 대
	 * {@code gabolle:place:TOURAPI:}) 섞이면 아무 오류 없이 있는 장소를 "없어서 못 넣음" 으로
	 * 세고 끝난다. 그래서 모르는 원천은 짐작하지 않고 거절한다.
	 */
	// 패키지 전용이다 — SubwayExitLoader 가 같은 열쇠 체계로 장소를 찾느라 이 계산을 그대로
	// 재사용한다. 새 계산을 또 만들면 두 곳의 공식이 갈라질 여지가 생긴다.
	static UUID placeIdOf(String sourceType, String key) {
		if (TourApiPlaceLoader.SOURCE_TYPE.equals(sourceType)) {
			return TourApiPlaceLoader.placeIdOf(key);
		}
		if (SbizPlaceLoader.SOURCE_TYPE.equals(sourceType)) {
			return SbizPlaceLoader.placeIdOf(key);
		}
		throw new IllegalArgumentException(
				"모르는 원천이다: " + sourceType + " — 장소 아이디를 짐작해서 만들지 않는다");
	}

	/**
	 * 같은 이유로 피처 아이디도 원천을 따라간다. {@code featureKey} 는 태그형만 값이 있고,
	 * {@code null} 이면 그대로 문자열 {@code "null"} 로 이어붙는다 — 고치면 이미 적재된 행의
	 * 아이디가 새 공식과 어긋난다.
	 */
	static UUID featureIdOf(String sourceType, String key, String featureType, String featureKey) {
		if (TourApiPlaceLoader.SOURCE_TYPE.equals(sourceType)) {
			return TourApiPlaceLoader.featureIdOf(key, featureType, featureKey);
		}
		if (SbizPlaceLoader.SOURCE_TYPE.equals(sourceType)) {
			return SbizPlaceLoader.featureIdOf(key, featureType, featureKey);
		}
		throw new IllegalArgumentException(
				"모르는 원천이다: " + sourceType + " — 피처 아이디를 짐작해서 만들지 않는다");
	}

}
