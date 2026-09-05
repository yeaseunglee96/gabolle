package com.gabolle.backend.itinerary.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * 일정 저장소 — S15P21E201-313. {@code itineraries}·{@code itinerary_versions} 를
 * PostgreSQL 로 옮겼다.
 *
 * <p>🔴 <b>새 일정을 만드는 경로 — {@link #create}(S15P21E201-604).</b> V150000 마이그레이션이
 * "그 경로가 생기는 티켓이 최초 값을 명시적으로 넣어야 한다"고 남긴 자리다. {@link #append} 는
 * 여전히 <b>기존 일정에 판을 더하는 것</b>만 한다 — 둘의 책임이 갈린다.
 *
 * <h2>🔴 UNIQUE 위반을 409 로 바꾸는 자리 — 왜 예외를 안 쓰는가</h2>
 * PostgreSQL 은 한 트랜잭션 안에서 문장 하나가 실패하면 <b>그 트랜잭션 전체가
 * "aborted" 상태</b>가 된다 — 실패를 잡고 계속 진행해도 다음 문장은 전부
 * {@code current transaction is aborted} 로 죽는다. {@link ItineraryEditService#edit}
 * 가 이미 트랜잭션 안이므로(확인·저장·포인터 이동이 한 트랜잭션), 실패를 예외로 받으면
 * 그 뒤 {@code itineraries} 갱신도 함께 죽는다.
 *
 * <p>🔴 2026-09-04 — 처음엔 {@code PROPAGATION_NESTED}(SAVEPOINT)로 이 INSERT 만 감쌌는데,
 * CI 의 진짜 PostgreSQL 에서 {@code NestedTransactionNotSupportedException} 이 났다(로컬은
 * 도커가 없어 계속 "건너뜀"으로만 확인되고 있었다). Spring Boot 가 자동 설정하는
 * {@code PlatformTransactionManager} 빈이 감싸져 있어 {@code setNestedTransactionAllowed}
 * 가 실제 인스턴스에 반영되지 않았다.
 *
 * <p><b>그래서 실패 자체가 안 나게 만든다.</b> {@code INSERT ... ON CONFLICT (itinerary_id,
 * version) DO NOTHING} 은 충돌해도 SQL 오류를 내지 않는다 — 영향받은 행이 0이면 진 것이다.
 * 트랜잭션이 절대 aborted 상태가 되지 않으므로 SAVEPOINT 도, 트랜잭션 매니저의 특정
 * 구현에 기대는 것도 필요 없다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaItineraryRepository implements ItineraryRepository {

	private static final String INSERT_VERSION_ON_CONFLICT_DO_NOTHING = """
			INSERT INTO itinerary_versions
			    (itinerary_version_id, itinerary_id, version, base_version, operation, created_by,
			     request_id, source_request_id, model_version, feature_version, ontology_version,
			     policy_version, dataset_version, created_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
			ON CONFLICT (itinerary_id, version) DO NOTHING
			""";

	private final ItineraryJpaRepository itineraryJpaRepository;

	private final ItineraryVersionJpaRepository versionJpaRepository;

	private final ItineraryItemJpaRepository itemJpaRepository;

	private final ItineraryLegJpaRepository legJpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaItineraryRepository(ItineraryJpaRepository itineraryJpaRepository,
			ItineraryVersionJpaRepository versionJpaRepository, ItineraryItemJpaRepository itemJpaRepository,
			ItineraryLegJpaRepository legJpaRepository) {
		this.itineraryJpaRepository = itineraryJpaRepository;
		this.versionJpaRepository = versionJpaRepository;
		this.itemJpaRepository = itemJpaRepository;
		this.legJpaRepository = legJpaRepository;
	}

	@Override
	public Optional<Itinerary> findById(String itineraryId) {
		return itineraryJpaRepository.findById(UUID.fromString(itineraryId)).map(JpaItineraryRepository::toDomain);
	}

	@Override
	@Transactional
	public ItineraryVersion append(ItineraryVersion version) {
		UUID itineraryId = UUID.fromString(version.itineraryId());

		int inserted = insertVersion(version);

		if (inserted == 0) {
			// 🔴 충돌 — SQL 오류가 아니라 그냥 0행이 반영된 것이다. 트랜잭션은 멀쩡하다.
			int latest = itineraryJpaRepository.findById(itineraryId)
					.map(ItineraryJpaEntity::latestVersion)
					.orElse(version.version());
			int attempted = version.baseVersion() != null ? version.baseVersion() : latest;
			throw new StaleItineraryVersionException(version.itineraryId(), attempted, latest);
		}

		// 🔴 InMemoryItineraryRepository 에서는 Itinerary.moveTo(next) 가 같은 객체를
		//    직접 고쳐서 이 반영이 "저절로" 일어난다(참조 동일성). JPA 는 findById 마다
		//    새 도메인 객체를 만들어 그 트릭이 안 통하므로, 여기서 명시적으로 반영한다.
		ItineraryJpaEntity itinerary = itineraryJpaRepository.findById(itineraryId).orElseThrow();
		itinerary.updateLatestVersion(version.version());
		itineraryJpaRepository.save(itinerary);

		return version;
	}

	/**
	 * 🔴 일정을 처음 만든다 — S15P21E201-604. {@link #append} 와 달리 이 itineraryId 는
	 * 이번에 처음 등장하므로(호출자가 매번 새 UUID 를 만든다) 경쟁할 대상이 없다. 그래도
	 * {@code itinerary_versions} INSERT 는 {@link #insertVersion} 을 그대로 재사용한다 —
	 * 같은 추천 요청이 두 번 실행되는 경우( {@code uq_itinerary_version_source_request} )는
	 * 이 경로에서도 여전히 가능하고, 그때는 이 메서드가 던지는 원시 제약 위반을 그대로
	 * 위로 흘려보낸다. {@link #append} 처럼 409 로 바꿔 줄 "재시도하면 되는 흔한 경쟁"이
	 * 아니라, 같은 작업이 중복 실행됐다는 이례적인 상황이기 때문이다.
	 */
	@Override
	@Transactional
	public Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion) {
		OffsetDateTime createdAt = toOffset(firstVersion.createdAt());
		ItineraryJpaEntity entity = new ItineraryJpaEntity(
				UUID.fromString(itinerary.itineraryId()),
				UUID.fromString(itinerary.tripId()),
				itinerary.latestVersion(),
				createdAt);
		itineraryJpaRepository.save(entity);

		insertVersion(firstVersion);

		return itinerary;
	}

	@Override
	@Transactional
	public void saveContent(String itineraryVersionId, List<ItineraryItem> items, List<ItineraryLeg> legs) {
		UUID versionId = UUID.fromString(itineraryVersionId);

		List<ItineraryItemJpaEntity> itemEntities = items.stream()
				.map((item) -> toEntity(versionId, item))
				.toList();
		itemJpaRepository.saveAll(itemEntities);

		List<ItineraryLegJpaEntity> legEntities = legs.stream()
				.map((leg) -> toEntity(versionId, leg))
				.toList();
		legJpaRepository.saveAll(legEntities);
	}

	@Override
	public Optional<ItineraryContent> findContent(String itineraryId, int version) {
		return findVersion(itineraryId, version).map((v) -> {
			UUID versionId = UUID.fromString(v.itineraryVersionId());
			List<ItineraryItem> items = itemJpaRepository
					.findByItineraryVersionIdOrderByDayIndexAscSequenceAsc(versionId).stream()
					.map(JpaItineraryRepository::toDomain)
					.toList();
			List<ItineraryLeg> legs = legJpaRepository
					.findByItineraryVersionIdOrderByDayIndexAscSequenceAsc(versionId).stream()
					.map(JpaItineraryRepository::toDomain)
					.toList();
			return new ItineraryContent(v, items, legs);
		});
	}

	@Override
	public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
		return versionJpaRepository.findByItineraryIdAndVersion(UUID.fromString(itineraryId), version)
				.map(JpaItineraryRepository::toDomain);
	}

	/**
	 * {@code itinerary_versions} 한 행을 {@code ON CONFLICT (itinerary_id, version) DO
	 * NOTHING} 으로 넣는다. {@link #append}·{@link #create} 가 공유한다 — 왜 예외를 안
	 * 던지는 SQL 을 쓰는지는 클래스 javadoc 을 본다.
	 *
	 * @return 실제로 삽입된 행 수. 0이면 (itinerary_id, version) 이 이미 있었다는 뜻이다
	 */
	private int insertVersion(ItineraryVersion version) {
		ItineraryVersion.Versions v = version.versions();

		Query insert = entityManager.createNativeQuery(INSERT_VERSION_ON_CONFLICT_DO_NOTHING)
				.setParameter(1, UUID.fromString(version.itineraryVersionId()))
				.setParameter(2, UUID.fromString(version.itineraryId()))
				.setParameter(3, version.version())
				.setParameter(4, version.baseVersion())
				.setParameter(5, version.operation().name())
				.setParameter(6, UUID.fromString(version.createdBy()))
				.setParameter(7, version.requestId())
				.setParameter(8, version.sourceRequestId() != null ? UUID.fromString(version.sourceRequestId()) : null)
				.setParameter(9, v != null ? v.modelVersion() : null)
				.setParameter(10, v != null ? v.featureVersion() : null)
				.setParameter(11, v != null ? v.ontologyVersion() : null)
				.setParameter(12, v != null ? v.policyVersion() : null)
				.setParameter(13, v != null ? v.datasetVersion() : null)
				.setParameter(14, toOffset(version.createdAt()));

		return insert.executeUpdate();
	}

	private static ItineraryVersion toDomain(ItineraryVersionJpaEntity e) {
		ItineraryVersion.Versions versions = new ItineraryVersion.Versions(
				e.modelVersion(), e.featureVersion(), e.ontologyVersion(), e.policyVersion(), e.datasetVersion());
		return new ItineraryVersion(
				e.itineraryVersionId().toString(),
				e.itineraryId().toString(),
				e.version(),
				e.baseVersion(),
				e.operation(),
				e.createdBy().toString(),
				e.requestId(),
				versions,
				toInstant(e.createdAt()),
				e.sourceRequestId() != null ? e.sourceRequestId().toString() : null);
	}

	private static Itinerary toDomain(ItineraryJpaEntity e) {
		return new Itinerary(e.itineraryId().toString(), e.tripId().toString(), e.latestVersion());
	}

	private static ItineraryItem toDomain(ItineraryItemJpaEntity e) {
		return new ItineraryItem(
				e.itineraryItemId().toString(),
				e.itineraryVersionId().toString(),
				e.itemKey().toString(),
				e.dayIndex(),
				e.visitDate(),
				e.sequence(),
				e.placeId().toString(),
				e.startTime(),
				e.endTime(),
				e.stayMinutes(),
				e.locked(),
				e.estimatedCostKrw(),
				e.dataStatus(),
				List.of(e.reasonCodes()),
				List.of(e.warningCodes()),
				e.sourceRequestId() != null ? e.sourceRequestId().toString() : null,
				toInstant(e.createdAt()));
	}

	private static ItineraryItemJpaEntity toEntity(UUID versionId, ItineraryItem item) {
		return new ItineraryItemJpaEntity(
				UUID.fromString(item.itineraryItemId()),
				versionId,
				UUID.fromString(item.itemKey()),
				item.dayIndex(),
				item.visitDate(),
				item.sequence(),
				UUID.fromString(item.placeId()),
				item.startTime(),
				item.endTime(),
				item.stayMinutes(),
				item.locked(),
				item.estimatedCostKrw(),
				item.dataStatus(),
				item.reasonCodes().toArray(String[]::new),
				item.warningCodes().toArray(String[]::new),
				item.sourceRequestId() != null ? UUID.fromString(item.sourceRequestId()) : null,
				toOffset(item.createdAt()));
	}

	private static ItineraryLeg toDomain(ItineraryLegJpaEntity e) {
		return new ItineraryLeg(
				e.itineraryLegId().toString(),
				e.itineraryVersionId().toString(),
				e.dayIndex(),
				e.sequence(),
				e.fromPlaceId() != null ? e.fromPlaceId().toString() : null,
				e.toPlaceId().toString(),
				e.travelMode(),
				e.distanceM(),
				e.durationMin(),
				e.walkingMeters(),
				e.ascentM(),
				e.stairSteps(),
				toInstant(e.createdAt()));
	}

	private static ItineraryLegJpaEntity toEntity(UUID versionId, ItineraryLeg leg) {
		return new ItineraryLegJpaEntity(
				UUID.fromString(leg.itineraryLegId()),
				versionId,
				leg.dayIndex(),
				leg.sequence(),
				leg.fromPlaceId() != null ? UUID.fromString(leg.fromPlaceId()) : null,
				UUID.fromString(leg.toPlaceId()),
				leg.travelMode(),
				leg.distanceM(),
				leg.durationMin(),
				leg.walkingMeters(),
				leg.ascentM(),
				leg.stairSteps(),
				toOffset(leg.createdAt()));
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
