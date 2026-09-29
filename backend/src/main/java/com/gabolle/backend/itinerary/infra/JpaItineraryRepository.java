package com.gabolle.backend.itinerary.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 일정 저장소 — {@code itineraries}·{@code itinerary_versions} 의 PostgreSQL 구현.
 * {@link #create} 는 새 일정을 만들고 {@link #appendVersion} 은 기존 일정에 판을 더한다.
 * UNIQUE 위반을 예외로 받지 않고 {@code INSERT ... ON CONFLICT DO NOTHING} 으로 처리한다.
 * PostgreSQL 은 한 트랜잭션 안에서 문장 하나가 실패하면 그 트랜잭션 전체가 aborted 가 되어,
 * 실패를 잡고 계속 진행해도 다음 문장이 전부 {@code current transaction is aborted} 로 죽는다.
 * 호출자가 이미 트랜잭션 안(확인·저장·포인터 이동이 한 트랜잭션)이라 그 뒤 {@code itineraries}
 * 갱신까지 함께 죽는다.
 * SAVEPOINT({@code PROPAGATION_NESTED})도 쓰지 않는다 — 자동 설정된
 * {@code PlatformTransactionManager} 빈이 감싸져 있어 {@code setNestedTransactionAllowed} 가
 * 실제 인스턴스에 반영되지 않고 {@code NestedTransactionNotSupportedException} 이 난다.
 * {@code ON CONFLICT DO NOTHING} 은 충돌해도 SQL 오류를 내지 않아(영향받은 행이 0이면 진 것이다)
 * 트랜잭션이 절대 aborted 가 되지 않는다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaItineraryRepository implements ItineraryRepository {

	/**
	 * 구간 선형({@code itinerary_leg.path}) JSON 을 읽을 때만 쓴다. 이 클래스의 매핑 메서드가
	 * 전부 {@code static} 이라 주입받지 않고 상수로 둔다 — 읽기 전용이고 상태가 없어 안전하다.
	 */
	private static final ObjectMapper PATH_MAPPER = new ObjectMapper();

	/**
	 * {@code warning_codes}(배열 칸)를 원시 SQL 에 실을 때는 {@code CAST(?15 AS varchar[])} 에
	 * PostgreSQL 배열 리터럴 문자열(예: {@code "{A,B}"}, 비면 {@code "{}"})을 바인딩한다 —
	 * JDBC 드라이버가 {@code String[]} 을 원시 SQL 파라미터 자리에 그대로 못 받는다. 엔티티 매핑을
	 * 거치는 쪽은 {@code @JdbcTypeCode(SqlTypes.ARRAY)} 로 되지만 여기는 원시 {@code INSERT} 다.
	 * 이스케이프를 하지 않는다 — {@link #toArrayLiteral} 이 붙이는 값은 전부 코드 상수뿐이고,
	 * 쉼표·중괄호·따옴표가 들어올 여지가 있는 사용자 자유 입력은 이 칸에 들어오지 않는다.
	 * 그 전제가 깨지면 이 리터럴 조립도 다시 봐야 한다.
	 */
	private static final String INSERT_VERSION_ON_CONFLICT_DO_NOTHING = """
			INSERT INTO itinerary_versions
			    (itinerary_version_id, itinerary_id, version, base_version, operation, created_by,
			     request_id, source_request_id, model_version, feature_version, ontology_version,
			     policy_version, dataset_version, created_at, warning_codes, reverted_from_version)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, CAST(?15 AS varchar[]), ?16)
			ON CONFLICT (itinerary_id, version) DO NOTHING
			""";

	/**
	 * 최신 판 포인터를 내가 본 값에서만 옮긴다. 그 사이 누가 옮겼으면 0행이 반영되고, 그건 409 다.
	 */
	private static final String MOVE_POINTER_IF_UNCHANGED = """
			UPDATE itineraries
			   SET latest_version = ?1
			 WHERE itinerary_id = ?2
			   AND latest_version = ?3
			""";

	/** 409 응답에 실을 "지금 실제 최신" — 1차 캐시를 거치지 않으려고 원시 SQL 로 읽는다. */
	private static final String SELECT_LATEST_VERSION = """
			SELECT latest_version FROM itineraries WHERE itinerary_id = ?1
			""";

	private final ItineraryJpaRepository itineraryJpaRepository;

	private final ItineraryVersionJpaRepository versionJpaRepository;

	private final ItineraryItemJpaRepository itemJpaRepository;

	private final ItineraryLegJpaRepository legJpaRepository;

	private final ItineraryExclusionJpaRepository exclusionJpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaItineraryRepository(ItineraryJpaRepository itineraryJpaRepository,
			ItineraryVersionJpaRepository versionJpaRepository, ItineraryItemJpaRepository itemJpaRepository,
			ItineraryLegJpaRepository legJpaRepository, ItineraryExclusionJpaRepository exclusionJpaRepository) {
		this.itineraryJpaRepository = itineraryJpaRepository;
		this.versionJpaRepository = versionJpaRepository;
		this.itemJpaRepository = itemJpaRepository;
		this.legJpaRepository = legJpaRepository;
		this.exclusionJpaRepository = exclusionJpaRepository;
	}

	/**
	 * 번호 형식이 틀리면 「그런 일정 없음」이다 — 예외로 두면 부르는 쪽 오류 번역기가 몰라 500 이 된다.
	 * 운영에서 추천 화면이 코스 2·3안의 번호({@code 요청번호:1})를 일정 번호로 넘겨 실제로 그랬다
	 * (S15P21E201-1609).
	 */
	@Override
	public Optional<Itinerary> findById(String itineraryId) {
		UUID id;
		try {
			id = UUID.fromString(itineraryId);
		}
		catch (IllegalArgumentException malformed) {
			return Optional.empty();
		}
		return itineraryJpaRepository.findById(id).map(JpaItineraryRepository::toDomain);
	}

	/**
	 * 판 + 내용 + 포인터를 한 트랜잭션으로 넣는다.
	 * 순서를 뒤집지 않는다 — {@code itinerary_versions} INSERT(진 쪽은 여기서 끝난다), 항목·구간
	 * INSERT(이 판을 가리키는 외래키가 있으므로 판이 먼저여야 한다), 그다음 조건부 포인터 이동.
	 * 두 방어선이 서로 다른 것을 지키고 둘 다 필요하다. {@code ON CONFLICT (itinerary_id, version)
	 * DO NOTHING} 은 판 번호 슬롯을 하나만 차지하게 하고, {@code UPDATE ... WHERE latest_version =
	 * :base} 는 포인터가 {@code baseVersion} 에서만 움직이게 한다.
	 * 포인터를 "읽고 · 고치고 · 쓰기" 로 옮기면 안 된다. PostgreSQL 의 기본 격리 수준인
	 * READ COMMITTED(다른 트랜잭션이 커밋한 것만 보이는 수준. 같은 값을 두 트랜잭션이 각자 읽는
	 * 것은 막지 않는다)에서는 두 트랜잭션이 같은 {@code latest_version} 을 읽을 수 있다. 조건을
	 * WHERE 에 두면 그 판정을 DB 가 한다 — CAS(Compare-And-Swap, "내가 읽은 뒤로 바뀐 게 없을
	 * 때만 쓴다")다.
	 */
	@Override
	@Transactional
	public ItineraryVersion appendVersion(ItineraryVersion version, List<ItineraryItem> items,
			List<ItineraryLeg> legs, List<ItineraryExclusion> exclusions) {

		int inserted = insertVersion(version);

		if (inserted == 0) {
			// 충돌 — SQL 오류가 아니라 그냥 0행이 반영된 것이다. 트랜잭션은 멀쩡하다.
			throw staleFor(version);
		}

		saveContent(UUID.fromString(version.itineraryVersionId()), items, legs, exclusions);

		int expected = version.baseVersion() != null ? version.baseVersion() : version.version() - 1;
		int moved = entityManager.createNativeQuery(MOVE_POINTER_IF_UNCHANGED)
				.setParameter(1, version.version())
				.setParameter(2, UUID.fromString(version.itineraryId()))
				.setParameter(3, expected)
				.executeUpdate();

		if (moved == 0) {
			// 판 번호는 땄는데 그 사이 포인터가 움직였다. 이 트랜잭션 전체가 되돌려진다.
			throw staleFor(version);
		}

		// 🔴 포인터를 원시 SQL 로 옮겼으니 1차 캐시의 ItineraryJpaEntity 는 옛 latestVersion 을 들고 있다.
		//    open-in-view 로 요청 하나가 영속성 컨텍스트 하나를 쓰므로, 편집 컨트롤러가 앞에서
		//    requireEditor 로 읽어 둔 엔티티를 뒤의 getDetail 이 그대로 받아 바뀌기 전 판을 응답했다 —
		//    앱이 그 번호로 부른 재계산이 늘 409 였다(S15P21E201-1785). DB 에서 다시 읽어 맞춘다.
		entityManager.refresh(entityManager.getReference(ItineraryJpaEntity.class, UUID.fromString(version.itineraryId())));

		return version;
	}

	/**
	 * 일정을 처음 만든다. 이 itineraryId 는 이번에 처음 등장하므로(호출자가 매번 새 UUID 를
	 * 만든다) 경쟁할 대상이 없다. 그래도 {@code itinerary_versions} INSERT 는
	 * {@link #insertVersion} 을 그대로 재사용한다 — 같은 추천 요청이 두 번 실행되는 경우
	 * ({@code uq_itinerary_version_source_request})는 이 경로에서도 가능하고, 그때는 원시 제약
	 * 위반을 그대로 위로 흘려보낸다. {@link #appendVersion} 처럼 409 로 바꿔 줄 흔한 경쟁이
	 * 아니라 같은 작업이 중복 실행됐다는 이례적인 상황이기 때문이다.
	 * 포인터는 옮기지 않는다 — 첫 판은 {@code latestVersion = 1} 로 이미 그 값을 들고 태어난다.
	 */
	@Override
	@Transactional
	public Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion, List<ItineraryItem> items,
			List<ItineraryLeg> legs) {
		OffsetDateTime createdAt = toOffset(firstVersion.createdAt());
		ItineraryJpaEntity entity = new ItineraryJpaEntity(
				UUID.fromString(itinerary.itineraryId()),
				UUID.fromString(itinerary.tripId()),
				itinerary.latestVersion(),
				createdAt);
		itineraryJpaRepository.save(entity);

		insertVersion(firstVersion);

		// 새로 만드는 일정은 아직 제외할 것이 없다 — 제외는 사용자가 편집으로만 만든다.
		saveContent(UUID.fromString(firstVersion.itineraryVersionId()), items, legs, List.of());

		return itinerary;
	}

	private void saveContent(UUID versionId, List<ItineraryItem> items, List<ItineraryLeg> legs,
			List<ItineraryExclusion> exclusions) {
		List<ItineraryItemJpaEntity> itemEntities = items.stream()
				.map((item) -> toEntity(versionId, item))
				.toList();
		itemJpaRepository.saveAll(itemEntities);

		List<ItineraryLegJpaEntity> legEntities = legs.stream()
				.map((leg) -> toEntity(versionId, leg))
				.toList();
		legJpaRepository.saveAll(legEntities);

		List<ItineraryExclusionJpaEntity> exclusionEntities = exclusions.stream()
				.map((exclusion) -> toEntity(versionId, exclusion))
				.toList();
		exclusionJpaRepository.saveAll(exclusionEntities);
	}

	/**
	 * 지금 실제로 최신인 판 번호를 담은 409 예외를 만든다.
	 * 엔티티가 아니라 원시 SQL 로 읽는다. 이 트랜잭션은 앞서 {@link ItineraryJpaEntity} 를 이미
	 * 불러 뒀을 수 있고, 그 사본은 영속성 컨텍스트(한 트랜잭션 동안 불러온 엔티티를 들고 있는
	 * 1차 캐시)에 남아 다른 트랜잭션이 옮긴 값을 반영하지 않는다. 낡은 번호를 응답에 실으면
	 * 화면이 그 번호로 다시 시도해서 또 409 를 받는다.
	 */
	private StaleItineraryVersionException staleFor(ItineraryVersion version) {
		List<Integer> found = entityManager.createNativeQuery(SELECT_LATEST_VERSION, Integer.class)
				.setParameter(1, UUID.fromString(version.itineraryId()))
				.getResultList();

		int latest = found.isEmpty() ? version.version() : found.get(0);
		int attempted = version.baseVersion() != null ? version.baseVersion() : latest;
		return new StaleItineraryVersionException(version.itineraryId(), attempted, latest);
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
			List<ItineraryExclusion> exclusions = exclusionJpaRepository
					.findByItineraryVersionIdOrderByCreatedAtAsc(versionId).stream()
					.map(JpaItineraryRepository::toDomain)
					.toList();
			return new ItineraryContent(v, items, legs, exclusions);
		});
	}

	@Override
	public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
		return versionJpaRepository.findByItineraryIdAndVersion(UUID.fromString(itineraryId), version)
				.map(JpaItineraryRepository::toDomain);
	}

	/**
	 * 최신 판이 먼저(version DESC). {@code ix_itinerary_version_itinerary (itinerary_id, version
	 * DESC)} 가 이 정렬을 위해 있는 색인이다.
	 */
	@Override
	public VersionPage findVersions(String itineraryId, int page, int size) {
		Page<ItineraryVersionJpaEntity> found = versionJpaRepository
				.findByItineraryIdOrderByVersionDesc(UUID.fromString(itineraryId), PageRequest.of(page, size));
		return new VersionPage(found.getContent().stream()
				.map(JpaItineraryRepository::toDomain)
				.toList(), found.hasNext());
	}

	@Override
	public List<Itinerary> findByTripId(String tripId) {
		return itineraryJpaRepository.findByTripIdOrderByCreatedAtAsc(UUID.fromString(tripId)).stream()
				.map(JpaItineraryRepository::toDomain)
				.toList();
	}

	@Override
	public List<ItineraryVersion> findRecentVersions(Collection<String> itineraryIds, int limit) {
		if (itineraryIds.isEmpty() || limit < 1) {
			return List.of();
		}
		List<UUID> ids = itineraryIds.stream().map(UUID::fromString).toList();
		return versionJpaRepository.findByItineraryIdInOrderByCreatedAtDesc(ids, PageRequest.of(0, limit)).stream()
				.map(JpaItineraryRepository::toDomain)
				.toList();
	}

	/**
	 * {@code itinerary_versions} 한 행을 {@code ON CONFLICT (itinerary_id, version) DO NOTHING} 으로
	 * 넣는다. 왜 예외를 안 던지는 SQL 을 쓰는지는 클래스 javadoc 을 본다.
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
				.setParameter(14, toOffset(version.createdAt()))
				.setParameter(15, toArrayLiteral(version.warningCodes()))
				.setParameter(16, version.revertedFromVersion());

		return insert.executeUpdate();
	}

	/**
	 * PostgreSQL 배열 리터럴 문자열로 바꾼다 — {@code {A,B}}, 비어 있으면 {@code {}}.
	 * 이스케이프를 하지 않는 이유는 {@link #INSERT_VERSION_ON_CONFLICT_DO_NOTHING} javadoc 에 있다.
	 */
	private static String toArrayLiteral(List<String> values) {
		return "{" + String.join(",", values) + "}";
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
				e.sourceRequestId() != null ? e.sourceRequestId().toString() : null,
				e.warningCodes() == null ? List.of() : List.of(e.warningCodes()),
				e.revertedFromVersion());
	}

	private static ItineraryExclusion toDomain(ItineraryExclusionJpaEntity e) {
		return new ItineraryExclusion(
				e.itineraryExclusionId().toString(),
				e.itineraryVersionId().toString(),
				e.placeId().toString(),
				e.itemKey() != null ? e.itemKey().toString() : null,
				e.excludedBy().toString(),
				e.reasonCode(),
				e.operationalReason(),
				toInstant(e.createdAt()));
	}

	private static ItineraryExclusionJpaEntity toEntity(UUID versionId, ItineraryExclusion exclusion) {
		return new ItineraryExclusionJpaEntity(
				UUID.fromString(exclusion.itineraryExclusionId()),
				versionId,
				UUID.fromString(exclusion.placeId()),
				exclusion.itemKey() != null ? UUID.fromString(exclusion.itemKey()) : null,
				UUID.fromString(exclusion.excludedBy()),
				exclusion.reasonCode(),
				exclusion.operationalReason(),
				toOffset(exclusion.createdAt()));
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
		List<double[]> path = decodePath(e.path());
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
				e.dataStatus(),
				e.fareKrw(),
				path,
				decodePieces(e.pieces(), path),
				e.uncalibratedDurationMin(),
				toInstant(e.createdAt()));
	}

	private static ItineraryLegJpaEntity toEntity(UUID versionId, ItineraryLeg leg) {
		String path = encodePath(leg.path());
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
				leg.dataStatus(),
				leg.fareKrw(),
				path,
				// 선형을 못 적었으면 조각도 적지 않는다 — 가리킬 자리가 없는 조각이 된다.
				(path == null) ? null : encodePieces(leg.pieces()),
				leg.uncalibratedDurationMin(),
				toOffset(leg.createdAt()));
	}

	/**
	 * 경사·계단 조각을 {@code [{"from":0,"to":3,"slopePercent":2.5,"stairs":false}, …]} JSON 으로 적는다 — 경로 API 의
	 * {@code pieces} 와 같은 모양이다. 없으면 {@code null}. 선형과 같은 이유로 손으로 잇는다(숫자·참거짓뿐이다).
	 * 경사가 NaN·무한대면 JSON 이 아니므로 모름({@code null})으로 적는다.
	 */
	static String encodePieces(List<ItineraryLeg.Piece> pieces) {
		if (pieces == null || pieces.isEmpty()) {
			return null;
		}
		StringBuilder json = new StringBuilder(pieces.size() * 56).append('[');
		for (int i = 0; i < pieces.size(); i++) {
			ItineraryLeg.Piece piece = pieces.get(i);
			if (i > 0) {
				json.append(',');
			}
			Double slope = piece.slopePercent();
			json.append("{\"from\":").append(piece.from())
					.append(",\"to\":").append(piece.to())
					.append(",\"slopePercent\":").append((slope == null || !Double.isFinite(slope)) ? "null" : slope)
					.append(",\"stairs\":").append(piece.stairs())
					.append('}');
		}
		return json.append(']').toString();
	}

	/**
	 * 조각 JSON 을 읽는다. 선형이 없거나, 값이 깨졌거나, 번호가 선형 밖을 가리키면 {@code null} — 선형과 같이 그 구간만
	 * 조각 없이 두고 일정 전체를 실패시키지 않는다. 틀린 자리를 칠하느니 안 칠하는 편이 낫다.
	 */
	static List<ItineraryLeg.Piece> decodePieces(String json, List<double[]> path) {
		if (json == null || json.isBlank() || path == null) {
			return null;
		}
		try {
			JsonNode root = PATH_MAPPER.readTree(json);
			if (!root.isArray() || root.size() == 0) {
				return null;
			}
			List<ItineraryLeg.Piece> pieces = new ArrayList<>(root.size());
			for (JsonNode node : root) {
				JsonNode from = node.get("from");
				JsonNode to = node.get("to");
				JsonNode slope = node.get("slopePercent");
				JsonNode stairs = node.get("stairs");
				if (from == null || !from.isInt() || to == null || !to.isInt() || stairs == null
						|| !stairs.isBoolean() || (slope != null && !slope.isNull() && !slope.isNumber())) {
					return null;
				}
				if (from.intValue() < 0 || to.intValue() < from.intValue() || to.intValue() >= path.size()) {
					return null;
				}
				pieces.add(new ItineraryLeg.Piece(from.intValue(), to.intValue(),
						(slope == null || slope.isNull()) ? null : slope.doubleValue(), stairs.booleanValue()));
			}
			return pieces;
		}
		catch (JacksonException malformed) {
			return null;
		}
	}

	/**
	 * 선형을 {@code [[경도,위도], …]} JSON 으로 적는다. 없으면 {@code null} — 빈 배열을 적지
	 * 않는다(도메인 {@code ItineraryLeg.normalizePath} 와 DB {@code ck_itinerary_leg_path} 가
	 * 같은 것을 막는다).
	 *
	 * <p>손으로 이어 붙이는 이유는 이 값이 숫자 쌍의 배열뿐이라서다 — 문자열이 섞일 수 없어
	 * 이스케이프할 것이 없고, 매퍼를 들이면 이 클래스의 매핑 메서드가 전부 static 인 결을
	 * 깨야 한다.
	 */
	private static String encodePath(List<double[]> path) {
		if (path == null || path.size() < 2) {
			return null;
		}
		StringBuilder json = new StringBuilder(path.size() * 24).append('[');
		for (int i = 0; i < path.size(); i++) {
			double[] point = path.get(i);
			if (point == null || point.length < 2
					|| !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
				// 좌표 하나가 깨졌으면 선 전체를 버린다. 그 점만 빼면 길이 슬쩍 달라진 채로
				// 「실제로 잰 길」이라고 주장하게 된다.
				//
				// 🔴 NaN·무한대를 따로 막는다. StringBuilder 는 그것을 "NaN"·"Infinity" 라고
				//    적는데 둘 다 JSON 이 아니다. 그대로 두면 이 구간 하나 때문에 INSERT 가
				//    깨져 일정 생성 전체가 실패한다 — 선 하나 못 그리는 것과 여행을 못 만드는
				//    것은 값이 다르다.
				return null;
			}
			if (i > 0) {
				json.append(',');
			}
			json.append('[').append(point[0]).append(',').append(point[1]).append(']');
		}
		return json.append(']').toString();
	}

	/**
	 * 값이 깨져 있으면 그 구간만 선형 없이 두고 일정 전체를 실패시키지 않는다 — 선형은
	 * 지도에 선을 그리는 데만 쓰이고, 없으면 예전처럼 안 그릴 뿐이다.
	 */
	private static List<double[]> decodePath(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonNode root = PATH_MAPPER.readTree(json);
			if (!root.isArray() || root.size() < 2) {
				return null;
			}
			List<double[]> path = new ArrayList<>(root.size());
			for (JsonNode point : root) {
				if (!point.isArray() || point.size() < 2
						|| !point.get(0).isNumber() || !point.get(1).isNumber()) {
					return null;
				}
				path.add(new double[] { point.get(0).doubleValue(), point.get(1).doubleValue() });
			}
			return path;
		}
		catch (JacksonException malformed) {
			return null;
		}
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}

	@Override
	@Transactional
	public void markChosen(String itineraryId, Instant at) {
		// 엔티티에 칸을 두지 않는다 — 저장할 때 늘 DB 기본값(now())이 채우고, 바꾸는 곳은 여기 하나다.
		entityManager.createNativeQuery("UPDATE itineraries SET chosen_at = ?1 WHERE itinerary_id = ?2")
				.setParameter(1, java.sql.Timestamp.from(at))
				.setParameter(2, UUID.fromString(itineraryId))
				.executeUpdate();
	}
}
