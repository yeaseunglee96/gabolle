package com.gabolle.backend.placesuggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.PlaceSuggestionSliceApplication;
import com.gabolle.testslice.PlaceSuggestionSliceApplication.RecordingNotifier;

import tools.jackson.databind.json.JsonMapper;

/**
 * 현지인 장소 제안 — 제출·중복·상한·검토·승인 알림·「현지인 추천」 표시(S15P21E201-2031).
 */
@SpringBootTest(classes = PlaceSuggestionSliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(PlaceSuggestionIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class PlaceSuggestionIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestSecurity {

		@Bean
		SecurityFilterChain placeSuggestionTestChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated());
			return http.build();
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/** 광안리 해변 앞 — 부산 경계 안. */
	private static final double IN_LAT = 35.1532;

	private static final double IN_LNG = 129.1186;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RecordingNotifier notifier;

	private final JsonMapper json = JsonMapper.builder().build();

	private UUID local;

	private UUID admin;

	@BeforeEach
	void setUp() {
		this.local = insertUser("부산토박이");
		this.admin = insertUser("운영자");
		this.jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE user_id = ?", this.admin);
		this.notifier.approvedSuggesters.clear();
	}

	@Test
	@DisplayName("제안은 PENDING 으로 쌓이고 place 표에는 아직 안 들어간다 — 추천 후보가 아니다")
	void submitIsPendingAndNotAPlace() throws Exception {
		String name = uniqueName("민락 골목 국밥");
		submit(this.local, body(name, IN_LAT, IN_LNG)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PENDING"));

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM place WHERE name_ko = ?", Integer.class, name))
				.isZero();
		this.mockMvc.perform(get("/api/v1/place-suggestions/mine").with(as(this.local)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].name").value(name));
	}

	@Test
	@DisplayName("🔴 부산 경계 밖(김해 장유)은 거절한다 — 사각형이 아니라 행정 경계로 본다")
	void outsideBusanIsRejected() throws Exception {
		submit(this.local, body(uniqueName("장유 맛집"), 35.1925, 128.8100)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_OUTSIDE_BUSAN"));
	}

	@Test
	@DisplayName("🔴 150m 안에 비슷한 이름의 장소가 있으면 「이미 있는 장소」로 막고 그 장소를 알려 준다")
	void duplicateNearbyPlaceIsRejected() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 4);
		UUID existing = insertPlace("민락수변공원" + tag, IN_LAT, IN_LNG);

		this.mockMvc.perform(get("/api/v1/place-suggestions/duplicates").with(as(this.local))
				.param("name", "민락 수변공원" + tag).param("lat", String.valueOf(IN_LAT + 0.0005))
				.param("lng", String.valueOf(IN_LNG))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].placeId").value(existing.toString()));

		submit(this.local, body("민락 수변공원" + tag, IN_LAT + 0.0005, IN_LNG)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_DUPLICATE"))
				.andExpect(jsonPath("$.error.fields[0]").value(existing.toString()));
	}

	@Test
	@DisplayName("같은 이름이어도 1km 떨어져 있으면 다른 장소다(지점)")
	void sameNameFarAwayIsNotDuplicate() throws Exception {
		String name = uniqueName("할매 국밥");
		insertPlace(name, IN_LAT, IN_LNG);
		submit(this.local, body(name, IN_LAT + 0.01, IN_LNG)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("🔴 하루 다섯 건이 넘으면 429 — 한 사람이 목록을 채울 수 없다")
	void rateLimited() throws Exception {
		for (int i = 0; i < 5; i++) {
			submit(this.local, body(uniqueName("가게" + i), IN_LAT + 0.003 * i, IN_LNG)).andExpect(status().isCreated());
		}
		submit(this.local, body(uniqueName("여섯째"), IN_LAT - 0.004, IN_LNG))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_RATE_LIMITED"));
	}

	@Test
	@DisplayName("🔴 욕설·광고 문구는 받지 않는다")
	void abusiveTextIsRejected() throws Exception {
		Map<String, Object> b = body(uniqueName("어느 가게"), IN_LAT, IN_LNG);
		b.put("reason", "씨발 여기 진짜 최고의 집이다. " + "가".repeat(40));
		submit(this.local, b).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_TEXT_BLOCKED"));

		Map<String, Object> ad = body(uniqueName("광고 가게"), IN_LAT, IN_LNG);
		ad.put("reason", "카톡 문의 http://spam.example " + "가".repeat(40));
		submit(this.local, ad).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_TEXT_BLOCKED"));
	}

	@Test
	@DisplayName("🔴 남이 올린 사진·없는 사진은 붙일 수 없다")
	void foreignPhotoIsRejected() throws Exception {
		UUID other = insertUser("다른 사람");
		String url = insertUploadedImage(other);
		Map<String, Object> b = body(uniqueName("사진 가게"), IN_LAT, IN_LNG);
		b.put("photoUrl", url);
		submit(this.local, b).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_PHOTO_INVALID"));

		Map<String, Object> mine = body(uniqueName("내 사진 가게"), IN_LAT, IN_LNG);
		mine.put("photoUrl", insertUploadedImage(this.local));
		submit(this.local, mine).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("갈래는 있는 갈래 중 하나여야 한다")
	void unknownCategoryIsRejected() throws Exception {
		Map<String, Object> b = body(uniqueName("이상한 갈래"), IN_LAT, IN_LNG);
		b.put("category", "CASINO");
		submit(this.local, b).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_INVALID"));
	}

	@Test
	@DisplayName("🔴 운영자가 아니면 검토 목록을 못 연다")
	void reviewListIsAdminOnly() throws Exception {
		this.mockMvc.perform(get("/api/v1/admin/place-suggestions").with(as(this.local)))
				.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/v1/admin/place-suggestions").with(asAdmin(this.admin)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("🔴 승인하면 CURATED · source=USER 장소가 생기고, 제안한 사람에게 알림이 가고, 「현지인 추천」이 보인다")
	void approveCreatesCuratedUserPlace() throws Exception {
		String name = uniqueName("수영 숨은 횟집");
		Map<String, Object> b = body(name, IN_LAT, IN_LNG);
		b.put("openingHours", "11:00-22:00");
		b.put("phone", "051-000-0000");
		String id = idOf(submit(this.local, b).andExpect(status().isCreated()));

		this.mockMvc.perform(get("/api/v1/admin/place-suggestions").with(asAdmin(this.admin)))
				.andExpect(jsonPath("$.data.items[?(@.suggestionId == '" + id + "')].suggesterName").value("부산토박이"));

		String placeId = this.json.readTree(this.mockMvc
				.perform(post("/api/v1/admin/place-suggestions/" + id + "/approve").with(asAdmin(this.admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andReturn().getResponse().getContentAsString()).path("data").path("placeId").asString();

		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT name_ko, category, curation_status, source_type, source_id, dataset_version FROM place WHERE place_id = ?",
				UUID.fromString(placeId));
		assertThat(row.get("name_ko")).isEqualTo(name);
		assertThat(row.get("curation_status")).isEqualTo("CURATED");
		assertThat(row.get("source_type")).isEqualTo("USER");
		assertThat(row.get("source_id")).isEqualTo(id);
		assertThat(row.get("dataset_version")).isNotNull();
		assertThat(this.notifier.approvedSuggesters).containsExactly(this.local);

		this.mockMvc.perform(get("/api/v1/places/" + placeId + "/local-pick").with(as(this.local)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.suggesterName").value("부산토박이"))
				.andExpect(jsonPath("$.data.reason").value("현지인만 아는 아침 국밥집이에요. 새벽 시장 상인들이 먼저 와서 먹고 가는 곳이라 일곱 시 전에 가야 자리가 있어요"))
				.andExpect(jsonPath("$.data.openingHours").value("11:00-22:00"));

		// 두 번 승인은 안 된다
		this.mockMvc.perform(post("/api/v1/admin/place-suggestions/" + id + "/approve").with(asAdmin(this.admin)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("거절하면 place 는 안 생기고 REJECTED 로 남는다 — 알림도 없다")
	void rejectKeepsNoPlace() throws Exception {
		String name = uniqueName("거절될 곳");
		String id = idOf(submit(this.local, body(name, IN_LAT, IN_LNG)).andExpect(status().isCreated()));
		this.mockMvc.perform(post("/api/v1/admin/place-suggestions/" + id + "/reject").with(asAdmin(this.admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"이미 문을 닫았어요\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM place WHERE name_ko = ?", Integer.class, name))
				.isZero();
		assertThat(this.notifier.approvedSuggesters).isEmpty();
	}

	@Test
	@DisplayName("제안으로 생긴 장소가 아니면 local-pick 은 404")
	void localPickMissing() throws Exception {
		UUID plain = insertPlace(uniqueName("그냥 장소"), IN_LAT, IN_LNG);
		this.mockMvc.perform(get("/api/v1/places/" + plain + "/local-pick").with(as(this.local)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("사진은 선택이다 — 없어도 받는다(사용자 지시 2026-10-10)")
	void photoIsOptional() throws Exception {
		submit(this.local, body(uniqueName("사진 없는 가게"), IN_LAT, IN_LNG)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("🔴 추천 이유는 50자 이상 100자 이하")
	void reasonLength() throws Exception {
		Map<String, Object> b = body(uniqueName("짧은 이유"), IN_LAT, IN_LNG);
		b.put("reason", "가".repeat(49));
		submit(this.local, b).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PLACE_SUGGESTION_INVALID"))
				.andExpect(jsonPath("$.error.fields[0]").value("reason"));
		b.put("reason", "가".repeat(101));
		submit(this.local, b).andExpect(status().isBadRequest());
		b.put("reason", "가".repeat(100));
		submit(this.local, b).andExpect(status().isCreated());
	}

	// ── 도우미 ────────────────────────────────────────────────

	private ResultActions submit(UUID who, Map<String, Object> body) throws Exception {
		return this.mockMvc.perform(post("/api/v1/place-suggestions").with(as(who))
				.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(body)));
	}

	private String idOf(ResultActions result) throws Exception {
		return this.json.readTree(result.andReturn().getResponse().getContentAsString()).path("data")
				.path("suggestionId").asString();
	}

	private Map<String, Object> body(String name, double lat, double lng) {
		Map<String, Object> b = new java.util.HashMap<>();
		b.put("name", name);
		b.put("category", "FOOD");
		b.put("lat", lat);
		b.put("lng", lng);
		b.put("reason", "현지인만 아는 아침 국밥집이에요. 새벽 시장 상인들이 먼저 와서 먹고 가는 곳이라 일곱 시 전에 가야 자리가 있어요");
		return b;
	}

	private static String uniqueName(String base) {
		return base + " " + UUID.randomUUID().toString().substring(0, 6);
	}

	private static RequestPostProcessor as(UUID id) {
		return user(id.toString()).roles("USER");
	}

	private static RequestPostProcessor asAdmin(UUID id) {
		return user(id.toString()).roles("ADMIN");
	}

	private UUID insertUser(String displayName) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
				+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", userId, displayName, now, now);
		return userId;
	}

	private UUID insertPlace(String name, double lat, double lng) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, category, lat, lng, created_at) VALUES (?, ?, 'FOOD', ?, ?, now())",
				placeId, name, lat, lng);
		return placeId;
	}

	private String insertUploadedImage(UUID uploader) {
		String key = "story/2026/10/suggest-" + UUID.randomUUID() + ".jpg";
		String url = "/api/v1/uploads/images/" + key;
		this.jdbc.update("INSERT INTO uploaded_image (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at) "
				+ "VALUES (?, ?, ?, ?, 'image/jpeg', 10, now())", UUID.randomUUID(), uploader, key, url);
		return url;
	}
}
