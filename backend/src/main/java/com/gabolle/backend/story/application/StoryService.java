package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryImage;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.presentation.dto.StoryUpdateRequest;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 기록의 작성·조회·수정·삭제 — S15P21E201-207 · -221 · -226.
 *
 * <h2>누가 무엇을 볼 수 있나</h2>
 * 판정 자체는 {@link StoryVisibilityPolicy} 가 한다({@code StoryReportService} 도 같은 것을 쓴다 —
 * S15P21E201-254 뽑아내기). 이 클래스가 그 결과로 무엇을 하는지만 적는다.
 * <ul>
 *   <li>작성자는 자기 기록을 언제나 본다 — 공개 전이든, 나만 보기든</li>
 *   <li>남은 <b>공개 시각이 지난</b> 기록만, 그것도 PUBLIC 이거나 (FOLLOWERS 이고 그 사람을 팔로우할 때)만 본다</li>
 *   <li>🔴 못 보는 기록은 <b>404</b> 다. 403 은 "있는데 못 본다" 를 알려 주는 셈이라 존재 사실이 새어 나간다.
 *       보이는 기록을 남이 고치려 할 때만 403 이다 — 그 사람은 이미 그 기록을 봤으니 감출 것이 없다</li>
 * </ul>
 *
 * <h2>공개 시각의 기본값</h2>
 * 요청이 안 주면 여행 종료 <b>다음 날 0시</b>(여행의 시간대, 없으면 Asia/Seoul)다. 여행 중인 사람의
 * "지금 여기" 가 기록으로 새지 않게 — 기획서 5.3 D-5. 여행이 없는 기록은 지금이 기본값이다.
 *
 * <h2>삭제</h2>
 * 행을 지우지 않고 {@code deleted_at} 을 찍는다. 딸린 사진은 저장소에서 지우는데, 하나를 못 지웠다고
 * 삭제를 되돌리지 않는다 — {@code StorageCleanupService} 가 못 지운 키를 목록에 남기고 나중에 다시
 * 지운다(S15P21E201-226). 사용자에게는 삭제가 끝난 것이다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryService {

	/** 여행에 시간대가 없을 때. 부산 서비스라 이것이 기본이다. */
	static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private final StoryRepository storyRepository;

	private final StoryImageRepository storyImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final UserFollowRepository userFollowRepository;

	private final TripRepository tripRepository;

	private final PlaceRepository placeRepository;

	private final StorageCleanupService storageCleanupService;

	private final StoryResponseAssembler assembler;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final Clock clock;

	public StoryService(StoryRepository storyRepository, StoryImageRepository storyImageRepository,
			UploadedImageRepository uploadedImageRepository, UserFollowRepository userFollowRepository,
			TripRepository tripRepository, PlaceRepository placeRepository,
			StorageCleanupService storageCleanupService, StoryResponseAssembler assembler,
			StoryVisibilityPolicy visibilityPolicy, Clock clock) {
		this.storyRepository = storyRepository;
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.userFollowRepository = userFollowRepository;
		this.tripRepository = tripRepository;
		this.placeRepository = placeRepository;
		this.storageCleanupService = storageCleanupService;
		this.assembler = assembler;
		this.visibilityPolicy = visibilityPolicy;
		this.clock = clock;
	}

	@Transactional
	public StoryResponse create(UUID authorUserId, StoryCreateRequest request) {
		Instant now = this.clock.instant();

		Trip trip = null;
		if (request.tripId() != null) {
			trip = this.tripRepository.findById(request.tripId().toString())
					.orElseThrow(() -> new InvalidReferenceException("tripId", "그 여행을 찾을 수 없습니다."));
		}
		Place place = null;
		if (request.placeId() != null) {
			place = this.placeRepository.findById(request.placeId())
					.orElseThrow(() -> new InvalidReferenceException("placeId", "그 장소를 찾을 수 없습니다."));
		}

		List<UploadedImage> images = resolveImages(authorUserId, request.imageUrlsOrEmpty());

		String region = request.region() != null && !request.region().isBlank()
				? request.region()
				: regionOf(place);
		Instant publishAt = request.publishAt() != null ? request.publishAt() : defaultPublishAt(trip, now);

		Story story = new Story(UUID.randomUUID(), authorUserId, request.tripId(), request.placeId(),
				request.body(), region, request.visibilityOrDefault(), publishAt, now);
		this.storyRepository.save(story);

		List<StoryImage> attached = new ArrayList<>(images.size());
		for (int i = 0; i < images.size(); i++) {
			attached.add(new StoryImage(UUID.randomUUID(), story.getStoryId(), images.get(i).getUploadedImageId(),
					i + 1, now));
		}
		this.storyImageRepository.saveAll(attached);

		return this.assembler.one(story, authorUserId, now);
	}

	/**
	 * 🔴 S15P21E201-254 — {@code findVisibleById} 를 쓴다({@code findActiveById} 가 아니다). 신고를
	 * 받으면 상세에서도 즉시 사라져야 하는데, 그 조건({@code moderation_state = 'VISIBLE'})은 여기서만
	 * 걸어야 한다 — {@link #requireAuthor}(수정·삭제 경로)까지 같이 걸면 작성자가 신고당한 자기
	 * 기록을 고치거나 지울 수 없게 된다({@code StoryRepository.findActiveById} 주석 참고).
	 */
	@Transactional(readOnly = true)
	public StoryResponse get(UUID storyId, UUID viewer) {
		Instant now = this.clock.instant();
		Story story = this.storyRepository.findVisibleById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, viewer, now)) {
			throw new StoryNotFoundException(storyId);
		}
		return this.assembler.one(story, viewer, now);
	}

	/**
	 * 수정 — S15P21E201-770 이후로 <b>만든 사람과 공동 작성자가 함께</b> 고친다.
	 *
	 * <p>다만 <b>공개 범위와 공개 시각은 만든 사람만</b> 바꾼다. 이 둘은 "누가 이 글을 볼 수
	 * 있는가" 를 정하는 값이라, 공동 작성자가 바꿀 수 있으면 만든 사람이 나만 보기로 써 둔 글이
	 * 남의 손에 공개될 수 있다. 본문을 함께 쓰는 것과 그 글을 세상에 내보이는 것은 다른 결정이다.
	 *
	 * <p>동시에 고치면 나중에 저장한 쪽이 이긴다(팀 결정). 버전을 견주어 막지 않는다. 대신
	 * {@code lastEditedBy} 가 남아서 화면이 "방금 누가 고쳤는지" 를 보여줄 수 있다.
	 */
	@Transactional
	public StoryResponse update(UUID storyId, UUID editor, StoryUpdateRequest request) {
		Instant now = this.clock.instant();
		Story story = requireParticipant(storyId, editor, now);
		// 🔴 S15P21E201-137 — 검토로 감춰진 기록은 고칠 수 없다.
		//
		// 지우는 것은 열어 둔다(StoryRepository.findActiveById 주석이 그 이유를 적어 뒀다).
		// 그런데 그 자리가 수정까지 함께 열어 두고 있었다. 그러면 운영자가 감춘 글을 작성자가
		// 다른 내용으로 바꿔 둘 수 있고, 기각으로 되살아나는 순간 운영자가 본 적 없는 글이
		// 공개된다. 검토란 그 시점의 내용을 두고 판단하는 일이라 그 사이 내용이 바뀌면
		// 판단의 대상이 사라진다.
		//
		// 404 가 아니라 409 인 이유는 여기까지 온 사람이 이미 참여자라서다. 그 사람에게는
		// 기록의 존재가 비밀이 아니므로 감출 것이 없고, 대신 지금은 왜 안 되는지를 알려주는
		// 편이 낫다.
		if (!story.getModerationState().visibleToOthers()) {
			throw new StoryUnderModerationException(storyId);
		}
		if (!story.isAuthor(editor) && (request.visibility() != null || request.publishAt() != null)) {
			throw new StoryForbiddenException(storyId);
		}
		if (request.placeId() != null && !this.placeRepository.existsById(request.placeId())) {
			throw new InvalidReferenceException("placeId", "그 장소를 찾을 수 없습니다.");
		}
		story.edit(request.body(), request.region(), request.visibility(), request.publishAt(), request.placeId(),
				request.clearPlaceOrFalse(), editor, now);
		return this.assembler.one(story, editor, now);
	}

	/**
	 * 삭제. 기록은 {@code deleted_at} 을 찍고, 딸린 사진은 저장소에서 지운다.
	 *
	 * <p>🔴 사진 지우기는 {@code StorageCleanupService.deleteOrEnqueue} 로 한다 — 실패해도 예외가 나오지
	 * 않아 이 트랜잭션이 롤백되지 않는다. 그 대신 못 지운 키가 {@code storage_cleanup_queue} 에 남는다.
	 * "지웠다고 했는데 남아 있다" 와 "몇 번을 눌러도 안 지워진다" 중 어느 쪽도 만들지 않는 방법이다.
	 */
	@Transactional
	public void delete(UUID storyId, UUID editor) {
		Instant now = this.clock.instant();
		Story story = requireAuthor(storyId, editor, now);
		story.markDeleted(now);

		List<StoryImage> images = this.storyImageRepository.findByStoryIdOrderByPositionAsc(storyId);
		if (!images.isEmpty()) {
			List<UUID> uploadIds = images.stream().map(StoryImage::getUploadedImageId).toList();
			for (UploadedImage upload : this.uploadedImageRepository.findByUploadedImageIdIn(uploadIds)) {
				upload.markDeleted(now);
				this.storageCleanupService.deleteOrEnqueue(upload.getStorageKey(),
						StorageCleanupEntry.REASON_STORY_DELETED);
			}
		}
	}

	// ---- 판정 ----

	/**
	 * 지워지지 않은 기록을 공개 범위와 무관하게 가져온다 — S15P21E201-770 의 초대 수락이 쓴다.
	 *
	 * <p>여기에만 이 창구가 있는 이유가 있다. 초대 수락은 <b>표(token)를 가진 것 자체가 열쇠</b>다.
	 * 열람 권한을 먼저 요구하면 나만 보기 기록에 초대받은 사람이 수락하기도 전에 404 를 받고,
	 * 그러면 "우리끼리 쓰는 기록에 사람을 부른다" 는 이 기능의 주 사용처가 통째로 막힌다.
	 * 여행 초대도 같은 방식이다 — 표가 곧 잠금이다.
	 *
	 * <p>그러니 이 메서드를 다른 곳에서 쓰지 않는다. 조회·수정 경로는 반드시
	 * {@link #requireVisible} 을 지나야 한다.
	 */
	Story requireActiveIgnoringVisibility(UUID storyId) {
		return this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
	}

	Story requireVisible(UUID storyId, UUID viewer, Instant now) {
		Story story = this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, viewer, now)) {
			throw new StoryNotFoundException(storyId);
		}
		// 🔴 S15P21E201-137 — 검토로 감춰진 기록은 참여자 아닌 사람에게 없는 것으로 답한다.
		//
		// 상세 조회는 findVisibleById 가 따로 막고 있었는데, 이 판정을 지나는 다른 경로들이
		// 검토 상태를 안 보고 있었다. 참여자 목록 조회가 그래서 열려 있었다 — 신고돼서 사라진
		// 글인데 "거기 누가 참여했나" 를 물으면 아무 로그인 사용자에게나 표시 이름을 그대로
		// 돌려줬다. 감췄다는 것은 그 글에 딸린 것도 함께 감췄다는 뜻이어야 한다.
		//
		// 참여자를 빼 두는 것이 중요하다. 참여자까지 막으면 작성자가 신고당한 자기 글을
		// 지울 수 없다(삭제도 이 메서드를 지난다). 그리고 이 검사를 한 단계 아래인
		// canView 에 넣으면 안 된다 — 신고 접수가 그것을 쓰고, 그쪽은 검토 중인 기록도
		// 받아야 두 번째 신고자가 세어진다.
		if (!story.getModerationState().visibleToOthers() && !this.visibilityPolicy.isParticipant(story, viewer)) {
			throw new StoryNotFoundException(storyId);
		}
		return story;
	}

	/**
	 * 만든 사람만 — 삭제와, 공개 범위·공개 시각 변경이 여기를 지난다.
	 *
	 * <p>{@link #requireVisible} 을 먼저 지나므로 <b>볼 수도 없는 기록</b>에는 403 이 아니라
	 * 404 가 나간다. 403 을 주면 "그 기록은 존재한다" 를 알려주는 셈이라 존재 자체가 샌다.
	 */
	private Story requireAuthor(UUID storyId, UUID editor, Instant now) {
		Story story = requireVisible(storyId, editor, now);
		if (!story.isAuthor(editor)) {
			throw new StoryForbiddenException(storyId);
		}
		return story;
	}

	/**
	 * 만든 사람 또는 공동 작성자 — 본문·사진 수정이 여기를 지난다 (S15P21E201-770).
	 *
	 * <p>판정 자체는 {@link StoryVisibilityPolicy#isParticipant} 한 곳에만 있다. 열람과 수정이
	 * 같은 명단을 봐야 "고칠 수는 있는데 볼 수는 없는" 사람이 안 생긴다.
	 */
	private Story requireParticipant(UUID storyId, UUID editor, Instant now) {
		Story story = requireVisible(storyId, editor, now);
		if (!this.visibilityPolicy.isParticipant(story, editor)) {
			throw new StoryForbiddenException(storyId);
		}
		return story;
	}

	/** 요청자가 작성자에 대해 볼 수 있는 공개 범위 목록 — 프로필 피드가 쓴다. */
	List<String> visibleScopesOf(UUID author, UUID viewer) {
		if (author.equals(viewer)) {
			return List.of(StoryVisibility.PUBLIC.name(), StoryVisibility.FOLLOWERS.name(),
					StoryVisibility.PRIVATE.name());
		}
		if (this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, author))) {
			return List.of(StoryVisibility.PUBLIC.name(), StoryVisibility.FOLLOWERS.name());
		}
		return List.of(StoryVisibility.PUBLIC.name());
	}

	// ---- 도우미 ----

	/**
	 * 요청이 준 사진 주소를 업로드 행으로 바꾼다. 없는 주소, 남이 올린 주소, 이미 다른 기록에 붙은 주소,
	 * 저장소에서 지운 주소, 중복 주소는 전부 400 이다 — 이유를 필드에 적어 돌려준다.
	 */
	private List<UploadedImage> resolveImages(UUID authorUserId, List<String> imageUrls) {
		if (imageUrls.isEmpty()) {
			return List.of();
		}
		if (imageUrls.size() > Story.MAX_IMAGES) {
			throw new InvalidReferenceException("imageUrls", "사진은 " + Story.MAX_IMAGES + "장까지 붙일 수 있습니다.");
		}
		if (new HashSet<>(imageUrls).size() != imageUrls.size()) {
			throw new InvalidReferenceException("imageUrls", "같은 사진을 두 번 붙일 수 없습니다.");
		}
		Map<String, UploadedImage> byUrl = new HashMap<>();
		for (UploadedImage upload : this.uploadedImageRepository.findByImageUrlIn(imageUrls)) {
			byUrl.put(upload.getImageUrl(), upload);
		}
		List<UploadedImage> ordered = new ArrayList<>(imageUrls.size());
		for (String url : imageUrls) {
			UploadedImage upload = byUrl.get(url);
			if (upload == null || upload.isDeleted()) {
				throw new InvalidReferenceException("imageUrls", "올라가 있지 않은 사진 주소입니다: " + url);
			}
			if (!upload.isOwnedBy(authorUserId)) {
				// 남의 업로드를 내 기록에 붙이는 것 — 주소를 훔쳐도 안 된다.
				throw new InvalidReferenceException("imageUrls", "내가 올린 사진만 붙일 수 있습니다: " + url);
			}
			if (this.storyImageRepository.existsByUploadedImageId(upload.getUploadedImageId())) {
				throw new InvalidReferenceException("imageUrls", "이미 다른 기록에 붙은 사진입니다: " + url);
			}
			ordered.add(upload);
		}
		return ordered;
	}

	/** 장소 주소의 앞 두 마디 — "부산광역시 해운대구 우동 …" 에서 "부산광역시 해운대구". 좌표는 쓰지 않는다. */
	static String regionOf(Place place) {
		if (place == null || place.getAddress() == null || place.getAddress().isBlank()) {
			return null;
		}
		String[] tokens = place.getAddress().trim().split("\\s+");
		if (tokens.length == 1) {
			return tokens[0];
		}
		return tokens[0] + " " + tokens[1];
	}

	/** 여행 종료 다음 날 0시(여행 시간대). 여행이 없으면 지금. */
	static Instant defaultPublishAt(Trip trip, Instant now) {
		if (trip == null) {
			return now;
		}
		LocalDate finish = trip.finishDate();
		ZoneId zone = DEFAULT_ZONE;
		if (trip.timezone() != null && !trip.timezone().isBlank()) {
			try {
				zone = ZoneId.of(trip.timezone());
			}
			catch (RuntimeException ignored) {
				zone = DEFAULT_ZONE;
			}
		}
		return finish.plusDays(1).atStartOfDay(zone).toInstant();
	}

	// ---- 예외 ----

	/** 없는 기록, 지운 기록, 그리고 <b>볼 수 없는</b> 기록. 셋을 구분하지 않는다 — 404. */
	public static class StoryNotFoundException extends RuntimeException {

		private final UUID storyId;

		public StoryNotFoundException(UUID storyId) {
			super("기록을 찾을 수 없습니다.");
			this.storyId = storyId;
		}

		public UUID storyId() {
			return storyId;
		}
	}

	/** 볼 수는 있지만 작성자가 아니다 — 403. */
	public static class StoryForbiddenException extends RuntimeException {

		public StoryForbiddenException(UUID storyId) {
			super("내 기록만 고치거나 지울 수 있습니다.");
		}
	}

	/**
	 * 검토로 감춰진 기록을 고치려 했다 — 409 (S15P21E201-137).
	 *
	 * <p>지우는 것은 여전히 된다. 신고당한 글을 스스로 내리는 길까지 막으면 사용자가 할 수
	 * 있는 일이 없어진다. 막는 것은 <b>내용을 바꾸는 것</b>뿐이다.
	 */
	public static class StoryUnderModerationException extends RuntimeException {

		public StoryUnderModerationException(UUID storyId) {
			super("신고 검토 중인 기록은 고칠 수 없습니다. 지우는 것은 됩니다.");
		}
	}

	/** 요청이 가리킨 것(사진 주소·장소·여행)이 쓸 수 없는 것이다 — 400. */
	public static class InvalidReferenceException extends RuntimeException {

		private final String field;

		public InvalidReferenceException(String field, String message) {
			super(message);
			this.field = field;
		}

		public String field() {
			return field;
		}
	}
}
