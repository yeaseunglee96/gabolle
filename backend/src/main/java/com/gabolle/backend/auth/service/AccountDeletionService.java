package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.api.AccountDeletionPreviewResponse;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 계정과 그 사람의 데이터를 지운다 (S15P21E201-425, -183 을 함께 다룬다).
 *
 * <p>계정을 만들 수 있는 서비스는 그 안에서 지울 수도 있어야 한다. App Store 심사 지침 5.1.1(v)
 * 이기도 하다.
 *
 * <h2>🔴 계정 행은 지우지 않고 비운다</h2>
 *
 * 티켓은 "계정을 지운다" 고 적었는데 표 구조가 그것을 허락하지 않는다.
 * {@code itinerary_versions.created_by} 가 <b>필수 값이면서</b> {@code app_user} 를 가리키고
 * {@code ON DELETE} 규칙이 없다. 이 사람이 동행자의 일정을 편집한 적이 있으면, 계정 행을 지우려면
 * <b>남의 일정 편집 이력까지 함께 지워야</b> 한다. 탈퇴한 사람의 권한 밖이다.
 *
 * <p>그래서 로그인에 필요한 것과 본인 데이터는 전부 지우고, 계정 행 하나만 개인정보를 비운 채
 * 남긴다. {@code app_user} 에 {@code DELETED} 상태와 {@code deleted_at} 칸이 원래 이 용도로
 * 만들어져 있다 — 표를 만든 사람도 지우는 것이 아니라 비우는 것을 전제했다.
 *
 * <p>사용자가 보기에 달라지는 것은 없다. 로그인이 안 되고, 이메일이 풀려 같은 주소로 다시 가입할 수
 * 있고, 남는 행에는 개인을 알아볼 값이 없다.
 *
 * <h2>🔴 남의 패키지 코드를 고치지 않고 행만 지운다</h2>
 *
 * 여행·일정·추천은 다른 담당자의 코드다. 그쪽 클래스를 고치지 않고 JPQL 로 행만 지운다. 엔티티
 * 이름을 문자열로 쓰므로 import 도 없다 — 대신 그쪽이 엔티티 이름을 바꾸면 컴파일이 아니라
 * 실행에서 터진다. 그것을 잡으라고 통합 테스트가 실제 DB 에서 이 경로를 통째로 돌린다.
 *
 * <p>네이티브 SQL 을 쓰지 않는다. 운영은 {@code gabolle} schema 를 쓰는데 네이티브 SQL 은 그것을
 * 물려받지 않고, 그 어긋남은 테스트로 안 잡힌다 ({@code docs/DB-STANDARD.md} 2절).
 */
@Service
@Profile({"db", "dev"})
public class AccountDeletionService {

	@PersistenceContext
	private EntityManager entityManager;

	private final LocalCredentialRepository credentialRepository;

	private final AuthSessionRepository sessionRepository;

	private final AuthIdentityRepository identityRepository;

	private final UserConsentRepository consentRepository;

	private final AppUserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public AccountDeletionService(LocalCredentialRepository credentialRepository,
			AuthSessionRepository sessionRepository, AuthIdentityRepository identityRepository,
			UserConsentRepository consentRepository, AppUserRepository userRepository,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.credentialRepository = credentialRepository;
		this.sessionRepository = sessionRepository;
		this.identityRepository = identityRepository;
		this.consentRepository = consentRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * 비밀번호를 다시 확인하고 지운다.
	 *
	 * <p>🔴 한 트랜잭션이다. 도중에 실패하면 아무것도 지워지지 않은 상태로 돌아간다. 반쯤 지워진
	 * 계정은 로그인도 안 되고 데이터도 못 찾는, 아무도 손댈 수 없는 상태가 된다.
	 */
	@Transactional
	public void delete(UUID userId, String password) {
		LocalCredential credential = this.credentialRepository.findByUserUserId(userId)
				.orElseThrow(() -> new AuthException("LOCAL_CREDENTIAL_REQUIRED",
						"비밀번호로 가입한 계정만 이 방법으로 탈퇴할 수 있습니다.", HttpStatus.CONFLICT));

		// 🔴 비밀번호를 먼저 확인한다. 틀리면 아무것도 지우지 않는다.
		if (!this.passwordEncoder.matches(password, credential.getPasswordHash())) {
			throw new AuthException("INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.",
					HttpStatus.UNAUTHORIZED);
		}

		AppUser user = this.userRepository.findById(userId)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));

		List<UUID> tripIds = ownedTripIds(userId);
		deleteTripData(userId, tripIds);
		deleteStories(userId);
		deleteLoginMeans(userId, credential);
		detachEvents(userId);

