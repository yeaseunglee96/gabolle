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
import com.gabolle.backend.story.domain.StoryImage;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 기록 목록을 응답으로 바꾼다 — 사진·작성자·장소를 <b>한 페이지에 한 번씩</b> 읽는다.
 *
 * <p>기록마다 사진 질의·작성자 질의를 하면 20건 피드가 60번의 왕복이 된다(N+1). 여기서는 페이지의
 * 식별자를 모아 세 번만 읽고 메모리에서 붙인다.
 */
@Component
@Profile({ "db", "dev" })
public class StoryResponseAssembler {

	private final StoryImageRepository storyImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final AppUserRepository appUserRepository;

	private final PlaceRepository placeRepository;

	public StoryResponseAssembler(StoryImageRepository storyImageRepository,
			UploadedImageRepository uploadedImageRepository, AppUserRepository appUserRepository,
			PlaceRepository placeRepository) {
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.appUserRepository = appUserRepository;
		this.placeRepository = placeRepository;
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

		List<StoryResponse> out = new ArrayList<>(stories.size());
		for (Story story : stories) {
			AppUser author = authors.get(story.getAuthorUserId());
			// 탈퇴한 계정은 행이 남아 있되 이름이 비워진다(DEC-AUTH-009) — 그때는 "탈퇴한 사용자" 로 낸다.
			// 🔴 S15P21E201-835 — displayName 은 회원가입 때 사용자가 자유롭게 정하는 값이라
			//    story.body 와 같은 종류의 입력이다. 인코딩한다.
			String displayName = HtmlOutputEncoder.forHtml(
					(author == null || author.getDeletedAt() != null || author.getDisplayName() == null
							|| author.getDisplayName().isBlank()) ? "탈퇴한 사용자" : author.getDisplayName());
			Place place = story.getPlaceId() == null ? null : places.get(story.getPlaceId());
			List<StoryResponse.Image> images = new ArrayList<>();
			for (StoryImage image : imagesByStory.getOrDefault(story.getStoryId(), List.of())) {
				UploadedImage upload = uploads.get(image.getUploadedImageId());
				if (upload != null) {
					images.add(new StoryResponse.Image(upload.getImageUrl(), image.getPosition()));
				}
			}
			out.add(new StoryResponse(
					story.getStoryId().toString(),
					new StoryResponse.Author(story.getAuthorUserId().toString(), displayName),
					// 🔴 S15P21E201-835 — ZAP 이 잡은 지속형 XSS. 도메인은 원문을 그대로 갖고,
					//    응답으로 나가는 여기서만 인코딩한다(클래스 상단 HtmlOutputEncoder 참고).
					HtmlOutputEncoder.forHtml(story.getBody()),
					story.getRegion(),
					// S15P21E201-1189 — 주소도 영문도 이미 읽어 둔 place 행에 있다. 장소를 다시 조회하지 않는다.
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
					// S15P21E201-1183 — 이미 손에 있는 값이다. 부모를 다시 조회하지 않는다.
					story.getParentStoryId() == null ? null : story.getParentStoryId().toString(),
					story.getReplyCount(),
					// S15P21E201-1204 — 누적 칸이라 이미 손에 있다. 낱개를 세지 않는다.
					story.getViewCount(),
					story.getLinkCopyCount()));
		}
		return out;
	}
}
