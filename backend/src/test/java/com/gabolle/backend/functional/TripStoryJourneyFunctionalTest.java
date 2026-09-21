package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.moderation.presentation.dto.StoryReportRequest;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.presentation.dto.TripStoriesResponse;
import com.gabolle.backend.trip.presentation.dto.AcceptInviteResponse;
import com.gabolle.backend.trip.presentation.dto.CreateTripInviteRequest;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TripDto;
import com.gabolle.backend.trip.presentation.dto.TripInviteResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 여행에 달린 기록 조회 여정. 추억 지도가 부르는 경로다.
 *
 * <p>실제 HTTP 로 재는 것은 판정이 두 겹이기 때문이다 — 여행 참여자인지와 그 기록을 볼 수 있는지.
 * 서비스만 부르면 인증 주체를 테스트가 직접 넣어 주므로 «요청자가 누구로 정해지는가»가 검사 밖에
 * 남는다. 진짜 소켓으로 나가면 {@code SecurityFilterChain} 과 {@code AuthenticatedUsers.requireId}
 * 를 함께 지난다.
 */
class TripStoryJourneyFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("그 여행의 볼 수 있는 기록만 시간순으로 오고, 남의 나만 보기·신고된 기록·다른 여행 기록은 안 온다")
	void tripStoriesJourneyEndToEnd() {
		AuthedClient owner = loginAsNewUser("trip-story-owner");
		AuthedClient member = loginAsNewUser("trip-story-member");
		AuthedClient outsider = loginAsNewUser("trip-story-outsider");

		// 1) 여행 둘 — 하나는 이 조회의 대상이고, 하나는 "섞이지 않는다" 를 재기 위한 것이다.
		String tripId = createTrip(owner);
		String otherTripId = createTrip(owner);
		assertThat(tripId).isNotEqualTo(otherTripId);

		// 2) 동행자 합류 — 초대 발급과 수락을 실제 경로로 지난다(표가 곧 열쇠다).
		joinAsMember(owner, member, tripId);

		// 3) 좌표가 있는 장소 하나. 가입 경로로 만들 수 없는 자료라 시드에서 직접 넣는다
		//    (StoryFixture 가 lat 35.16 · lng 129.16 을 박아 둔다).
		UUID placeId = StoryFixture.insertPlace(this.jdbcTemplate, "동래할매파전", "부산광역시 동래구 명륜동",
				"Dongnae Halmae Pajeon", "1, Myeongnyun-ro, Dongnae-gu, Busan");

		// 4) 기록 넷.
		//    publishAt 을 과거로 명시한다 — 안 주면 기본값이 "여행 종료 다음 날 0시" 라
		//    7일 뒤 여행에서는 전부 공개 전이 되고, 그러면 "남에게 안 보인다" 가 공개 시각
		//    때문인지 공개 범위 때문인지 구분되지 않는다.
		Instant published = Instant.now().minusSeconds(3600);
		String ownerStory = createStory(owner, "첫째 날 파전", placeId, tripId, StoryVisibility.PUBLIC, published);
		String memberStory = createStory(member, "동행자가 쓴 기록", null, tripId, StoryVisibility.PUBLIC, published);
		String memberPrivate = createStory(member, "동행자의 나만 보기", null, tripId, StoryVisibility.PRIVATE,
				published);
		String otherTripStory = createStory(owner, "다른 여행 기록", null, otherTripId, StoryVisibility.PUBLIC,
				published);

		// 5) 참여자가 보는 목록 — 완료 기준 두 줄이 여기서 갈린다.
		List<StoryResponse> items = tripStories(owner, tripId);
		assertThat(items).extracting(StoryResponse::id)
				.as("같은 여행의 공개 기록이 안 온다")
				.contains(ownerStory, memberStory);
		assertThat(items).extracting(StoryResponse::id)
				.as("남의 나만 보기 기록이 여행에 달렸다는 이유로 새어 나왔다 — S15P21E201-137")
				.doesNotContain(memberPrivate);
		assertThat(items).extracting(StoryResponse::id)
				.as("다른 여행의 기록이 섞였다")
				.doesNotContain(otherTripStory);

		// 6) 좌표와 주소 — 이것이 없으면 화면이 장소마다 GET /api/v1/places/{id} 를 한 번씩 더 부른다.
		StoryResponse withPlace = items.stream().filter((s) -> s.id().equals(ownerStory)).findFirst().orElseThrow();
		assertThat(withPlace.place()).as("장소 칸이 비어 있다").isNotNull();
		assertThat(withPlace.place().lat()).as("사진 마커를 찍을 위도가 안 온다").isEqualTo(35.16);
		assertThat(withPlace.place().lng()).as("사진 마커를 찍을 경도가 안 온다").isEqualTo(129.16);
		// 3) 에서 넣은 주소와 영문이 그대로 돌아와야 한다. 진짜 소켓으로 나가므로 이 단언은
		// "칸을 더했다" 가 아니라 "JSON 으로 직렬화돼 화면까지 간다" 를 잰다.
		assertPlaceFields(withPlace, "목록");

		// 상세에도 실려야 한다. 목록과 상세가 StoryResponse 하나를 같이 쓰므로 자동일 것이지만,
		// 자동이라고 믿는 대신 잰다 — 한쪽에만 실으면 다른 쪽이 못 그리거나 값이 어긋난다.
		assertPlaceFields(storyDetail(owner, ownerStory), "상세");

		// 7) 쓴 순서대로 — 화면이 그대로 그릴 수 있어야 한다.
		List<String> ids = items.stream().map(StoryResponse::id).toList();
		assertThat(ids.indexOf(ownerStory)).as("먼저 쓴 기록이 뒤에 있다").isLessThan(ids.indexOf(memberStory));

		// 8) 신고가 들어오면 참여자에게도 사라진다 — 한 건으로 UNDER_REVIEW 가 된다.
		ResponseEntity<Void> report = outsider.post("/api/v1/stories/" + memberStory + "/reports",
				new StoryReportRequest("OFFENSIVE", null), Void.class);
		assertThat(report.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(tripStories(owner, tripId)).extracting(StoryResponse::id)
				.as("신고로 가려진 기록이 추억 지도에 남아 있다")
				.doesNotContain(memberStory);

		// 9) 그 여행의 회원이 아니면 존재를 감춘 404 — 403 을 주면 "그 여행은 있다" 를 알려 준다.
		ResponseEntity<String> denied = outsider.get("/api/v1/trips/" + tripId + "/stories", String.class);
		assertThat(denied.getStatusCode()).as("응답 본문: %s", denied.getBody()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(denied.getBody()).contains("TRIP_NOT_FOUND");
	}

	/** 기록 한 건을 상세로 읽는다. 목록과 같은 모양이 오는지 재려고 쓴다. */
	private StoryResponse storyDetail(AuthedClient client, String storyId) {
		ResponseEntity<ApiResponse<StoryResponse>> response = client.get("/api/v1/stories/" + storyId,
				new ParameterizedTypeReference<ApiResponse<StoryResponse>>() {
				});
		assertThat(response.getStatusCode()).as("응답 본문: %s", response.getBody()).isEqualTo(HttpStatus.OK);
		return response.getBody().data();
	}

	/** 장소 칸 넷을 한자리에서 잰다 — 목록과 상세에 같은 잣대를 댄다. */
	private void assertPlaceFields(StoryResponse story, String where) {
		assertThat(story.place()).as("%s: 장소 칸이 비어 있다", where).isNotNull();
		assertThat(story.place().address())
				.as("%s: 장소 주소가 안 온다 — 카드가 주소 한 줄 때문에 장소마다 한 번씩 더 조회하게 된다", where)
				.isEqualTo("부산광역시 동래구 명륜동");
		assertThat(story.place().nameEn())
				.as("%s: 영문 이름이 안 온다 — 영어 화면이 한글 이름을 그린다", where)
				.isEqualTo("Dongnae Halmae Pajeon");
		assertThat(story.place().addressEn())
				.as("%s: 영문 주소가 안 온다 — 이름만 영어이고 주소는 한글인 화면이 된다", where)
				.isEqualTo("1, Myeongnyun-ro, Dongnae-gu, Busan");
	}

	private List<StoryResponse> tripStories(AuthedClient client, String tripId) {
		ResponseEntity<ApiResponse<TripStoriesResponse>> response = client.get(
				"/api/v1/trips/" + tripId + "/stories",
				new ParameterizedTypeReference<ApiResponse<TripStoriesResponse>>() {
				});
		assertThat(response.getStatusCode()).as("응답 본문: %s", response.getBody()).isEqualTo(HttpStatus.OK);
		return response.getBody().data().items();
	}

	private String createTrip(AuthedClient client) {
		LocalDate start = LocalDate.now().plusDays(7);
		CreateTripRequest request = new CreateTripRequest(start, start.plusDays(1), 35.1152, 129.0423, null, 2, null,
				null, List.of(), null, List.of(), null, null, null, null, null);
		ResponseEntity<ApiResponse<TripDto>> response = client.post("/api/v1/trips", request,
				new ParameterizedTypeReference<ApiResponse<TripDto>>() {
				});
		assertThat(response.getStatusCode()).as("응답 본문: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().data().tripId();
	}

	private void joinAsMember(AuthedClient owner, AuthedClient invitee, String tripId) {
		ResponseEntity<ApiResponse<TripInviteResponse>> issued = owner.post(
				"/api/v1/trips/" + tripId + "/invites", new CreateTripInviteRequest("EDITOR"),
				new ParameterizedTypeReference<ApiResponse<TripInviteResponse>>() {
				});
		assertThat(issued.getStatusCode()).as("응답 본문: %s", issued.getBody()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<ApiResponse<AcceptInviteResponse>> accepted = invitee.post(
				"/api/v1/trip-invites/" + issued.getBody().data().token() + "/accept", null,
				new ParameterizedTypeReference<ApiResponse<AcceptInviteResponse>>() {
				});
		assertThat(accepted.getStatusCode()).as("응답 본문: %s", accepted.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(accepted.getBody().data().tripId()).isEqualTo(tripId);
	}

	private String createStory(AuthedClient client, String body, UUID placeId, String tripId,
			StoryVisibility visibility, Instant publishAt) {
		StoryCreateRequest request = new StoryCreateRequest(body, List.of(), placeId, UUID.fromString(tripId), null,
				visibility, publishAt);
		ResponseEntity<ApiResponse<StoryResponse>> response = client.post("/api/v1/stories", request,
				new ParameterizedTypeReference<ApiResponse<StoryResponse>>() {
				});
		assertThat(response.getStatusCode()).as("응답 본문: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().data().id();
	}
}