		user.anonymizeForDeletion(this.clock.instant());
	}

	/**
	 * 삭제 전 안내 화면이 보여줄 실제 영향 수 — S15P21E201-188/195(진미리 님 요청).
	 *
	 * <p>{@link #delete}가 실제로 지우는 것과 같은 기준으로 센다 — {@code recordCount}(기록·
	 * {@code story})는 2026-09-07부터 {@link #delete}도 함께 지우므로 이 숫자가 실제
	 * 삭제 개수와 일치한다({@link #deleteStories} 참고).
	 */
	@Transactional(readOnly = true)
	public AccountDeletionPreviewResponse preview(UUID userId) {
		long ownedTripCount = this.entityManager
				.createQuery("SELECT count(t) FROM TripJpaEntity t WHERE t.ownerUserId = :userId", Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		long itineraryCount = this.entityManager
				.createQuery("""
						SELECT count(i) FROM ItineraryJpaEntity i WHERE i.tripId IN
						(SELECT t.tripId FROM TripJpaEntity t WHERE t.ownerUserId = :userId)
						""", Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		// 🔴 story 엔티티를 import 하지 않는다 — 위 델리트 메서드들과 같은 이유
		// (클래스 상단 "남의 패키지 코드를 고치지 않고 행만 지운다").
		long recordCount = this.entityManager
				.createQuery("SELECT count(s) FROM Story s WHERE s.authorUserId = :userId AND s.deletedAt IS NULL",
						Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		return new AccountDeletionPreviewResponse(ownedTripCount, itineraryCount, recordCount);
	}

	/** 이 사람이 소유한 여행. 동행자로만 참여한 여행은 여기 없다 — 그건 남의 여행이다. */
	private List<UUID> ownedTripIds(UUID userId) {
		return this.entityManager
				.createQuery("SELECT t.tripId FROM TripJpaEntity t WHERE t.ownerUserId = :userId", UUID.class)
				.setParameter("userId", userId)
				.getResultList();
	}

	/**
	 * 여행과 그 아래 달린 것을 지운다.
	 *
	 * <p>🔴 순서가 곧 정확성이다. 자식을 먼저 지우지 않으면 외래키에 걸려 통째로 실패한다. JPQL 벌크
	 * 삭제는 데이터베이스의 {@code ON DELETE CASCADE} 를 타지 않으므로, 자동으로 지워질 것도 여기서
	 * 직접 적어야 한다.
	 */
	private void deleteTripData(UUID userId, List<UUID> tripIds) {
		if (!tripIds.isEmpty()) {
			// 🔴 2026-09-05 (S15P21E201-604) — 일정을 추천 작업보다 <b>먼저</b> 지운다.
			//    itinerary_item·itinerary_versions 의 source_request_id 가
			//    recommendation_job.request_id 를 가리키게 되면서, 예전 순서(작업 먼저)로는
			//    일정을 한 번이라도 만든 사용자의 탈퇴가 외래키 위반으로 통째로 실패한다.
			//    이 메서드 머리말이 경고한 "순서가 곧 정확성" 이 실제로 걸린 자리다.
			execute("""
					DELETE FROM ItineraryLegJpaEntity l WHERE l.itineraryVersionId IN
					(SELECT v.itineraryVersionId FROM ItineraryVersionJpaEntity v WHERE v.itineraryId IN
					 (SELECT i.itineraryId FROM ItineraryJpaEntity i WHERE i.tripId IN :tripIds))
					""", "tripIds", tripIds);
			execute("""
					DELETE FROM ItineraryItemJpaEntity it WHERE it.itineraryVersionId IN
					(SELECT v.itineraryVersionId FROM ItineraryVersionJpaEntity v WHERE v.itineraryId IN
					 (SELECT i.itineraryId FROM ItineraryJpaEntity i WHERE i.tripId IN :tripIds))
					""", "tripIds", tripIds);
			execute("""
					DELETE FROM ItineraryVersionJpaEntity v WHERE v.itineraryId IN
					(SELECT i.itineraryId FROM ItineraryJpaEntity i WHERE i.tripId IN :tripIds)
					""", "tripIds", tripIds);
			execute("DELETE FROM ItineraryJpaEntity i WHERE i.tripId IN :tripIds", "tripIds", tripIds);

			execute("""
					DELETE FROM RecommendationCandidate c WHERE c.requestId IN
					(SELECT j.requestId FROM RecommendationJob j WHERE j.tripId IN :tripIds)
					""", "tripIds", tripIds);
			execute("DELETE FROM RecommendationJob j WHERE j.tripId IN :tripIds", "tripIds", tripIds);
		}

		// 추천 기록은 여행 없이도 남을 수 있다 (지금 위치 기준 추천 등).
		execute("""
				DELETE FROM RecommendationCandidate c WHERE c.requestId IN
				(SELECT j.requestId FROM RecommendationJob j WHERE j.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM RecommendationJob j WHERE j.userId = :userId", "userId", userId);

		execute("""
				DELETE FROM ConstraintAnswerJpaEntity a WHERE a.constraintSnapshotId IN
				(SELECT s.constraintSnapshotId FROM ConstraintSnapshotJpaEntity s WHERE s.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM ConstraintSnapshotJpaEntity s WHERE s.userId = :userId", "userId", userId);
		execute("""
				DELETE FROM PreferenceAnswerJpaEntity a WHERE a.preferenceSnapshotId IN
				(SELECT s.preferenceSnapshotId FROM PreferenceSnapshotJpaEntity s WHERE s.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM PreferenceSnapshotJpaEntity s WHERE s.userId = :userId", "userId", userId);

		// 🔴 남의 여행의 동행자 자격도 지운다 — 탈퇴했으면 그 여행에서도 빠지는 것이 맞다.
		execute("DELETE FROM TripMemberJpaEntity m WHERE m.userId = :userId", "userId", userId);

		if (!tripIds.isEmpty()) {
			execute("DELETE FROM TripMemberJpaEntity m WHERE m.tripId IN :tripIds", "tripIds", tripIds);
			execute("DELETE FROM TripJpaEntity t WHERE t.tripId IN :tripIds", "tripIds", tripIds);
		}
	}

	/**
	 * 로그인에 쓰이는 것을 전부 지운다.
	 *
	 * <p>일회용 토큰은 {@code local_credential} 에, refresh token 은 {@code auth_session} 에 각각
	 * {@code ON DELETE CASCADE} 로 달려 있다. 여기서는 엔티티를 지우므로 그 규칙이 그대로 돈다.
	 */
	private void deleteLoginMeans(UUID userId, LocalCredential credential) {
		this.sessionRepository.deleteAll(this.sessionRepository.findAllByUserUserId(userId));
		this.identityRepository.deleteAll(this.identityRepository.findAllByUserUserId(userId));
		this.credentialRepository.delete(credential);
		this.consentRepository.deleteAll(this.consentRepository.findAllByUserUserId(userId));
	}

	/**
	 * 이 사람이 남긴 기록(story)도 지운다 — S15P21E201-188(2026-09-07, 진미리 님 확정 요청).
	 *
	 * <p>🔴 {@code story} 표의 자기 방식대로 지운다 — 행을 지우지 않고 {@code deleted_at} 을
	 * 찍는다({@code StoryService.delete} 와 같은 규칙, {@code V20260906160000} 마이그레이션
	 * 주석 참고). 그래야 {@link #preview}의 {@code recordCount}(살아있는 기록만 센다)와
	 * 실제 삭제 결과가 어긋나지 않는다.
	 *
	 * <p>🔴 <b>딸린 사진 파일은 여기서 지우지 않는다.</b> {@code StoryService.delete}는
	 * {@code StorageCleanupService}로 파일 저장소의 실제 파일까지 지우는데, 이 메서드는
	 * (클래스 상단이 정한 대로) JPQL 벌크 갱신 하나뿐이라 그 경로를 안 탄다. 그래서 이 사람의
	 * 기록 사진은 DB에서는 안 보이지만 저장소에는 당분간 남는다 — 알려진 한계다. 사람 단위
	 * 일괄 삭제 빈도가 낮고, 물리 파일 정리는 story 쪽 정기 청소(S15P21E201-226)가 이미
	 * {@code storage_cleanup_queue}로 못 지운 키를 다시 시도하는 것과 같은 성격의 문제라
	 * 그쪽에 맡긴다.
	 */
	private void deleteStories(UUID userId) {
		this.entityManager.createQuery(
				"UPDATE Story s SET s.deletedAt = :now WHERE s.authorUserId = :userId AND s.deletedAt IS NULL")
				.setParameter("now", this.clock.instant())
				.setParameter("userId", userId)
				.executeUpdate();
	}

	/**
	 * 이벤트 기록에서 사람만 떼어 낸다.
	 *
	 * <p>🔴 지우지 않는 이유. 이벤트는 "무슨 일이 몇 번 일어났나" 를 보는 집계의 원본이라, 지우면
	 * 지난 통계가 소급해서 바뀐다. {@code user_id} 는 비울 수 있는 칸이라 사람만 떼어 내고 사건은
	 * 남긴다.
	 */
	private void detachEvents(UUID userId) {
		execute("UPDATE EventOutbox e SET e.userId = null WHERE e.userId = :userId", "userId", userId);
	}

	private void execute(String jpql, String parameterName, Object value) {
		this.entityManager.createQuery(jpql).setParameter(parameterName, value).executeUpdate();
	}
}
