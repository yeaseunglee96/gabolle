package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.gabolle.backend.place.service.UserSubmittedPlaceService;
import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryImage;
import com.gabolle.backend.story.domain.StoryVideo;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.domain.UploadedVideo;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.presentation.dto.StoryUpdateRequest;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.StoryLinkCopyRepository;
import com.gabolle.backend.story.repository.StoryViewRepository;
import com.gabolle.backend.story.repository.StoryVideoRepository;
import com.gabolle.backend.story.repository.UploadedVideoRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 기록의 작성·조회·수정·삭제.
 *
 * <p>열람 판정은 {@link StoryVisibilityPolicy} 한 곳에만 있다. 못 보는 기록은 404 다 — 403 은
 * "있는데 못 본다" 를 알려 주는 셈이라 존재 사실이 샌다. 403 은 보이는 기록을 남이 고치려 할 때만 나간다.
 *
 * <p>공개 시각의 기본값은 여행 종료 다음 날 0시(여행 시간대, 없으면 Asia/Seoul)다. 여행 중인 사람의
 * "지금 여기" 가 기록으로 새지 않게 한 것이다. 여행이 없는 기록은 지금이 기본값이다.
 *
 * <p>삭제는 행을 지우지 않고 {@code deleted_at} 을 찍는다. 딸린 사진 하나를 못 지웠다고 삭제를
 * 되돌리지 않는다 — {@code StorageCleanupService} 가 못 지운 키를 남기고 나중에 다시 지운다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryService {

	/**
	 * 「하루 한 번」의 그 하루를 재는 시간대. 서버 시간대나 DB 의 {@code current_date} 를 쓰지 않는다 —
	 * UTC 로 세면 한국 시각 오전 9시에 날짜가 바뀌어 「어제 본 글을 오늘 또 봐도 안 세는」 구간이 생긴다.
	 */
	private static final ZoneId COUNTING_ZONE = ZoneId.of("Asia/Seoul");

	/** 여행에 시간대가 없을 때. 부산 서비스라 이것이 기본이다. */
	static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private final StoryRepository storyRepository;

	private final StoryImageRepository storyImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final UserFollowRepository userFollowRepository;

	private final TripRepository tripRepository;

	private final PlaceRepository placeRepository;

	private final UserSubmittedPlaceService userSubmittedPlaces;

	private final StorageCleanupService storageCleanupService;

	private final StoryResponseAssembler assembler;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final StoryViewRepository storyViewRepository;

	private final StoryLinkCopyRepository storyLinkCopyRepository;

	private final StoryVideoRepository storyVideoRepository;

	private final UploadedVideoRepository uploadedVideoRepository;

	private final Clock clock;

	public StoryService(StoryRepository storyRepository, StoryImageRepository storyImageRepository,
			UploadedImageRepository uploadedImageRepository, UserFollowRepository userFollowRepository,
			TripRepository tripRepository, PlaceRepository placeRepository,
			UserSubmittedPlaceService userSubmittedPlaces,
			StorageCleanupService storageCleanupService, StoryResponseAssembler assembler,
			StoryVisibilityPolicy visibilityPolicy, StoryViewRepository storyViewRepository,
			StoryLinkCopyRepository storyLinkCopyRepository, StoryVideoRepository storyVideoRepository,
			UploadedVideoRepository uploadedVideoRepository, Clock clock) {
		this.storyRepository = storyRepository;
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.userFollowRepository = userFollowRepository;
		this.tripRepository = tripRepository;
		this.placeRepository = placeRepository;
		this.userSubmittedPlaces = userSubmittedPlaces;
		this.storageCleanupService = storageCleanupService;
		this.assembler = assembler;
		this.visibilityPolicy = visibilityPolicy;
		this.storyViewRepository = storyViewRepository;
		this.storyLinkCopyRepository = storyLinkCopyRepository;
		this.storyVideoRepository = storyVideoRepository;
		this.uploadedVideoRepository = uploadedVideoRepository;
		this.clock = clock;
	}

	@Transactional
	public StoryResponse create(UUID authorUserId, StoryCreateRequest request) {
		Instant now = this.clock.instant();

		if (request.parentStoryId() != null) {
			return createReply(authorUserId, request, now);
		}

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
		else if (request.place() != null) {
			// 우리 표에 없는 장소를 골랐다. 서버가 먼저 만들고 그 id 를 쓴다 — 앱이 우리 표에 없는
			// 식별자를 저장하는 것이 아니다 (S15P21E201-1426).
			place = resolveSubmittedPlace(request.place());
		}

		List<UploadedImage> images = resolveImages(authorUserId, request.imageUrlsOrEmpty());
		// 글을 저장하기 전에 먼저 본다. 저장한 뒤에 거절하면 본문만 남은 기록이 생긴다.
		ResolvedVideo video = resolveVideo(authorUserId, request.videoUrl(), request.thumbnailUrl());

		String region = request.region() != null && !request.region().isBlank()
				? request.region()
				: regionOf(place);
		Instant publishAt = request.publishAt() != null ? request.publishAt() : defaultPublishAt(trip, now);

		// request.placeId() 가 아니라 해석된 장소의 id 다. 스냅샷으로 만든 장소는 요청에 id 가 없다.
		Story story = new Story(UUID.randomUUID(), authorUserId, request.tripId(),
				place == null ? null : place.getPlaceId(),
				request.body(), region, request.visibilityOrDefault(), publishAt, now);
		this.storyRepository.save(story);

		List<StoryImage> attached = new ArrayList<>(images.size());
		for (int i = 0; i < images.size(); i++) {
			attached.add(new StoryImage(UUID.randomUUID(), story.getStoryId(), images.get(i).getUploadedImageId(),
					i + 1, now));
		}
		this.storyImageRepository.saveAll(attached);
		attachVideo(story.getStoryId(), video, now);

		return this.assembler.one(story, authorUserId, now);
	}

	/**
	 * 댓글을 만든다. 볼 수 있는 글에만 달 수 있다 — 그 판정이 없으면 비공개 글에 댓글을 달아 존재를
	 * 알아낼 수 있다. 부모가 댓글이어도 막지 않는다. 깊이 제한은 없다.
	 *
	 * <p>공개범위·공개시각·여행·장소·지역은 요청에 있어도 안 읽는다. 댓글에는 그 개념이 없고
	 * {@link Story#reply} 가 그 값을 자기가 정한다.
	 *
	 * <p>{@code addReply()} 는 부모 하나에만 부른다. {@code reply_count} 의 뜻이 「직접 달린 것」이다.
	 */
	private StoryResponse createReply(UUID authorUserId, StoryCreateRequest request, Instant now) {
		Story parent = requireVisible(request.parentStoryId(), authorUserId, now);

		List<UploadedImage> images = resolveImages(authorUserId, request.imageUrlsOrEmpty());
		// 댓글도 원글과 같은 요청 모양을 쓰므로 동영상을 붙일 수 있다.
		ResolvedVideo video = resolveVideo(authorUserId, request.videoUrl(), request.thumbnailUrl());

		Story reply = Story.reply(UUID.randomUUID(), authorUserId, parent.getStoryId(), request.body(), now);
		this.storyRepository.save(reply);

		List<StoryImage> attached = new ArrayList<>(images.size());
		for (int i = 0; i < images.size(); i++) {
			attached.add(new StoryImage(UUID.randomUUID(), reply.getStoryId(),
					images.get(i).getUploadedImageId(), i + 1, now));
		}
		this.storyImageRepository.saveAll(attached);
		attachVideo(reply.getStoryId(), video, now);

		// 같은 트랜잭션에서 올린다. 따로 세면 「댓글은 달렸는데 수가 안 오른」 상태가 생긴다.
		parent.addReply();

		return this.assembler.one(reply, authorUserId, now);
	}

	/** 이 글에 직접 달린 댓글. 목록을 여는 것도 조회라 {@link #requireVisible} 을 똑같이 지난다. */
	@Transactional(readOnly = true)
	public List<StoryResponse> replies(UUID storyId, UUID viewer, int limit) {
		Instant now = this.clock.instant();
		Story parent = requireVisible(storyId, viewer, now);
		List<Story> replies = this.storyRepository.findReplies(parent.getStoryId(),
				org.springframework.data.domain.PageRequest.of(0, limit));
		return this.assembler.many(replies, viewer, now);
	}

	/**
	 * 상세 조회. 읽기 전용이 아니다 — 조회수를 같은 트랜잭션에서 올린다.
	 *
	 * <p>{@code findVisibleById} 를 쓴다({@code findActiveById} 가 아니다). 신고를 받으면 상세에서도
	 * 즉시 사라져야 한다. 그 조건은 여기서만 건다 — {@link #requireAuthor}(수정·삭제 경로)까지 걸면
	 * 작성자가 신고당한 자기 기록을 고치거나 지울 수 없게 된다.
	 *
	 * <p>볼 수 있는지 판정한 뒤에 센다. 순서가 반대면 못 보는 글을 찔러도 수가 오르고, 그 수가 곧
	 * 「그 글이 있다」는 사실의 유출이 된다.
	 */
	@Transactional
	public StoryResponse get(UUID storyId, UUID viewer, UUID anonymousSessionId) {
		Instant now = this.clock.instant();
		Story story = this.storyRepository.findVisibleById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, viewer, now)) {
			throw new StoryNotFoundException(storyId);
		}
		recordView(story, viewer, anonymousSessionId, now);
		return this.assembler.one(story, viewer, now);
	}

	/**
	 * 조회를 한 번 센다. 세 가지 규칙이 있다 — 작성자 본인은 안 세고, 식별할 수 없으면(회원도
	 * 익명 세션도 없으면) 안 세고, 같은 사람은 하루 한 번만 센다. 비회원이라 안 세는 것이 아니라
	 * 익명 세션이 있으면 센다.
	 *
	 * <p>중복은 넣어 보고 돌아온 행 수로 판정한다. 「오늘 것이 있나」를 먼저 읽으면 같은 사람이
	 * 두 기기에서 동시에 열 때 둘 다 통과한다.
	 */
	private void recordView(Story story, UUID viewer, UUID anonymousSessionId, Instant now) {
		if (viewer != null && story.isAuthor(viewer)) {
			return;
		}
		if (viewer == null && anonymousSessionId == null) {
			return;
		}
		LocalDate viewedOn = LocalDate.ofInstant(now, COUNTING_ZONE);
		int inserted = this.storyViewRepository.insertIfAbsent(UUID.randomUUID(), story.getStoryId(),
				viewer, viewer == null ? anonymousSessionId : null, viewedOn, now);
		if (inserted == 1) {
			story.recordView();
		}
	}

	/**
	 * 링크 복사를 한 번 센다. 복사는 앱 안에서 끝나는 행동이라 앱이 알려 주지 않으면 서버가 영영
	 * 모른다. 그래서 조회수와 달리 부르는 자리가 따로 있다.
	 *
	 * <p>{@link #get} 과 같은 이유로 볼 수 있는지 먼저 판정한다. 못 보는 글은 404 다.
	 *
	 * <p>수가 안 올랐다고 실패로 답하지 않는다. 오늘 이미 센 사람이 또 눌러도, 작성자 본인이 눌러도
	 * 복사 자체는 일어난 일이다. 응답은 언제나 그 글의 지금 모습이다.
	 */
	@Transactional
	public StoryResponse recordLinkCopy(UUID storyId, UUID actor, UUID anonymousSessionId) {
		Instant now = this.clock.instant();
		Story story = this.storyRepository.findVisibleById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, actor, now)) {
			throw new StoryNotFoundException(storyId);
		}
		countLinkCopy(story, actor, anonymousSessionId, now);
		return this.assembler.one(story, actor, now);
	}

	/**
	 * 규칙은 {@link #recordView} 와 같다. 그런데도 합치지 않았다 — 합치려면 대상 표와 누적 칸을
	 * 인자로 받아야 하고, 규칙이 갈리는 날 그 인자가 조건문으로 자란다. 두 표를 나눈 이유가 그것이다.
	 */
	private void countLinkCopy(Story story, UUID actor, UUID anonymousSessionId, Instant now) {
		if (actor != null && story.isAuthor(actor)) {
			return;
		}
		if (actor == null && anonymousSessionId == null) {
			return;
		}
		LocalDate copiedOn = LocalDate.ofInstant(now, COUNTING_ZONE);
		int inserted = this.storyLinkCopyRepository.insertIfAbsent(UUID.randomUUID(), story.getStoryId(),
				actor, actor == null ? anonymousSessionId : null, copiedOn, now);
		if (inserted == 1) {
			story.recordLinkCopy();
		}
	}

	/**
	 * 수정. 만든 사람과 공동 작성자가 함께 고치되, 공개 범위와 공개 시각은 만든 사람만 바꾼다 —
	 * 본문을 함께 쓰는 것과 그 글을 세상에 내보이는 것은 다른 결정이다.
	 *
	 * <p>동시에 고치면 나중에 저장한 쪽이 이긴다. 버전을 견주어 막지 않는다.
	 */
	@Transactional
	public StoryResponse update(UUID storyId, UUID editor, StoryUpdateRequest request) {
		Instant now = this.clock.instant();
		Story story = requireParticipant(storyId, editor, now);
		// 검토로 감춰진 기록은 고칠 수 없다. 내용이 바뀌면 검토의 대상이 사라지고, 기각으로
		// 되살아나는 순간 운영자가 본 적 없는 글이 공개된다. 지우는 것은 열어 둔다.
		// 404 가 아니라 409 인 것은 여기까지 온 사람이 이미 참여자라 감출 것이 없어서다.
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
	 * <p>사진 지우기는 {@code StorageCleanupService.deleteOrEnqueue} 로 한다 — 실패해도 예외가 나오지
	 * 않아 이 트랜잭션이 롤백되지 않고, 못 지운 키가 {@code storage_cleanup_queue} 에 남는다.
	 */
	@Transactional
	public void delete(UUID storyId, UUID editor) {
		Instant now = this.clock.instant();
		Story story = requireAuthor(storyId, editor, now);
		story.markDeleted(now);

		// 댓글을 지우면 부모의 세기를 같은 트랜잭션에서 내린다. 이 댓글에 달린 자식은 건드리지
		// 않는다 — 부모가 지워졌다고 자식까지 지우면 남의 글이 사라진다.
		if (story.isReply()) {
			this.storyRepository.findActiveById(story.getParentStoryId()).ifPresent(Story::removeReply);
		}

		List<StoryImage> images = this.storyImageRepository.findByStoryIdOrderByPositionAsc(storyId);
		if (!images.isEmpty()) {
			List<UUID> uploadIds = images.stream().map(StoryImage::getUploadedImageId).toList();
			for (UploadedImage upload : this.uploadedImageRepository.findByUploadedImageIdIn(uploadIds)) {
				upload.markDeleted(now);
				this.storageCleanupService.deleteOrEnqueue(upload.getStorageKey(),
						StorageCleanupEntry.REASON_STORY_DELETED);
			}
		}
		deleteVideoFiles(storyId, now);
	}

	/**
	 * 딸린 동영상의 파일 둘을 지운다 — 재생 파일과 썸네일.
	 *
	 * <p>썸네일은 {@code uploaded_image} 행이지만 {@code story_image} 에는 안 들어가므로 위의
	 * 사진 정리가 보지 못한다. 여기서 같이 지운다.
	 *
	 * <p>{@code story_video} 행 자체는 남긴다. 지우는 것은 파일뿐이다.
	 */
	private void deleteVideoFiles(UUID storyId, Instant now) {
		this.storyVideoRepository.findByStoryId(storyId).ifPresent(storyVideo -> {
			this.uploadedVideoRepository.findById(storyVideo.getUploadedVideoId()).ifPresent(video -> {
				video.markDeleted(now);
				this.storageCleanupService.deleteOrEnqueue(video.getStorageKey(),
						StorageCleanupEntry.REASON_STORY_DELETED);
			});
			// 썸네일은 없을 수 있다. List.of 는 null 을 받으면 NPE 를 던져 기록 삭제가 통째로
			// 죽으므로 먼저 거른다.
			UUID thumbnailUploadId = storyVideo.getThumbnailUploadId();
			if (thumbnailUploadId == null) {
				return;
			}
			for (UploadedImage thumbnail : this.uploadedImageRepository
					.findByUploadedImageIdIn(List.of(thumbnailUploadId))) {
				thumbnail.markDeleted(now);
				this.storageCleanupService.deleteOrEnqueue(thumbnail.getStorageKey(),
						StorageCleanupEntry.REASON_STORY_DELETED);
			}
		});
	}

	// ---- 판정 ----

	/**
	 * 지워지지 않은 기록을 공개 범위와 무관하게 가져온다. 초대 수락 전용이다 — 초대는 표(token)를
	 * 가진 것 자체가 열쇠라, 열람 권한을 먼저 요구하면 나만 보기 기록에 초대받은 사람이 수락하기도
	 * 전에 404 를 받는다.
	 *
	 * <p>다른 곳에서 쓰지 않는다. 조회·수정 경로는 반드시 {@link #requireVisible} 을 지나야 한다.
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
		// 검토로 감춰진 기록은 참여자 아닌 사람에게 없는 것으로 답한다. 참여자를 빼 두어야
		// 작성자가 신고당한 자기 글을 지울 수 있다(삭제도 이 메서드를 지난다).
		// 이 검사를 한 단계 아래인 canView 에 넣으면 안 된다 — 신고 접수가 그것을 쓰고,
		// 그쪽은 검토 중인 기록도 받아야 두 번째 신고자가 세어진다.
		if (!story.getModerationState().visibleToOthers() && !this.visibilityPolicy.isParticipant(story, viewer)) {
			throw new StoryNotFoundException(storyId);
		}
		return story;
	}

	/**
	 * 만든 사람만 — 삭제와 공개 범위·공개 시각 변경이 여기를 지난다. {@link #requireVisible} 을 먼저
	 * 지나므로 볼 수도 없는 기록에는 403 이 아니라 404 가 나간다.
	 */
	private Story requireAuthor(UUID storyId, UUID editor, Instant now) {
		Story story = requireVisible(storyId, editor, now);
		if (!story.isAuthor(editor)) {
			throw new StoryForbiddenException(storyId);
		}
		return story;
	}

	/**
	 * 만든 사람 또는 공동 작성자 — 본문·사진 수정이 여기를 지난다. 판정은
	 * {@link StoryVisibilityPolicy#isParticipant} 한 곳에만 둔다. 열람과 수정이 같은 명단을 봐야
	 * "고칠 수는 있는데 볼 수는 없는" 사람이 안 생긴다.
	 */
	private Story requireParticipant(UUID storyId, UUID editor, Instant now) {
		Story story = requireVisible(storyId, editor, now);
		if (!this.visibilityPolicy.isParticipant(story, editor)) {
			throw new StoryForbiddenException(storyId);
		}
		return story;
	}

	/** 본인이 본인 것을 볼 때 — 비공개까지 전부. */
	static final List<String> SELF_SCOPES = List.of(StoryVisibility.PUBLIC.name(), StoryVisibility.FOLLOWERS.name(),
			StoryVisibility.PRIVATE.name());

	/** 팔로우하는 사람이 볼 때 — 공개 + 팔로워 공개. */
	static final List<String> FOLLOWER_SCOPES = List.of(StoryVisibility.PUBLIC.name(),
			StoryVisibility.FOLLOWERS.name());

	/** 그 밖의 사람이 볼 때 — 공개만. */
	static final List<String> STRANGER_SCOPES = List.of(StoryVisibility.PUBLIC.name());

	/**
	 * 요청자가 작성자에 대해 볼 수 있는 공개 범위 목록 — 프로필 피드가 쓴다.
	 *
	 * <p>위 셋을 상수로 뽑아 둔 것은 기록 수를 묶어서 세는 쪽이 같은 표를 쓰게 하기 위해서다.
	 * 저쪽에 범위를 다시 적으면 프로필의 숫자와 목록의 숫자가 소리 없이 어긋난다.
	 */
	List<String> visibleScopesOf(UUID author, UUID viewer) {
		// 🔴 로그인하지 않은 사람은 «남»이다 — S15P21E201-1373.
		//    이 줄이 없으면 아래에서 viewer 가 null 인 채로 팔로우 관계를 찾게 된다.
		//    공개 피드는 전용 질의(findPublicFeedForAnonymous)로 돌아서 여기에 안 닿았고,
		//    작성자별 피드를 익명에 열면서 처음으로 닿는다.
		if (viewer == null) {
			return STRANGER_SCOPES;
		}
		if (author.equals(viewer)) {
			return SELF_SCOPES;
		}
		if (this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, author))) {
			return FOLLOWER_SCOPES;
		}
		return STRANGER_SCOPES;
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
				throw new InvalidReferenceException("imageUrls", "내가 올린 사진만 붙일 수 있습니다: " + url);
			}
			if (this.storyImageRepository.existsByUploadedImageId(upload.getUploadedImageId())) {
				throw new InvalidReferenceException("imageUrls", "이미 다른 기록에 붙은 사진입니다: " + url);
			}
			ordered.add(upload);
		}
		return ordered;
	}

	/**
	 * 요청이 준 동영상 주소를 업로드 행으로 바꾼다. {@link #resolveImages} 와 같은 검사를 같은
	 * 순서로 한다. {@code uploaderUserId} 를 대조하므로 주소를 알아도 남의 업로드는 못 붙인다.
	 *
	 * <p>썸네일은 없어도 되지만, 동영상 없이 썸네일만 보내는 것은 거절한다.
	 *
	 * @return 붙일 것이 없으면 {@code null}
	 */
	private ResolvedVideo resolveVideo(UUID authorUserId, String videoUrl, String thumbnailUrl) {
		if (videoUrl == null || videoUrl.isBlank()) {
			if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
				throw new InvalidReferenceException("thumbnailUrl", "동영상 없이 썸네일만 붙일 수 없습니다.");
			}
			return null;
		}

		UploadedVideo video = this.uploadedVideoRepository.findByVideoUrl(videoUrl)
				.orElseThrow(() -> new InvalidReferenceException("videoUrl",
						"올라가 있지 않은 동영상 주소입니다: " + videoUrl));
		if (video.isDeleted()) {
			throw new InvalidReferenceException("videoUrl", "올라가 있지 않은 동영상 주소입니다: " + videoUrl);
		}
		if (!video.isOwnedBy(authorUserId)) {
			// 남의 업로드를 내 기록에 붙이는 것 — 주소를 훔쳐도 안 된다.
			throw new InvalidReferenceException("videoUrl", "내가 올린 동영상만 붙일 수 있습니다: " + videoUrl);
		}
		if (this.storyVideoRepository.existsByUploadedVideoId(video.getUploadedVideoId())) {
			throw new InvalidReferenceException("videoUrl", "이미 다른 기록에 붙은 동영상입니다: " + videoUrl);
		}

		if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
			return new ResolvedVideo(video, null);
		}
		UploadedImage thumbnail = this.uploadedImageRepository.findByImageUrlIn(List.of(thumbnailUrl)).stream()
				.findFirst()
				.orElseThrow(() -> new InvalidReferenceException("thumbnailUrl",
						"올라가 있지 않은 사진 주소입니다: " + thumbnailUrl));
		if (thumbnail.isDeleted()) {
			throw new InvalidReferenceException("thumbnailUrl", "올라가 있지 않은 사진 주소입니다: " + thumbnailUrl);
		}
		if (!thumbnail.isOwnedBy(authorUserId)) {
			throw new InvalidReferenceException("thumbnailUrl", "내가 올린 사진만 붙일 수 있습니다: " + thumbnailUrl);
		}
		// 사진으로도 쓰이고 썸네일로도 쓰이면 한쪽을 지울 때 다른 쪽이 깨진다.
		// uq_story_image_upload·uq_story_video_thumbnail 이 각자 막지만 오류 모양을 위해 먼저 본다.
		if (this.storyImageRepository.existsByUploadedImageId(thumbnail.getUploadedImageId())
				|| this.storyVideoRepository.existsByThumbnailUploadId(thumbnail.getUploadedImageId())) {
			throw new InvalidReferenceException("thumbnailUrl", "이미 다른 기록에 붙은 사진입니다: " + thumbnailUrl);
		}
		return new ResolvedVideo(video, thumbnail);
	}

	/** 붙일 준비가 끝난 동영상과 그 썸네일. 썸네일은 {@code null} 일 수 있다. */
	private record ResolvedVideo(UploadedVideo video, UploadedImage thumbnail) {
	}

	/** 기록에 동영상을 붙인다. 붙일 것이 없으면 아무것도 안 한다. */
	private void attachVideo(UUID storyId, ResolvedVideo resolved, Instant now) {
		if (resolved == null) {
			return;
		}
		this.storyVideoRepository.save(new StoryVideo(UUID.randomUUID(), storyId,
				resolved.video().getUploadedVideoId(),
				resolved.thumbnail() == null ? null : resolved.thumbnail().getUploadedImageId(), now));
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

	/** 없는 기록, 지운 기록, 볼 수 없는 기록. 셋을 구분하지 않는다 — 404. */
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

	/** 검토로 감춰진 기록을 고치려 했다 — 409. 막는 것은 내용을 바꾸는 것뿐이고 삭제는 된다. */
	public static class StoryUnderModerationException extends RuntimeException {

		public StoryUnderModerationException(UUID storyId) {
			super("신고 검토 중인 기록은 고칠 수 없습니다. 지우는 것은 됩니다.");
		}
	}

	/**
	 * 사용자가 검색 결과에서 고른 장소를 우리 표의 장소로 바꾼다 — S15P21E201-1426.
	 *
	 * <p>규칙은 {@link UserSubmittedPlaceService} 에 있다. 여기서는 그쪽이 내는 오류를 이 API 의
	 * 400 으로 옮기기만 한다 — 그러지 않으면 앱이 보낸 값이 잘못됐는데 500 이 나간다.
	 */
	private Place resolveSubmittedPlace(StoryCreateRequest.PlaceSnapshotRequest snapshot) {
		try {
			return this.userSubmittedPlaces.findOrCreate(new UserSubmittedPlaceService.Snapshot(
					snapshot.source(), snapshot.externalId(), snapshot.name(), snapshot.address(),
					snapshot.lat(), snapshot.lng(), snapshot.category()));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidReferenceException("place", ex.getMessage());
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
