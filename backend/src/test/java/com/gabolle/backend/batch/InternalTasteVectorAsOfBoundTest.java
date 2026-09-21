package com.gabolle.backend.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.batch.application.TasteVectorBatchReport;
import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.presentation.InternalTasteVectorController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미래의 {@code asOf} 를 받지 않는다.
 *
 * <p>표시({@code user_taste_vector.observed_until})는 앞으로만 간다. {@code fold} 는 표시가
 * {@code asOf} 보다 뒤가 아니면 아무것도 안 하고, 되돌리는 API 도 CHECK 도 없다. 그래서
 * {@code asOf} 를 한 번 먼 미래로 적으면 그 사람의 설문과 행동은 영영 안 접히고, 그 상태가
 * 초록으로 보인다 — {@code staleUsers} 가 빈 목록을 내고 DAG 는 건너뜀으로 끝난다.
 *
 * <p>막는 자리가 컨트롤러라서 배치도 DB 도 타지 않는다. 시계는 고정한 {@link Clock} 을
 * 넣고, 배치는 가짜를 넣어 호출이 아예 안 갔는지까지 본다.
 */
class InternalTasteVectorAsOfBoundTest {

	/** 이 검사의 "지금". 실제 시계를 안 쓰므로 언제 돌려도 결과가 같다. */
	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00Z");

	private final TasteVectorBatchService batchService = mock(TasteVectorBatchService.class);

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new InternalTasteVectorController(this.batchService,
					Clock.fixed(NOW, ZoneOffset.UTC)))
			.build();

	/**
	 * 막히는 검사에서도 배치를 정상 동작하게 세워 둔다. 세워 두지 않으면 가짜가
	 * {@code null} 을 돌려주고, 가드를 지웠을 때 400 대신 {@code NullPointerException} 으로
	 * 터져 「막았다」와 「그냥 깨졌다」가 구분이 안 된다.
	 */
	@BeforeEach
	void batchServiceWorksNormally() {
		when(this.batchService.staleUsers(any(), anyInt()))
				.thenReturn(new TasteVectorBatchService.StalePage(List.of(), false, 500));
		when(this.batchService.rebuild(any(), any()))
				.thenReturn(new TasteVectorBatchReport(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
						Map.of(), List.of(), List.of()));
	}

	@Test
	@DisplayName("🔴 먼 미래의 asOf 는 400 이고, 배치는 아예 안 돈다 — 한 번 밀린 표시는 못 되돌린다")
	void farFutureAsOfIsRejectedAndNeverReachesTheBatch() throws Exception {
		this.mockMvc.perform(post("/internal/v1/batch/taste-vectors/rebuild")
						.contentType("application/json")
						.content("{\"userIds\":[\"" + UUID.randomUUID() + "\"],"
								+ "\"asOf\":\"2999-01-01T00:00:00Z\"}"))
				.andExpect(status().isBadRequest());

		// 상태 코드만 보면 모자란다. 배치가 이미 돌아서 표시를 밀어 놓고 400 을 냈다면 막은
		// 것이 아니다.
		verify(this.batchService, never()).rebuild(any(), any());
	}

	@Test
	@DisplayName("🔴 stale 도 같이 막는다 — 미래 asOf 면 '전부 뒤처졌다' 는 목록이 나온다")
	void farFutureAsOfIsRejectedOnStaleToo() throws Exception {
		this.mockMvc.perform(get("/internal/v1/batch/taste-vectors/stale")
						.param("asOf", "2999-01-01T00:00:00Z"))
				.andExpect(status().isBadRequest());

		verify(this.batchService, never()).staleUsers(any(), anyInt());
	}

	@Test
	@DisplayName("과거 asOf 는 그대로 받는다 — backfill 은 전부 과거라 이 검사에 안 걸린다")
	void pastAsOfStillWorks() throws Exception {
		this.mockMvc.perform(get("/internal/v1/batch/taste-vectors/stale")
						.param("asOf", "2026-08-01T00:00:00Z"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("시계 어긋남만큼은 봐준다 — 몇 분 앞선 asOf 로 정상 실행이 막히면 안 된다")
	void smallClockSkewIsTolerated() throws Exception {
		// asOf 를 만드는 것은 Airflow 컨테이너, 재는 것은 이 서버다. 손으로 돌린 DAG 는
		// data_interval_end 가 거의 「지금」이라 몇 초 차이로 막히면 안 된다.
		OffsetDateTime slightlyAhead = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(5);

		this.mockMvc.perform(get("/internal/v1/batch/taste-vectors/stale")
						.param("asOf", slightlyAhead.toString()))
				.andExpect(status().isOk());
	}
}
