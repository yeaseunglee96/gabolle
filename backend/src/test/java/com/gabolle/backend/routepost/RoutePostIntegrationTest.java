package com.gabolle.backend.routepost;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.testslice.RoutePostSliceApplication;

/**
 * 루트 포스트(-2027)와 장소별 루트(-2028). 일정 조회는 가짜로 바꿔 끼운다 — 여기서 재는 것은
 * 올리기·피드·인기순·공개 범위(차단·지움)·반응 중복이고, 숙소 빼기는 {@link RouteSnapshotBuilderTest} 가 잰다.
 */
@SpringBootTest(classes = { RoutePostSliceApplication.class, RoutePostIntegrationTest.FakeSource.class },
		properties = { "spring.profiles.active=db", "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@ExtendWith(PostgresAvailableCondition.class)
class RoutePostIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/** 일정 id 를 "장소id,장소id,..." 로 받아 그대로 정류장으로 만든다. 다른 사람의 일정은 "foreign". */
	@TestConfiguration
	static class FakeSource {

		@Bean
		@Primary
		RouteSnapshotSource fakeRouteSnapshotSource() {
			return (user, itineraryId, dayIndex) -> {
				if ("foreign".equals(itineraryId)) {
					throw new RoutePostExceptions.SourceNotFoundException();
				}
				List<RouteSnapshot.Stop> stops = java.util.Arrays.stream(itineraryId.split(","))
						.map(id -> new RouteSnapshot.Stop(0, UUID.fromString(id), "곳", 35.1, 129.1, 30)).toList();
				return new RouteSnapshot(null, "it-test", dayIndex, 1, stops,
						List.of(new double[] { 35.1, 129.1 }, new double[] { 35.2, 129.2 }), 90, 1200);
			};
		}
	}

	@Autowired
	private RoutePostController controller;

	@Autowired
	private RoutePostExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	private UUID author;

	private UUID reader;

	private UUID placeA;

	private UUID placeB;

	private UUID placeC;

	@BeforeEach
	void setUp() {
		this.mvc = MockMvcBuilders.standaloneSetup(this.controller).setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "루트쓴이");
		this.reader = StoryFixture.insertUser(this.jdbc, "구경꾼");
		this.placeA = StoryFixture.insertPlace(this.jdbc, "광안리", "부산 수영구");
		this.placeB = StoryFixture.insertPlace(this.jdbc, "민락수변공원", "부산 수영구");
		this.placeC = StoryFixture.insertPlace(this.jdbc, "흰여울마을", "부산 영도구");
	}

	private String publish(UUID who, String title, UUID... places) throws Exception {
		String ids = String.join(",", java.util.Arrays.stream(places).map(UUID::toString).toList());
		String body = "{\"itineraryId\":\"" + ids + "\",\"dayIndex\":0,\"title\":\"" + title + "\",\"body\":\"좋았다\"}";
		String json = this.mvc.perform(post("/api/v1/route-posts").principal(StoryFixture.as(who))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(json, "$.data.routePostId");
	}

	@Test
	@DisplayName("올리면 정류장 순서·시간·거리·지도 선이 실린 글이 되고, 최신 피드에 뜬다")
	void publishAndSeeInFeed() throws Exception {
		String id = publish(this.author, "광안리 저녁 산책", this.placeA, this.placeB);

		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stops.length()").value(2))
				.andExpect(jsonPath("$.data.stops[0].name").value("광안리"))
				.andExpect(jsonPath("$.data.stops[1].placeId").value(this.placeB.toString()))
				.andExpect(jsonPath("$.data.durationMin").value(90))
				.andExpect(jsonPath("$.data.distanceM").value(1200))
				.andExpect(jsonPath("$.data.path.length()").value(2))
				.andExpect(jsonPath("$.data.mine").value(false))
				.andExpect(jsonPath("$.data.author.displayName").value("루트쓴이"));

		this.mvc.perform(get("/api/v1/route-posts").param("limit", "50").principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.routePostId == '" + id + "')]").exists());
	}

	@Test
	@DisplayName("🔴 남의 일정은 올릴 수 없다 — 404")
	void cannotPublishSomeoneElsesItinerary() throws Exception {
		this.mvc.perform(post("/api/v1/route-posts").principal(StoryFixture.as(this.author))
				.contentType(MediaType.APPLICATION_JSON).content("{\"itineraryId\":\"foreign\",\"title\":\"x\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));
	}

	@Test
	@DisplayName("제목이 없으면 거절한다")
	void titleRequired() throws Exception {
		this.mvc.perform(post("/api/v1/route-posts").principal(StoryFixture.as(this.author))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itineraryId\":\"" + this.placeA + "," + this.placeB + "\",\"title\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_TITLE"));
	}

	@Autowired
	private RoutePostService service;

	@Test
	@DisplayName("🔴 복사로 만든 여행을 지우면 「따라 간 사람」에서 빠진다 — 모르는 여행을 지우면 그대로다 (S15P21E201-2043)")
	void deletingTheCopiedTripUncounts() throws Exception {
		String id = publish(this.author, "따라가기", this.placeA, this.placeB);
		this.mvc.perform(post("/api/v1/route-posts/{id}/copies", id).param("itineraryId", "not-a-uuid")
				.principal(StoryFixture.as(this.reader))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.copyCount").value(1));
		UUID trip = UUID.randomUUID();
		this.jdbc.update("UPDATE route_post_reaction SET trip_id = ? WHERE route_post_id = ?::uuid AND kind = 'COPY'", trip, id);

		this.service.onTripDeleted(new com.gabolle.backend.trip.domain.TripDeleted(UUID.randomUUID().toString()));
		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.copyCount").value(1));

		this.service.onTripDeleted(new com.gabolle.backend.trip.domain.TripDeleted(trip.toString()));
		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.copyCount").value(0));

		// 다시 따라 하면 다시 하나 — 지운 뒤 새 여행은 새로 센다.
		this.mvc.perform(post("/api/v1/route-posts/{id}/copies", id).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.copyCount").value(1));
	}

	@Test
	@DisplayName("🔴 좋아요는 연타해도 한 번, 복사는 같은 사람이 여러 번 해도 한 번 센다")
	void reactionsAreIdempotent() throws Exception {
		String id = publish(this.author, "연타", this.placeA, this.placeB);
		for (int i = 0; i < 3; i++) {
			this.mvc.perform(put("/api/v1/route-posts/{id}/like", id).principal(StoryFixture.as(this.reader)))
					.andExpect(status().isOk());
			this.mvc.perform(post("/api/v1/route-posts/{id}/copies", id).principal(StoryFixture.as(this.reader)))
					.andExpect(status().isOk());
		}
		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.likeCount").value(1))
				.andExpect(jsonPath("$.data.copyCount").value(1))
				.andExpect(jsonPath("$.data.likedByMe").value(true));

		this.mvc.perform(delete("/api/v1/route-posts/{id}/like", id).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.likeCount").value(0));
		this.mvc.perform(delete("/api/v1/route-posts/{id}/like", id).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(0));
	}

	@Test
	@DisplayName("🔴 장소별 루트(-2028)는 그 장소가 들어간 글만, 인기순으로, limit 만큼 준다")
	void routesForPlaceArePopularFirst() throws Exception {
		String quiet = publish(this.author, "조용한", this.placeA, this.placeB);
		String popular = publish(this.author, "인기", this.placeB, this.placeA);
		String elsewhere = publish(this.author, "영도", this.placeB, this.placeC);
		this.mvc.perform(put("/api/v1/route-posts/{id}/save", popular).principal(StoryFixture.as(this.reader)));

		this.mvc.perform(get("/api/v1/places/{id}/route-posts", this.placeA).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].routePostId").value(popular))
				.andExpect(jsonPath("$.data[1].routePostId").value(quiet));
		this.mvc.perform(get("/api/v1/places/{id}/route-posts", this.placeB).param("limit", "1")
				.principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data.length()").value(1));
		this.mvc.perform(get("/api/v1/places/{id}/route-posts", this.placeC).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data[0].routePostId").value(elsewhere));
	}

	@Test
	@DisplayName("인기순 피드는 점수(좋아요·저장·복사)가 높은 글이 위다")
	void popularFeedOrdersByScore() throws Exception {
		String low = publish(this.author, "덜", this.placeA, this.placeB);
		String high = publish(this.author, "더", this.placeA, this.placeC);
		this.mvc.perform(post("/api/v1/route-posts/{id}/copies", high).principal(StoryFixture.as(this.reader)));
		this.mvc.perform(put("/api/v1/route-posts/{id}/like", low).principal(StoryFixture.as(this.reader)));

		String json = this.mvc.perform(get("/api/v1/route-posts").param("sort", "POPULAR").param("limit", "50")
				.principal(StoryFixture.as(this.reader))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> ids = com.jayway.jsonpath.JsonPath.read(json, "$.data.items[*].routePostId");
		org.assertj.core.api.Assertions.assertThat(ids.indexOf(high)).isLessThan(ids.indexOf(low));
	}

	@Test
	@DisplayName("커서로 다음 쪽을 이어 받는다 — 겹치지 않는다")
	void cursorPaging() throws Exception {
		publish(this.author, "1", this.placeA, this.placeB);
		publish(this.author, "2", this.placeA, this.placeB);
		publish(this.author, "3", this.placeA, this.placeB);
		String first = this.mvc.perform(get("/api/v1/route-posts").param("limit", "2")
				.principal(StoryFixture.as(this.reader))).andReturn().getResponse().getContentAsString();
		String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.data.nextCursor");
		List<String> a = com.jayway.jsonpath.JsonPath.read(first, "$.data.items[*].routePostId");
		String second = this.mvc.perform(get("/api/v1/route-posts").param("limit", "2").param("cursor", cursor)
				.principal(StoryFixture.as(this.reader))).andReturn().getResponse().getContentAsString();
		List<String> b = com.jayway.jsonpath.JsonPath.read(second, "$.data.items[*].routePostId");
		org.assertj.core.api.Assertions.assertThat(b).doesNotContainAnyElementsOf(a).isNotEmpty();
	}

	@Test
	@DisplayName("🔴 차단한·차단당한 사이에서는 글이 404 이고 장소별 목록에도 없다")
	void blockedAuthorIsHidden() throws Exception {
		String id = publish(this.author, "차단", this.placeC, this.placeB);
		StoryFixture.insertBlock(this.jdbc, this.author, this.reader);

		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/v1/places/{id}/route-posts", this.placeC).principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data[?(@.routePostId == '" + id + "')]").doesNotExist());
		// 로그인 안 한 사람은 차단과 무관하게 공개 글을 본다.
		this.mvc.perform(get("/api/v1/route-posts/{id}", id)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("🔴 남의 글은 못 지운다(403). 내가 지우면 그 뒤로 아무도 못 본다(404)")
	void deleteOnlyByAuthor() throws Exception {
		String id = publish(this.author, "지울 글", this.placeA, this.placeB);

		this.mvc.perform(delete("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isForbidden());
		this.mvc.perform(delete("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNoContent());
		this.mvc.perform(get("/api/v1/route-posts/{id}", id).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("저장한 루트는 내 저장 목록에 뜬다")
	void savedList() throws Exception {
		String id = publish(this.author, "저장", this.placeA, this.placeB);
		this.mvc.perform(put("/api/v1/route-posts/{id}/save", id).principal(StoryFixture.as(this.reader)));

		this.mvc.perform(get("/api/v1/me/saved-route-posts").principal(StoryFixture.as(this.reader)))
				.andExpect(jsonPath("$.data[0].routePostId").value(id))
				.andExpect(jsonPath("$.data[0].savedByMe").value(true));
	}
}
