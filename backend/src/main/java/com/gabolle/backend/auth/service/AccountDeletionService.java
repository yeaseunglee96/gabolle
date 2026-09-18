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
import com.gabolle.backend.story.application.StorageCleanupService;
import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
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

	/**
	 * 사용자가 탈퇴 화면에서 직접 쳐야 하는 값 — S15P21E201-837.
	 *
	 * <p>🔴 한국어 문구가 아니라 고정 영문이다. 앱이 KO·EN 두 언어를 쓰므로 문구를 언어별로 두면
	 * 서버가 어느 언어로 온 요청인지 알아야 하고, 그 판정이 틀리면 탈퇴가 막힌다. 화면이
	 * <i>"탈퇴하려면 DELETE 를 입력하세요"</i> 를 각 언어로 안내하고 값 자체는 이것 하나로 보낸다.
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

	public AccountDeletionService(LocalCredentialRepository credentialRepository,
			AuthSessionRepository sessionRepository, AuthIdentityRepository identityRepository,
			UserConsentRepository consentRepository, AppUserRepository userRepository,
			PasswordEncoder passwordEncoder, Clock clock, StorageCleanupService storageCleanupService) {
		this.credentialRepository = credentialRepository;
		this.sessionRepository = sessionRepository;
		this.identityRepository = identityRepository;
		this.consentRepository = consentRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.storageCleanupService = storageCleanupService;
	}

	/**
	 * 탈퇴 확인을 받고 지운다 — S15P21E201-837.
	 *
	 * <p>🔴 한 트랜잭션이다. 도중에 실패하면 아무것도 지워지지 않은 상태로 돌아간다. 반쯤 지워진
	 * 계정은 로그인도 안 되고 데이터도 못 찾는, 아무도 손댈 수 없는 상태가 된다.
	 *
	 * <h2>왜 비밀번호를 필수에서 내렸나 (2026-09-11)</h2>
	 *
	 * 그전에는 {@code local_credential} 이 없으면 {@code LOCAL_CREDENTIAL_REQUIRED} 로 끊었다.
	 * 그런데 소셜로만 가입한 계정에는 그 행이 아예 없다 — {@code OAuthAccountService} 는 가입을
	 * 끝낼 때 자격증명을 만들지 않는다({@code OAuthSignupRequest} 가 이메일·비밀번호를 안 받는다).
	 * 그래서 <b>소셜 사용자에게는 계정을 지울 길이 하나도 없었다.</b> 비밀번호 재설정으로 하나
	 * 만들어 볼 수도 없다 — {@code AuthOneTimeToken} 이 {@code local_credential} 에 NOT NULL 로
	 * 매달려 있어 자격증명이 없으면 재설정이 시작조차 안 된다.
	 *
	 * <p>그래서 본인 확인을 <b>사용자가 직접 치는 값</b>({@link #CONFIRMATION_PHRASE})으로 옮겼다.
	 * 두 종류의 계정이 같은 흐름을 타므로 화면이 계정 종류를 먼저 알아낼 필요가 없다.
	 *
	 * <h2>🔴 이 변경으로 약해지는 것</h2>
	 *
	 * 원래 비밀번호 재확인은 <i>"로그인한 채 자리를 비운 사이 남이 눌러 지우는 것"</i> 을 막으려는
	 * 것이었다. 확인 값은 화면에 적힌 것을 따라 치면 되므로 그 상황은 못 막는다. 받아들인 근거는
	 * 균형이다 — 지금도 로그인 상태면 남이 여행·일정·기록을 지울 수 있고 그쪽에는 재확인이 없다.
	 * 탈퇴만 높게 잠가 두면 소셜 사용자는 아예 못 지우는 대가를 치른다.
	 *
	 * <p>대신 실수로 누르는 것은 막는다. 확인을 참·거짓 한 칸으로 안 받는 이유가 그것이다 —
	 * 참·거짓이면 화면이 기본값으로 채워 보낼 수 있고, 그러면 확인이 아니라 형식이 된다.
	 *
	 * <p>나중에 소셜 재로그인 증명을 <b>선택 항목으로</b> 얹을 수 있게 계약을 열어 뒀다. 그때는
	 * 이 자리에 증명 검사를 하나 더하면 되고 이미 나간 앱은 안 깨진다.
	 *
	 * @param confirmation 사용자가 직접 친 확인 값. {@link #CONFIRMATION_PHRASE} 와 정확히 같아야 한다
	 * @param password     비밀번호로 가입한 계정만 보낸다. 비워도 되지만 <b>보냈으면 맞아야 한다</b>
	 */
	@Transactional
	public void delete(UUID userId, String confirmation, String password) {
		// 🔴 확인 값이 맨 먼저다. 이것이 틀리면 계정을 조회조차 하지 않는다.
		if (!CONFIRMATION_PHRASE.equals(confirmation)) {
			throw new AuthException("DELETION_NOT_CONFIRMED",
					"탈퇴를 확인하려면 '" + CONFIRMATION_PHRASE + "' 를 정확히 입력해야 합니다.",
					HttpStatus.BAD_REQUEST);
		}

		// 🔴 계정이 쓸 수 있는 상태인지가 비밀번호보다 먼저다. 순서를 반대로 뒀다가 CI 에서 잡혔다
		// (2026-09-11, 파이프라인 189067) — 이미 지운 계정에 비밀번호를 실어 다시 부르면 자격증명이
		// 없으므로 "이 계정에는 비밀번호가 없습니다(소셜 계정입니다)" 가 나갔다. 사실과 다른 안내이고,
		// 쓸 수 없는 계정에 대해 "비밀번호가 있는 계정인가" 를 알려 주는 것이기도 하다.
		AppUser user = this.userRepository.findById(userId)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));

		// 🔴 이미 지운 계정을 또 지우지 않는다 (S15P21E201-837). 전에는 자격증명이 사라진 덕분에
		// 두 번째 호출이 LOCAL_CREDENTIAL_REQUIRED 로 막혔는데, 자격증명이 선택이 된 지금은 그
		// 우연한 방어가 없다. 접속 표는 발급 시점부터 30분 살아 있으므로 탈퇴 직후에도 같은 표로
		// 한 번 더 부를 수 있다 — 그때 익명화가 두 번 도는 것을 여기서 끊는다.
		if (user.getStatus() == UserStatus.DELETED) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.UNAUTHORIZED);
		}

		LocalCredential credential = this.credentialRepository.findByUserUserId(userId).orElse(null);

		// 🔴 비밀번호는 선택이지만 보냈으면 반드시 맞아야 한다. 틀린 것을 조용히 무시하면 사용자는
		// 자기가 한 겹 더 확인했다고 믿는데 실제로는 그 겹이 없었던 것이 된다.
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

		// 🔴 이 네 줄의 순서는 취향이 아니라 외래키가 정한다 (2026-09-14). 넷이 걸려 있다.
		//
		//    recommendation_job.taste_vector_id              → user_taste_vector
		//    feed_build.taste_vector_id                      → user_taste_vector
		//    user_taste_vector.source_preference_snapshot_id → preference_snapshot
		//    feed_build.constraint_snapshot_id               → constraint_snapshot
		//    preference_snapshot.trip_id · constraint_snapshot.trip_id → trip
		//
		//    그래서 <b>추천 → 개인화 파생값 → 스냅샷 → 여행</b> 말고 다른 순서가 없다.
		//    전에는 스냅샷이 첫 칸(그때 이름은 deleteTripData)에 같이 들어 있어서 벡터보다 먼저
		//    지워졌고, 그 결과 <b>설문을 낸 뒤 배치가 한 번이라도 접은 사람은 탈퇴가 실패했다</b> —
		//    fk_user_taste_vector_preference_snapshot 은 ON DELETE 가 없어 NO ACTION 이다.
		//    트랜잭션이 하나라 500 만 나가고 아무것도 안 지워진다. 배치가 매일 다시 접으므로
		//    다시 시도해도 성공하는 날이 없다.
		deleteItineraryAndRecommendations(userId, tripIds);
		deletePersonalizationArtifacts(userId);
		deleteSnapshots(userId);
		deleteTripsAndMemberships(userId, tripIds);
		deleteUploadedImages(userId);
		deleteStories(userId);
		deleteLoginMeans(userId, credential);
		deleteUserOwnedRows(userId);
		detachEvents(userId);

		user.anonymizeForDeletion(this.clock.instant());
	}

	/**
	 * 🔴 <b>{@code ON DELETE CASCADE} 에만 기대던 표들</b> — S15P21E201-1157.
	 *
	 * <h2>왜 이 목록이 필요한가</h2>
	 *
	 * 이 서비스는 {@code app_user} 행을 <b>지우지 않고 익명화</b>한다({@link #delete} 의
	 * {@code anonymizeForDeletion}). {@code itinerary_versions.created_by} 가 {@code NOT NULL} 로
	 * 매달려 있어 계정 행을 지우면 남의 일정 편집 이력까지 지워야 하기 때문이고, 그 판단 자체는
	 * 맞다.
	 *
	 * <p>그런데 그 결과로 <b>{@code app_user} 를 가리키는 {@code ON DELETE CASCADE} 열아홉 개가
	 * 한 번도 발동하지 않는다.</b> 부모가 안 지워지니 자식도 안 지워진다. 그리고 <b>오류가 안 난다</b> —
	 * 탈퇴는 성공으로 끝나고 자료만 남는다. 2026-09-17 운영 실측에서 {@code saved_place} 1건과
	 * {@code menu_scan_usage} 10건이 탈퇴 뒤에도 그대로 있었다.
	 *
	 * <h2>🔴 새 표를 만드는 사람에게</h2>
	 *
	 * <b>{@code ON DELETE CASCADE} 를 달았다고 탈퇴 정리가 끝난 것이 아니다.</b> 계정 행이 안
	 * 지워지는 한 그 선언은 아무것도 하지 않는다. 사람을 가리키는 표를 새로 만들면 <b>이 목록에도
	 * 한 줄을 더해야</b> 한다.
	 *
	 * <h2>목록을 한 곳에 모아 둔 이유</h2>
	 *
	 * 삭제가 메서드 여덟 개에 흩어져 있어 <b>"무엇을 지우는가" 를 한눈에 볼 자리가 없었다.</b>
	 * 다음 티켓이 {@code pg_constraint} 에서 {@code app_user} 참조 외래키를 뽑아 이 목록과
	 * 대조하는 CI 검사를 붙인다 — 그때 대조할 대상이 이 상수다.
	 *
	 * <p>🔴 그 검사가 필요한 이유는 "표가 늘어나서" 가 아니다. <b>2026-09-17 에 이 외래키 수를
	 * 사람이 네 번 셌고 네 번 다 달랐다</b>(29 · 34 · 34 · 29). 손으로 세면 틀린다.
	 *
	 * <h2>여기 없는 것</h2>
	 *
	 * 순서가 중요한 것들({@code itinerary} → 개인화 파생값 → 스냅샷 → {@code trip})은 위의 전용
	 * 메서드에 그대로 둔다. 그 순서에는 이유가 있고({@link #delete} 주석), 목록으로 펴면 그 이유가
	 * 사라진다. 이 목록은 <b>"그 사람의 행을 그냥 지우면 되는 것"</b> 만 담는다.
	 */
	static final List<OwnedRows> USER_OWNED_ROWS = List.of(
			new OwnedRows("SavedPlace", "userId"),
			new OwnedRows("Collection", "userId"),
			new OwnedRows("PlaceReview", "userId"),
			new OwnedRows("PlaceVisitVerification", "userId"),
			new OwnedRows("MenuScanUsage", "userId"),
			// 🔴 팔로우·차단은 사람을 가리키는 칸이 둘이다. 한쪽만 지우면 "내가 없는데 나를
			//    팔로우한 기록" 이 남는다.
			new OwnedRows("UserFollow", "key.followerUserId"),
			new OwnedRows("UserFollow", "key.followeeUserId"),
			new OwnedRows("UserBlock", "key.blockerUserId"),
			new OwnedRows("UserBlock", "key.blockedUserId"),
			// 🔴 글에 단 좋아요·싫어요. ON DELETE CASCADE 가 걸려 있지만 위에 적은 이유로
			//    한 번도 안 터진다 — 새 표라서 처음부터 여기 넣는다(효준님 !1056 의 당부).
			//    묻힌 키의 필드 이름이 팔로우·차단과 다르다(key 가 아니라 id).
			new OwnedRows("StoryReaction", "id.userId"),
			// 🔴 S15P21E201-1227 — 글 저장·북마크. story_reaction 과 같은 이유로 여기 있다:
			//    ON DELETE CASCADE 가 걸려 있지만 계정 행을 익명화만 하므로 한 번도 안 터진다.
			new OwnedRows("StorySave", "userId"),
			// 🔴 S15P21E201-1201 — 조회·링크복사 낱개. story_reaction 과 같은 이유로 여기 있다:
			//    ON DELETE CASCADE 가 걸려 있지만 계정 행을 익명화만 하므로 한 번도 안 터진다.
			//    🔴 익명 세션이 남긴 낱개는 여기서 안 지운다 — 그 행은 사람을 안 가리킨다.
			//    90일 보관 규칙이 그쪽을 치우고, 누적 칸(story.view_count)은 어느 쪽이든 안 내린다.
			new OwnedRows("StoryView", "userId"),
			new OwnedRows("StoryLinkCopy", "userId"),
			// 🔴 S15P21E201-1231 — 여행 조건 모달의 답(알레르기·식단이 들어 있다). 새 표라
			//    처음부터 여기 넣는다. ON DELETE CASCADE 가 걸려 있지만 위에 적은 이유로
			//    한 번도 안 터진다.
			new OwnedRows("TravelConstraintJpaEntity", "userId"),
			// 🔴 이 둘은 남이 참조한다 — trip_member.trip_invite_id 와
			//    trip_seed_place.source_share_link_id. 둘 다 ON DELETE SET NULL 이라
			//    (V20260907130000) 남의 참여·씨앗은 남고 이 사람의 초대 기록만 끊긴다.
			//    그 자리를 다시 만들지 않는다 — 2026-09-05 에 같은 종류의 외래키가 탈퇴를
			//    통째로 깨뜨린 적이 있다.
			new OwnedRows("TripInviteJpaEntity", "createdBy"),
			new OwnedRows("TripShareLink", "createdBy"),
			// 🔴 이 칸은 @ManyToOne 이라 경로가 한 칸 더 들어간다. 지워도 안전한 이유는
			//    가입/연결을 끝내기 전의 10분짜리 1회용 티켓이라서다.
			new OwnedRows("OAuthSignupTicket", "existingUser.userId"));

	/**
	 * 지울 표 하나 — 엔티티 이름과 그 사람을 가리키는 칸.
	 *
	 * @param entityName JPQL 이 쓰는 <b>엔티티</b> 이름이다. DB 표 이름이 아니다
	 * @param userField 그 사람을 가리키는 칸의 JPQL 경로 (묻힌 키·관계면 점이 들어간다)
	 */
	record OwnedRows(String entityName, String userField) {
	}

	/**
	 * {@link #USER_OWNED_ROWS} 의 표에서 이 사람의 행을 지운다.
	 *
	 * <p>🔴 <b>순서를 신경 쓰지 않아도 되는 것만</b> 이 목록에 있다. 자식이 매달린 셋은 DB 가
	 * 알아서 처리한다 — {@code collection_item} 은 {@code CASCADE} 로 같이 지워지고, 초대·공유
	 * 링크를 가리키는 둘은 {@code SET NULL} 로 칸만 비워진다.
	 */
	private void deleteUserOwnedRows(UUID userId) {
		for (OwnedRows owned : USER_OWNED_ROWS) {
			execute("DELETE FROM %s e WHERE e.%s = :userId".formatted(owned.entityName(), owned.userField()),
					"userId", userId);
		}
	}

	/**
	 * 탈퇴한 사람이 올린 사진의 연결·DB 행·실제 파일을 함께 지운다 — S15P21E201-978.
	 *
	 * <p>{@code story_image}가 {@code uploaded_image}를 가리키므로 연결을 먼저 지운다. 파일 저장소
	 * 삭제는 현재 DB 트랜잭션이 커밋된 뒤 실행되고, 실패하면 뒷정리 대기열에 남는다. 사진 한 장
	 * 때문에 탈퇴 전체를 되돌리지 않으면서도 파일을 조용히 남기지 않는다.
	 */
	private void deleteUploadedImages(UUID userId) {
		List<String> storageKeys = this.entityManager.createQuery(
				"SELECT i.storageKey FROM UploadedImage i WHERE i.uploaderUserId = :userId", String.class)
				.setParameter("userId", userId)
				.getResultList();
		execute("""
				DELETE FROM StoryImage si WHERE si.uploadedImageId IN
				(SELECT i.uploadedImageId FROM UploadedImage i WHERE i.uploaderUserId = :userId)
				""", "userId", userId);
		execute("DELETE FROM UploadedImage i WHERE i.uploaderUserId = :userId", "userId", userId);
		storageKeys.forEach(key -> this.storageCleanupService.deleteOrEnqueue(
				key, StorageCleanupEntry.REASON_ACCOUNT_DELETED));
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
		// 지운 여행은 세지 않는다 — S15P21E201-913. 사용자가 "내 여행" 에서 보는 목록은
		// deletedAt 이 빈 것만 내주는데(JpaTripRepository.findTripsForMember), 여기서는 그
		// 조건이 빠져 있어 목록에 한 개뿐인 계정에 "3개가 삭제돼요" 가 떴다. 바로 아래
		// recordCount 는 같은 조건을 이미 걸고 있었다 — 한 메서드 안에서 기준이 갈렸던 것이다.
		//
		// 되돌릴 수 없는 동작의 안내 숫자라, 실제로 지워질 것보다 크게 보이면 사용자가 무엇을
		// 잃는지 잘못 알고 결정하게 된다.
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
	 * 일정과 추천 기록을 지운다 — 삭제 사슬의 <b>첫 칸</b>.
	 *
	 * <p>🔴 순서가 곧 정확성이다. 자식을 먼저 지우지 않으면 외래키에 걸려 통째로 실패한다.
	 *
	 * <p>🔴 <b>2026-09-14 — 스냅샷과 여행을 이 메서드에서 뺐다.</b> 예전에는 여행 아래 달린 것을
	 * 전부 여기서 지웠는데, 그러면 설문 스냅샷이 <b>취향 벡터보다 먼저</b> 지워진다.
	 * {@code user_taste_vector.source_preference_snapshot_id} 가 그 스냅샷을 가리키므로 탈퇴가
	 * 외래키 위반으로 실패했다. 자세한 것은 {@link #delete} 안의 순서 주석.
	 *
	 * <p>여기서 하는 일은 <b>추천 사슬을 끊는 것</b>까지다 — {@code recommendation_job} 이 사라져야
	 * 다음 칸에서 취향 벡터를 지울 수 있다({@code recommendation_job.taste_vector_id}).
	 */
	private void deleteItineraryAndRecommendations(UUID userId, List<UUID> tripIds) {
		if (!tripIds.isEmpty()) {
			// 🔴 2026-09-15 (S15P21E201-977) — 두 표가 서로를 가리킨다. 한쪽을 통째로 먼저
			//    지우는 것으로는 못 푼다.
			//
			//      itinerary_versions.source_request_id → recommendation_job.request_id
			//      recommendation_job.itinerary_id      → itineraries.itinerary_id
			//
			//    2026-09-05 (S15P21E201-604) 은 앞의 것만 보고 "일정을 먼저" 로 정했는데,
			//    그 뒤 V20260905120000 이 뒤의 것을 더하면서 반대 방향이 생겼다. 그래서 일정
			//    묶음을 통째로 먼저 지우면 이번엔 추천 작업이 걸린다 — <b>일정을 한 번이라도
			//    만든 계정은 탈퇴가 500 으로 실패했다.</b> 운영에서 실제로 그랬다.
			//
			//    푸는 자리는 <b>일정 행(itineraries)</b> 하나다. 그 위의 판·항목·구간은 작업을
			//    가리키므로 작업보다 먼저 지우고, 일정 행 자체는 작업이 가리키므로 작업보다
			//    나중에 지운다. 그래서 일정 묶음이 추천 작업을 사이에 두고 갈라진다.
			//
			//    🔴 itinerary_id 를 null 로 끊는 방법은 안 된다. ck_recommendation_job_result_present
			//    가 "성공한 일정 생성 작업은 itinerary_id 가 있어야 한다" 를 요구한다 — 끊는 순간
			//    그 CHECK 에 걸린다.
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

			// 🔴 일정 행은 여기서 지운다 — 위 주석의 고리 때문이다. 추천 작업이 이 행을
			//    가리키므로 작업보다 먼저 지울 수 없다. 이 줄을 위로 올리면 운영 탈퇴가
			//    다시 깨진다.
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
	 * 설문·제약 스냅샷을 지운다 — 삭제 사슬의 <b>셋째 칸</b>.
	 *
	 * <p>🔴 <b>개인화 파생값보다 뒤, 여행보다 앞</b>이어야 한다. 앞뒤로 외래키가 하나씩 걸려 있다.
	 * <ul>
	 * <li>앞: {@code user_taste_vector.source_preference_snapshot_id} 와
	 *     {@code feed_build.constraint_snapshot_id} 가 이 스냅샷들을 가리킨다. 둘 다
	 *     {@code ON DELETE} 가 없어 <b>NO ACTION</b> 이라, 가리키는 행이 살아 있으면 여기서 막힌다</li>
	 * <li>뒤: {@code preference_snapshot.trip_id} · {@code constraint_snapshot.trip_id} 가 여행을
	 *     가리킨다. 그래서 여행보다 먼저 지워야 한다</li>
	 * </ul>
	 * 가운데 자리가 하나뿐이고, 그 자리가 여기다.
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
	 * 동행자 자격과 본인 소유 여행을 지운다 — 삭제 사슬의 <b>마지막 칸</b>.
	 *
	 * <p>스냅샷이 {@code trip_id} 로 여행을 가리키므로 반드시 그 뒤에 온다.
	 */
	private void deleteTripsAndMemberships(UUID userId, List<UUID> tripIds) {
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
		// 🔴 소셜로만 가입한 계정은 자격증명이 없다 (S15P21E201-837). 그 계정에서 지울 것은
		// 소셜 신원(auth_identity) 쪽이고, 여기서 null 을 넘기면 지울 행이 없다는 뜻이다.
		if (credential != null) {
			this.credentialRepository.delete(credential);
		}
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
	 * <p>딸린 사진은 이 메서드 직전의 {@link #deleteUploadedImages}가 연결과 업로드 행을 지우고,
	 * 실제 파일도 커밋 뒤 삭제한다. 기록 행은 신고·검토 근거를 위해 익명화된 작성자와 함께
	 * 소프트 삭제 상태로 남지만 사진 주소와 저장 키는 남지 않는다.
	 */
	private void deleteStories(UUID userId) {
		// 🔴 S15P21E201-1183 — 댓글을 지우기 전에 부모의 세기를 먼저 내린다.
		//
		//    순서가 반대면 안 된다. 아래 UPDATE 가 deleted_at 을 찍고 나면 "이 사람이 쓴 댓글"
		//    을 더는 고를 수 없다 — 조건이 deleted_at IS NULL 이라서다. 그러면 세기가 영영
		//    안 내려가고, story.reply_count 는 5 인데 실제로 보이는 댓글은 4 인 상태가 남는다.
		//
		//    🔴 낱개로 세지 않고 한 문장으로 내린다. 탈퇴 한 번에 댓글이 수백 개일 수 있고,
		//    그때 부모마다 조회하면 질의가 그 수만큼 나간다 — 이 클래스가 피하려는 바로 그 모양이다.
		//
		//    자식 댓글은 건드리지 않는다. 지운 사람의 댓글에 달린 남의 답글은 그대로 남는다.
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
	 * 개인화가 만들어 둔 파생값을 지운다 — S15P21E201-549/566.
	 *
	 * <h2>🔴 여기가 비어 있었다</h2>
	 *
	 * 2026-09-11 까지 탈퇴는 계정·여행·일정·기록은 지우면서 <b>그 사람을 재료로 만든 것</b>은
	 * 그대로 뒀다. {@code user_taste_vector} · {@code user_taste_weight} · {@code feed_build} ·
	 * {@code user_feed} · {@code community_feed} 의 행이 살아 있는 {@code user_id} 를 들고
	 * 남았다 — 계정 행은 비웠지만 그 행들이 가리키는 대상은 여전히 그 사람이다.
	 *
	 * <p>Google Play 의 계정 삭제 요건은 <b>"데이터 안전 양식에 적은 것 전부"</b> 를 함께 지우라고
	 * 한다. 취향 벡터는 그 양식에서 "개인화" 목적으로 적을 값이므로 범위 안이다.
	 *
	 * <h2>🔴 순서</h2>
	 *
	 * 외래키 때문에 <b>피드 줄 → 피드 세대 → 취향 성분 → 취향 판</b> 이다.
	 * {@code feed_build.taste_vector_id} 가 {@code user_taste_vector} 를 가리키므로 세대를
	 * 먼저 지우지 않으면 판을 못 지운다.
	 *
	 * <p>{@code recommendation_job.taste_vector_id} 도 같은 표를 가리키는데, 그쪽은
	 * {@link #deleteItineraryAndRecommendations} 가 이미 이 사람의 추천 기록을 통째로 지운 뒤라
	 * 남아 있지 않다. <b>이 메서드를 그보다 먼저 부르면 외래키에 걸려 탈퇴 전체가 실패한다.</b>
	 *
	 * <p>🔴 <b>뒤쪽에도 사슬이 있다 (2026-09-14).</b> 여기서 지우는 {@code user_taste_vector} 와
	 * {@code feed_build} 가 이번에는 스냅샷을 가리킨다 — {@code source_preference_snapshot_id} ·
	 * {@code constraint_snapshot_id}. 그래서 이 메서드는 {@link #deleteSnapshots} 보다 <b>먼저</b>
	 * 와야 한다. 앞뒤가 다 막혀 있어 자리가 하나뿐이다: 추천 뒤, 스냅샷 앞.
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

		// 🔴 실제 방문 시각으로 만든 개인 속도 계수도 개인화 파생값이다 (S15P21E201-304 · 549 후속).
		//
		//    user_pace_factor 는 app_user 에 ON DELETE CASCADE 로 묶여 있는데, 이 서비스는
		//    계정 행을 지우지 않고 익명화하므로(delete 의 anonymizeForDeletion) 그 CASCADE 가
		//    영영 돌지 않는다. 표에 선언된 것과 실제로 일어나는 일이 다르다 — 그래서 여기서
		//    직접 지운다. 같은 규칙을 BehaviorPersonalizationReset.forget 도 쓴다.
		execute("DELETE FROM PaceFactorJpaEntity p WHERE p.userId = :userId", "userId", userId);
	}

	/**
	 * 이벤트 기록에서 사람만 떼어 낸다.
	 *
	 * <p>🔴 지우지 않는 이유. 이벤트는 "무슨 일이 몇 번 일어났나" 를 보는 집계의 원본이라, 지우면
	 * 지난 통계가 소급해서 바뀐다. {@code user_id} 는 비울 수 있는 칸이라 사람만 떼어 내고 사건은
	 * 남긴다.
	 *
	 * <h2>🔴 {@code partition_key} 도 함께 지운다 (2026-09-11)</h2>
	 *
	 * {@code user_id} 만 비우면 익명화가 안 된다. {@code partition_key} 는
	 * {@code EventIngestService.partitionKeyOf} 가 <b>사용자 UUID 문자열 그대로</b> 넣는 칸이라,
	 * 사람을 뗀 뒤에도 그 칸으로 같은 사람의 이벤트를 전부 다시 묶을 수 있었다. 비운 것이
	 * 아니라 <b>한 칸 옆으로 옮겨 둔 것</b>이었다.
	 *
	 * <p>{@code NOT NULL} 이라 비울 수는 없어서 {@code aggregate_id} 로 바꾼다 — 사용자 ID 가
	 * 없을 때 원래 쓰는 값이 그것이다(같은 메서드). 브로커로 보낼 때 필요한 "같은 대상은 같은
	 * 칸" 도 그대로 지켜진다.
	 *
	 * <h2>🔴 이 한 문장만 native SQL 이다 — 클래스 머리말의 규칙에 대한 예외</h2>
	 *
	 * 이유가 둘이다. {@code EventOutbox.partitionKey} 에 {@code @Column(updatable = false)} 가
	 * 붙어 있고, UUID 를 문자열로 바꾸는 일을 JPQL 의 {@code cast} 에 맡기면 방언에 따라
	 * 결과가 갈린다. {@code aggregate_id::text} 는 갈리지 않는다.
	 *
	 * <p>머리말이 native SQL 을 피하라고 한 이유(운영은 {@code gabolle} schema 인데 손으로 쓴
	 * SQL 이 그것을 안 물려받는 문제)는 {@code spring.datasource.hikari.schema} 가 연결 자체에
	 * 스키마를 걸면서 없어졌다 — {@code docs/DB-STANDARD.md} 2절, S15P21E201-546.
	 * 그 한 줄이 지워지면 이 문장이 먼저 죽는다.
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
