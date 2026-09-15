package com.gabolle.backend.batch.presentation;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.batch.application.TasteVectorBatchReport;
import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.application.TasteVectorFoldOutcome;
import com.gabolle.backend.batch.presentation.dto.RebuildTasteVectorsRequest;
import com.gabolle.backend.batch.presentation.dto.StaleUsersResponse;
import com.gabolle.backend.batch.presentation.dto.TasteVectorBatchResponse;

import jakarta.validation.Valid;

/**
 * 취향 벡터 배치 — 기계만 부른다. MLOps Phase 1.
 *
 * <pre>
 * GET  /internal/v1/batch/taste-vectors/stale?asOf=...&amp;limit=500   접을 사람 목록
 * POST /internal/v1/batch/taste-vectors/rebuild                     넘긴 사람들을 접는다
 * </pre>
 *
 * <h2>🔴 경로에 판 번호({@code /v1/})가 붙어 있다</h2>
 *
 * 이 계약은 <b>파트 브랜치 둘에 나뉘어 산다.</b> 서버는 {@code back/dev} 에, 부르는 DAG 는
 * {@code common/dev} 에 있고, 각자 다른 사다리로 올라가 서로 다른 때에 배포된다. 그래서
 * <b>둘이 잠깐 어긋나는 시간이 반드시 생긴다.</b>
 *
 * <p>판 번호가 없으면 그 시간에 옛 DAG 가 새 서버를 부르면서 <b>조용히 다른 뜻</b>으로
 * 동작한다. 번호가 있으면 모양을 바꿀 때 {@code /v2/} 를 새로 내고 한동안 둘을 같이 두면
 * 되므로, 배포 순서를 맞추지 않아도 된다.
 *
 * <p>🔴 <b>모양을 바꾸면 번호를 올린다.</b> 칸을 더하는 것은 번호를 안 올려도 되지만,
 * <b>칸 이름을 바꾸거나 없애는 것</b>은 올린다 — 부르는 쪽이 그 이름으로 읽기 때문이다.
 *
 * <h2>🔴 인가는 이 컨트롤러가 하지 않는다</h2>
 *
 * {@code SecurityConfig} 의 {@code requestMatchers("/internal/**").hasRole("INTERNAL")} 한 줄이
 * 한다. 권한은 {@code InternalTokenAuthenticationFilter} 가 {@code X-Internal-Token} 헤더를
 * 보고 심는다. {@code @PreAuthorize} 를 안 쓰는 이유는 이 저장소가 메서드 보안을 껐기
 * 때문이다 — 붙여도 <b>조용히 무시된다</b> ({@code AdminModerationController} 의 같은 설명).
 *
 * <h2>🔴 {@code asOf} 를 서버가 정하지 않는다 — 반드시 받는다</h2>
 *
 * 기본값을 "지금" 으로 두면, 8월 1일을 채우는 backfill 이 9월 데이터를 먹는다.
 * 그 오류는 값이 있기는 하므로 DB 도 검사도 안 잡는다. 그래서 <b>없으면 400</b> 이다 —
 * 배치가 시끄럽게 멈추는 것이 조용히 틀린 값을 적는 것보다 낫다.
 *
 * <h2>🔴 응답에 {@code ApiResponse} 봉투를 씌우지 않는다</h2>
 *
 * 사람이 쓰는 API 는 {@code data}/{@code error}/{@code meta} 봉투를 쓰고, 그 봉투는
 * {@code X-Request-Id} 를 요구한다. 이 경로는 우리 프로세스 둘 사이의 통로라 봉투를 씌우면
 * DAG 코드가 매번 {@code data} 를 벗기는 일만 늘어난다. 대신 <b>같은 이유로</b> 이 경로는
 * 공개 API 계약이 아니다 — 프론트가 이것을 부르면 안 된다.
 */
@RestController
@RequestMapping("/internal/v1/batch/taste-vectors")
@Profile({ "db", "dev" })
public class InternalTasteVectorController {

	/**
	 * {@code asOf} 가 "지금" 보다 이만큼까지는 앞서도 받는다 — S15P21E201-772 후속.
	 *
	 * <p>🔴 <b>0 이 아닌 이유는 시계가 둘이기 때문이다.</b> {@code asOf} 를 만드는 것은 Airflow
	 * 컨테이너이고 검사하는 것은 이 서버라, 둘의 시계가 몇 초 어긋나는 것만으로 정상적인 실행이
	 * 막히면 안 된다. 손으로 돌린 DAG 는 {@code data_interval_end} 가 거의 "지금" 이라 특히 그렇다.
	 *
	 * <p>🔴 <b>한 시간이면 넉넉하고, 막으려는 사고와는 자릿수가 다르다.</b> 이 검사가 잡으려는
	 * 것은 {@code -e 2030-01-01} 같은 실수이지 몇 초의 오차가 아니다.
	 */
	private static final Duration MAX_FUTURE_SKEW = Duration.ofHours(1);

	private final TasteVectorBatchService batchService;

	private final Clock clock;

