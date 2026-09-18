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

/**
 * S15P21E201-835 — 기록 본문·작성자 표시 이름이 응답에서 인코딩되는가.
 *
 * <p>ZAP 이 실제로 잡은 경로({@code story.body} 가 JSON 응답에 무이스케이프로 나가는 것)를
 * 재현한다 — 스크립트 태그를 담은 기록을 조립했을 때 응답에 그대로 남지 않아야 한다.
 */
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
		// 🔴 S15P21E201-1174 — 반응 수는 이 검사의 주제가 아니다(여기는 인코딩을 잰다).
		//    빈 목록을 돌려주게 두면 조립기가 0·0·null 로 채우고, 그게 「반응이 없는 글」의
		//    실제 모습이다. 진짜 집계는 StoryReactionResponseIntegrationTest 가 잰다.
		StoryReactionRepository storyReactionRepository = mock(StoryReactionRepository.class);
		// 🔴 S15P21E201-1279 — 동영상도 이 검사의 주제가 아니다(여기는 인코딩을 잰다).
		//    빈 목록이면 조립기가 media 를 빈 배열로 채우고, 그게 「동영상 없는 글」의 실제
		//    모습이다. media 가 실제로 실리는지는 StoryMediaResponseIntegrationTest 가 잰다.
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

	/**
	 * 🔴 {@code AppUser.userId} 는 {@code @GeneratedValue} 라 persist 전에는 null 이다 — 실제
	 * DB 없이 조립기만 검사하려면 그 자리에 이 테스트가 쓸 id 를 직접 심어야 한다.
	 */
	private AppUser authorNamed(UUID userId, String displayName) {
		AppUser author = AppUser.register(displayName, "ko", null, null, PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		ReflectionTestUtils.setField(author, "userId", userId);
		return author;
	}

	@Test
	@DisplayName("🔴 기록 본문의 스크립트 태그가 인코딩된 채로 응답에 실린다 — ZAP: Persistent XSS in JSON Response")
	void bodyIsHtmlEncoded() {
		// 🔴 AppUser.userId 는 @GeneratedValue 라 persist 전에는 null 이다 — Story 가 가리키는
		//    작성자 id 는 독립된 UUID 로 만들고, appUserRepository.findAllById() 를 그 id 로 답하게 한다.
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
