package com.gabolle.backend.itinerary.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;

/**
 * 일정 저장소 — S15P21E201-313. {@code itineraries}·{@code itinerary_versions} 를
 * PostgreSQL 로 옮겼다.
 *
 * <p>🔴 <b>새 일정을 만드는 경로는 여기 없다.</b> {@code InMemoryItineraryRepository} 와
 * 똑같은 범위다 — V150000 마이그레이션이 이미 남긴 말 그대로, "일정을 새로 만드는" 경로가
 * 아직 없고 {@link #append} 는 <b>기존 일정에 판을 더하는 것</b>만 한다. 그 경로가 생기는
 * 티켓이 최초 값을 명시적으로 넣어야 한다.
 *
 * <h2>🔴 UNIQUE 위반을 409 로 바꾸는 자리 — 왜 SAVEPOINT 가 필요한가</h2>
 * PostgreSQL 은 한 트랜잭션 안에서 문장 하나가 실패하면 <b>그 트랜잭션 전체가
 * "aborted" 상태</b>가 된다 — 실패를 잡고 계속 진행해도 다음 문장은 전부
 * {@code current transaction is aborted} 로 죽는다. {@link ItineraryEditService#edit}
 * 가 이미 트랜잭션 안이므로(확인·저장·포인터 이동이 한 트랜잭션), 이 메서드가 그 위에서
 * 그냥 실패를 잡으면 이후 {@code itineraries} 갱신도 함께 죽는다.
 *
 * <p>그래서 판 INSERT 만 {@link TransactionDefinition#PROPAGATION_NESTED}(진짜 DB
 * SAVEPOINT)로 감싼다 — 그 안에서만 실패하면 그 SAVEPOINT 까지만 되돌아가고, 바깥
 * 트랜잭션은 살아 있어서 {@code itineraries.latest_version} 갱신을 계속할 수 있다.
 *
 * <h2>🔴 2026-09-04 — CI 에서 NestedTransactionNotSupportedException (실측)</h2>
 * Spring Boot 가 자동 설정하는 {@code JpaTransactionManager} 는 중첩 트랜잭션이
 * <b>기본값 false</b> 다 — 그런 속성을 노출하는 설정 키가 없다. 로컬엔 도커가 없어서
 * 이 저장소의 통합 테스트가 계속 "건너뜀"으로만 확인됐고, 실제 PostgreSQL 위에서
 * 처음 돈 것이 CI 였다. 그래서 여기서 직접 {@code setNestedTransactionAllowed(true)}
 * 를 켠다 — PostgreSQL JDBC 드라이버는 SAVEPOINT 를 지원하므로 이 설정 하나면 된다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaItineraryRepository implements ItineraryRepository {

	private final ItineraryJpaRepository itineraryJpaRepository;

	private final ItineraryVersionJpaRepository versionJpaRepository;

	private final TransactionTemplate nestedInsertTemplate;

	public JpaItineraryRepository(ItineraryJpaRepository itineraryJpaRepository,
			ItineraryVersionJpaRepository versionJpaRepository,
			PlatformTransactionManager transactionManager) {
		this.itineraryJpaRepository = itineraryJpaRepository;
		this.versionJpaRepository = versionJpaRepository;
		if (transactionManager instanceof AbstractPlatformTransactionManager abstractManager) {
			abstractManager.setNestedTransactionAllowed(true);
		}
		this.nestedInsertTemplate = new TransactionTemplate(transactionManager);
		this.nestedInsertTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
	}

	@Override
	public Optional<Itinerary> findById(String itineraryId) {
		return itineraryJpaRepository.findById(UUID.fromString(itineraryId)).map(JpaItineraryRepository::toDomain);
	}

	@Override
	@Transactional
	public ItineraryVersion append(ItineraryVersion version) {
		UUID itineraryId = UUID.fromString(version.itineraryId());

		try {
			nestedInsertTemplate.executeWithoutResult(status -> {
				versionJpaRepository.saveAndFlush(toEntity(version));
			});
		} catch (DataIntegrityViolationException conflict) {
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

	@Override
	public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
		return versionJpaRepository.findByItineraryIdAndVersion(UUID.fromString(itineraryId), version)
				.map(JpaItineraryRepository::toDomain);
	}

	private static ItineraryVersionJpaEntity toEntity(ItineraryVersion v) {
		ItineraryVersion.Versions versions = v.versions();
		return new ItineraryVersionJpaEntity(
				UUID.fromString(v.itineraryVersionId()),
				UUID.fromString(v.itineraryId()),
				v.version(),
				v.baseVersion(),
				v.operation(),
				UUID.fromString(v.createdBy()),
				v.requestId(),
				versions != null ? versions.modelVersion() : null,
				versions != null ? versions.featureVersion() : null,
				versions != null ? versions.ontologyVersion() : null,
				versions != null ? versions.policyVersion() : null,
				versions != null ? versions.datasetVersion() : null,
				toOffset(v.createdAt()));
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
				toInstant(e.createdAt()));
	}

	private static Itinerary toDomain(ItineraryJpaEntity e) {
		return new Itinerary(e.itineraryId().toString(), e.tripId().toString(), e.latestVersion());
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
