package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.gabolle.backend.itinerary.domain.ItineraryRunRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.api.AccountDeletionPreviewResponse;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.story.application.StorageCleanupService;
import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 계정과 그 사람의 데이터를 지운다.
 *
 * <p>계정 행 자체는 지우지 않고 개인정보만 비운다. {@code itinerary_versions.created_by} 가
 * 필수 값이면서 {@code app_user} 를 가리키고 {@code ON DELETE} 규칙이 없어, 동행자의 일정을
 * 편집한 적이 있으면 계정 행을 지우려면 남의 편집 이력까지 지워야 하기 때문이다.
 * {@code app_user} 의 {@code DELETED} 상태와 {@code deleted_at} 칸이 이 용도다.
 *
 * <p>로그인에 필요한 것과 본인 데이터는 전부 지운다. 결과적으로 로그인이 안 되고, 이메일이 풀려
 * 같은 주소로 다시 가입할 수 있고, 남는 행에는 개인을 알아볼 값이 없다.
 *
 * <p>여행·일정·추천은 다른 패키지의 코드라 그쪽 클래스를 고치지 않고 JPQL 로 행만 지운다.
 * 엔티티 이름을 문자열로 쓰므로 그쪽이 이름을 바꾸면 컴파일이 아니라 실행에서 터지고,
 * 그것을 통합 테스트가 실제 DB 에서 잡는다.
 *
 * <p>네이티브 SQL 을 쓰지 않는다. 운영은 {@code gabolle} schema 를 쓰는데 네이티브 SQL 은 그것을
 * 물려받지 않고, 그 어긋남은 테스트로 안 잡힌다.
 */
@Service
@Profile({"db", "dev"})
public class AccountDeletionService {

	/**
	 * 사용자가 탈퇴 화면에서 직접 쳐야 하는 값. 언어와 무관한 고정 영문이다 — 언어별 문구를 두면
	 * 서버가 요청 언어를 판정해야 하고 그것이 틀리면 탈퇴가 막힌다. 안내는 화면이 각 언어로 한다.
	 */
	public static final String CONFIRMATION_PHRASE = "DELETE";

	@PersistenceContext
	private EntityManager entityManager;

	private final LocalCredentialRepository credentialRepository;

	private final AuthSessionRepository sessionRepository;

	private final AuthIdentityRepository identityRepository;

	private final UserConsentRepository consentRepository;

	private final AppUserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final StorageCleanupService storageCleanupService;

	/**
	 * 위치 궤적을 지우려고 받는다. {@link #USER_OWNED_ROWS} 로 못 지우는 이유는 그 표에
	 * 사람을 가리키는 칸이 없기 때문이다 — 일정을 거쳐 여행의 주인까지 가야 한다.
	 * <p>
	 * 🔴 FK 연쇄 삭제에 기대지 않고 따로 부른다. 위치 기록은 이 저장소에서 제일 민감한
	 * 자료라 <b>지우는 자리가 코드에 보여야</b> 한다. 연쇄 삭제는 스키마를 열어 봐야만 알 수
	 * 있고, 그 사이에 표가 하나 끼면 조용히 안 지워진다.
	 */
	private final ObjectProvider<ItineraryRunRepository> itineraryRuns;

	public AccountDeletionService(LocalCredentialRepository credentialRepository,
			AuthSessionRepository sessionRepository, AuthIdentityRepository identityRepository,
			UserConsentRepository consentRepository, AppUserRepository userRepository,
			PasswordEncoder passwordEncoder, Clock clock, StorageCleanupService storageCleanupService,
			ObjectProvider<ItineraryRunRepository> itineraryRuns) {
		this.credentialRepository = credentialRepository;
		this.sessionRepository = sessionRepository;
		this.identityRepository = identityRepository;
		this.consentRepository = consentRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.storageCleanupService = storageCleanupService;
		this.itineraryRuns = itineraryRuns;
	}

