package com.gabolle.backend.trip;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripTitleService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.presentation.TripTitleController;
import com.gabolle.backend.trip.presentation.TripTitleExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행에 이름을 붙인다 — S15P21E201-1023.
 *
 * <p>고치려는 것: 여행 카드의 제목 자리가 <b>날짜뿐</b>이라 같은 날짜로 두 번 계획하면
 * 두 카드가 글자 하나 다르지 않았다. 「다시 짜 보기」가 기본 동작인 서비스에서 그건 드문
 * 일이 아니다.
 */
class TripTitleTest {

	private static final Instant NOW = Instant.parse("2026-09-16T03:00:00Z");

	private static final String TRIP_ID = "trip_1";

	private final UUID requester = UUID.randomUUID();

	private static Trip trip() {
		return new Trip(TRIP_ID, UUID.randomUUID().toString(),
				LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20),
				null, null, null, 2, null, "Asia/Seoul",
				Instant.parse("2026-09-01T00:00:00Z"));
	}

	// ── 규칙은 도메인에 있다 ────────────────────────────────────────────────

	@Nested
	@DisplayName("이름 규칙")
	class Rules {

		@Test
		@DisplayName("앞뒤 공백을 떼고 저장한다")
		void trims() {
			Trip trip = trip();
			trip.rename("  9월 부산 혼자  ", NOW);
			assertThat(trip.title()).isEqualTo("9월 부산 혼자");
			assertThat(trip.updatedAt()).isEqualTo(NOW);
		}

		/**
		 * 🔴 「빈 이름」과 「이름 없음」을 가르지 않는다. 화면에서 구분할 수 없는 같은
		 * 것이라, 둘을 두면 화면이 두 가지를 다 검사해야 하고 언젠가 한쪽을 빠뜨린다.
		 */
		@Test
		@DisplayName("빈 이름을 보내는 것이 곧 이름을 지우는 것이다")
		void blankClearsTheName() {
			Trip trip = trip();
			trip.rename("잠깐 붙인 이름", NOW);

			trip.rename("   ", NOW);

			assertThat(trip.title()).isNull();
		}

		/**
		 * 🔴 이름은 <b>한 줄</b>이다. 줄바꿈이 들어가면 카드가 두 줄로 밀려 목록 전체가
		 * 어긋난다. 나중에 이 자리에 모델이 지어낸 이름이 들어오므로(-1025) 칸 자체가
		 * 안 받는 편이 확실하다.
		 */
		@Test
		@DisplayName("🔴 줄바꿈과 제어문자는 거부한다")
		void rejectsControlCharacters() {
			Trip trip = trip();
			assertThatThrownBy(() -> trip.rename("부산\n여행", NOW))
					.isInstanceOf(IllegalArgumentException.class);
			assertThat(trip.title()).isNull();
		}

		@Test
		@DisplayName("60자를 넘으면 거부하고, 몇 자인지 말한다")
		void rejectsTooLong() {
			Trip trip = trip();
			String tooLong = "가".repeat(Trip.TITLE_MAX_LENGTH + 1);

			assertThatThrownBy(() -> trip.rename(tooLong, NOW))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("61자");
		}

		/**
		 * 🔴 이모지 하나는 자바에서 두 칸을 차지하지만 DB {@code varchar(60)} 은 한 글자로
		 * 센다. 자바 길이로 재면 <b>DB 가 받아 줄 이름을 서버가 먼저 거부한다.</b>
		 */
		@Test
		@DisplayName("🔴 이모지는 한 글자로 센다 — DB 가 세는 방식과 맞춘다")
		void countsCodePointsNotJavaChars() {
			Trip trip = trip();
			String sixtyEmoji = "🌊".repeat(Trip.TITLE_MAX_LENGTH);

			trip.rename(sixtyEmoji, NOW);

			assertThat(trip.title()).isEqualTo(sixtyEmoji);
		}
	}

	// ── 누가 바꿀 수 있나 ───────────────────────────────────────────────────

	@Nested
	@DisplayName("권한")
	class Permission {

		private TripQueryService queryService;
		private TripRepository repository;
		private TripTitleService service;

		@BeforeEach
		void setUp() {
			this.queryService = mock(TripQueryService.class);
			this.repository = mock(TripRepository.class);
			this.service = new TripTitleService(this.queryService, this.repository,
					Clock.fixed(NOW, ZoneOffset.UTC));
		}

		private void requesterIs(TripMember.Role role, Trip trip) {
			when(this.queryService.get(eq(TRIP_ID), any()))
					.thenReturn(new TripQueryService.View(trip, List.of(), null, role));
		}

		@Test
		@DisplayName("만든 사람은 이름을 바꿀 수 있다")
		void ownerCanRename() {
			Trip trip = trip();
			requesterIs(TripMember.Role.OWNER, trip);

			Trip renamed = this.service.rename(TRIP_ID, requester.toString(), "9월 부산 혼자");

			assertThat(renamed.title()).isEqualTo("9월 부산 혼자");
			verify(this.repository).updateTitle(trip);
		}

		@Test
		@DisplayName("함께 짜는 동행자(EDITOR)도 바꿀 수 있다")
		void editorCanRename() {
			Trip trip = trip();
			requesterIs(TripMember.Role.EDITOR, trip);

			this.service.rename(TRIP_ID, requester.toString(), "둘이 가는 부산");

			assertThat(trip.title()).isEqualTo("둘이 가는 부산");
		}

		/**
		 * 🔴 보기만 하는 동행자가 이름을 바꾸면, <b>만든 사람의 목록에서 자기 여행이 다른
		 * 이름으로 보인다.</b> 초대 발급이 이미 같은 선을 긋고 있다.
		 */
		@Test
		@DisplayName("🔴 보기 전용 동행자는 바꿀 수 없고, 저장까지 가지 않는다")
		void viewerCannotRename() {
			Trip trip = trip();
			requesterIs(TripMember.Role.VIEWER, trip);

			assertThatThrownBy(() -> this.service.rename(TRIP_ID, requester.toString(), "내가 바꾼 이름"))
					.isInstanceOf(TripTitleService.TitleChangeForbiddenException.class);

			assertThat(trip.title()).isNull();
			verify(this.repository, never()).updateTitle(any());
		}

		/**
		 * 🔴 참여자가 아닌 사람에게는 <b>존재를 감춘 404</b> 가 간다. 그 판정은
		 * {@code TripQueryService} 한 곳에만 있고 이 서비스는 그것을 그대로 통과시킨다 —
		 * 여기서 다시 판정하면 같은 규칙이 두 곳에 생기고 언젠가 한쪽만 바뀐다.
		 */
		@Test
		@DisplayName("🔴 참여자가 아니면 여행의 존재부터 감춘다")
		void nonMemberSeesNotFound() {
			when(this.queryService.get(eq(TRIP_ID), any()))
					.thenThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

			assertThatThrownBy(() -> this.service.rename(TRIP_ID, requester.toString(), "남의 여행"))
					.isInstanceOf(TripQueryService.TripNotFoundException.class);

			verify(this.repository, never()).updateTitle(any());
		}
	}

	// ── HTTP 로 어떻게 나가나 ───────────────────────────────────────────────

	@Nested
	@DisplayName("응답")
	class Http {

		private TripTitleService service;
		private MockMvc mockMvc;

		@BeforeEach
		void setUp() {
			this.service = mock(TripTitleService.class);
			this.mockMvc = MockMvcBuilders.standaloneSetup(new TripTitleController(this.service))
					.setControllerAdvice(new TripTitleExceptionHandler())
					.build();
		}

		private Authentication principal() {
			return new UsernamePasswordAuthenticationToken(requester.toString(), null, List.of());
		}

		private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
			return this.mockMvc.perform(put("/api/v1/trips/{tripId}/title", TRIP_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body)
					.principal(principal()));
		}

		@Test
		@DisplayName("이름을 바꾸면 바뀐 이름과 손댄 시각이 돌아온다")
		void returnsNewTitle() throws Exception {
			Trip trip = trip();
			trip.rename("9월 부산 혼자", NOW);
			when(this.service.rename(eq(TRIP_ID), any(), eq("9월 부산 혼자"))).thenReturn(trip);

			send("{\"title\":\"9월 부산 혼자\"}")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.title").value("9월 부산 혼자"))
					.andExpect(jsonPath("$.data.tripId").value(TRIP_ID));
		}

		/** 🔴 이름이 지워진 것은 {@code null} 이다. 날짜 문자열을 서버가 대신 채우지 않는다. */
		@Test
		@DisplayName("🔴 이름을 지우면 null 이 간다 — 서버가 날짜로 대신 채우지 않는다")
		void clearedTitleIsNull() throws Exception {
			Trip trip = trip();
			when(this.service.rename(eq(TRIP_ID), any(), eq(""))).thenReturn(trip);

			send("{\"title\":\"\"}")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.title").doesNotExist());
		}

		@Test
		@DisplayName("보기 전용 동행자는 403 — 여행이 없다고 하지 않는다")
		void viewerGetsForbidden() throws Exception {
			when(this.service.rename(eq(TRIP_ID), any(), any()))
					.thenThrow(new TripTitleService.TitleChangeForbiddenException(TRIP_ID));

			send("{\"title\":\"내가 바꾼 이름\"}")
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("TRIP_FORBIDDEN"));
		}

		@Test
		@DisplayName("참여자가 아니면 404 — 존재를 감춘다")
		void nonMemberGetsNotFound() throws Exception {
			when(this.service.rename(eq(TRIP_ID), any(), any()))
					.thenThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

			send("{\"title\":\"남의 여행\"}")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
		}

		@Test
		@DisplayName("너무 긴 이름은 400 이고, 몇 자인지 말해 준다")
		void tooLongGetsBadRequest() throws Exception {
			when(this.service.rename(eq(TRIP_ID), any(), any()))
					.thenThrow(new IllegalArgumentException("여행 이름은 60자를 넘을 수 없다: 74자"));

			send("{\"title\":\"길다\"}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("TRIP_TITLE_INVALID"))
					.andExpect(jsonPath("$.error.message").value(
							org.hamcrest.Matchers.containsString("74자")));
		}
	}
}