	public InternalTasteVectorController(TasteVectorBatchService batchService, Clock clock) {
		this.batchService = batchService;
		this.clock = clock;
	}

	/**
	 * 미래의 {@code asOf} 를 막는다 — S15P21E201-772 후속.
	 *
	 * <h2>🔴 표시는 앞으로만 간다. 되돌리는 문이 없다</h2>
	 *
	 * {@code fold} 는 {@code user_taste_vector.observed_until} 을 {@code asOf} 로 밀어 놓고,
	 * 다음부터는 {@code watermark} 가 {@code asOf} 보다 뒤가 아니면 {@code UNCHANGED} 로 넘긴다.
	 * {@code observed_until} 에 CHECK 도 없다. 그래서 <b>한 번 2999년을 적으면 그 사람은 영영
	 * 안 접힌다</b> — 손으로 {@code UPDATE} 하는 것 말고는 되돌릴 방법이 없다.
	 *
	 * <p>더 나쁜 것은 <b>그게 초록으로 보인다</b>는 점이다. {@code staleUsers} 가 빈 목록을 내고,
	 * DAG 는 {@code AirflowSkipException} 으로 <b>실패가 아니라 건너뜀</b>이 되며, 로그에는
	 * "표시가 뒤처진 사람이 없다" 만 남는다. 설문도 행동도 그날부터 벡터에 안 들어가는데
	 * 아무 데도 빨간 것이 없다.
	 *
	 * <h2>🔴 이것은 "서버가 asOf 를 정한다" 가 아니다</h2>
	 *
	 * 이 클래스 머리말과 {@code TasteVectorFoldService} 가 <i>"서버가 시각을 정하지 않는다"</i> 고
	 * 못 박아 둔 것은 backfill 때문이고, 그 판단은 그대로다. 여기서 하는 것은 <b>고르는 것이
	 * 아니라 말이 안 되는 값을 거절하는 것</b>이다. 과거는 얼마든지 받는다 — backfill 은 전부
	 * 과거이므로 이 검사에 한 번도 안 걸린다.
	 *
	 * <h2>🔴 봉투 없이 400 을 낸다</h2>
	 *
	 * 이 경로는 {@code ApiResponse} 봉투를 안 쓴다(위 머리말). 그래서 항목별 오류 코드를 만드는
	 * {@code @RestControllerAdvice} 를 하나 더 두는 대신 {@link ResponseStatusException} 으로
	 * 그대로 400 을 낸다 — 부르는 쪽이 사람이 아니라 DAG 이고, DAG 이 보는 것은 상태 코드와
	 * 본문 한 줄이다.
	 */
	private void requireSaneAsOf(OffsetDateTime asOf) {
		OffsetDateTime limit = OffsetDateTime.now(this.clock).plus(MAX_FUTURE_SKEW);
		if (asOf.isAfter(limit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"asOf 가 미래다: " + asOf + " (허용 상한 " + limit + "). 표시는 되돌릴 수 없어서 "
							+ "미래 시각을 한 번 적으면 그 사람은 영영 안 접힌다");
		}
	}

	/**
	 * 표시가 뒤처진 사람 목록.
	 *
	 * @param asOf 이 시각까지 도착한 것을 본다. ISO-8601 (예 {@code 2026-09-07T15:00:00Z})
	 * @param limit 최대 몇 명. {@link TasteVectorBatchService#MAX_BATCH} 로 잘린다
	 */
	@GetMapping("/stale")
	public StaleUsersResponse stale(@RequestParam("asOf") OffsetDateTime asOf,
			@RequestParam(name = "limit", defaultValue = "500") int limit) {
		// 🔴 읽기만 하는 경로라 표시를 망가뜨리지는 않는다. 그래도 같이 막는다 — 미래 asOf 로
		//    부르면 "전부 뒤처졌다" 는 목록이 나오고, 부르는 쪽은 그것을 그대로 rebuild 에 넘긴다.
		//    잘못된 값은 쓰는 자리가 아니라 들어오는 자리에서 끊는 편이 낫다.
		requireSaneAsOf(asOf);
		TasteVectorBatchService.StalePage page = this.batchService.staleUsers(asOf, limit);
		return new StaleUsersResponse(asOf, page.userIds().size(), page.truncated(), page.limit(), page.userIds());
	}

	/** 넘긴 사람들을 접는다. 한 사람의 실패는 배치를 멈추지 않는다. */
	@PostMapping("/rebuild")
	public TasteVectorBatchResponse rebuild(@Valid @RequestBody RebuildTasteVectorsRequest request) {
		// 🔴 표시를 실제로 미는 자리다. 여기를 지나면 되돌릴 방법이 없다.
		requireSaneAsOf(request.asOf());
		TasteVectorBatchReport report = this.batchService.rebuild(request.userIds(), request.asOf());

		return new TasteVectorBatchResponse(report.asOf(), report.processed(),
				report.count(TasteVectorFoldOutcome.Action.REBUILT),
				report.count(TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED),
				report.count(TasteVectorFoldOutcome.Action.UNCHANGED),
				report.count(TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD), report.failed(), report.failedUserIds(),
				report.failures());
	}
}
