package com.gabolle.backend.story.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.StoryReactionRepository;
import com.gabolle.backend.story.repository.StoryVideoRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.repository.UploadedVideoRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 기록 본문과 작성자 표시 이름이 응답에서 HTML 인코딩되는가. */
class StoryResponseAssemblerTest {

	private static final String XSS_PAYLOAD = "<script>alert(1)</script>";

	private StoryResponseAssembler assembler;

	private AppUserRepository appUserRepository;

	@BeforeEach
	void setUp() {
		this.appUserRepository = mock(AppUserRepository.class);
		StoryImageRepository storyImageRepository = mock(StoryImageRepository.class);
		UploadedImageRepository uploadedImageRepository = mock(UploadedImageRepository.class);
		PlaceRepository placeRepository = mock(PlaceRepository.class);
		// 빈 목록을 돌려주면 조립기가 0·0·null 로 채운다. 반응이 없는 글의 실제 모습이다.
		StoryReactionRepository storyReactionRepository = mock(StoryReactionRepository.class);
		// 빈 목록이면 조립기가 media 를 빈 배열로 채운다 — 동영상 없는 글의 실제 모습이다.
		// media 가 실제로 실리는지는 StoryMediaResponseIntegrationTest 가 잰다.
		StoryVideoRepository storyVideoRepository = mock(StoryVideoRepository.class);
		UploadedVideoRepository uploadedVideoRepository = mock(UploadedVideoRepository.class);
		when(storyVideoRepository.findByStoryIdIn(any())).thenReturn(List.of());

		when(storyImageRepository.findByStoryIdInOrderByStoryIdAscPositionAsc(any())).thenReturn(List.of());
		when(storyReactionRepository.countByStories(any())).thenReturn(List.of());
		when(storyReactionRepository.findMineByStories(any(), any())).thenReturn(List.of());

		this.assembler = new StoryResponseAssembler(storyImageRepository, uploadedImageRepository,
				this.appUserRepository, placeRepository, storyReactionRepository, storyVideoRepository,
				uploadedVideoRepository);
	}

	/** {@code AppUser.userId} 는 {@code @GeneratedValue} 라 persist 전에는 null 이므로 직접 심는다. */
	private AppUser authorNamed(UUID userId, String displayName) {
		AppUser author = AppUser.register(displayName, "ko", null, null, PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		ReflectionTestUtils.setField(author, "userId", userId);
		return author;
	}

	@Test
	@DisplayName("🔴 기록 본문의 스크립트 태그가 인코딩된 채로 응답에 실린다 — ZAP: Persistent XSS in JSON Response")
	void bodyIsHtmlEncoded() {
		UUID authorId = UUID.randomUUID();
		AppUser author = authorNamed(authorId, "작성자");
		when(this.appUserRepository.findAllById(any())).thenReturn(List.of(author));

		Instant now = Instant.now();
		Story story = new Story(UUID.randomUUID(), authorId, null, null, XSS_PAYLOAD, "해운대구",
				StoryVisibility.PUBLIC, now, now);

		StoryResponse response = this.assembler.one(story, authorId, now);

		assertThat(response.body()).doesNotContain("<script>");
		assertThat(response.body()).contains("&lt;script&gt;");
	}

	@Test
	@DisplayName("🔴 작성자 표시 이름도 같은 자유 입력이라 인코딩된다")
	void displayNameIsHtmlEncoded() {
		UUID authorId = UUID.randomUUID();
		AppUser author = authorNamed(authorId, XSS_PAYLOAD);
		when(this.appUserRepository.findAllById(any())).thenReturn(List.of(author));

		Instant now = Instant.now();
		Story story = new Story(UUID.randomUUID(), authorId, null, null, "평범한 글", "해운대구",
				StoryVisibility.PUBLIC, now, now);

		StoryResponse response = this.assembler.one(story, authorId, now);

		assertThat(response.author().displayName()).doesNotContain("<script>");
	}
}
