package com.gabolle.backend.story.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.common.security.HtmlOutputEncoder;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryCoauthor;
import com.gabolle.backend.story.domain.ReactionType;
import com.gabolle.backend.story.domain.StoryImage;
import com.gabolle.backend.story.domain.StoryVideo;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.domain.UploadedVideo;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryCoauthorRepository;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.StoryVideoRepository;
import com.gabolle.backend.story.repository.StoryReactionRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.repository.UploadedVideoRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 기록 목록을 응답으로 바꾼다. 사진·작성자·장소·반응을 기록마다가 아니라 한 페이지에 한 번씩 읽는다 —
 * 기록마다 읽으면 20건 피드가 그만큼의 왕복이 된다.
 */
@Component
@Profile({ "db", "dev" })
public class StoryResponseAssembler {

	private final StoryImageRepository storyImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final StoryVideoRepository storyVideoRepository;

	private final UploadedVideoRepository uploadedVideoRepository;

	private final AppUserRepository appUserRepository;

	private final PlaceRepository placeRepository;

	private final StoryReactionRepository storyReactionRepository;

	private final StoryCoauthorRepository storyCoauthorRepository;

	public StoryResponseAssembler(StoryImageRepository storyImageRepository,
			UploadedImageRepository uploadedImageRepository, AppUserRepository appUserRepository,
			PlaceRepository placeRepository, StoryReactionRepository storyReactionRepository,
			StoryVideoRepository storyVideoRepository, UploadedVideoRepository uploadedVideoRepository,
			StoryCoauthorRepository storyCoauthorRepository) {
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.appUserRepository = appUserRepository;
		this.placeRepository = placeRepository;
		this.storyReactionRepository = storyReactionRepository;
		this.storyVideoRepository = storyVideoRepository;
		this.uploadedVideoRepository = uploadedVideoRepository;
		this.storyCoauthorRepository = storyCoauthorRepository;
	}

	public StoryResponse one(Story story, UUID viewer, Instant now) {
		return many(List.of(story), viewer, now).get(0);
	}

