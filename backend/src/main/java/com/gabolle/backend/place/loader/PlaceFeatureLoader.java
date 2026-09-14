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
 * <h2>🔴 없는 장소에는 안 넣는다 — 세어서 돌려준다</h2>
 *
 * 두 산출물의 열쇠는 상가업소번호이고, {@code place} 에 그 번호로 만든 행이 <b>있어야만</b> 넣을 수
 * 있다({@code fk_place_feature_place}). 상가정보 전체(53,716곳)를 적재하지 않은 환경에서는 값은
 * 있는데 장소가 없는 번호가 나온다. 그때 예외로 멈추면 나머지가 통째로 안 들어가고, 조용히
 * 넘어가면 <b>"967곳을 넣었다" 고 믿는데 실제로는 300곳만 들어간 상태</b>가 된다.
 * 그래서 <b>건너뛰되 세어서 돌려준다</b> — 넣은 수와 못 넣은 수를 부르는 쪽이 함께 남긴다.
 *
 * <h2>🔴 이미 있는 사실은 건너뛴다 — 고치지 않는다</h2>
 *
 * {@link SbizPlaceLoader#saveChunk} 와 같은 규칙이다. 같은 파일을 두 번 돌려도 행이 두 배가 되지
 * 않는 것이 여기서 지키는 전부고, "새 판으로 갱신한다" 는 별개의 결정이라 여기서 미리 정하지 않는다.
 * 표에 {@code (place_id, feature_type) WHERE feature_key IS NULL} 부분 유일 색인이 걸려 있다
 * ({@code uq_place_feature_unkeyed}).
 *
 * <h2>🔴 S15P21E201-948 — "먼저 조회해서 없으면 넣는다" 가 아니라 {@code ON CONFLICT DO NOTHING} 이다</h2>
 *
 * 전에는 {@code findAllById} 로 기존 행을 미리 읽어 메모리 {@code Set} 으로 중복을 걸렀다. 이 적재는
 * 사람이 CLI 로 한 번 돌리는 것이 정상 경로라 동시 실행 확률은 낮지만, 같은 파일을 실수로 두 번
 * 동시에 돌리거나 서버 두 대에서 각각 돌리면 그 사이(읽고 나서 쓰기 전) 다른 실행이 같은 사실을
 * 먼저 넣을 수 있다 — 그러면 이 트랜잭션의 insert 가 유일 색인 위반으로 실패하고, PostgreSQL 은
 * 트랜잭션 안에서 문장 하나가 실패하면 그 트랜잭션 전체를 못 쓰게 만든다({@code JpaItineraryRepository}
 * ·{@code JpaItineraryItemActualRepository} 클래스 주석이 같은 실측을 남겨 뒀다). 그래서 여기서도
 * 같은 해법을 쓴다 — 행마다 {@code ON CONFLICT DO NOTHING} 으로 넣어, 충돌해도 예외 없이 그 행만
 * 건너뛴다.
 *
 * <h2>🔴 {@code ESTIMATED} 로 넣는다</h2>
 *
 * 가격대는 <b>조사원이 적은 것이지 가게에 확인한 것이 아니다.</b> {@code VERIFIED} 로 적으면
 * 나중에 아무도 이 값을 의심하지 않는다. {@code PRICE_LEVEL} 은 안전 피처가 아니라
 * {@code ESTIMATED} 가 DB 에서 허용된다
 * ({@code ck_place_feature_safety_never_estimated} 는 알레르기·식단·접근성·계단만 막는다).
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureLoader {

	/**
	 * {@code feature_key} 는 이 로더가 항상 {@code null} 로 넣으므로({@code SbizPlaceLoader.featureIdOf}
	 * 세 번째 인자) 부분 색인 {@code uq_place_feature_unkeyed} 의 조건과 같은 {@code WHERE} 를 준다 —
	 * 대상이 부분 색인이면 {@code ON CONFLICT} 도 같은 조건을 적어야 그 색인을 가리킨다.
	 *
	 * <p>🔴 {@code ?4::jsonb} 처럼 순번 파라미터 바로 뒤에 {@code ::} 캐스트를 붙이면 Hibernate 네이티브
	 * 쿼리 파서가 {@code 4::jsonb} 를 파라미터 번호로 통째로 읽으려다 {@code ParameterLabelException}
	 * ("Ordinal parameter label was not an integer")을 던진다(CI 파이프라인 193865 에서 실측) —
	 * {@code CAST(... AS jsonb)} 로 쓴다.
	 */
	private static final String INSERT_IF_ABSENT = """
			INSERT INTO place_feature
			    (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
			     source_type, source_id, observed_at, source_version, created_at)
			VALUES (?1, ?2, ?3, NULL, CAST(?4 AS jsonb), ?5, ?6, ?7, ?8, ?9, ?10)
			ON CONFLICT (place_id, feature_type) WHERE feature_key IS NULL DO NOTHING
			""";

	private final PlaceRepository placeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PlaceFeatureLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * 넣은 것과 못 넣은 것.
	 *
	 * @param inserted 실제로 들어간 행
	 * @param missingPlace 🔴 그 상가업소번호로 만든 장소가 표에 없어서 못 넣은 것
	 * @param alreadyPresent 같은 장소에 같은 종류가 이미 있어서 건너뛴 것
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
	 * 한 덩어리를 넣는다.
	 *
	 * @param sourceType {@code place_feature.source_type}. 어느 산출물에서 온 값인지 되짚는 자리다
	 * @param datasetVersion {@code place_feature.source_version}. 🔴 없으면 이 값으로 만든 추천을
	 *     나중에 되짚을 수 없다 — 부르는 쪽이 검사한다
	 */
	@Transactional
	public Saved saveChunk(List<PlaceFeatureNdjsonReader.Fact> facts, String sourceType, String datasetVersion,
			OffsetDateTime collectedAt) {
		List<UUID> placeIds = facts.stream().map(fact -> SbizPlaceLoader.placeIdOf(fact.storeId())).toList();
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository.findAllById(placeIds).forEach(place -> knownPlaces.add(place.getPlaceId()));

		int inserted = 0;
		int missingPlace = 0;
		int alreadyPresent = 0;
		for (PlaceFeatureNdjsonReader.Fact fact : facts) {
			UUID placeId = SbizPlaceLoader.placeIdOf(fact.storeId());
			if (!knownPlaces.contains(placeId)) {
				missingPlace++;
				continue;
			}
			UUID featureId = SbizPlaceLoader.featureIdOf(fact.storeId(), fact.featureType(), null);
			int affected = this.entityManager.createNativeQuery(INSERT_IF_ABSENT)
					.setParameter(1, featureId)
					.setParameter(2, placeId)
					.setParameter(3, fact.featureType())
					.setParameter(4, fact.value())
					.setParameter(5, PlaceEvidenceStatus.ESTIMATED.name())
					.setParameter(6, sourceType)
					.setParameter(7, fact.storeId())
					// 🔴 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다 —
					// 어느 산출물인지는 sourceVersion 이 말해 준다.
					.setParameter(8, (OffsetDateTime) null)
					.setParameter(9, datasetVersion)
					.setParameter(10, collectedAt)
					.executeUpdate();
			if (affected == 1) {
				inserted++;
			}
			else {
				// 🔴 같은 덩어리 안의 중복도 여기서 걸린다 — 앞선 행이 같은 트랜잭션 안에서 이미
				// 커밋 전 상태로 들어가 있어, 뒤이은 행의 INSERT 가 그 행과 충돌한다.
				alreadyPresent++;
			}
		}
		return new Saved(inserted, missingPlace, alreadyPresent);
	}

}
