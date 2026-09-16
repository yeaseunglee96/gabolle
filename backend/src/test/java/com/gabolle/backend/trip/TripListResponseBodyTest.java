package com.gabolle.backend.trip;

import java.util.Optional;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;

/**
 * {@code GET /api/v1/trips} 가 실제로 어떤 <b>본문</b>을 내보내는지 고정한다 — S15P21E201-764.
 *
 * <p>왜 이 파일이 따로 있나. {@code TripListIntegrationTest} 는
 * {@code queryService.list(...)} 를 직접 부른다. 그래서 <b>누가 어떤 여행을 보는가</b> 는
 * 재지만 <b>그 결과가 JSON 으로 어떻게 나가는가</b> 는 한 줄도 안 잰다. 그 빈자리로
 * 실제 사고가 났다 — 앱이 목록을 {@code {"trips": [...]}} 객체로 읽고 있었는데 서버는
 * 배열을 그대로 내보내고 있었고, 앱에서 목록이 {@code undefined} 가 되어 "내 여행" 탭이
 * 통째로 죽었다(S15P21E201-762). 양쪽 다 자기 테스트는 초록이었다.
 *
 * <p>그래서 여기서 재는 것은 계약 한 가지다 — <b>봉투의 {@code data} 자리에 배열이
 * 그대로 온다.</b> 이 저장소의 다른 목록 응답들과 같은 모양이고(일정 판 목록·행사 목록),
 * 화면 쪽은 그 전제로 파싱한다. 누군가 응답을 객체로 감싸면 이 검사가 빨개진다.
 *
 * <p>Spring 컨텍스트도 DB 도 안 띄운다. 재는 것이 <b>직렬화 결과</b>뿐이라
 * {@link MockMvcBuilders#standaloneSetup} 으로 컨트롤러만 올리는 것으로 충분하고,
 * 그 편이 이 잡을 느리게 만들지 않는다. 목록 질의 자체(정렬·상한·지운 여행 제외)는
 * 진짜 PostgreSQL 을 쓰는 {@code TripListIntegrationTest} 가 계속 맡는다.
 */
class TripListResponseBodyTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InMemoryTripRepository repository = new InMemoryTripRepository();
		Clock clock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);
		TripController controller = new TripController(
				new TripCreationService(repository, clock, new PreferenceDefaultsService(repository, clock), ConsentGuards.granting(), Optional.empty(), Optional.empty()),
				new TripQueryService(repository),
				new TripDeletionService(repository, clock));
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new TripExceptionHandler())
				.build();
	}

	/** 헤더가 아니라 인증 principal 로 사용자를 정한다 — S15P21E201-610. */
	private static Authentication asUser(String userId) {
		return new TestingAuthenticationToken(userId, null);
	}

	@Test
	@DisplayName("여행이 하나도 없으면 data 가 빈 배열이다 — 오류도 null 도 아니다")
	void emptyListIsAnEmptyArrayNotAnErrorAndNotNull() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips").principal(asUser(UUID.randomUUID().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.length()").value(0))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	/**
	 * 🔴 이 검사가 이 파일의 핵심이다. {@code $.data} 가 배열이 아니라 객체가 되는 순간
	 * 화면이 죽으므로, 배열이라는 것과 목록 줄의 칸 이름을 함께 못 박는다.
	 */
	@Test
	@DisplayName("여행이 있으면 data 가 배열이고, 한 줄에 화면이 쓰는 칸 아홉이 그 이름 그대로 있다")
	void listRowsCarryTheFieldNamesTheAppReads() throws Exception {
		String userId = UUID.randomUUID().toString();
		String created = this.mockMvc.perform(post("/api/v1/trips")
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asUser(userId))
						.content("""
								{
								  "startDate": "2026-09-20",
								  "finishDate": "2026-09-22",
								  "partySize": 2,
								  "originLat": 35.1587,
								  "originLng": 129.1604
								}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String tripId = created.split("\"tripId\":\"")[1].split("\"")[0];

		this.mockMvc.perform(get("/api/v1/trips").principal(asUser(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].tripId").value(tripId))
				.andExpect(jsonPath("$.data[0].startDate").value("2026-09-20"))
				.andExpect(jsonPath("$.data[0].endDate").value("2026-09-22"))
				.andExpect(jsonPath("$.data[0].dayCount").value(3))
				.andExpect(jsonPath("$.data[0].partySize").value(2))
				// 🔴 갓 만든 여행은 PLANNING 이다(조건만 있고 일정이 없는 상태). 값 자체를 여기
				// 박아 두는 이유는 이름이 화면과 어긋난 적이 있어서다 — 앱의 TripStatus 가
				// ACTIVE·ARCHIVED·CANCELLED 로 적혀 있었는데 서버가 쓰는 이름과 하나도 안 겹쳤다.
				// 화면이 아직 이 값으로 분기하지 않아 드러나지 않았을 뿐이다(S15P21E201-762 에서 함께 고쳤다).
				.andExpect(jsonPath("$.data[0].status").value("PLANNING"))
				.andExpect(jsonPath("$.data[0].role").value("OWNER"))
				.andExpect(jsonPath("$.data[0].createdAt").exists())
				.andExpect(jsonPath("$.data[0].updatedAt").exists())
				// 목록에 없는 것도 계약이다 — 앱이 제목과 방문지 수를 일정에서 채우기로 했다.
				.andExpect(jsonPath("$.data[0].title").doesNotExist())
				.andExpect(jsonPath("$.data[0].placeCount").doesNotExist());
	}

	@Test
	@DisplayName("목록을 객체로 감싸지 않는다 — data 아래에 trips 같은 칸이 없다")
	void listIsNotWrappedInAnObject() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips").principal(asUser(UUID.randomUUID().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data").value(Matchers.empty()))
				.andExpect(jsonPath("$.data.trips").doesNotExist());
	}
}