	public List<StoryResponse> many(List<Story> stories, UUID viewer, Instant now) {
		if (stories.isEmpty()) {
			return List.of();
		}
		List<UUID> storyIds = stories.stream().map(Story::getStoryId).toList();

		Map<UUID, List<StoryImage>> imagesByStory = new HashMap<>();
		Set<UUID> uploadIds = new HashSet<>();
		for (StoryImage image : this.storyImageRepository.findByStoryIdInOrderByStoryIdAscPositionAsc(storyIds)) {
			imagesByStory.computeIfAbsent(image.getStoryId(), (k) -> new ArrayList<>()).add(image);
			uploadIds.add(image.getUploadedImageId());
		}
		// 동영상을 사진 업로드 조회보다 먼저 읽는다. 썸네일도 uploaded_image 행이라 아래 조회에
		// 같이 태워야 질의가 하나 늘지 않는다.
		Map<UUID, StoryVideo> videoByStory = new HashMap<>();
		Set<UUID> videoUploadIds = new HashSet<>();
		for (StoryVideo storyVideo : this.storyVideoRepository.findByStoryIdIn(storyIds)) {
			videoByStory.put(storyVideo.getStoryId(), storyVideo);
			videoUploadIds.add(storyVideo.getUploadedVideoId());
			if (storyVideo.getThumbnailUploadId() != null) {
				uploadIds.add(storyVideo.getThumbnailUploadId());
			}
		}
		Map<UUID, UploadedVideo> videoUploads = new HashMap<>();
		if (!videoUploadIds.isEmpty()) {
			for (UploadedVideo upload : this.uploadedVideoRepository.findByUploadedVideoIdIn(videoUploadIds)) {
				videoUploads.put(upload.getUploadedVideoId(), upload);
			}
		}

		Map<UUID, UploadedImage> uploads = new HashMap<>();
		if (!uploadIds.isEmpty()) {
			for (UploadedImage upload : this.uploadedImageRepository.findByUploadedImageIdIn(uploadIds)) {
				uploads.put(upload.getUploadedImageId(), upload);
			}
		}

		Set<UUID> authorIds = new HashSet<>();
		Set<UUID> placeIds = new HashSet<>();
		for (Story story : stories) {
			authorIds.add(story.getAuthorUserId());
			if (story.getPlaceId() != null) {
				placeIds.add(story.getPlaceId());
			}
		}
		// 공동 작성자의 이름은 작성자 이름 조회에 같이 태운다 — 질의를 하나 더 늘리지 않는다.
		Map<UUID, List<StoryCoauthor>> coauthorsByStory = new HashMap<>();
		for (StoryCoauthor coauthor : this.storyCoauthorRepository
				.findByKeyStoryIdInOrderByJoinedAtAscKeyUserIdAsc(storyIds)) {
			coauthorsByStory.computeIfAbsent(coauthor.getStoryId(), (k) -> new ArrayList<>()).add(coauthor);
			authorIds.add(coauthor.getUserId());
		}
		Map<UUID, AppUser> authors = new HashMap<>();
		for (AppUser user : this.appUserRepository.findAllById(authorIds)) {
			authors.put(user.getUserId(), user);
		}
		Map<UUID, Place> places = new HashMap<>();
		if (!placeIds.isEmpty()) {
			for (Place place : this.placeRepository.findAllById(placeIds)) {
				places.put(place.getPlaceId(), place);
			}
		}

		Map<UUID, int[]> reactionCounts = new HashMap<>();
		for (StoryReactionRepository.StoryReactionCount row : this.storyReactionRepository
				.countByStories(storyIds)) {
			int[] slot = reactionCounts.computeIfAbsent(row.getStoryId(), (k) -> new int[2]);
			slot[row.getReaction() == ReactionType.LIKE ? 0 : 1] = (int) row.getCount();
		}
		// 비회원(viewer == null)이면 질의 자체를 안 한다. 복합 키의 한 칸이 null 인 조회는 동작이
		// 보장되지 않는다.
		Map<UUID, ReactionType> myReactions = new HashMap<>();
		if (viewer != null) {
			for (StoryReactionRepository.StoryViewerReaction row : this.storyReactionRepository
					.findMineByStories(storyIds, viewer)) {
				myReactions.put(row.getStoryId(), row.getReaction());
			}
		}

		List<StoryResponse> out = new ArrayList<>(stories.size());
		for (Story story : stories) {
			AppUser author = authors.get(story.getAuthorUserId());
			// 탈퇴한 계정은 행이 남아 있되 이름이 비워진다 — 그때는 "탈퇴한 사용자" 로 낸다.
			// 🔴 이름은 원문 그대로 낸다 (S15P21E201-1655). 인코딩해서 내면 Text 로 그리는 앱에 「&amp;」가 보이고, 같은
			//    이름이 프로필 응답(원문)과 달라진다. 앱은 모든 글을 Text 로 그려 주입 경로가 없다 — 근거는
			//    docs/VULNERABILITY-REPORT.md. 본문은 아래에서 인코딩을 유지한다(S15P21E201-835).
			String displayName = (author == null || author.getDeletedAt() != null || author.getDisplayName() == null
					|| author.getDisplayName().isBlank()) ? "탈퇴한 사용자" : author.getDisplayName();
			// 프로필 사진은 팔로우 목록(RelationItemResponse)과 같은 칸(app_user.avatar_url)에서 그대로 낸다.
			// 탈퇴한 계정은 사진도 내지 않는다 — 이름을 지운 계정의 얼굴만 남으면 안 된다 (S15P21E201-1803).
			String authorAvatarUrl = avatarOf(author);
			Place place = story.getPlaceId() == null ? null : places.get(story.getPlaceId());
			// 기본값으로 공유 배열을 건네지 않는다. 그 배열에 쓰는 변경이 하나라도 들어오면
			// 모든 글의 수가 예외 없이 조용히 오염된다.
			int[] counts = reactionCounts.get(story.getStoryId());
			int likeCount = (counts == null) ? 0 : counts[0];
			int dislikeCount = (counts == null) ? 0 : counts[1];
			List<StoryResponse.Image> images = new ArrayList<>();
			for (StoryImage image : imagesByStory.getOrDefault(story.getStoryId(), List.of())) {
				UploadedImage upload = uploads.get(image.getUploadedImageId());
				if (upload != null) {
					images.add(new StoryResponse.Image(upload.getImageUrl(), image.getPosition()));
				}
			}
			// 동영상은 기록당 0개 또는 1개다(uq_story_video_story). 썸네일이 없으면 null 을
			// 넣는다 — 빈 문자열이나 자리표시 주소는 화면에 「있다」로 읽혀 깨진 그림이 뜬다.
			List<StoryResponse.Media> media = new ArrayList<>();
			StoryVideo storyVideo = videoByStory.get(story.getStoryId());
			if (storyVideo != null) {
				UploadedVideo video = videoUploads.get(storyVideo.getUploadedVideoId());
				if (video != null) {
					UploadedImage thumbnail = (storyVideo.getThumbnailUploadId() == null) ? null
							: uploads.get(storyVideo.getThumbnailUploadId());
					media.add(new StoryResponse.Media("VIDEO", video.getVideoUrl(),
							thumbnail == null ? null : thumbnail.getImageUrl(), video.getDurationSec()));
				}
			}
			// 작성자와 달리 「탈퇴한 사용자」 글자를 박지 않고 null 로 둔다 — 계약이 그렇게 정했다.
			List<StoryResponse.Coauthor> coauthors = new ArrayList<>();
			for (StoryCoauthor coauthor : coauthorsByStory.getOrDefault(story.getStoryId(), List.of())) {
				AppUser user = authors.get(coauthor.getUserId());
				// 이름은 원문 그대로 — 작성자 이름과 같은 이유(S15P21E201-1655).
				String name = (user == null || user.getDeletedAt() != null || user.getDisplayName() == null
						|| user.getDisplayName().isBlank()) ? null : user.getDisplayName();
				coauthors.add(new StoryResponse.Coauthor(coauthor.getUserId().toString(), name, avatarOf(user)));
			}
			out.add(new StoryResponse(
					story.getStoryId().toString(),
					new StoryResponse.Author(story.getAuthorUserId().toString(), displayName, authorAvatarUrl),
					// 도메인은 원문을 그대로 갖고, 응답으로 나가는 여기서만 인코딩한다. 본문은 ZAP 이 잡은 자리라
					// 방어층을 유지한다(S15P21E201-835) — 앱은 마크다운으로 파싱하며 되돌려 그린다.
					HtmlOutputEncoder.forHtml(story.getBody()),
					story.getRegion(),
					place == null ? null
							: new StoryResponse.PlaceRef(place.getPlaceId().toString(), place.getNameKo(),
									place.getLat(), place.getLng(), place.getAddress(), place.getNameEn(),
									place.getAddressEn()),
					story.getTripId() == null ? null : story.getTripId().toString(),
					images,
					story.getVisibility().name(),
					story.getPublishAt().toString(),
					story.getCreatedAt().toString(),
					story.getUpdatedAt().toString(),
					story.isAuthor(viewer),
					story.isPublishedAt(now),
					story.getParentStoryId() == null ? null : story.getParentStoryId().toString(),
					story.getReplyCount(),
					story.getViewCount(),
					story.getLinkCopyCount(),
					likeCount,
					dislikeCount,
					myReactions.containsKey(story.getStoryId())
							? myReactions.get(story.getStoryId()).name()
							: null,
					media,
					coauthors));
		}
		return out;
	}

	/** 탈퇴했거나 사진을 안 골랐으면 {@code null}. 빈 문자열은 「없음」으로 친다. */
	private static String avatarOf(AppUser user) {
		if (user == null || user.getDeletedAt() != null) {
			return null;
		}
		String url = user.getAvatarUrl();
		return (url == null || url.isBlank()) ? null : url;
	}
}