	/**
	 * 탈퇴 확인을 받고 지운다.
	 *
	 * <p>한 트랜잭션이다. 도중에 실패하면 아무것도 지워지지 않은 상태로 돌아간다 — 반쯤 지워진
	 * 계정은 로그인도 안 되고 데이터도 못 찾는, 아무도 손댈 수 없는 상태가 된다.
	 *
	 * <p>비밀번호는 필수가 아니다. 소셜로만 가입한 계정에는 {@code local_credential} 행이 아예
	 * 없고, 그 계정은 비밀번호 재설정도 시작할 수 없어 필수로 두면 탈퇴할 길이 없어진다.
	 * 본인 확인은 사용자가 직접 치는 {@link #CONFIRMATION_PHRASE} 가 맡는다.
	 *
	 * <p>확인을 참·거짓 한 칸으로 받지 않는다. 그러면 화면이 기본값으로 채워 보낼 수 있어
	 * 확인이 아니라 형식이 된다. 이 확인은 실수로 누르는 것을 막을 뿐, 로그인한 채 자리를 비운
	 * 사이 남이 지우는 것은 막지 못한다.
	 *
	 * @param confirmation 사용자가 직접 친 확인 값. {@link #CONFIRMATION_PHRASE} 와 정확히 같아야 한다
	 * @param password     비밀번호로 가입한 계정만 보낸다. 비워도 되지만 보냈으면 맞아야 한다
	 */
	@Transactional
	public void delete(UUID userId, String confirmation, String password) {
		// 확인 값이 맨 먼저다. 이것이 틀리면 계정을 조회조차 하지 않는다.
		if (!CONFIRMATION_PHRASE.equals(confirmation)) {
			throw new AuthException("DELETION_NOT_CONFIRMED",
					"탈퇴를 확인하려면 '" + CONFIRMATION_PHRASE + "' 를 정확히 입력해야 합니다.",
					HttpStatus.BAD_REQUEST);
		}

		// 계정이 쓸 수 있는 상태인지가 비밀번호보다 먼저다. 반대로 두면 이미 지운 계정에 대해
		// "비밀번호가 없는 소셜 계정입니다" 가 나가, 사실과 다를 뿐 아니라 쓸 수 없는 계정의
		// 로그인 수단을 알려 주게 된다.
		AppUser user = this.userRepository.findById(userId)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));

		// 이미 지운 계정을 또 지우지 않는다. 접속 토큰은 발급 시점부터 30분 살아 있어 탈퇴
		// 직후에도 같은 토큰으로 한 번 더 부를 수 있다 — 익명화가 두 번 도는 것을 여기서 끊는다.
		if (user.getStatus() == UserStatus.DELETED) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.UNAUTHORIZED);
		}

		LocalCredential credential = this.credentialRepository.findByUserUserId(userId).orElse(null);

		// 비밀번호는 선택이지만 보냈으면 반드시 맞아야 한다. 틀린 것을 조용히 무시하면 사용자는
		// 한 겹 더 확인했다고 믿는데 실제로는 그 겹이 없다.
		if (password != null && !password.isBlank()) {
			if (credential == null) {
				throw new AuthException("PASSWORD_NOT_SET",
						"이 계정에는 비밀번호가 없습니다. 소셜 로그인으로 가입한 계정입니다.",
						HttpStatus.BAD_REQUEST);
			}
			if (!this.passwordEncoder.matches(password, credential.getPasswordHash())) {
				throw new AuthException("INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.",
						HttpStatus.UNAUTHORIZED);
			}
		}

		List<UUID> tripIds = ownedTripIds(userId);

		// 이 네 줄의 순서는 외래키가 정한다. recommendation_job·feed_build 가
		// user_taste_vector 를, user_taste_vector 가 preference_snapshot 을, feed_build 가
		// constraint_snapshot 을, 두 스냅샷이 trip 을 가리킨다. 그래서
		// 추천 → 개인화 파생값 → 스냅샷 → 여행 말고 다른 순서가 없다.
		// fk_user_taste_vector_preference_snapshot 은 ON DELETE 가 없어 NO ACTION 이라,
		// 스냅샷을 벡터보다 먼저 지우면 트랜잭션 전체가 실패한다.
		deleteItineraryAndRecommendations(userId, tripIds);
		deletePersonalizationArtifacts(userId);
		deleteSnapshots(userId);
		deleteTripsAndMemberships(userId, tripIds);
		deleteUploadedFiles(userId);
		deleteStories(userId);
		deleteLoginMeans(userId, credential);
		deleteUserOwnedRows(userId);
		deleteLocationTrails(userId);
		detachEvents(userId);

		user.anonymizeForDeletion(this.clock.instant());
	}

	/**
	 * {@code ON DELETE CASCADE} 에만 기대던 표들.
	 *
	 * <p>이 서비스는 {@code app_user} 행을 지우지 않고 익명화하므로, {@code app_user} 를 가리키는
	 * {@code ON DELETE CASCADE} 는 한 번도 발동하지 않는다. 부모가 안 지워지니 자식도 안 지워지고
	 * 오류도 안 난다 — 탈퇴는 성공으로 끝나고 행만 남는다.
	 *
	 * <p>그래서 사람을 가리키는 표를 새로 만들면 {@code ON DELETE CASCADE} 와 별개로 이 목록에
	 * 한 줄을 더해야 한다.
	 *
	 * <p>순서가 중요한 것들({@code itinerary} → 개인화 파생값 → 스냅샷 → {@code trip})은 위의
	 * 전용 메서드에 둔다. 이 목록에는 그 사람의 행을 그냥 지우면 되는 것만 담는다.
	 */
	static final List<OwnedRows> USER_OWNED_ROWS = List.of(
			new OwnedRows("SavedPlace", "userId"),
			new OwnedRows("Collection", "userId"),
			new OwnedRows("PlaceReview", "userId"),
			new OwnedRows("PlaceVisitVerification", "userId"),
			new OwnedRows("MenuScanUsage", "userId"),
			// 음식 그림을 만든 횟수. 그림(dish_image)과 설명(dish_description)은 사람을 안
			// 가리키고 모두가 함께 쓴다 — 지울 것은 누가 몇 번 만들었나뿐이다.
			new OwnedRows("DishImageUsage", "userId"),
			// 팔로우·차단은 사람을 가리키는 칸이 둘이다. 한쪽만 지우면 없는 사람을 팔로우한
			// 기록이 남는다.
			new OwnedRows("UserFollow", "key.followerUserId"),
			new OwnedRows("UserFollow", "key.followeeUserId"),
			new OwnedRows("UserBlock", "key.blockerUserId"),
			new OwnedRows("UserBlock", "key.blockedUserId"),
			// 글에 단 좋아요·싫어요. 묻힌 키의 필드 이름이 팔로우·차단과 다르다(key 가 아니라 id).
			new OwnedRows("StoryReaction", "id.userId"),
			new OwnedRows("StorySave", "userId"),
			// 조회·링크복사 낱개. 익명 세션이 남긴 낱개는 여기서 안 지운다 — 그 행은 사람을
			// 안 가리킨다. 90일 보관 규칙이 그쪽을 치우고, 누적 칸(story.view_count)은 어느
			// 쪽이든 안 내린다.
			new OwnedRows("StoryView", "userId"),
			new OwnedRows("StoryLinkCopy", "userId"),
			// 여행 조건 모달의 답. 알레르기·식단이 들어 있다.
			new OwnedRows("TravelConstraintJpaEntity", "userId"),
			// 이 둘은 남이 참조한다 — trip_member.trip_invite_id 와
			// trip_seed_place.source_share_link_id. 둘 다 ON DELETE SET NULL 이라
			// 남의 참여·씨앗은 남고 이 사람의 초대 기록만 끊긴다.
			new OwnedRows("TripInviteJpaEntity", "createdBy"),
			new OwnedRows("TripShareLink", "createdBy"),
			// 이 칸은 @ManyToOne 이라 경로가 한 칸 더 들어간다. 지워도 안전한 이유는
			// 가입/연결을 끝내기 전의 10분짜리 1회용 티켓이라서다.
			new OwnedRows("OAuthSignupTicket", "existingUser.userId"),
			// 🔴 기기 푸시 토큰 (S15P21E201-1391). 표에 ON DELETE CASCADE 를 걸어 두었지만
			//    이 서비스는 app_user 행을 «지우지 않고 익명화» 하므로 그 규칙은 한 번도 안 돈다.
			//    남기면 탈퇴한 사람의 폰으로 그 여행 알림이 계속 간다 — 동행자가 일정을 고칠
			//    때마다, 계정이 없어진 뒤에도.
			new OwnedRows("PushTokenJpaEntity", "userId"),
			// 여행 별점 (S15P21E201-1908). 남의 여행에 매긴 별점은 여행이 남으므로 CASCADE 로 안 지워진다.
			new OwnedRows("TripRatingJpaEntity", "id.userId"),
			// 여행 돈 (S15P21E201-1935) — 이 사람이 «적은» 줄. 남이 적은 줄의 낸 사람 칸은 남는다(동행의 장부가 통째로 비면 안 된다).
			new OwnedRows("TripExpenseJpaEntity", "createdBy"));

	/**
	 * 지울 표 하나 — 엔티티 이름과 그 사람을 가리키는 칸.
	 *
	 * @param entityName JPQL 이 쓰는 엔티티 이름이다. DB 표 이름이 아니다
	 * @param userField 그 사람을 가리키는 칸의 JPQL 경로 (묻힌 키·관계면 점이 들어간다)
	 */
	record OwnedRows(String entityName, String userField) {
	}

	/**
	 * {@link #USER_OWNED_ROWS} 의 표에서 이 사람의 행을 지운다.
	 *
	 * <p>순서를 신경 쓰지 않아도 되는 것만 이 목록에 있다. 자식이 매달린 셋은 DB 가 처리한다 —
	 * {@code collection_item} 은 {@code CASCADE} 로 같이 지워지고, 초대·공유 링크를 가리키는
	 * 둘은 {@code SET NULL} 로 칸만 비워진다.
	 */
	/**
	 * 이 사람의 위치 궤적을 지운다.
	 *
	 * <p>빈이 없는 판(일정 쪽을 안 띄우는 시험 슬라이스)에서는 아무 일도 안 한다 — 지울
	 * 궤적도 없다.
	 */
	private void deleteLocationTrails(UUID userId) {
		ItineraryRunRepository runs = this.itineraryRuns.getIfAvailable();
		if (runs != null) {
			runs.deletePingsOfUser(userId.toString());
		}
	}

	private void deleteUserOwnedRows(UUID userId) {
		for (OwnedRows owned : USER_OWNED_ROWS) {
			execute("DELETE FROM %s e WHERE e.%s = :userId".formatted(owned.entityName(), owned.userField()),
					"userId", userId);
		}
	}

	/**
	 * 탈퇴한 사람이 올린 사진과 동영상의 연결·DB 행·실제 파일을 함께 지운다.
	 *
	 * <p>순서가 규칙이다. {@code story_image}·{@code story_video} 가 업로드 표를 가리키는데 그
	 * 외래키에 {@code ON DELETE} 가 없어, 연결을 먼저 지우지 않고 업로드 행을 지우면 외래키
	 * 위반으로 트랜잭션이 통째로 되돌아간다. 사진과 동영상을 한 메서드에서 다루는 것도 그
	 * 순서 규칙을 두 곳에 두지 않기 위해서다.
	 *
	 * <p>동영상 썸네일은 사진 창구로 올라와 {@code uploaded_image} 행이 된다. 이 메서드는
	 * {@code story_image} 를 걸지 않고 올린 사람으로 찾으므로 그 썸네일도 함께 걸린다.
	 *
	 * <p>파일 저장소 삭제는 현재 DB 트랜잭션이 커밋된 뒤 실행되고, 실패하면 뒷정리 대기열에
	 * 남는다. 파일 하나 때문에 탈퇴 전체를 되돌리지 않으면서도 파일을 조용히 남기지 않는다.
	 */
	private void deleteUploadedFiles(UUID userId) {
		List<String> imageKeys = this.entityManager.createQuery(
				"SELECT i.storageKey FROM UploadedImage i WHERE i.uploaderUserId = :userId", String.class)
				.setParameter("userId", userId)
				.getResultList();
		List<String> videoKeys = this.entityManager.createQuery(
				"SELECT v.storageKey FROM UploadedVideo v WHERE v.uploaderUserId = :userId", String.class)
				.setParameter("userId", userId)
				.getResultList();

		// 가리키는 행을 먼저 지운다. story_video 는 동영상과 썸네일 둘 다를 가리키므로
		// 어느 쪽이 이 사용자 것이든 걸리게 한다.
		execute("""
				DELETE FROM StoryVideo sv WHERE sv.uploadedVideoId IN
				(SELECT v.uploadedVideoId FROM UploadedVideo v WHERE v.uploaderUserId = :userId)
				OR sv.thumbnailUploadId IN
				(SELECT i.uploadedImageId FROM UploadedImage i WHERE i.uploaderUserId = :userId)
				""", "userId", userId);
		execute("""
				DELETE FROM StoryImage si WHERE si.uploadedImageId IN
				(SELECT i.uploadedImageId FROM UploadedImage i WHERE i.uploaderUserId = :userId)
				""", "userId", userId);

		// 그다음 업로드 행. 이제 아무도 안 가리킨다.
		execute("DELETE FROM UploadedVideo v WHERE v.uploaderUserId = :userId", "userId", userId);
		execute("DELETE FROM UploadedImage i WHERE i.uploaderUserId = :userId", "userId", userId);

		imageKeys.forEach(key -> this.storageCleanupService.deleteOrEnqueue(
				key, StorageCleanupEntry.REASON_ACCOUNT_DELETED));
		videoKeys.forEach(key -> this.storageCleanupService.deleteOrEnqueue(
				key, StorageCleanupEntry.REASON_ACCOUNT_DELETED));
	}

	/**
	 * 삭제 전 안내 화면이 보여줄 실제 영향 수. {@link #delete} 가 실제로 지우는 것과 같은
	 * 기준으로 센다.
	 */
	@Transactional(readOnly = true)
	public AccountDeletionPreviewResponse preview(UUID userId) {
		// 지운 여행은 세지 않는다 — 사용자가 "내 여행" 에서 보는 목록과 기준을 맞춘다.
		// 되돌릴 수 없는 동작의 안내 숫자라, 실제로 지워질 것보다 크면 사용자가 무엇을 잃는지
		// 잘못 알고 결정하게 된다.
		long ownedTripCount = this.entityManager
				.createQuery("SELECT count(t) FROM TripJpaEntity t WHERE t.ownerUserId = :userId "
						+ "AND t.deletedAt IS NULL", Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		long itineraryCount = this.entityManager
				.createQuery("""
						SELECT count(i) FROM ItineraryJpaEntity i WHERE i.tripId IN
						(SELECT t.tripId FROM TripJpaEntity t WHERE t.ownerUserId = :userId
						 AND t.deletedAt IS NULL)
						""", Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		// story 엔티티를 import 하지 않는다 — 삭제 메서드들과 같은 이유다.
		long recordCount = this.entityManager
				.createQuery("SELECT count(s) FROM Story s WHERE s.authorUserId = :userId AND s.deletedAt IS NULL",
						Long.class)
				.setParameter("userId", userId)
				.getSingleResult();

		return new AccountDeletionPreviewResponse(ownedTripCount, itineraryCount, recordCount);
	}

	/** 이 사람이 소유한 여행. 동행자로만 참여한 여행은 여기 없다 — 그건 남의 여행이다. */
	/**
	 * 익명 세션이 만든 여행과 그 일정·추천·스냅샷을 지운다 — {@link AnonymousSessionCleanupService} 전용.
	 *
	 * <p>탈퇴의 삭제 사슬에서 여행에 매달린 세 칸만 같은 순서로 돈다. 익명 세션에는 취향 벡터·피드·
	 * 업로드처럼 사람에게 붙는 것이 없다 — 그 표들은 app_user 외래키가 있어 세션 ID 로는 애초에 못
	 * 들어간다. 순서를 여기서 다시 쓰지 않고 빌려 쓰는 것은, 일정 쪽 표가 늘 때 탈퇴만 고쳐지고 이쪽이
	 * 남는 일을 막으려는 것이다.
	 *
	 * <p>트랜잭션은 부르는 쪽 것이다.
	 */
	void deleteAnonymousOwnerData(UUID sessionId) {
		List<UUID> tripIds = ownedTripIds(sessionId);
		deleteItineraryAndRecommendations(sessionId, tripIds);
		deleteSnapshots(sessionId);
		deleteTripsAndMemberships(sessionId, tripIds);
	}

	private List<UUID> ownedTripIds(UUID userId) {
		return this.entityManager
				.createQuery("SELECT t.tripId FROM TripJpaEntity t WHERE t.ownerUserId = :userId", UUID.class)
				.setParameter("userId", userId)
				.getResultList();
	}

	/**
	 * 일정과 추천 기록을 지운다 — 삭제 사슬의 첫 칸.
	 *
	 * <p>순서가 곧 정확성이다. 자식을 먼저 지우지 않으면 외래키에 걸려 통째로 실패한다.
	 * 스냅샷과 여행은 여기서 지우지 않는다 — {@link #delete} 의 순서 주석 참고.
	 *
	 * <p>여기서 하는 일은 추천 사슬을 끊는 것까지다. {@code recommendation_job} 이 사라져야
	 * 다음 칸에서 취향 벡터를 지울 수 있다({@code recommendation_job.taste_vector_id}).
	 */
	private void deleteItineraryAndRecommendations(UUID userId, List<UUID> tripIds) {
		if (!tripIds.isEmpty()) {
			// 두 표가 서로를 가리키므로 한쪽을 통째로 먼저 지우는 것으로는 못 푼다.
			//
			//   itinerary_versions.source_request_id → recommendation_job.request_id
			//   recommendation_job.itinerary_id      → itineraries.itinerary_id
			//
			// 푸는 자리는 일정 행(itineraries) 하나다. 그 위의 판·항목·구간은 추천 작업을
			// 가리키므로 작업보다 먼저 지우고, 일정 행 자체는 작업이 가리키므로 작업보다
			// 나중에 지운다. 그래서 일정 묶음이 추천 작업을 사이에 두고 갈라진다.
			//
			// itinerary_id 를 null 로 끊는 방법은 안 된다. ck_recommendation_job_result_present
			// 가 성공한 일정 생성 작업에 itinerary_id 를 요구해 그 CHECK 에 걸린다.
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
			execute("""
					DELETE FROM RecommendationCandidate c WHERE c.requestId IN
					(SELECT j.requestId FROM RecommendationJob j WHERE j.tripId IN :tripIds)
					""", "tripIds", tripIds);
			execute("DELETE FROM RecommendationJob j WHERE j.tripId IN :tripIds", "tripIds", tripIds);

			// 일정 행은 여기서 지운다. 추천 작업이 이 행을 가리키므로 작업보다 먼저 지울 수 없다.
			execute("DELETE FROM ItineraryJpaEntity i WHERE i.tripId IN :tripIds", "tripIds", tripIds);
		}

		// 추천 기록은 여행 없이도 남을 수 있다 (지금 위치 기준 추천 등).
		execute("""
				DELETE FROM RecommendationCandidate c WHERE c.requestId IN
				(SELECT j.requestId FROM RecommendationJob j WHERE j.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM RecommendationJob j WHERE j.userId = :userId", "userId", userId);
	}

	/**
	 * 설문·제약 스냅샷을 지운다 — 삭제 사슬의 셋째 칸.
	 *
	 * <p>개인화 파생값보다 뒤, 여행보다 앞이어야 한다. 앞에서는
	 * {@code user_taste_vector.source_preference_snapshot_id} 와
	 * {@code feed_build.constraint_snapshot_id} 가 이 스냅샷들을 가리키고 둘 다
	 * {@code ON DELETE} 가 없어 NO ACTION 이며, 뒤에서는
	 * {@code preference_snapshot.trip_id}·{@code constraint_snapshot.trip_id} 가 여행을 가리킨다.
	 */
	private void deleteSnapshots(UUID userId) {
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
	}

	/**
	 * 동행자 자격과 본인 소유 여행을 지운다 — 삭제 사슬의 마지막 칸.
	 *
	 * <p>스냅샷이 {@code trip_id} 로 여행을 가리키므로 반드시 그 뒤에 온다.
	 */
	private void deleteTripsAndMemberships(UUID userId, List<UUID> tripIds) {
		// 남의 여행의 동행자 자격도 지운다 — 탈퇴했으면 그 여행에서도 빠진다.
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
		// 소셜로만 가입한 계정은 자격증명이 없다. null 은 지울 행이 없다는 뜻이다.
		if (credential != null) {
			this.credentialRepository.delete(credential);
		}
		this.consentRepository.deleteAll(this.consentRepository.findAllByUserUserId(userId));
	}

	/**
	 * 이 사람이 남긴 기록(story)을 지운다.
	 *
	 * <p>{@code story} 표의 방식대로 행을 지우지 않고 {@code deleted_at} 을 찍는다. 그래야
	 * {@link #preview} 의 {@code recordCount} 와 실제 삭제 결과가 어긋나지 않는다.
	 *
	 * <p>딸린 사진은 {@link #deleteUploadedFiles} 가 연결과 업로드 행을 지우고 실제 파일도 커밋
	 * 뒤 삭제한다. 기록 행은 신고·검토 근거로 익명화된 작성자와 함께 남지만 사진 주소와 저장
	 * 키는 남지 않는다.
	 */
	private void deleteStories(UUID userId) {
		// 댓글을 지우기 전에 부모의 세기를 먼저 내린다. 아래 UPDATE 가 deleted_at 을 찍고 나면
		// 조건이 deleted_at IS NULL 이라 이 사람의 댓글을 더는 고를 수 없고, 세기가 영영 안
		// 내려간다. 부모마다 조회하지 않고 한 문장으로 내린다 — 탈퇴 한 번에 댓글이 수백 개일
		// 수 있다. 지운 사람의 댓글에 달린 남의 답글은 그대로 남는다.
		this.entityManager.createQuery("""
				UPDATE Story p SET p.replyCount = p.replyCount -
				    (SELECT count(r) FROM Story r
				      WHERE r.parentStoryId = p.storyId AND r.authorUserId = :userId AND r.deletedAt IS NULL)
				 WHERE p.storyId IN
				    (SELECT r2.parentStoryId FROM Story r2
				      WHERE r2.authorUserId = :userId AND r2.deletedAt IS NULL AND r2.parentStoryId IS NOT NULL)
				""")
				.setParameter("userId", userId)
				.executeUpdate();

		this.entityManager.createQuery(
				"UPDATE Story s SET s.deletedAt = :now WHERE s.authorUserId = :userId AND s.deletedAt IS NULL")
				.setParameter("now", this.clock.instant())
				.setParameter("userId", userId)
				.executeUpdate();
	}

	/**
	 * 개인화가 만들어 둔 파생값을 지운다 — 취향 벡터·성분, 피드 세대와 그 줄.
	 *
	 * <p>안에서의 순서는 외래키가 정한다. 피드 줄 → 피드 세대 → 취향 성분 → 취향 판이다.
	 * {@code feed_build.taste_vector_id} 가 {@code user_taste_vector} 를 가리키므로 세대를
	 * 먼저 지우지 않으면 판을 못 지운다.
	 *
	 * <p>이 메서드 자체의 자리는 추천 뒤, 스냅샷 앞 하나뿐이다.
	 * {@code recommendation_job.taste_vector_id} 가 취향 판을 가리키므로
	 * {@link #deleteItineraryAndRecommendations} 보다 뒤여야 하고, 여기서 지우는
	 * {@code user_taste_vector}·{@code feed_build} 가 스냅샷을 가리키므로
	 * {@link #deleteSnapshots} 보다 앞이어야 한다.
	 */
	private void deletePersonalizationArtifacts(UUID userId) {
		execute("""
				DELETE FROM UserFeedEntry e WHERE e.id.buildId IN
				(SELECT b.buildId FROM FeedBuild b WHERE b.userId = :userId)
				""", "userId", userId);
		execute("""
				DELETE FROM CommunityFeedEntry e WHERE e.id.buildId IN
				(SELECT b.buildId FROM FeedBuild b WHERE b.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM FeedBuild b WHERE b.userId = :userId", "userId", userId);

		execute("""
				DELETE FROM UserTasteWeight w WHERE w.id.tasteVectorId IN
				(SELECT v.tasteVectorId FROM UserTasteVector v WHERE v.userId = :userId)
				""", "userId", userId);
		execute("DELETE FROM UserTasteVector v WHERE v.userId = :userId", "userId", userId);

		// 🔴 (사람, 장소) 의 취향 반영 상태 (S15P21E201-1500). 소비자가 증분할 때 「직전이
		//    무엇이었나」를 여기서 읽는다 — 남겨 두면 탈퇴한 사람의 행동 이력이 그대로 남는다.
		//
		//    app_user 에 외래키로 묶여 있지만 ON DELETE CASCADE 는 일부러 안 걸었다. 탈퇴가
		//    계정 행을 지우지 않고 익명화하므로 그 CASCADE 는 한 번도 안 돈다 — push_token 이
		//    그렇게 만들어졌다가 「탈퇴한 사람 폰으로 알림이 계속 간다」로 잡혔다.
		//
		//    엔티티가 없어 네이티브다. 쓰는 쪽(TasteAttributionService)이 JdbcTemplate 이라
		//    매핑을 따로 두지 않았다.
		this.entityManager.createNativeQuery("DELETE FROM user_place_taste_state WHERE user_id = :userId")
			.setParameter("userId", userId)
			.executeUpdate();

		// 실제 방문 시각으로 만든 개인 속도 계수도 개인화 파생값이다. user_pace_factor 는
		// app_user 에 ON DELETE CASCADE 로 묶여 있지만 계정 행을 익명화만 하므로 그 CASCADE 가
		// 돌지 않아 여기서 직접 지운다.
		execute("DELETE FROM PaceFactorJpaEntity p WHERE p.userId = :userId", "userId", userId);
	}

	/**
	 * 이벤트 기록에서 사람만 떼어 낸다.
	 *
	 * <p>이벤트는 집계의 원본이라 지우면 지난 통계가 소급해서 바뀐다. 그래서 사람만 떼고 사건은
	 * 남긴다.
	 *
	 * <p>{@code partition_key} 도 함께 바꾼다. 그 칸에는 사용자 UUID 문자열이 그대로 들어가므로
	 * {@code user_id} 만 비우면 그 칸으로 같은 사람의 이벤트를 다시 묶을 수 있다. {@code NOT NULL}
	 * 이라 비울 수 없어 {@code aggregate_id} 로 바꾼다 — 사용자 ID 가 없을 때 원래 쓰는 값이다.
	 *
	 * <p>이 클래스에서 native SQL 을 쓰는 유일한 자리다. {@code EventOutbox.partitionKey} 에
	 * {@code @Column(updatable = false)} 가 붙어 있고, UUID 를 문자열로 바꾸는 일을 JPQL 의
	 * {@code cast} 에 맡기면 방언에 따라 결과가 갈린다. 이 문장은
	 * {@code spring.datasource.hikari.schema} 가 연결에 스키마를 걸어 두는 것에 기대고 있다.
	 */
	private void detachEvents(UUID userId) {
		this.entityManager.createNativeQuery("""
				UPDATE event_outbox SET user_id = NULL, partition_key = aggregate_id::text
				 WHERE user_id = :userId
				""").setParameter("userId", userId).executeUpdate();
	}

	private void execute(String jpql, String parameterName, Object value) {
		this.entityManager.createQuery(jpql).setParameter(parameterName, value).executeUpdate();
	}
}
